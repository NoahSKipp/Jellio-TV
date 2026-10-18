package com.jellio.tv.data

import com.jellio.tv.data.model.AchievementsDto
import com.jellio.tv.data.model.AuthenticateByNameRequest
import com.jellio.tv.data.model.AvatarPresetDto
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.data.model.ClientConfigDto
import com.jellio.tv.data.model.FeedEntryDto
import com.jellio.tv.data.model.ForgotPasswordPinRequest
import com.jellio.tv.data.model.ForgotPasswordRequest
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.ProfileDto
import com.jellio.tv.data.model.RealWatchRequest
import com.jellio.tv.data.model.ReportDurationRequest
import com.jellio.tv.data.model.SetBioRequest
import com.jellio.tv.data.model.SetPrivacyRequest
import com.jellio.tv.data.model.SleepTimerStartRequest
import com.jellio.tv.data.model.SleepTimerStatusDto
import com.jellio.tv.data.model.TrickplayInfoDto
import com.jellio.tv.data.model.MediaStreamDto
import com.jellio.tv.data.model.PlaybackInfoRequest
import com.jellio.tv.data.model.PlaybackReportRequest
import com.jellio.tv.data.model.UpdatePasswordRequest
import com.jellio.tv.data.model.UserConfigurationDto
import com.jellio.tv.data.model.UserDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.recommend.CandidateEntry
import com.jellio.tv.data.session.RememberedUserEntry
import com.jellio.tv.data.session.RememberedUsersStore
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// BackdropImageTags is not included by default (real bug found live:
// the hero backdrop silently never loaded because this app never
// asked the server whether one even existed), same real field
// screens/home.js's own hero already knows to ask for.
private const val PREFETCH_WINDOW_MS = 8 * 60_000L
// How long a title page can be drawn from what was last seen of it, while
// a fresh copy loads behind it.
private const val PRELOAD_TTL_MS = 30 * 60_000L

// One card per title: Gelato can hand back the same film under more than one
// id, which filled a genre row with copies of one title.
internal fun distinctTitles(items: List<BaseItemDto>): List<BaseItemDto> {
    val seen = HashSet<String>()
    return items.filter { item ->
        val newId = seen.add("i:" + item.Id)
        val newTitle = seen.add("t:" + com.jellio.tv.data.recommend.titleKey(item))
        newId && newTitle
    }
}
// Overview so Home's showcase can describe a title without asking again.
private const val ITEM_FIELDS = "PrimaryImageAspectRatio,BackdropImageTags,Overview"

// Mirrors runtime/api.js's own getItemDetails(): a detail screen needs
// real metadata a plain row/grid fetch never asks for. Trickplay only for
// the player: the server looks it up per stream version, which for a
// Gelato title made opening its page far slower than on web.
internal const val DETAIL_FIELDS = "Overview,Genres,People,ProductionYear,RunTimeTicks,PremiereDate,RemoteTrailers," +
    "BackdropImageTags,OfficialRating,CommunityRating,ParentBackdropItemId,ParentBackdropImageTags"
private const val SHOWCASE_FIELDS = "Overview,Genres,ProductionYear,RunTimeTicks,PremiereDate,CommunityRating,BackdropImageTags," +
    "ParentBackdropItemId,ParentBackdropImageTags"
internal const val PLAYER_DETAIL_FIELDS = "$DETAIL_FIELDS,Trickplay"

// Two distinct real patterns, same distinction navShared.js's own
// getPrimaryNavLinks()/isAnimeCollection() draw: a real hand-made
// Anime library is only ever literally named that, but a collection
// with no Stremio provider id to fall back on (anything imported
// before Gelato started writing one, or made by hand) is matched
// more loosely.
private val ANIME_VIEW_NAME = Regex("anime", RegexOption.IGNORE_CASE)
private val ANIME_COLLECTION_NAME = Regex("anime|anilist|kitsu", RegexOption.IGNORE_CASE)
private const val ANIME_ITEM_ID_LIMIT = 500

sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(val message: String) : LoginResult
}

data class SeriesPlayTarget(val episode: BaseItemDto, val resume: Boolean)

// Real MediaSource plus the one real playback session id
// (PlaySessionId) a fresh PlaybackInfo negotiation hands back, both a
// player screen needs to build a real stream URL and report real
// progress against.
data class PlaybackTarget(
    val streamUrl: String,
    val mediaSource: MediaSourceDto,
    val playSessionId: String?,
    val startPositionTicks: Long,
    // Lets PlayerViewModel skip a full re-negotiation entirely when
    // switching audio tracks on a source already Direct Playing - see
    // switchAudioTrack()'s own header for why that real distinction
    // matters there.
    val directPlay: Boolean,
)

// A native Media3/ExoPlayer decode envelope on real Android TV
// hardware is far broader than a browser <video> element's own (HEVC,
// AC3/EAC3, MKV all commonly hardware or software decode here), so
// this real allowlist is deliberately wider than runtime/api.js's own
// canBrowserDirectPlay(), not a guess: the same real MediaSourceInfo
// fields, judged against what this real player can actually decode
// instead of what a browser tag can.
private val DIRECT_PLAY_CONTAINERS = setOf("mp4", "webm", "m4v", "mkv", "m2ts", "ts", "avi")
private val DIRECT_PLAY_VIDEO_CODECS = setOf("h264", "avc", "hevc", "h265", "vp8", "vp9", "av1", "mpeg4")
private val DIRECT_PLAY_AUDIO_CODECS = setOf("aac", "mp3", "opus", "vorbis", "flac", "ac3", "eac3", "dts", "truehd")

// Real fallback runtime/api.js's own estimateVideoBitrate() uses when
// a source reports no real bitrate of its own to negotiate a forced
// transcode against: leaving this unset let the server fall back to
// its own real default, real feedback found every transcoded stream
// coming back noticeably, incorrectly low quality regardless of the
// source's own real resolution.
private const val FALLBACK_VIDEO_BITRATE = 20000000L
private const val COPY_RANGE_TYPES = "SDR,HDR10,HDR10Plus,HLG,DOVIWithHDR10,DOVIWithHLG,DOVIWithSDR,DOVIWithHDR10Plus"

// Real port of runtime/api.js's own cached()/invalidateCache(): a
// small in-memory cache keyed by request identity, TTL'd rather than
// invalidated by hand for the same real reason that file's own header
// gives, nothing here persists past a process restart anyway, same
// as the rest of this app's own in-memory state. A real Mutex per
// key, not just a plain map read/write, so two callers landing
// within the same real tick (JellioRepository is a real @Singleton,
// shared by every screen's own ViewModel) share one real in-flight
// fetch instead of firing two, same real guarantee that file's own
// header calls out caching the in-flight promise itself, not just
// the resolved value, for.
private const val CACHE_TTL_MS = 60_000L
// Real SHORT_CACHE_TTL_MS: long enough to collapse the real near-
// simultaneous duplicate requests the detail screen, the player and
// the player's own episode panel make for the same series' own
// seasons/episodes, and what a watchlist/filmography screen asks for
// on repeat visits, short enough that a mark watched/unwatched or a
// watchlist toggle reads correctly again well within the time it
// takes to navigate back and look.
private const val SHORT_CACHE_TTL_MS = 8_000L

private class TtlCache {
    private data class Entry(val value: Any?, val expiresAt: Long)
    private val entries = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> get(key: String, ttlMs: Long, fetcher: suspend () -> T): T {
        entries[key]?.let { entry -> if (System.currentTimeMillis() < entry.expiresAt) return entry.value as T }
        val lock = locks.getOrPut(key) { Mutex() }
        return lock.withLock {
            entries[key]?.let { entry -> if (System.currentTimeMillis() < entry.expiresAt) return@withLock entry.value as T }
            val value = fetcher()
            entries[key] = Entry(value, System.currentTimeMillis() + ttlMs)
            value
        }
    }

    fun invalidate(key: String) {
        entries.remove(key)
    }

    fun clear() {
        entries.clear()
    }
}

