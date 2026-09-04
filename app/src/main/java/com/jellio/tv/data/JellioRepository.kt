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
import com.jellio.tv.data.model.NowPlayingSessionDto
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
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import com.jellio.tv.data.recommend.CandidateEntry
import com.jellio.tv.data.session.RememberedUserEntry
import com.jellio.tv.data.session.RememberedUsersStore
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val APP_VERSION = "0.1.0"

// BackdropImageTags is not included by default (real bug found live:
// the hero backdrop silently never loaded because this app never
// asked the server whether one even existed), same real field
// screens/home.js's own hero already knows to ask for.
private const val ITEM_FIELDS = "PrimaryImageAspectRatio,BackdropImageTags"

// Mirrors runtime/api.js's own getItemDetails(): a detail screen needs
// real metadata a plain row/grid fetch never asks for.
private const val DETAIL_FIELDS = "Overview,Genres,People,ProductionYear,RunTimeTicks,PremiereDate,RemoteTrailers," +
    "BackdropImageTags,OfficialRating,CommunityRating,ParentBackdropItemId,ParentBackdropImageTags,Trickplay"

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
    fun clearCache() = cache.clear()

    // The one real server address a fresh install has never asked for
    // yet: null only before the very first successful connectAndLogin()
    // this device ever makes, since SessionManager.clearSession() keeps
    // it across a sign out the same real way runtime/auth.js's own
    // clearSession() leaves SERVER_ADDRESS_KEY alone. LoginViewModel's
    // own start() reads this to decide whether there is a real server
    // to show a profile picker for at all, or whether this is a true
    // first run.
    suspend fun knownServerAddress(): String? = sessionManager.serverAddress()

    suspend fun connectAndLogin(serverAddress: String, username: String, password: String): LoginResult {
        val normalized = serverAddress.trim().trimEnd('/')
        if (normalized.isEmpty()) {
            return LoginResult.Failure("Enter your server address")
        }
        sessionManager.saveServerAddress(normalized)
        return try {
            // Confirms the address is a real reachable Jellyfin server
            // before ever sending real credentials to it.
            api.getPublicSystemInfo()
            val deviceId = sessionManager.deviceId()
            val authHeader = buildEmbyAuthorizationHeader(deviceId, APP_VERSION)
            val result = api.authenticateByName(authHeader, AuthenticateByNameRequest(username, password))
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
        rememberedUsersStore.getRememberedUsers(serverAddress)

    suspend fun forgetRememberedUser(serverAddress: String, userId: String) {
        rememberedUsersStore.forgetUser(serverAddress, userId)
    }

    private suspend fun rememberUser(serverAddress: String, userId: String, accessToken: String, name: String, primaryImageTag: String?) {
        rememberedUsersStore.rememberUser(
            serverAddress,
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
        sessionManager.saveServerAddress(serverAddress)
        return api.getPublicUsers()
    }

    // Real port of runtime/auth.js's own quickSignIn(): spends one real
    // GET on the remembered token before ever trusting it, dropping
    // that remembered entry the same real way on a real 401/expired
    // token rather than silently committing a session the very next
    // request would fail.
    suspend fun quickSignIn(serverAddress: String, userId: String): LoginResult {
        val entry = rememberedUsersStore.getRememberedUsers(serverAddress)[userId]
            ?: return LoginResult.Failure("No remembered sign-in for this profile")
        sessionManager.saveServerAddress(serverAddress)
        return try {
            val user = api.getUserWithToken(entry.accessToken, userId)
            clearCache()
            sessionManager.saveSession(serverAddress, entry.accessToken, user.Id, user.Name)
            rememberUser(serverAddress, user.Id, entry.accessToken, user.Name, user.PrimaryImageTag)
            LoginResult.Success
        } catch (err: Exception) {
            rememberedUsersStore.forgetUser(serverAddress, userId)
            LoginResult.Failure("That saved sign-in no longer works. Sign in again.")
        }
    }

    fun userImageUrl(serverAddress: String, userId: String, tag: String?, maxWidth: Int = 300): String {
        val base = "$serverAddress/Users/$userId/Images/Primary?maxWidth=$maxWidth"
        return if (!tag.isNullOrEmpty()) "$base&tag=$tag" else base
    }

    // Real Controllers/ProfileBannerController.cs's own GET {userId}:
    // cache-busted with a real timestamp query param, same real reason
    // screens/profile.js's own buildBanner() appends '&t=' +
    // Date.now() (a fresh upload replacing the file at this same real
    // path otherwise keeps serving whatever Coil already cached for
    // it). That controller carries [Authorize], same as avatarPresetUrl's
    // own real Jellio/avatars endpoint just above, so this needs the
    // exact same api_key query param that one already sends: never
    // loaded at all on device without it, Coil's own image request
    // getting back a plain 401 no <img> tag / browser cookie session
    // ever hits.
    fun bannerUrl(serverAddress: String, accessToken: String, userId: String): String =
        "$serverAddress/Jellio/profile/banner/$userId?api_key=$accessToken&t=${System.currentTimeMillis()}"

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

        val animeEntry = when {
            realAnimeView != null -> realAnimeView
            tvView != null -> {
                val hasAnimeCatalogs = try {
                    getCollections(userId).any { isAnimeCollection(it) }
                } catch (err: Exception) {
                    false
                }
                if (hasAnimeCatalogs) tvView.copy(Name = "Anime") else null
            }
            else -> null
        }

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
            fields = "PrimaryImageAspectRatio,ProductionYear,CommunityRating,Genres,BackdropImageTags",
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

    suspend fun getGenreItems(userId: String, parentId: String?, itemType: String, genre: String, limit: Int = 20): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            parentId = parentId,
            includeItemTypes = itemType,
            recursive = true,
            limit = limit,
            genres = genre,
            sortBy = "CommunityRating",
            sortOrder = "Descending",
            fields = "PrimaryImageAspectRatio,ProductionYear,CommunityRating",
        ).Items

    suspend fun searchItems(userId: String, term: String, limit: Int = 50): List<BaseItemDto> {
        if (term.isBlank()) return emptyList()
        return api.getItems(
            userId = userId,
            includeItemTypes = "Movie,Series",
            recursive = true,
            limit = limit,
            searchTerm = term,
            fields = "PrimaryImageAspectRatio",
        ).Items
    }

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

    suspend fun getItemDetails(userId: String, itemId: String): BaseItemDto =
        api.getItem(userId, itemId, fields = DETAIL_FIELDS)

    suspend fun getItem(userId: String, itemId: String): BaseItemDto =
        cache.get("item:$itemId", CACHE_TTL_MS) { api.getItem(userId, itemId) }

    // screens/person.js's own real getPerson(): a plain item lookup,
    // real Jellyfin Person items share the same BaseItemDto shape as
    // everything else this app already reads.
    suspend fun getPerson(userId: String, personId: String): BaseItemDto = api.getItem(userId, personId)

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
                fields = "PrimaryImageAspectRatio,ProductionYear",
                personIds = personId,
            ).Items
        }

    suspend fun getSeasons(seriesId: String, userId: String): List<BaseItemDto> =
        cache.get("seasons:$seriesId", SHORT_CACHE_TTL_MS) { api.getSeasons(seriesId, userId).Items }

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
                    fields = "Genres,ProductionYear,CommunityRating,RunTimeTicks",
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
                    fields = "Genres,ProductionYear,CommunityRating,RunTimeTicks",
                    personIds = people.joinToString(",") { it.Id },
                ).Items
            }.getOrDefault(emptyList()).forEach { item ->
                val existing = byId[item.Id]
                byId[item.Id] = if (existing != null) existing.copy(viaPerson = true) else CandidateEntry(item, viaPerson = true)
            }
        }

        return byId.values.toList()
    }

    // Every real item crediting one specific person as Actor or
    // Director, real "More with [actor]" row aggregate.
    suspend fun getPersonItems(userId: String, personId: String, limit: Int): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            includeItemTypes = "Movie,Series",
            recursive = true,
            limit = limit,
            sortBy = "CommunityRating",
            sortOrder = "Descending",
            fields = "ProductionYear,CommunityRating,Genres",
            personIds = personId,
        ).Items

    suspend fun getEpisodes(seriesId: String, userId: String, seasonId: String): List<BaseItemDto> =
        cache.get("episodes:$seriesId:$seasonId", SHORT_CACHE_TTL_MS) {
            api.getEpisodes(seriesId, userId, seasonId, fields = "Overview,PrimaryImageAspectRatio").Items
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

    suspend fun getMediaSources(userId: String, itemId: String): List<MediaSourceDto> =
        api.getItem(userId, itemId, fields = "MediaSources").MediaSources ?: emptyList()

    suspend fun toggleFavorite(userId: String, item: BaseItemDto): Boolean {
        val isFavorite = item.UserData?.IsFavorite ?: false
        return if (isFavorite) {
            api.removeFavorite(userId, item.Id).IsFavorite
        } else {
            api.addFavorite(userId, item.Id).IsFavorite
        }
    }

    suspend fun setPlayed(userId: String, itemId: String, played: Boolean): UserItemDataDto =
        if (played) api.markPlayed(userId, itemId) else api.markUnplayed(userId, itemId)

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

    // Real port of runtime/api.js's own getNowPlayingSessions(): a
    // plain passthrough, same as getCalendarEntries above, the polling
    // loop's own caller decides what a failed request means rather
    // than this method swallowing it.
    suspend fun getNowPlayingSessions(): List<NowPlayingSessionDto> = api.getNowPlayingSessions()

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

    // Token goes on as an api_key query param, same real reason
    // getStreamUrl/getTrickplayUrl already do this: AvatarsController's
    // [Authorize] gate has nothing to check on a plain Coil AsyncImage
    // request, which never carries this app's own X-Emby-Authorization
    // header the way a Retrofit call does.
    fun avatarPresetUrl(serverAddress: String, accessToken: String, id: String): String =
        "$serverAddress/Jellio/avatars/${encodeAvatarId(id)}?api_key=$accessToken"

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
        val forceTranscode = burnInSubtitleStreamIndex != null ||
            (audioStreamIndex != null && audioStreamIndex != mediaSource.DefaultAudioStreamIndex)
        val directPlay = !forceTranscode && canDirectPlay(mediaSource)
        val serverAddress = sessionManager.serverAddress() ?: throw IllegalStateException("Not signed in")
        val token = sessionManager.accessToken() ?: throw IllegalStateException("Not signed in")
        val deviceId = sessionManager.deviceId()
        val resolvedMediaSourceId = mediaSource.Id ?: itemId
        val container = if (directPlay) mediaSource.Container ?: "mp4" else "mp4"

        val streamUrl = buildString {
            append(serverAddress)
            append("/Videos/").append(itemId).append("/stream.").append(container)
            append("?MediaSourceId=").append(resolvedMediaSourceId)
            append("&DeviceId=").append(deviceId)
            append("&api_key=").append(token)
            append("&StartTimeTicks=").append(startTimeTicks)
            response.PlaySessionId?.let { append("&PlaySessionId=").append(it) }
            if (directPlay) {
                append("&Static=true")
            } else {
                append("&VideoCodec=h264&AudioCodec=aac")
                append("&VideoBitRate=").append(estimateVideoBitrate(mediaSource))
                append("&AudioBitRate=192000")
            }
            if (audioStreamIndex != null) {
                append("&AudioStreamIndex=").append(audioStreamIndex)
            }
            if (burnInSubtitleStreamIndex != null) {
                append("&SubtitleStreamIndex=").append(burnInSubtitleStreamIndex)
                append("&SubtitleMethod=Encode")
            }
        }

        return PlaybackTarget(streamUrl, mediaSource, response.PlaySessionId, startTimeTicks)
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
        "$serverAddress/Videos/$itemId/Trickplay/$width/$tileIndex.jpg?api_key=$accessToken&mediaSourceId=${mediaSourceId ?: itemId}"

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
    suspend fun buildSubtitleUrl(itemId: String, mediaSourceId: String, stream: MediaStreamDto): String {
        val serverAddress = sessionManager.serverAddress() ?: throw IllegalStateException("Not signed in")
        if (stream.DeliveryMethod == "External" && !stream.DeliveryUrl.isNullOrEmpty()) {
            return if (stream.IsExternalUrl == true) stream.DeliveryUrl else serverAddress + stream.DeliveryUrl
        }
        val token = sessionManager.accessToken()
        val base = "$serverAddress/Videos/$itemId/$mediaSourceId/Subtitles/${stream.Index}/Stream.vtt"
        return if (!token.isNullOrEmpty()) "$base?ApiKey=$token" else base
    }

    private fun canDirectPlay(mediaSource: MediaSourceDto): Boolean {
        if (mediaSource.SupportsDirectPlay == false && mediaSource.SupportsDirectStream == false) return false
        val container = mediaSource.Container?.lowercase() ?: return false
        if (container !in DIRECT_PLAY_CONTAINERS) return false
        val streams = mediaSource.MediaStreams ?: emptyList()
        val video = streams.firstOrNull { it.Type == "Video" }
        val audio = streams.firstOrNull { it.Type == "Audio" }
        if (video != null && video.Codec?.lowercase() !in DIRECT_PLAY_VIDEO_CODECS) return false
        if (audio != null && audio.Codec?.lowercase() !in DIRECT_PLAY_AUDIO_CODECS) return false
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

    // No auth header on image requests yet (real gap, tracked, not
    // hidden): most self-hosted Jellyfin instances leave image
    // serving open, but a hardened one may not, and this app's own
    // Coil setup does not attach the session token to these requests.
    fun imageUrl(
        serverAddress: String,
        itemId: String,
        tag: String?,
        imageType: String = "Primary",
        maxWidth: Int = 400,
    ): String {
        val base = "$serverAddress/Items/$itemId/Images/$imageType?maxWidth=$maxWidth"
        return if (!tag.isNullOrEmpty()) "$base&tag=$tag" else base
    }
}