// The one real place both auth and every other Jellyfin call go
// through, mirroring runtime/auth.js and runtime/api.js's own
// combined real job on the web side.
@Singleton
class JellioRepository @Inject constructor(
    private val api: JellyfinApi,
    private val sessionManager: SessionManager,
    private val rememberedUsersStore: RememberedUsersStore,
) {
    val sessionFlow: Flow<Session?> = sessionManager.sessionFlow
    private val cache = TtlCache()

    // Own scope rather than viewModelScope: prefetchStreams below is
    // called from a poster's own onFocusChanged, fired from five
    // different screens (Home/Library/Watchlist/Service rows, Search's
    // own grid), each with its own ViewModel and its own lifetime. A
    // request already in flight when a reader navigates away should
    // still finish and warm Gelato's own cache rather than being
    // cancelled with the screen that happened to trigger it, same real
    // reasoning this @Singleton's own clearCache() above already
    // applies to living longer than any one screen.
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Stream lookups: reused across screens (SourceCache), one request per
    // title at a time, and prefetches at most two at once, once per title
    // every 8 minutes.
    private val sourceCache = com.jellio.tv.data.streams.SourceCache<MediaSourceDto>()
    private val sourcesInFlight = ConcurrentHashMap<String, Deferred<List<MediaSourceDto>>>()
    private val prefetchedAt = ConcurrentHashMap<String, Long>()
    private val prefetchSlots = java.util.concurrent.Semaphore(2)

    // Real port of runtime/api.js's own clearCache(): every real cache
    // entry keyed off the previously signed in user (items, details,
    // seasons/episodes, the user object itself, ...) is still real,
    // still fresh data for that user, just the wrong one the moment
    // this same real @Singleton switches to a different real account
    // without a real process restart. Called from every real place
    // sessionManager.saveSession() below actually changes which user is
    // signed in (connectAndLogin, quickSignIn) plus logout() itself, so
    // nothing cached under the outgoing account can leak into the next
    // one that signs in during this same real process.
    fun clearCache() {
        cache.clear()
        preloaded.clear()
        recentLists.clear()
        sourceCache.clear()
        prefetchedAt.clear()
    }

    // The one real server address a fresh install has never asked for
    // yet: null only before the very first successful connectAndLogin()
    // this device ever makes, since SessionManager.clearSession() keeps
    // it across a sign out the same real way runtime/auth.js's own
    // clearSession() leaves SERVER_ADDRESS_KEY alone. LoginViewModel's
    // own start() reads this to decide whether there is a real server
    // to show a profile picker for at all, or whether this is a true
    // first run.
    suspend fun knownServerAddress(): String? = sessionManager.serverAddress()

    // An address typed without http:// or https:// is tried both ways (the
    // likelier one first) and the first that answers as a Jellyfin server
    // wins; the failure names every address tried.
    suspend fun resolveServerAddress(input: String): Result<String> {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Enter your server address"))
        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        val first = com.jellio.tv.di.normalizeServerAddress(trimmed)
        val candidates = if (hasScheme) {
            listOf(first)
        } else if (first.startsWith("https://")) {
            listOf(first, "http://" + first.removePrefix("https://"))
        } else {
            listOf(first, "https://" + first.removePrefix("http://"))
        }
        for (candidate in candidates) {
            sessionManager.saveServerAddress(candidate)
            if (runCatching { api.getPublicSystemInfo() }.isSuccess) return Result.success(candidate)
        }
        sessionManager.saveServerAddress(first)
        val tried = candidates.joinToString(" and ")
        return Result.failure(java.io.IOException("Couldn't reach a Jellyfin server. Tried $tried."))
    }

    suspend fun connectAndLogin(serverAddress: String, username: String, password: String): LoginResult {
        val normalized = resolveServerAddress(serverAddress).getOrElse {
            return LoginResult.Failure(it.message ?: "Could not reach that server")
        }
        return try {
            // NetworkModule.kt's own auth interceptor attaches this
            // request's own real Authorization header itself now (device
            // metadata, no session token to fold in yet at this point).
            val result = api.authenticateByName(AuthenticateByNameRequest(username, password))
            clearCache()
            sessionManager.saveSession(normalized, result.AccessToken, result.User.Id, result.User.Name)
            rememberUser(normalized, result.User.Id, result.AccessToken, result.User.Name, result.User.PrimaryImageTag)
            LoginResult.Success
        } catch (err: Exception) {
            LoginResult.Failure(err.message ?: "Could not reach that server")
        }
    }

    // Real port of runtime/auth.js's own {userId: entry} store: written
    // on every real successful sign in (setSession()'s own real
    // rememberUser() call), read back by LoginScreen.kt's own "Who's
    // watching?" grid the next time this device opens the login screen
    // for the same real server.
    suspend fun getRememberedUsers(serverAddress: String): Map<String, RememberedUserEntry> =
        rememberedUsersStore.getRememberedUsers(com.jellio.tv.di.normalizeServerAddress(serverAddress))

    suspend fun forgetRememberedUser(serverAddress: String, userId: String) {
        rememberedUsersStore.forgetUser(com.jellio.tv.di.normalizeServerAddress(serverAddress), userId)
    }

    private suspend fun rememberUser(serverAddress: String, userId: String, accessToken: String, name: String, primaryImageTag: String?) {
        rememberedUsersStore.rememberUser(
            com.jellio.tv.di.normalizeServerAddress(serverAddress),
            userId,
            RememberedUserEntry(accessToken, name, primaryImageTag, System.currentTimeMillis()),
        )
    }

    // Real port of runtime/auth.js's own getPublicUsers(): unauthenticated,
    // a real admin's own "Display this user on the login screen" toggle
    // already enforced server side. Throws rather than swallowing a
    // real failure to an empty list now: LoginViewModel's own
    // loadProfiles() needs to tell "the server said zero users are
    // public" apart from "this request never actually reached the
    // server" to prune a remembered profile the server no longer
    // vouches for without also wiping every remembered profile out
    // over a server that is just briefly unreachable, same real
    // distinction runtime/auth.js's own getPublicUsers() now draws.
    suspend fun getPublicUsers(serverAddress: String): List<UserDto> {
        val normalized = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        sessionManager.saveServerAddress(normalized)
        return api.getPublicUsers()
    }

    // Real port of runtime/auth.js's own quickSignIn(): spends one real
    // GET on the remembered token before ever trusting it, dropping
    // that remembered entry the same real way on a real 401/expired
    // token rather than silently committing a session the very next
    // request would fail.
    suspend fun quickSignIn(serverAddress: String, userId: String): LoginResult {
        val normalized = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        val entry = rememberedUsersStore.getRememberedUsers(normalized)[userId]
            ?: return LoginResult.Failure("No remembered sign-in for this profile")
        sessionManager.saveServerAddress(normalized)
        return try {
            val authorization = com.jellio.tv.data.network.buildEmbyAuthorizationHeader(
                sessionManager.getCachedDeviceId(),
                com.jellio.tv.data.network.APP_VERSION,
                entry.accessToken,
            )
            val user = api.getUserWithToken(entry.accessToken, userId, authorization)
            clearCache()
            sessionManager.saveSession(normalized, entry.accessToken, user.Id, user.Name)
            rememberUser(normalized, user.Id, entry.accessToken, user.Name, user.PrimaryImageTag)
            LoginResult.Success
        } catch (err: retrofit2.HttpException) {
            // Only a token the server actually rejects is forgotten.
            if (err.code() == 401 || err.code() == 403) {
                rememberedUsersStore.forgetUser(normalized, userId)
                LoginResult.Failure("That saved sign-in no longer works. Sign in again.")
            } else {
                LoginResult.Failure("Could not switch to that profile right now.")
            }
        } catch (err: Exception) {
            LoginResult.Failure("Could not reach the server. Try again.")
        }
    }

    fun userImageUrl(serverAddress: String, userId: String, tag: String?, maxWidth: Int = 300, accessToken: String? = null): String {
        val cleanServer = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        val base = "$cleanServer/Users/$userId/Images/Primary?maxWidth=$maxWidth"
        val withTag = if (!tag.isNullOrEmpty()) "$base&tag=$tag" else base
        return if (!accessToken.isNullOrEmpty()) "$withTag&ApiKey=$accessToken&api_key=$accessToken" else withTag
    }

    // Real Controllers/ProfileBannerController.cs's own GET {userId}:
    // cache-busted with a real timestamp query param, same real reason
    // screens/profile.js's own buildBanner() appends '&t=' +
    // Date.now() (a fresh upload replacing the file at this same real
    // path otherwise keeps serving whatever Coil already cached for
    // it). That controller carries [Authorize], same as avatarPresetUrl's
    // own real Jellio/avatars endpoint just above, so this needs the
    // exact same ApiKey query param that one already sends: never
    // loaded at all on device without it, Coil's own image request
    // getting back a plain 401 no <img> tag / browser cookie session
    // ever hits.
    fun bannerUrl(serverAddress: String, accessToken: String, userId: String): String {
        val cleanServer = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        return "$cleanServer/Jellio/profile/banner/$userId?ApiKey=$accessToken&api_key=$accessToken&t=${System.currentTimeMillis()}"
    }

    // Real port of runtime/auth.js's own requestPasswordReset(): a real
    // failure comes back true anyway, the same real leak-prevention
    // design UserController.cs's own ForgotPassword action documents
    // (a nonexistent username gets the exact same real response a real
    // one does), so this app has nothing more specific to report than
    // whether the request itself reached the server at all.
    suspend fun requestPasswordReset(serverAddress: String, username: String): Boolean {
        sessionManager.saveServerAddress(serverAddress)
        return try {
            api.requestPasswordReset(ForgotPasswordRequest(username))
            true
        } catch (err: Exception) {
            false
        }
    }

    // Real port of screens/login.js's own forgot password flow past a
    // real Success redeem: that real call clears the account's own
    // password server side rather than setting the one the reader
    // asked for (real Jellyfin behaviour, not a choice made here), so
    // this signs back in with a blank one immediately after, then
    // calls updatePassword with the real new one, the same two real
    // calls that file's own header comment documents.
    suspend fun redeemPasswordReset(serverAddress: String, username: String, pin: String, newPassword: String): LoginResult {
        return try {
            val redeemed = api.redeemPasswordResetPin(ForgotPasswordPinRequest(pin))
            if (!redeemed.Success) {
                return LoginResult.Failure("That reset code is invalid or has expired.")
            }
            val loginResult = connectAndLogin(serverAddress, username, "")
            if (loginResult is LoginResult.Failure) return loginResult
            val userId = sessionManager.sessionFlow.first()?.userId
                ?: return LoginResult.Failure("Could not finish resetting your password.")
            updatePassword(userId, "", newPassword)
            LoginResult.Success
        } catch (err: Exception) {
            LoginResult.Failure("Could not reset the password. Try again.")
        }
    }

    suspend fun logout() {
        clearCache()
        sessionManager.clearSession()
    }

    suspend fun getLibraries(userId: String): List<BaseItemDto> =
        cache.get("views:$userId", CACHE_TTL_MS) { api.getUserViews(userId).Items }

    // Mirrors components/navShared.js's own real getPrimaryNavLinks():
    // Anime has no real Jellyfin library of its own (Gelato resolves
    // one global SeriesPath for every series import, so AniList
    // titles physically live in the TV library). A real hand-made
    // Anime view wins if one exists; otherwise the TV library itself
    // stands in for it, but only when there is really something to
    // show behind it (a real anime/anilist catalog among the reader's
    // own collections), same real ProviderIds.Stremio check
    // isAnimeCollection() already makes on the web side.
    suspend fun getLibraryNavEntries(userId: String): List<BaseItemDto> {
        val views = try {
            getLibraries(userId)
        } catch (err: Exception) {
            emptyList()
        }

        val moviesView = views.firstOrNull { it.CollectionType == "movies" }
        val tvView = views.firstOrNull { it.CollectionType == "tvshows" }
        val realAnimeView = views.firstOrNull { ANIME_VIEW_NAME.containsMatchIn(it.Name ?: "") }

        // Real components/navShared.js's own getPrimaryNavLinks(): this
        // used to wait on a real getCollections() round trip first,
        // gating the fallback Anime entry on an actual anime/anilist
        // catalog turning up, real feedback traced to that same real
        // bug that file's own header now documents: Anime sat visibly
        // slower to appear than Movies/Shows, and any failure or empty
        // real result along that path (this one adds a second real
        // network call getCollections() doesn't need to make at all)
        // dropped the entry outright rather than degrading to an empty
        // state. Pushed unconditionally now, the same real bet every
        // other library entry here already makes: a server with no
        // real anime catalogs still gets a real Anime entry, its own
        // screen's own empty state the one real place that check lives.
        val animeEntry = realAnimeView ?: tvView?.copy(Name = "Anime")

        return listOfNotNull(moviesView, tvView, animeEntry)
    }

    // Real bug runtime/api.js's own getAllCollections() documents and
    // fixes, ported the same way rather than left in the single-page
    // form this used to take: a single Limit: 200 page silently drops
    // every collection past it, alphabetically, no error, an entire
    // real service missing from the hub strip and catalog rows both
    // with nothing wrong server side. Pages through the real
    // TotalRecordCount instead.
    suspend fun getCollections(userId: String): List<BaseItemDto> = cache.get("collections:$userId", CACHE_TTL_MS) {
        val pageSize = 100
        val collected = mutableListOf<BaseItemDto>()
        var startIndex = 0
        while (true) {
            val result = api.getItems(
                userId = userId,
                includeItemTypes = "BoxSet",
                recursive = true,
                sortBy = "SortName",
                limit = pageSize,
                startIndex = startIndex,
                fields = "ProviderIds,ChildCount",
            )
            collected.addAll(result.Items)
            if (result.Items.size < pageSize || collected.size >= result.TotalRecordCount) break
            startIndex += pageSize
        }
        collected
    }

    // Gelato's own GetOrCreateBoxSetAsync writes a collection's
    // ProviderIds.Stremio as "{catalogType}.{catalogId}", catalogType
    // being the literal type string configured on that catalog in
    // AIOStreams: "movie", "series", or "anime". A real signal
    // straight from Gelato, not a guess off a name a reader can
    // rename freely.
    // Controllers/AnimeController.cs: every title in an anime catalog, so
    // anime stays in the Anime library and out of Home and the Movies and
    // Shows pages. Empty when the server can't say, so nothing is hidden
    // by mistake.
    suspend fun getJellioNotifications(): List<com.jellio.tv.data.model.JellioNotificationDto> = api.getJellioNotifications()

    // An announcement's picture; adds ApiKey query param so Jellyfin authorizes it.
    suspend fun announcementImageUrl(imageId: String): String? {
        val server = sessionManager.serverAddress() ?: return null
        if (imageId.startsWith("http://", ignoreCase = true) || imageId.startsWith("https://", ignoreCase = true)) {
            return imageId
        }
        if (imageId.startsWith("/")) {
            return "$server$imageId"
        }
        val token = sessionManager.accessToken()
        val base = "$server/Jellio/notifications/image/" + java.net.URLEncoder.encode(imageId, "UTF-8")
        return if (!token.isNullOrEmpty()) "$base?ApiKey=$token&api_key=$token" else base
    }

    suspend fun getPendingOverlays(after: Long): com.jellio.tv.data.model.PendingOverlaysDto = api.getPendingOverlays(after)

    suspend fun overlayImageUrl(imageId: String): String? {
        val server = sessionManager.serverAddress() ?: return null
        if (imageId.startsWith("http://", ignoreCase = true) || imageId.startsWith("https://", ignoreCase = true)) {
            return imageId
        }
        if (imageId.startsWith("/")) {
            return "$server$imageId"
        }
        val token = sessionManager.accessToken()
        val base = "$server/Jellio/overlays/image/" + java.net.URLEncoder.encode(imageId, "UTF-8")
        return if (!token.isNullOrEmpty()) "$base?ApiKey=$token&api_key=$token" else base
    }

    suspend fun getPendingSounds(after: Long): com.jellio.tv.data.model.PendingSoundsDto = api.getPendingSounds(after)

    suspend fun soundUrl(soundId: String): String? {
        val server = sessionManager.serverAddress() ?: return null
        return "$server/Jellio/sounds/file/" + java.net.URLEncoder.encode(soundId, "UTF-8")
    }

    // Same headers the video player sends, a token in the URL can be
    // dropped by a proxy in front of Jellyfin.
    suspend fun soundRequestHeaders(): Map<String, String> {
        val token = sessionManager.accessToken() ?: return emptyMap()
        return mapOf(
            "X-Emby-Token" to token,
            "Authorization" to com.jellio.tv.data.network.buildEmbyAuthorizationHeader("AndroidTV", com.jellio.tv.data.network.APP_VERSION, token),
        )
    }

    suspend fun getAnimeIds(): Set<String> =
        runCatching {
            cache.get("anime-ids", CACHE_TTL_MS) { api.getAnimeIds().Ids.map { it.replace("-", "").lowercase() }.toSet() }
        }.getOrDefault(emptySet())

    fun isAnimeCollection(collection: BaseItemDto): Boolean {
        val stremio = collection.ProviderIds?.get("Stremio") ?: collection.ProviderIds?.get("stremio")
        if (!stremio.isNullOrEmpty()) {
            return stremio.substringBefore('.').lowercase() == "anime"
        }
        return ANIME_COLLECTION_NAME.containsMatchIn(collection.Name ?: "")
    }

    // Mirrors runtime/api.js's own collectionKind() exactly: anime
    // always reads as the tvshows kind (the anime library stands in
    // for it, same real reasoning getLibraryNavEntries() above already
    // documents), otherwise the real Stremio catalogType decides,
    // movies kind the real fallback for anything with no provider id
    // to read at all.
    fun collectionKind(collection: BaseItemDto): String {
        if (isAnimeCollection(collection)) return "tvshows"
        val stremio = collection.ProviderIds?.get("Stremio") ?: collection.ProviderIds?.get("stremio")
        if (!stremio.isNullOrEmpty()) {
            return if (stremio.substringBefore('.').lowercase() == "movie") "movies" else "tvshows"
        }
        return "movies"
    }

    // Real port of screens/library.js's own isAnime check
    // (params.get('jellioKind') === 'anime'): both a real dedicated
    // Anime view and getLibraryNavEntries()'s own synthetic
    // tvView.copy(Name = "Anime") stand-in match this exact real
    // regex against their own Name, the one real signal a caller
    // needs to tell "this is the Anime page" apart from the plain
    // Shows page without threading a second flag through the route.
    fun isAnimeLibrary(library: BaseItemDto): Boolean = ANIME_VIEW_NAME.containsMatchIn(library.Name ?: "")

    // Real port of screens/library.js's own getAnimeItemIds(): every
    // real item id any real anime/anilist catalog collection
    // currently claims, best effort same as that file's own real
    // reasoning documents (no CollectionType tells a real anime
    // Series apart from any other one sharing the same real TV
    // library). The Shows hub's own main row and genre rows drop
    // anything in this set, real feedback's own direct ask. Failure
    // of any part of this resolves to an empty real Set rather than
    // throwing, the Shows hub renders unfiltered same as before this
    // existed rather than breaking outright over a real best effort
    // feature.
    // A real perf bug found live testing on device, fixed the same way
    // runtime/api.js's own getAnimeItemIds() already does
    // (Promise.allSettled(animeCollections.map(...))): every real
    // anime/anilist catalog collection's own item fetch now fires
    // together instead of the second catalog waiting on the first
    // one's own response, real seconds shaved off every Shows library
    // load that used to await this one collection at a time.
    suspend fun getAnimeItemIds(userId: String): Set<String> = coroutineScope {
        val animeCollections = runCatching { getCollections(userId) }.getOrDefault(emptyList()).filter { isAnimeCollection(it) }
        if (animeCollections.isEmpty()) return@coroutineScope emptySet()
        animeCollections.map { collection ->
            async {
                runCatching { getCollectionItems(userId, collection.Id, "tvshows", ANIME_ITEM_ID_LIMIT) }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().mapTo(mutableSetOf()) { it.Id }
    }

    suspend fun getCollectionItems(userId: String, collectionId: String, kind: String, limit: Int = 24): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            parentId = collectionId,
            includeItemTypes = if (kind == "movies") "Movie" else "Series",
            recursive = true,
            limit = limit,
            sortBy = "SortName",
            // BackdropImageTags is not part of the default field set:
            // screens/service.js's own pickHeroItem() needs it to judge
            // which real item across a matched collection is even
            // eligible to lead that page's own hero.
            fields = "PrimaryImageAspectRatio,ProductionYear,CommunityRating,Genres,BackdropImageTags,Overview",
        ).Items

    // Real runtime/api.js's own getResumeItems(): RunTimeTicks
    // alongside the fields already asked for, real bug that file's own
    // comment documents and fixes: components/card.js's own landscape
    // Continue Watching card computes a real "Nm left" label from that
    // and UserData.PlaybackPositionTicks (already a default real
    // field), not something this query fetched before.
    suspend fun getContinueWatching(userId: String): List<BaseItemDto> =
        api.getResumeItems(userId, fields = "$ITEM_FIELDS,RunTimeTicks").Items

    // Real runtime/api.js's own getNextUp(): Genres/People beyond the
    // default PrimaryImageAspectRatio (the same real fields
    // runtime/recommend.js's own scorer needs off this exact shared
    // real fetch, no second query added just to get them), RunTimeTicks
    // for the same real landscape card reason getResumeItems above
    // documents.
    //
    // Real gap in stock Jellyfin, same header runtime/api.js's own
    // getNextUp() already documents: no endpoint hides one series from
    // this row on its own, only ever the side effect of marking its
    // current episode played, which just advances that same series to
    // its own next episode instead of actually leaving. Filtered here
    // against Controllers/NextUpHiddenController.cs's own per user list
    // rather than in HomeViewModel, so every real caller of this
    // function gets the same real exclusion for free.
    suspend fun getNextUp(userId: String, limit: Int = 20): List<BaseItemDto> {
        val items = api.getNextUp(userId, limit, fields = "$ITEM_FIELDS,Genres,People,RunTimeTicks", enableResumable = false).Items
        seedPreviews(items)
        val hidden = runCatching { api.getHiddenNextUpSeries() }.getOrDefault(emptyList())
        if (hidden.isEmpty()) return items
        return items.filterNot { hidden.contains(it.SeriesId) }
    }

    suspend fun hideSeriesFromNextUp(seriesId: String) {
        api.hideSeriesFromNextUp(seriesId)
    }

    // Scoped to one series, screens/detail.js's own resolveSeriesPlayTarget():
    // enableResumable stays at its own real server default (true) here,
    // unlike the Up Next row above, this call's whole point is
    // surfacing a title actually in progress.
    suspend fun getSeriesNextUp(seriesId: String, userId: String): BaseItemDto? =
        api.getNextUp(userId, limit = 1, fields = "PrimaryImageAspectRatio,RunTimeTicks", seriesId = seriesId)
            .Items.firstOrNull()

    // Mirrors runtime/api.js's own getHeroCandidates(): a real random
    // Movie/Series, never an Episode (the web hero never once asks for
    // one), so there is no episode title/art to ever land in a hero in
    // the first place, same real reasoning as the fix itself rather
    // than a patch bolted onto whatever a Continue Watching item
    // happened to be.
    suspend fun getHeroCandidates(userId: String, limit: Int = 8, parentId: String? = null): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = "Movie,Series",
            recursive = true,
            limit = limit,
            sortBy = "Random",
            fields = "$ITEM_FIELDS,Genres",
        ).Items

    suspend fun getLibraryItems(
        userId: String,
        parentId: String,
        limit: Int = 24,
        includeItemTypes: String? = null,
        sortBy: String = "DateCreated",
        sortOrder: String = "Descending",
        genre: String? = null,
        startIndex: Int? = null,
    ): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = includeItemTypes,
            recursive = true,
            limit = limit,
            startIndex = startIndex,
            sortBy = sortBy,
            sortOrder = sortOrder,
            genres = genre,
            fields = ITEM_FIELDS,
        ).Items

    // Sampled rather than a real dedicated endpoint (none exists),
    // mirrors runtime/api.js's own discoverGenres(): counted off a
    // random sample of the library, not every genre Jellyfin has ever
    // heard of, dropped below a real minimum count so a genre row is
    // never built with nothing worth scrolling behind it, the real
    // remaining ones led by real count rather than left in whatever
    // order a Kotlin Map happens to iterate (a real bug found rereading
    // that file directly: the count itself was never actually sorted
    // on before this).
    suspend fun discoverGenres(userId: String, parentId: String?, itemType: String, limit: Int = 6, minCount: Int = 8): List<String> {
        // The server's cached genre list first; sampling items is the fallback.
        runCatching { api.getGenreCounts(itemType, parentId).Genres }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { counts ->
            return counts.filter { it.Count >= minCount }.sortedByDescending { it.Count }.map { it.Name }.take(limit)
        }
        val sample = api.getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = itemType,
            recursive = true,
            limit = 300,
            sortBy = "Random",
            fields = "Genres",
        ).Items
        val counts = linkedMapOf<String, Int>()
        sample.forEach { item ->
            item.Genres?.forEach { genre -> counts[genre] = (counts[genre] ?: 0) + 1 }
        }
        return counts.filterValues { it >= minCount }
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(limit)
    }

    // The whole genre list for a library, A to Z, for the genre picker.
    // The reader's chosen audio language (Settings > Language), e.g. "ger".
    // The reader's default subtitle language, unless their subtitle mode
    // is set to never show any.
    suspend fun getSubtitleLanguagePreference(userId: String): String? =
        runCatching {
            val config = getUser(userId)?.Configuration
            if (config?.SubtitleMode.equals("None", ignoreCase = true)) null else config?.SubtitleLanguagePreference
        }.getOrNull()?.trim()?.ifBlank { null }

    suspend fun getAudioLanguagePreference(userId: String): String? =
        runCatching { getUser(userId)?.Configuration?.AudioLanguagePreference }.getOrNull()?.trim()?.ifBlank { null }

    suspend fun getAllGenres(userId: String, parentId: String?, itemType: String): List<String> =
        api.getGenres(userId = userId, parentId = parentId, includeItemTypes = itemType).Items
            .mapNotNull { it.Name?.trim()?.ifBlank { null } }
            .distinct()

    // From the server's few-minute cache of the row, else asked directly.
    suspend fun getGenreItems(userId: String, parentId: String?, itemType: String, genre: String, limit: Int = 20): List<BaseItemDto> =
        distinctTitles(
            runCatching { api.getCachedGenreItems(genre, itemType, parentId, limit).Items }.getOrNull()
                ?: api.getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = itemType,
            recursive = true,
            limit = limit,
            genres = genre,
            sortBy = "CommunityRating",
            sortOrder = "Descending",
            fields = "$ITEM_FIELDS,ProductionYear,CommunityRating",
        ).Items,
        )

    // Showcase details for titles whose row entry came without them (older
    // cached rows, episodes needing their show): one request for the item and
    // its show together, kept for half an hour.
    private val showcaseCache = ConcurrentHashMap<String, Pair<Long, BaseItemDto>>()

    suspend fun getShowcaseItems(userId: String, ids: List<String>): Map<String, BaseItemDto> {
        val now = System.currentTimeMillis()
        val fresh = ids.mapNotNull { id -> showcaseCache[id]?.takeIf { now - it.first < PRELOAD_TTL_MS }?.second }.associateBy { it.Id }
        val missing = ids.filter { it !in fresh }.distinct()
        if (missing.isEmpty()) return fresh
        val fetched = api.getItemsByIds(userId, missing.joinToString(","), SHOWCASE_FIELDS).Items
        if (showcaseCache.size > 400) showcaseCache.clear()
        fetched.forEach { showcaseCache[it.Id] = now to it }
        return fresh + fetched.associateBy { it.Id }
    }

    suspend fun getCommunityUsers(): List<com.jellio.tv.data.model.CommunityUserDto> =
        api.getCommunityUsers()

    suspend fun getCommunityWatching(excludedUserIds: List<String> = emptyList()): List<BaseItemDto> {
        val excludedParam = if (excludedUserIds.isNotEmpty()) excludedUserIds.joinToString(",") else null
        val response = api.getCommunityWatching(excludedParam)
        return response.Items.map { entry ->
            entry.Item.copy(Watchers = entry.Watchers)
        }
    }

    suspend fun searchItems(userId: String, term: String, limit: Int = 50): List<BaseItemDto> {
        if (term.isBlank()) return emptyList()
        return api.getItems(
            userId = userId,
            includeItemTypes = "Movie,Series",
            recursive = true,
            limit = limit,
            searchTerm = term,
            fields = ITEM_FIELDS,
        ).Items
    }

    // Ported from Jellio Web runtime/api.js's own getSeasonalItems():
    // Queries items matching seasonal genres (e.g. Horror, Mystery) sorted by rating,
    // plus items matching search terms (e.g. Halloween, Spooky), then merges and dedupes.
    suspend fun getSeasonalItems(
        userId: String,
        genres: List<String>,
        searchTerms: List<String>,
        limit: Int = 60,
    ): List<BaseItemDto> = coroutineScope {
        val jobs = mutableListOf<Deferred<List<BaseItemDto>>>()
        if (genres.isNotEmpty()) {
            jobs.add(
                async {
                    runCatching {
                        api.getItems(
                            userId = userId,
                            recursive = true,
                            includeItemTypes = "Movie,Series",
                            genres = genres.joinToString("|"),
                            limit = limit,
                            fields = "$ITEM_FIELDS,Genres,ProductionYear,CommunityRating",
                            sortBy = "CommunityRating",
                            sortOrder = "Descending",
                        ).Items
                    }.getOrDefault(emptyList())
                }
            )
        }
        searchTerms.take(2).forEach { term ->
            jobs.add(
                async {
                    runCatching {
                        api.getItems(
                            userId = userId,
                            recursive = true,
                            includeItemTypes = "Movie,Series",
                            searchTerm = term,
                            limit = 25,
                            fields = "$ITEM_FIELDS,Genres,ProductionYear,CommunityRating",
                        ).Items
                    }.getOrDefault(emptyList())
                }
            )
        }
        val results = jobs.awaitAll()
        val combined = mutableListOf<BaseItemDto>()
        val seen = mutableSetOf<String>()
        for (list in results) {
            for (item in list) {
                if (seen.add(item.Id)) {
                    combined.add(item)
                }
            }
        }
        combined
    }

    // Split halves of searchItems above, fired independently by
    // SearchViewModel so a slower half never holds up the faster one.
    // JellyfinApi.kt's own header on these documents the real bottleneck
    // (Gelato's own SearchActionFilter.cs waiting on both before
    // answering); no runCatching here, SearchViewModel's own per-call
    // try/catch already needs the real thrown exception to tell an HTTP
    // 404 (no Gelato on this server, fall back to searchItems) apart
    // from any other real failure.
    // Only what the library already holds: Gelato passes a "local:" search
    // straight to Jellyfin's own search (tens of milliseconds) instead of
    // asking the addons. Without Gelato nothing matches it.
    suspend fun searchLibrary(userId: String, term: String): List<BaseItemDto> {
        if (term.isBlank()) return emptyList()
        return api.getItems(
            userId = userId,
            includeItemTypes = "Movie,Series",
            recursive = true,
            limit = 50,
            searchTerm = "local:$term",
            fields = "$ITEM_FIELDS,ProviderIds,ProductionYear",
        ).Items
    }

    suspend fun searchMovies(term: String): List<BaseItemDto> = api.searchGelatoMovies(term)

    suspend fun searchSeries(term: String): List<BaseItemDto> = api.searchGelatoSeries(term)

    suspend fun getWatchlistItems(userId: String, limit: Int = 100): List<BaseItemDto> =
        cache.get("watchlist:$userId:$limit", SHORT_CACHE_TTL_MS) {
            api.getItems(
                userId = userId,
                includeItemTypes = "Movie,Series",
                recursive = true,
                limit = limit,
                filters = "IsFavorite",
                fields = ITEM_FIELDS,
            ).Items
        }

    suspend fun getItemDetails(userId: String, itemId: String, forPlayer: Boolean = false): BaseItemDto =
        cache.get("details:$itemId", SHORT_CACHE_TTL_MS) {
            fetchItemInfo(userId, itemId, DETAIL_FIELDS)
        }.also {
            previews[it.Id] = it
            preloaded[itemId] = System.currentTimeMillis() to it
        }

    suspend fun getTrickplayInfo(userId: String, itemId: String, mediaSourceId: String?): TrickplayInfoDto? =
        cache.get("trickplay:$itemId", CACHE_TTL_MS) {
            fetchItemInfo(userId, itemId, "Trickplay")
        }.let { pickTrickplayInfo(it, mediaSourceId) }

    // Full details fetched while the viewer rests on a card, so the title
    // page can open complete; fresh for a minute, the page still refreshes.
    private val preloaded = ConcurrentHashMap<String, Pair<Long, BaseItemDto>>()
    private val preloadSlots = java.util.concurrent.Semaphore(2)

    fun preloadDetails(itemId: String) {
        val now = System.currentTimeMillis()
        if (preloaded[itemId]?.let { now - it.first < PRELOAD_TTL_MS } == true) return
        if (!preloadSlots.tryAcquire()) return
        prefetchScope.launch {
            try {
                val userId = sessionManager.sessionFlow.first()?.userId ?: return@launch
                val item = fetchItemInfo(userId, itemId, DETAIL_FIELDS)
                if (preloaded.size > 200) preloaded.clear()
                preloaded[itemId] = System.currentTimeMillis() to item
            } catch (_: Exception) {
            } finally {
                preloadSlots.release()
            }
        }
    }

    fun preloadedDetails(itemId: String): BaseItemDto? {
        val entry = preloaded[itemId] ?: return null
        return entry.second.takeIf { System.currentTimeMillis() - entry.first < PRELOAD_TTL_MS }
    }

    // Items already seen in a row or list, so a title page can draw them
    // at once while the full details load.
    private val previews = ConcurrentHashMap<String, BaseItemDto>()

    fun seedPreviews(items: List<BaseItemDto>) {
        if (previews.size > 600) previews.clear()
        items.forEach { previews[it.Id] = it }
    }

    fun previewItem(itemId: String): BaseItemDto? = previews[itemId]

    suspend fun getItem(userId: String, itemId: String): BaseItemDto =
        cache.get("item:$itemId", CACHE_TTL_MS) { fetchItemInfo(userId, itemId, null) }

    // Overview and artwork for several items in one request (launcher cards).
    suspend fun getItemsInfo(userId: String, ids: List<String>): Map<String, BaseItemDto> {
        if (ids.isEmpty()) return emptyMap()
        return api.getItemsByIds(userId, ids.distinct().joinToString(","), "Overview,BackdropImageTags").Items
            .associateBy { it.Id }
    }

    // Title info through /Items?ids=, never with MediaSources or
    // MediaStreams. A title Gelato has not imported yet only exists once
    // it is asked for by id, so that request stays as the fallback.
    private suspend fun fetchItemInfo(userId: String, itemId: String, fields: String?): BaseItemDto =
        runCatching { api.getItemsByIds(userId, itemId, fields).Items.firstOrNull() }.getOrNull()
            ?: api.getItem(userId, itemId, fields)

    // screens/person.js's own real getPerson(): a plain item lookup,
    // real Jellyfin Person items share the same BaseItemDto shape as
    // everything else this app already reads.
    suspend fun getPerson(userId: String, personId: String): BaseItemDto = fetchItemInfo(userId, personId, null)

    // Real endpoint, GET /Items?personIds=X (Jellyfin.Api's own
    // ItemsController, confirmed against screens/person.js's own
    // getPersonFilmography() before porting this): every real Movie/
    // Series this person is credited on.
    suspend fun getPersonFilmography(userId: String, personId: String, limit: Int = 50): List<BaseItemDto> =
        cache.get("filmography:$personId:$limit", CACHE_TTL_MS) {
            api.getItems(
                userId = userId,
                includeItemTypes = "Movie,Series",
                recursive = true,
                limit = limit,
                sortBy = "PremiereDate",
                sortOrder = "Descending",
                fields = "$ITEM_FIELDS,ProductionYear",
                personIds = personId,
            ).Items
        }

    suspend fun getSeasons(seriesId: String, userId: String): List<BaseItemDto> =
        cache.get("seasons:$seriesId", SHORT_CACHE_TTL_MS) { api.getSeasons(seriesId, userId).Items }
            .also { recentLists["seasons:$seriesId"] = System.currentTimeMillis() to it }

    // Seasons and episodes as last seen, to draw a series page at once.
    private val recentLists = ConcurrentHashMap<String, Pair<Long, List<BaseItemDto>>>()

    fun recentSeasons(seriesId: String): List<BaseItemDto>? = recentList("seasons:$seriesId")

    fun recentEpisodes(seriesId: String, seasonId: String): List<BaseItemDto>? = recentList("episodes:$seriesId:$seasonId")

    private fun recentList(key: String): List<BaseItemDto>? {
        val entry = recentLists[key] ?: return null
        return entry.second.takeIf { System.currentTimeMillis() - entry.first < PRELOAD_TTL_MS }
    }

    // Real endpoint, runtime/recommend.js's own real seed history:
    // real Jellyfin Filters=IsPlayed, sorted by DatePlayed, real real
    // recency signal a Gelato server's own DateCreated cannot give
    // (every import lands at once, DateCreated means nothing there).
    suspend fun getRecentlyCompleted(userId: String, limit: Int): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            includeItemTypes = "Movie,Series",
            recursive = true,
            filters = "IsPlayed",
            sortBy = "DatePlayed",
            sortOrder = "Descending",
            limit = limit,
            fields = "Genres,People,ProductionYear,CommunityRating,RunTimeTicks",
        ).Items

    // One seed's own real candidate pool for RecommendationEngine's
    // own scorer: its own genres and its own top billed cast/director,
    // each a separate real query narrowed server side rather than
    // scoring the whole library client side to fill one row. Mirrors
    // runtime/api.js's own getRecommendationCandidates() exactly,
    // including its own real Genres=a|b OR-join convention.
    suspend fun getRecommendationCandidates(userId: String, seed: BaseItemDto, limit: Int): List<CandidateEntry> {
        val genres = seed.Genres.orEmpty()
        val people = seed.People.orEmpty().filter { it.Id.isNotEmpty() && (it.Type == "Actor" || it.Type == "Director") }.take(5)
        if (genres.isEmpty() && people.isEmpty()) return emptyList()

        val byId = linkedMapOf<String, CandidateEntry>()

        if (genres.isNotEmpty()) {
            runCatching {
                api.getItems(
                    userId = userId,
                    includeItemTypes = "Movie,Series",
                    recursive = true,
                    limit = limit,
                    sortBy = "Random",
                    fields = "$ITEM_FIELDS,Genres,ProductionYear,CommunityRating,RunTimeTicks",
                    genres = genres.joinToString("|"),
                ).Items
            }.getOrDefault(emptyList()).forEach { item ->
                byId.getOrPut(item.Id) { CandidateEntry(item, viaPerson = false) }
            }
        }

        if (people.isNotEmpty()) {
            runCatching {
                api.getItems(
                    userId = userId,
                    includeItemTypes = "Movie,Series",
                    recursive = true,
                    limit = limit,
                    sortBy = "Random",
                    fields = "$ITEM_FIELDS,Genres,ProductionYear,CommunityRating,RunTimeTicks",
                    personIds = people.joinToString(",") { it.Id },
                ).Items
            }.getOrDefault(emptyList()).forEach { item ->
                val existing = byId[item.Id]
                byId[item.Id] = if (existing != null) existing.copy(viaPerson = true) else CandidateEntry(item, viaPerson = true)
            }
        }

        return byId.values.toList()
    }

    suspend fun getEpisodes(seriesId: String, userId: String, seasonId: String): List<BaseItemDto> =
        cache.get("episodes:$seriesId:$seasonId", SHORT_CACHE_TTL_MS) {
            api.getEpisodes(seriesId, userId, seasonId, fields = "Overview,PrimaryImageAspectRatio").Items
        }.also {
            seedPreviews(it)
            if (recentLists.size > 300) recentLists.clear()
            recentLists["episodes:$seriesId:$seasonId"] = System.currentTimeMillis() to it
        }

    // Real port of runtime/api.js's own getNextEpisode(): the next real
    // episode in this same season if this one is not the season's own
    // last, otherwise season one's own first episode of the next real
    // season, null past the series finale, same real fallback that
    // file's own comment documents.
    suspend fun getNextEpisode(userId: String, item: BaseItemDto): BaseItemDto? {
        if (item.Type != "Episode") return null
        val seriesId = item.SeriesId ?: return null
        val seasonId = item.SeasonId
        if (seasonId != null) {
            val episodes = getEpisodes(seriesId, userId, seasonId)
            val index = episodes.indexOfFirst { it.Id == item.Id }
            if (index != -1 && index + 1 < episodes.size) return episodes[index + 1]
        }
        val seasons = getSeasons(seriesId, userId)
        val seasonIndex = seasons.indexOfFirst { it.Id == seasonId }
        val nextSeason = if (seasonIndex != -1) seasons.getOrNull(seasonIndex + 1) else null
        nextSeason ?: return null
        return getEpisodes(seriesId, userId, nextSeason.Id).firstOrNull()
    }

    // Real port of runtime/api.js's own getIntroSkipperSegments(): any
    // failure (plugin not installed, unknown item) resolves to null
    // rather than throwing, same real soft-dependency reasoning that
    // file's own try/catch already documents (no segments is a normal
    // outcome, not an error worth surfacing).
    suspend fun getIntroSkipperSegments(itemId: String): IntroSkipperSegmentsDto? =
        runCatching { api.getIntroSkipperSegments(itemId) }.getOrNull()

    // A series' own hero Play button (screens/detail.js's own
    // resolveSeriesPlayTarget()): the next real unfinished episode if
    // one exists, otherwise season one's own first episode, a real
    // fallback for a series with no watch history at all.
    suspend fun resolveSeriesPlayTarget(seriesId: String, userId: String): SeriesPlayTarget? {
        val nextUp = try {
            getSeriesNextUp(seriesId, userId)
        } catch (err: Exception) {
            null
        }
        val episode = nextUp ?: try {
            val seasons = getSeasons(seriesId, userId)
            val ordered = seasons.sortedBy { if (isSpecialsSeason(it)) 1 else 0 }
            val firstSeason = ordered.firstOrNull()
            firstSeason?.let { getEpisodes(seriesId, userId, it.Id).firstOrNull() }
        } catch (err: Exception) {
            null
        } ?: return null

        val isFirstEpisode = episode.ParentIndexNumber == 1 && episode.IndexNumber == 1
        val hasProgress = (episode.UserData?.PlaybackPositionTicks ?: 0) > 0
        val resume = hasProgress || !isFirstEpisode
        return SeriesPlayTarget(episode, resume)
    }

    fun isSpecialsSeason(season: BaseItemDto): Boolean {
        if (season.IndexNumber == 0) return true
        return Regex("special", RegexOption.IGNORE_CASE).containsMatchIn(season.Name ?: "")
    }

    // With no releases found, Gelato lists the title's own placeholder so
    // the list is never empty: no file behind it, no size, and named after
    // the title itself (or not at all) rather than by the addon the way a
    // real release is. It can't play, so it's dropped.
    suspend fun getMediaSources(
        userId: String,
        itemId: String,
        reason: String = "play",
        forceFresh: Boolean = false,
    ): List<MediaSourceDto> {
        // Stream lists are ranked and badged by it.
        loadVideoTranscodePolicy()
        if (!forceFresh) {
            sourceCache.get(itemId, System.currentTimeMillis())?.let {
                com.jellio.tv.data.streams.StreamLookupLog.cacheHit()
                return it
            }
        }
        val request = sourcesInFlight.computeIfAbsent(itemId) {
            prefetchScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                val started = System.currentTimeMillis()
                val sources = fetchMediaSources(userId, itemId)
                if (sources.isNotEmpty()) {
                    sourceCache.put(itemId, sources, System.currentTimeMillis())
                }
                com.jellio.tv.data.streams.StreamLookupLog.lookup(itemId, reason, sources.size, System.currentTimeMillis() - started)
                sources
            }
        }
        request.start()
        try {
            return request.await()
        } finally {
            sourcesInFlight.remove(itemId, request)
        }
    }

    private val streamInfoNamePattern = Regex(
        "(^|[^a-z0-9])(web-?dl|web-?rip|bluray|blu-ray|bdrip|brrip|remux|hdtv|hevc|[xh]\\.?265|[xh]\\.?264|avc|av1|vp9|hdr10?\\+?|dovi|dolby\\s?vision|hlg|10\\s?bit|aac|e?-?ac-?3|ddp|dd\\+|dts(-?hd|-?x)?|truehd|atmos|flac|l?pcm|[257]\\.[01]|dual[-.\\s]?audio|multi[-.\\s]?audio|\\d+(\\.\\d+)?\\s*(gb|mb|gib|mib|mbps|kbps))([^a-z0-9]|$)",
        RegexOption.IGNORE_CASE,
    )

    private fun hasFlagEmoji(text: String): Boolean {
        if (text.isEmpty()) return false
        val points = text.codePoints().toArray()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (a in 0x1F1E6..0x1F1FF && b in 0x1F1E6..0x1F1FF) return true
        }
        return false
    }

    private fun isRealStreamValue(value: String?): Boolean {
        val clean = value?.trim()?.lowercase() ?: return false
        return clean.isNotEmpty() && clean != "unknown" && clean != "und"
    }

    private fun hasStreamInfo(source: MediaSourceDto): Boolean {
        if (com.jellio.tv.ui.detail.sourceQuality(source) != null) return true
        if ((source.Size ?: 0L) >= 1024L * 1024L) return true

        val hasProbedStream = source.MediaStreams?.any { s ->
            when (s.Type) {
                "Video" -> (s.Width ?: 0) > 0 || (s.Height ?: 0) > 0 || (s.BitRate ?: 0L) > 0L
                "Audio" -> isRealStreamValue(s.Codec) || (s.Channels ?: 0) > 0 || isRealStreamValue(s.Language)
                else -> false
            }
        } == true
        if (hasProbedStream) return true

        val name = source.Name.orEmpty()
        if (streamInfoNamePattern.containsMatchIn(name)) return true
        if (hasFlagEmoji(name)) return true

        return false
    }

    private suspend fun fetchMediaSources(userId: String, itemId: String): List<MediaSourceDto> {
        val item = api.getItem(userId, itemId, fields = "MediaSources,OriginalTitle,PremiereDate,ProductionYear")
        val sources = item.MediaSources ?: emptyList()
        if (sources.size == 1 && !hasStreamInfo(sources[0])) {
            return emptyList()
        }
        val seenMultiLine = mutableMapOf<String, Int>()
        val result = mutableListOf<MediaSourceDto>()
        for (s in sources) {
            val name = s.Name?.trim().orEmpty()
            if (!name.contains("\n")) {
                result.add(s)
                continue
            }
            val existingIdx = seenMultiLine[name]
            if (existingIdx != null) {
                val existing = result[existingIdx]
                val sHasProbed = s.MediaStreams?.any { it.Type == "Video" } == true
                val exHasProbed = existing.MediaStreams?.any { it.Type == "Video" } == true
                val merged = if (!sHasProbed && exHasProbed) {
                    s.copy(
                        MediaStreams = existing.MediaStreams,
                        RunTimeTicks = s.RunTimeTicks ?: existing.RunTimeTicks,
                        Size = s.Size ?: existing.Size,
                        Container = s.Container ?: existing.Container,
                    )
                } else {
                    s
                }
                result[existingIdx] = merged
            } else {
                seenMultiLine[name] = result.size
                result.add(s)
            }
        }
        return result
    }


    suspend fun toggleFavorite(userId: String, item: BaseItemDto): Boolean {
        val isFavorite = item.UserData?.IsFavorite ?: false
        return if (isFavorite) {
            api.removeFavorite(userId, item.Id).IsFavorite
        } else {
            api.addFavorite(userId, item.Id).IsFavorite
        }
    }

    // Real bug found live: this app's own cache.get(key, ttl) wrapper
    // around getCollectionItems/getLibraryItems/getGenreItems/
    // getResumeItems (this repository's own real Home/Library/Continue
    // Watching data) kept serving whatever it already had cached for up
    // to CACHE_TTL_MS after a real watch just finished, so a reader
    // landing back on Home/Library right after finishing something kept
    // seeing it without a real watched checkmark until that cache
    // entry aged out on its own. A played-state flip can appear in any
    // one of those real cached lists, not just one, so the whole real
    // cache clears here rather than guessing which specific keys need
    // it, the same real blunt real reset connectAndLogin/quickSignIn/
    // logout already reach for on an account switch.
    suspend fun setPlayed(userId: String, itemId: String, played: Boolean): UserItemDataDto {
        val result = if (played) api.markPlayed(userId, itemId) else api.markUnplayed(userId, itemId)
        cache.clear()
        return result
    }

    // Takes a title off Continue Watching without marking it watched. Servers
    // without the user data endpoint get watched then unwatched, which also
    // resets the position.
    suspend fun clearResumePosition(userId: String, itemId: String): UserItemDataDto {
        val result = runCatching { api.updateUserData(itemId, userId, com.jellio.tv.data.model.UserDataUpdate(0L)) }
            .getOrElse {
                api.markPlayed(userId, itemId)
                api.markUnplayed(userId, itemId)
            }
        cache.clear()
        return result
    }

    suspend fun setRating(userId: String, itemId: String, likes: Boolean?): UserItemDataDto =
        if (likes == null) api.clearRating(userId, itemId) else api.setRating(userId, itemId, likes)

    // Real port of runtime/api.js's own deleteItem(): removes the
    // item's own record outright. The caller's own job to gate this
    // behind Policy.IsAdministrator/EnableContentDeletion first, same
    // real reason components/cardOptionsMenu.js's own header gives:
    // showing this to every reader and letting the request itself
    // fail reads live as a broken button, not a real permission
    // boundary.
    suspend fun deleteItem(itemId: String) = api.deleteItem(itemId)

    // Shared real gate every screen offering Remove from Library
    // checks before showing it at all, same real Policy fields
    // deleteItem()'s own header documents. Failing the underlying
    // getUser() call (a real network hiccup, not a real permission
    // answer) reads as false rather than throwing, same real
    // fail-closed default a broken button would otherwise become.
    suspend fun canDeleteItems(userId: String): Boolean {
        val policy = runCatching { getUser(userId) }.getOrNull()?.Policy ?: return false
        return policy.IsAdministrator || policy.EnableContentDeletion
    }

    // Mirrors runtime/api.js's own prefetchStreams(): fired from a
    // poster's own onFocusChanged (PosterCard.kt), well before a real
    // click/Enter commits to opening it, so Gelato's own stream sync is
    // already warm by the time GetStaticMediaSources actually needs it.
    // Non-suspend and best-effort by design, same as the web version:
    // a Composable's onFocusChanged callback is not itself a coroutine,
    // and a server without Gelato installed, or an old build without
    // this route yet, must fail silently here, never surface anywhere
    // a reader could see it. A failed prefetch still counts for the
    // 8 minute window, so a card refocused after a failure does not fire
    // the same request again straight away.
    fun prefetchStreams(itemId: String) {
        val now = System.currentTimeMillis()
        val last = prefetchedAt[itemId]
        if (last != null && now - last < PREFETCH_WINDOW_MS) return
        if (sourceCache.get(itemId, now) != null || sourcesInFlight.containsKey(itemId)) return
        // Two already running: skip, a later dwell on this card tries again.
        if (!prefetchSlots.tryAcquire()) return
        prefetchedAt[itemId] = now
        com.jellio.tv.data.streams.StreamLookupLog.prefetch(itemId)
        prefetchScope.launch {
            try {
                runCatching { api.prefetchGelatoStreams(itemId) }
            } finally {
                prefetchSlots.release()
            }
        }
    }

    // Pre-warms the stream connection and Usenet/nzbdav reader for the upcoming episode.
    fun prewarmStreamUrl(streamUrl: String) {
        prefetchScope.launch {
            runCatching {
                val url = java.net.URL(streamUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "GET"
                conn.setRequestProperty("Range", "bytes=0-65535")
                conn.connect()
                val code = conn.responseCode
                if (code in 200..206) {
                    val stream = conn.inputStream
                    val buf = ByteArray(8192)
                    stream.read(buf)
                    stream.close()
                }
                conn.disconnect()
            }
        }
    }

    suspend fun getCalendarEntries(): List<CalendarEntryDto> = api.getCalendarEntries()

    // Plain passthrough, same as getCalendarEntries above: FeedViewModel's
    // own load() decides what a failed request means (a real retry
    // state, matching screens/feed.js's own try/catch), not this layer.
    suspend fun getFeed(): List<FeedEntryDto> = api.getFeed()

    // Plain passthroughs, same real reason getFeed above is:
    // ProfileViewModel's own load() decides what a failure means (a
    // real retry state, matching screens/profile.js's own try/catch),
    // not this layer.
    suspend fun getProfile(userId: String): ProfileDto = api.getProfile(userId)

    suspend fun getAchievements(userId: String): AchievementsDto = api.getAchievements(userId)

    suspend fun setProfileBio(bio: String?) {
        api.setProfileBio(SetBioRequest(bio))
    }

    suspend fun getProfileSettings(): ProfileDto = api.getProfileSettings()

    suspend fun setProfilePrivacy(isPrivate: Boolean) {
        api.setProfilePrivacy(SetPrivacyRequest(isPrivate))
    }

    // PlayerViewModel's own real markRealWatchComplete()/
    // reportRealDurationIfUseful() decide when either of these actually
    // fires, off ui/player's own real ExoPlayer signals, not this
    // layer.
    suspend fun creditRealWatch(itemId: String) = api.creditRealWatch(RealWatchRequest(itemId))

    suspend fun reportRealDuration(itemId: String, durationTicks: Long) =
        api.reportRealDuration(ReportDurationRequest(itemId, durationTicks))

    // Real port of the same real uploadUserAvatarBlob() shape
    // setUserAvatarFromBytes() above already uses, pointed at
    // ProfileBannerController.cs's own upload route instead.
    suspend fun setProfileBannerFromBytes(bytes: ByteArray, contentType: String) {
        val body = withContext(Dispatchers.Default) {
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            base64.toRequestBody(contentType.toMediaTypeOrNull())
        }
        api.uploadProfileBanner(body)
    }

    suspend fun deleteProfileBanner() = api.deleteProfileBanner()

    suspend fun getJellioConfig(): ClientConfigDto? = runCatching { api.getJellioConfig() }.getOrNull()
        ?.also { com.jellio.tv.data.playback.VideoTranscodePolicy.set(it.VideoTranscodePolicy) }

    // The admin's Video transcode policy, re-read every few minutes.
    suspend fun loadVideoTranscodePolicy(): String {
        if (!com.jellio.tv.data.playback.VideoTranscodePolicy.isFresh()) getJellioConfig()
        return com.jellio.tv.data.playback.VideoTranscodePolicy.policy
    }

    // One line in the Jellyfin log per playback: play method and why.
    suspend fun logPlaybackDecision(itemId: String, mediaSource: MediaSourceDto, directPlay: Boolean) {
        val policy = com.jellio.tv.data.playback.VideoTranscodePolicy
        val classification = policy.classify(mediaSource)
        val serverReasons = mediaSource.TranscodingUrl
            ?.let { Regex("[?&]TranscodeReasons=([^&]*)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        runCatching {
            api.logPlaybackDecision(
                com.jellio.tv.data.model.PlaybackDecisionBody(
                    ItemId = itemId,
                    MediaSourceId = mediaSource.Id,
                    PlayMethod = com.jellio.tv.data.playback.playMethodFor(classification, directPlay).name,
                    Reasons = classification.reasons,
                    ServerTranscodeReasons = serverReasons,
                    Policy = policy.policy,
                    Guessed = classification.guessed,
                ),
            )
        }
    }

    suspend fun startSleepTimer(minutes: Int): SleepTimerStatusDto = api.startSleepTimer(SleepTimerStartRequest(minutes))

    // Real screens/player.js's own cancelSleepTimer(): a real 404 when
    // nothing was active is not an error worth surfacing there either
    // (that file's own fetch() never even inspects response.ok before
    // its .then() fires), same real reasoning for swallowing it here.
    suspend fun cancelSleepTimer() {
        runCatching { api.cancelSleepTimer() }
    }

    suspend fun getSleepTimerStatus(): SleepTimerStatusDto? = runCatching { api.getSleepTimerStatus() }.getOrNull()

    suspend fun getUser(userId: String): UserDto =
        cache.get("user:$userId", CACHE_TTL_MS) { api.getUser(userId) }

    // Mirrors runtime/api.js's own updateLanguagePreferences() exactly:
    // starts from the signed in user's own current Configuration and
    // only overwrites the two real fields screens/settings.js's own
    // Language section exposes, AudioLanguagePreference and
    // SubtitleLanguagePreference, real ISO 639-2 codes Jellyfin's own
    // PlaybackInfo negotiation already reads server side.
    suspend fun updateLanguagePreferences(userId: String, audioLanguage: String?, subtitleLanguage: String?) {
        val current = api.getUser(userId).Configuration ?: UserConfigurationDto()
        val configuration = current.copy(
            AudioLanguagePreference = audioLanguage.orEmpty(),
            SubtitleLanguagePreference = subtitleLanguage.orEmpty(),
        )
        api.updateUserConfiguration(userId, configuration)
        cache.invalidate("user:$userId")
    }

    suspend fun updatePassword(userId: String, currentPassword: String, newPassword: String) {
        api.updatePassword(userId, UpdatePasswordRequest(CurrentPw = currentPassword, NewPw = newPassword))
    }

    suspend fun isQuickConnectEnabled(): Boolean = runCatching { api.isQuickConnectEnabled() }.getOrDefault(false)

    suspend fun authorizeQuickConnect(code: String): Boolean = api.authorizeQuickConnect(code)

    suspend fun getAvatarPresets(): List<AvatarPresetDto> = api.getAvatarPresets()

    // id can carry a real "/" (a grouped preset's own subfolder, see
    // AvatarPresetDto's own header): each real segment gets its own
    // percent-encoding, java.net.URLEncoder left for URLEncoder itself
    // (which also turns a literal "/" into %2F, real form-encoding
    // syntax a URL path segment never wants), joined back with a real
    // "/" so AvatarsController.cs's own {**id} catch-all route reads
    // real path segments the same way FrontendController's own
    // {**path} already does.
    private fun encodeAvatarId(id: String): String =
        id.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }

    // Token goes on as an ApiKey query param, same real reason
    // getStreamUrl/getTrickplayUrl already do this: AvatarsController's
    // [Authorize] gate has nothing to check on a plain Coil AsyncImage
    // request, which never carries this app's own X-Emby-Authorization
    // header the way a Retrofit call does.
    fun avatarPresetUrl(serverAddress: String, accessToken: String, id: String): String =
        "$serverAddress/Jellio/avatars/${encodeAvatarId(id)}?ApiKey=$accessToken&api_key=$accessToken"

    // Real screens/settings.js's own navigateTo('#/dashboard'): that
    // file's own real hash just moves an already loaded jellyfin-web
    // page onto its own real dashboard route rather than fetching a
    // real new page, nothing this app has a WebView open for. Same
    // real hash, handed to a device browser instead, the same real
    // fallback discipline every other unmigrated route already gets
    // (this app's own version of it: launched fresh rather than
    // showing through underneath, since there is no underneath here).
    fun adminDashboardUrl(serverAddress: String): String = "$serverAddress/web/index.html#/dashboard"

    // Real port of runtime/api.js's own setUserAvatar(presetId): fetch
    // that preset's own real bytes off Jellio's own AvatarsController,
    // hand them to the same real upload path a device file already
    // goes through below (setUserAvatarFromBytes).
    suspend fun setUserAvatarFromPreset(userId: String, presetId: String) {
        val response = api.getAvatarPresetImage(encodeAvatarId(presetId))
        // Reading a real ResponseBody's own bytes is blocking I/O
        // (@Streaming keeps it off the main network dispatcher's own
        // buffering), off the main thread here for the same real
        // reason the file picker's own ContentResolver read is too.
        val (bytes, contentType) = withContext(Dispatchers.IO) {
            response.bytes() to (response.contentType()?.toString() ?: "image/png")
        }
        uploadUserAvatarBlob(userId, bytes, contentType)
    }

    // Real port of runtime/api.js's own setUserAvatarFromFile(file):
    // a real file the reader picked off their own device, real
    // Jellyfin already accepts this natively (an animated gif
    // included) for a user's own avatar.
    suspend fun setUserAvatarFromBytes(userId: String, bytes: ByteArray, contentType: String) {
        uploadUserAvatarBlob(userId, bytes, contentType)
    }

    // Real port of runtime/api.js's own uploadUserAvatarBlob(): base64
    // encode whatever real image bytes the caller already has, POST to
    // the same real endpoint the stock profile page's own file upload
    // already uses, body the base64 payload itself with Content-Type
    // set to the image's own real mime type, not JSON.
    private suspend fun uploadUserAvatarBlob(userId: String, bytes: ByteArray, contentType: String) {
        val body = withContext(Dispatchers.Default) {
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            base64.toRequestBody(contentType.toMediaTypeOrNull())
        }
        api.uploadUserAvatar(userId, body)
        // Mirrors runtime/api.js's own setUserAvatar() calling its own
        // invalidateCurrentUser() right after: the one real place a
        // cached user can go visibly stale sooner than CACHE_TTL_MS,
        // an avatar the reader just picked should show up on the very
        // next real getUser() call, not up to a minute later.
        cache.invalidate("user:$userId")
    }

    // Real mechanism, mirrors runtime/api.js's own getPlaybackInfo() +
    // buildStreamUrl(): POST /Items/{id}/PlaybackInfo negotiates a real
    // MediaSource plus a PlaySessionId, then a plain /Videos/{id}/stream
    // URL carrying that source's own id is something ExoPlayer can just
    // open directly, same real flow, no jellyfin-web playbackManager
    // involved.
    suspend fun resolvePlayback(
        userId: String,
        itemId: String,
        mediaSourceId: String?,
        startTimeTicks: Long,
        burnInSubtitleStreamIndex: Int? = null,
        audioStreamIndex: Int? = null,
        forceTranscode: Boolean = false,
    ): PlaybackTarget {
        val response = api.getPlaybackInfo(
            itemId,
            PlaybackInfoRequest(
                UserId = userId,
                StartTimeTicks = startTimeTicks,
                MediaSourceId = mediaSourceId,
                // Real fields runtime/api.js's own getPlaybackInfo()
                // also sends: a bare stream URL query param change alone
                // (no fresh negotiation carrying these) never once
                // produced a genuinely new transcode job server side,
                // confirmed against a real server log before this
                // existed, same real reasoning that file's own comment
                // documents.
                AudioStreamIndex = audioStreamIndex,
                SubtitleStreamIndex = burnInSubtitleStreamIndex,
            ),
        )
        val mediaSource = response.MediaSources.firstOrNull { it.Id == mediaSourceId }
            ?: response.MediaSources.firstOrNull()
            ?: throw IllegalStateException("No playable source for this title")

        // Mirrors runtime/api.js's own buildStreamUrl() exactly: always
        // this app's own /Videos/{id}/stream.{container} endpoint, never
        // MediaSourceInfo.TranscodingUrl (that file never once reads
        // that field either, a forced transcode is this same endpoint
        // with VideoCodec/AudioCodec params instead of Static=true). A
        // non-default real audio track forces the same real transcode a
        // burned in subtitle already does: Static=true (a direct
        // playable file) serves every embedded track as is, no way to
        // tell the server which one to actually decode.
        val targetAudioStream = if (audioStreamIndex != null) {
            mediaSource.MediaStreams?.firstOrNull { it.Type == "Audio" && it.Index == audioStreamIndex }
        } else {
            null
        }
        val audioNeedsTranscode = if (audioStreamIndex != null && audioStreamIndex != mediaSource.DefaultAudioStreamIndex) {
            targetAudioStream == null || !canDirectPlayAudio(targetAudioStream.Codec)
        } else {
            false
        }
        val shouldTranscode = forceTranscode ||
            burnInSubtitleStreamIndex != null ||
            audioNeedsTranscode
        val directPlay = !shouldTranscode && canDirectPlay(mediaSource, audioStreamIndex)
        val serverAddress = sessionManager.serverAddress() ?: throw IllegalStateException("Not signed in")
        val token = sessionManager.accessToken() ?: throw IllegalStateException("Not signed in")
        val deviceId = sessionManager.deviceId()
        val resolvedMediaSourceId = mediaSource.Id ?: itemId
        val parsedContainer = normalizeStreamContainer(mediaSource.Container)
        val container = if (directPlay) parsedContainer else "mp4"

        val streamUrl = buildString {
            append(serverAddress)
            append("/Videos/").append(itemId).append("/stream.").append(container)
            append("?MediaSourceId=").append(resolvedMediaSourceId)
            append("&DeviceId=").append(deviceId)
            append("&ApiKey=").append(token)
            append("&api_key=").append(token)
            append("&StartTimeTicks=").append(startTimeTicks)
            response.PlaySessionId?.let { append("&PlaySessionId=").append(it) }
            if (directPlay) {
                append("&Static=true")
            } else {
                if (com.jellio.tv.data.playback.VideoTranscodePolicy.avoiding) {
                    // Every codec this TV decodes, no bitrate cap: the
                    // server copies the video and converts only the
                    // container or audio.
                    val codecs = com.jellio.tv.data.playback.copyableVideoCodecs(com.jellio.tv.data.playback.DeviceDecoderCaps)
                    append("&VideoCodec=").append(codecs.joinToString(","))
                    append("&AudioCodec=aac&AudioBitRate=192000")
                    append("&AllowVideoStreamCopy=true&AllowAudioStreamCopy=true")
                    val ranges = if (com.jellio.tv.data.playback.DeviceDecoderCaps.dolbyVision) "$COPY_RANGE_TYPES,DOVI" else COPY_RANGE_TYPES
                    codecs.filter { it != "h264" }.forEach { codec ->
                        append("&").append(codec).append("-videorangetype=").append(ranges)
                    }
                } else {
                    append("&VideoCodec=h264&AudioCodec=aac")
                    append("&VideoBitRate=").append(estimateVideoBitrate(mediaSource))
                    append("&AudioBitRate=192000")
                }
            }
            if (audioStreamIndex != null) {
                append("&AudioStreamIndex=").append(audioStreamIndex)
            }
            if (burnInSubtitleStreamIndex != null) {
                append("&SubtitleStreamIndex=").append(burnInSubtitleStreamIndex)
                append("&SubtitleMethod=Encode")
            }
        }

        return PlaybackTarget(streamUrl, mediaSource, response.PlaySessionId, startTimeTicks, directPlay)
    }

    // Real port of runtime/api.js's own getAudioStreams().
    fun getAudioStreams(mediaSource: MediaSourceDto): List<MediaStreamDto> =
        mediaSource.MediaStreams?.filter { it.Type == "Audio" } ?: emptyList()

    // Real port of runtime/api.js's own pickTrickplayInfo(): the
    // smallest real generated width for this MediaSourceId, same real
    // reasoning that function's own comment documents (a scrub preview
    // reads at a glance, not full detail, and a small real sheet is the
    // cheaper real download on every seek). Width folded into the
    // returned value the same way that function's own
    // Object.assign({ Width: widths[0] }, ...) does, real key taking
    // priority over whatever that sheet's own value already carried.
    fun pickTrickplayInfo(item: BaseItemDto, mediaSourceId: String?): TrickplayInfoDto? {
        val bySource = item.Trickplay?.get(mediaSourceId) ?: return null
        val width = bySource.keys.mapNotNull { it.toIntOrNull() }.minOrNull() ?: return null
        val info = bySource[width.toString()] ?: return null
        return info.copy(Width = width)
    }

    // Real endpoint, GET /Videos/{itemId}/Trickplay/{width}/{index}.jpg
    // (TrickplayController.cs's own GetTrickplayTileImage), confirmed
    // against runtime/api.js's own getTrickplayTileUrl(): index is a
    // real tile sheet's own position, several real thumbnails packed
    // into one sheet, not a single thumbnail's own index.
    fun trickplayTileUrl(serverAddress: String, accessToken: String, itemId: String, mediaSourceId: String?, width: Int, tileIndex: Int): String =
        "$serverAddress/Videos/$itemId/Trickplay/$width/$tileIndex.jpg?ApiKey=$accessToken&api_key=$accessToken&mediaSourceId=${mediaSourceId ?: itemId}"

    private fun estimateVideoBitrate(mediaSource: MediaSourceDto): Long {
        val video = mediaSource.MediaStreams?.firstOrNull { it.Type == "Video" }
        return video?.BitRate ?: mediaSource.Bitrate ?: FALLBACK_VIDEO_BITRATE
    }

    // Real endpoint confirmed against SubtitleController.cs's own
    // registered route: GET /Videos/{itemId}/{mediaSourceId}/Subtitles/
    // {streamIndex}/Stream.vtt converts any real text subtitle format
    // to WebVTT server side, so requesting .vtt always works for a
    // text stream regardless of its own real source codec. An already
    // external stream (DeliveryMethod == "External") carries its own
    // DeliveryUrl instead, absolute when IsExternalUrl is set,
    // otherwise still relative to this same server.
    // offsetTicks: where a transcode began. Its clock starts at zero there,
    // so the server shifts the cues by the same amount
    // (Subtitles/{index}/{startPositionTicks}/Stream.vtt).
    suspend fun buildSubtitleUrl(itemId: String, mediaSourceId: String, stream: MediaStreamDto, offsetTicks: Long = 0): String {
        val serverAddress = sessionManager.serverAddress() ?: throw IllegalStateException("Not signed in")
        if (stream.DeliveryMethod == "External" && !stream.DeliveryUrl.isNullOrEmpty()) {
            return if (stream.IsExternalUrl == true) stream.DeliveryUrl else serverAddress + stream.DeliveryUrl
        }
        val token = sessionManager.accessToken()
        val base = if (offsetTicks > 0) {
            "$serverAddress/Videos/$itemId/$mediaSourceId/Subtitles/${stream.Index}/$offsetTicks/Stream.vtt"
        } else {
            "$serverAddress/Videos/$itemId/$mediaSourceId/Subtitles/${stream.Index}/Stream.vtt"
        }
        return if (!token.isNullOrEmpty()) "$base?ApiKey=$token&api_key=$token" else base
    }

    internal fun canDirectPlayAudio(codec: String?): Boolean {
        val c = codec?.lowercase() ?: return false
        if (c in setOf("aac", "mp3", "opus", "vorbis", "flac", "pcm", "pcm_s16le", "pcm_s24le", "alac")) return true
        if (com.jellio.tv.ui.player.PlayerPrefs.audioPassthrough && c in DIRECT_PLAY_AUDIO_CODECS) return true
        val mimeType = when (c) {
            "ac3" -> "audio/ac3"
            "eac3" -> "audio/eac3"
            else -> return false // DTS, TrueHD, etc. have no native software decoder in Android/Media3 without passthrough
        }
        return try {
            val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        } catch (_: Exception) {
            false
        }
    }

    internal fun canDirectPlay(mediaSource: MediaSourceDto, audioStreamIndex: Int? = null): Boolean {
        if (mediaSource.SupportsDirectPlay != true) return false
        val container = mediaSource.Container?.lowercase() ?: return false
        if (container !in DIRECT_PLAY_CONTAINERS) return false

        val streams = mediaSource.MediaStreams ?: emptyList()
        val video = streams.firstOrNull { it.Type == "Video" }
        val audioStreams = streams.filter { it.Type == "Audio" }
        val targetAudioIndex = audioStreamIndex ?: mediaSource.DefaultAudioStreamIndex
        val audio = if (targetAudioIndex != null) {
            audioStreams.firstOrNull { it.Index == targetAudioIndex } ?: audioStreams.firstOrNull()
        } else {
            audioStreams.firstOrNull()
        }
        if (video != null && video.Codec?.lowercase() !in DIRECT_PLAY_VIDEO_CODECS) return false
        if (audio != null && !canDirectPlayAudio(audio.Codec)) return false
        return true
    }

    suspend fun reportPlaybackStart(itemId: String, mediaSourceId: String?, positionTicks: Long) {
        runCatching {
            api.reportPlaybackStart(PlaybackReportRequest(itemId, mediaSourceId, positionTicks))
        }
    }

    suspend fun reportPlaybackProgress(itemId: String, mediaSourceId: String?, positionTicks: Long, isPaused: Boolean) {
        runCatching {
            api.reportPlaybackProgress(PlaybackReportRequest(itemId, mediaSourceId, positionTicks, isPaused))
        }
    }

    suspend fun reportPlaybackStopped(itemId: String, mediaSourceId: String?, positionTicks: Long) {
        runCatching {
            api.reportPlaybackStopped(PlaybackReportRequest(itemId, mediaSourceId, positionTicks))
        }
    }

    fun imageUrl(
        serverAddress: String,
        itemId: String,
        tag: String?,
        imageType: String = "Primary",
        maxWidth: Int = 400,
        accessToken: String? = null,
    ): String {
        val cleanServer = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        val base = "$cleanServer/Items/$itemId/Images/$imageType?maxWidth=$maxWidth&quality=85"
        val withTag = if (!tag.isNullOrEmpty()) "$base&tag=$tag" else base
        return if (!accessToken.isNullOrEmpty()) "$withTag&ApiKey=$accessToken&api_key=$accessToken" else withTag
    }
}

internal fun normalizeStreamContainer(rawContainer: String?): String {
    val first = rawContainer?.lowercase()?.split(",")?.firstOrNull()?.trim().orEmpty()
    return when (first) {
        "", "mov" -> "mp4"
        "matroska" -> "mkv"
        else -> first
    }
}
