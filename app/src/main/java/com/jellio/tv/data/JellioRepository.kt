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
import com.jellio.tv.data.playback.SkipSegments
import com.jellio.tv.data.playback.chapterSkipSegments
import com.jellio.tv.data.playback.nativeSkipSegments
import com.jellio.tv.data.playback.toSkipSegments
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.ProfileDto
import com.jellio.tv.data.model.RealWatchRequest
import com.jellio.tv.data.model.ReportDurationRequest
import com.jellio.tv.data.model.SetBioRequest
import com.jellio.tv.data.model.SetPrivacyRequest
import com.jellio.tv.data.model.SleepTimerStartRequest
import com.jellio.tv.data.model.SleepTimerStatusDto
import com.jellio.tv.data.model.MediaStreamDto
import com.jellio.tv.data.model.UpdatePasswordRequest
import com.jellio.tv.data.model.UserDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.model.WatchlistAutoRemoveRequest
import com.jellio.tv.data.model.RichPersonDetails
import com.jellio.tv.data.cache.lruMap
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.prefs.WatchlistPreferences
import com.jellio.tv.data.recommend.CandidateEntry
import com.jellio.tv.data.session.RememberedUserEntry
import com.jellio.tv.data.session.RememberedUsersStore
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.session.SessionManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
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
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

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
private const val ITEM_FIELDS = "PrimaryImageAspectRatio,ProductionYear,PremiereDate,EndDate,CommunityRating,BackdropImageTags,Overview"

internal const val DETAIL_FIELDS = "Overview,Genres,Tags,People,ProductionYear,RunTimeTicks,PremiereDate,RemoteTrailers," +
    "BackdropImageTags,OfficialRating,CommunityRating,ParentBackdropItemId,ParentBackdropImageTags,Status,EndDate"
private const val SHOWCASE_FIELDS = "Overview,Genres,ProductionYear,RunTimeTicks,PremiereDate,CommunityRating,BackdropImageTags," +
    "ParentBackdropItemId,ParentBackdropImageTags"

private val ANIME_VIEW_NAME = Regex("anime", RegexOption.IGNORE_CASE)
private val ANIME_COLLECTION_NAME = Regex("anime|anilist|kitsu", RegexOption.IGNORE_CASE)
private val SPECIALS_SEASON_NAME = Regex("special", RegexOption.IGNORE_CASE)
// Named because their genres rarely say so; episodes stand alone.
private val KNOWN_ANTHOLOGIES = listOf(
    "black mirror",
    "love, death & robots",
    "love death and robots",
    "love death & robots",
    "love death + robots",
    "twilight zone",
    "cabinet of curiosities",
    "electric dreams",
    "room 104",
    "star wars: visions",
    "star wars visions",
    "inside no. 9",
    "inside no 9",
    "modern love",
    "oats studios",
    "tales from the crypt",
    "tales from the loop",
    "american horror stories",
)

sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(val reason: LoginFailure) : LoginResult
}

data class SeriesPlayTarget(val episode: BaseItemDto, val resume: Boolean)

data class OpenedLiveStream(
    val channelId: String,
    val liveStreamId: String?,
    val streamUrl: String,
    val mimeType: String? = null,
    val requestHeaders: Map<String, String>? = null,
    val fallbackUrl: String? = null,
    // Set when Jellyfin transcodes the channel, so it can be stopped on leaving.
    val playSessionId: String? = null,
)

class StreamLinkException(val code: String) : Exception(code)

data class PlaybackTarget(
    val streamUrl: String,
    val mediaSource: MediaSourceDto,
    val playSessionId: String?,
    val startPositionTicks: Long,
    val directPlay: Boolean,
)

private const val CACHE_TTL_MS = 60_000L
private const val SHORT_CACHE_TTL_MS = 8_000L
// Same as the web row cache's genres/people class: a title's genre and
// cast neighbours barely change, so Home refreshes reuse them.
private const val RECOMMENDATION_CACHE_TTL_MS = 30 * 60_000L

// Per-title hint maps (prefetch times, last stream lists, empty checks)
// keep at most this many titles, least recently used dropped first.
internal const val MAX_HINTS = 500

internal class TtlCache {
    private data class Entry(val value: Any?, val expiresAt: Long)
    private val entries = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private companion object {
        const val PRUNE_ABOVE = 300
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> get(key: String, ttlMs: Long, fetcher: suspend () -> T): T {
        entries[key]?.let { entry -> if (System.currentTimeMillis() < entry.expiresAt) return entry.value as T }
        val lock = locks.getOrPut(key) { Mutex() }
        return lock.withLock {
            entries[key]?.let { entry -> if (System.currentTimeMillis() < entry.expiresAt) return@withLock entry.value as T }
            val value = fetcher()
            entries[key] = Entry(value, System.currentTimeMillis() + ttlMs)
            if (entries.size > PRUNE_ABOVE) prune()
            value
        }
    }

    // Expired entries are otherwise only replaced when the same key is asked
    // for again; every title and genre visited would stay for the session.
    private fun prune() {
        val now = System.currentTimeMillis()
        entries.entries.removeIf { now >= it.value.expiresAt }
        locks.entries.removeIf { !it.value.isLocked && !entries.containsKey(it.key) }
    }

    fun invalidate(key: String) {
        entries.remove(key)
    }

    fun invalidatePrefix(prefix: String) {
        entries.keys.removeIf { it.startsWith(prefix) }
    }

    fun clear() {
        entries.clear()
    }
}

@Singleton
class JellioRepository @Inject constructor(
    internal val api: JellyfinApi,
    internal val sessionManager: SessionManager,
    private val rememberedUsersStore: RememberedUsersStore,
    private val watchlistPreferences: WatchlistPreferences,
    private val subtitleStylePreferences: com.jellio.tv.data.prefs.SubtitleStylePreferences,
    private val streams: StreamsRepository,
    private val playback: PlaybackRepository,
    private val config: JellioConfigRepository,
    private val imageUrls: ImageUrls,
    private val tmdbPeople: TmdbPeople,
    private val prefetch: PrefetchScope,
) {
    val streamClient: okhttp3.OkHttpClient get() = playback.streamClient

    val sessionFlow: Flow<Session?> = sessionManager.sessionFlow

    fun isRemoveFromWatchlistAfterWatchingEnabled(): Boolean =
        watchlistPreferences.isRemoveFromWatchlistAfterWatchingEnabled()

    suspend fun setRemoveFromWatchlistAfterWatching(enabled: Boolean) {
        watchlistPreferences.removeFromWatchlistAfterWatching = enabled
        catching { api.setWatchlistAutoRemove(WatchlistAutoRemoveRequest(enabled)) }
    }

    private fun syncRemoveFromWatchlistAfterWatching(enabled: Boolean) {
        watchlistPreferences.removeFromWatchlistAfterWatching = enabled
    }

    // One device id on every request, so Jellyfin lists a single device and session per TV.
    val deviceId: String get() = sessionManager.getCachedDeviceId()
    internal val cache = TtlCache()

    private val emptyTodayStreamCheckAt = lruMap<String, Long>(MAX_HINTS)

    fun clearCache() {
        // An earlier account's warm-ups must not refill the caches below.
        prefetch.cancelAll()
        streams.clear()
        cache.clear()
        preloaded.clear()
        recentLists.clear()
        // Seen items carry the last account's watched state, and prefetched
        // playback its token in the stream URL.
        previews.clear()
        showcaseCache.clear()
        playback.clear()
        emptyTodayStreamCheckAt.clear()
    }

    suspend fun knownServerAddress(): String? = sessionManager.serverAddress()

    suspend fun resolveServerAddress(input: String): Result<String> {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return Result.failure(ServerAddressException(LoginFailure.ENTER_SERVER, "no address"))
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
            if (catching { api.getPublicSystemInfo() }.isSuccess) return Result.success(candidate)
        }
        sessionManager.saveServerAddress(first)
        val tried = candidates.joinToString(" and ")
        android.util.Log.w("JellioAuth", "no Jellyfin server answered, tried $tried")
        return Result.failure(ServerAddressException(LoginFailure.SERVER_NOT_FOUND, "tried $tried"))
    }

    suspend fun connectAndLogin(serverAddress: String, username: String, password: String): LoginResult {
        val normalized = resolveServerAddress(serverAddress).getOrElse {
            return LoginResult.Failure(loginFailureOf(it, LoginFailure.SERVER_UNREACHABLE))
        }
        return try {
            val result = api.authenticateByName(AuthenticateByNameRequest(username, password))
            clearCache()
            sessionManager.saveSession(normalized, result.AccessToken, result.User.Id, result.User.Name)
            rememberUser(normalized, result.User.Id, result.AccessToken, result.User.Name, result.User.PrimaryImageTag)
            LoginResult.Success
        } catch (err: Exception) {
            android.util.Log.w("JellioAuth", "sign in failed", err)
            LoginResult.Failure(loginFailureOf(err, LoginFailure.SERVER_UNREACHABLE))
        }
    }

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

    suspend fun getPublicUsers(serverAddress: String): List<UserDto> {
        val normalized = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        sessionManager.saveServerAddress(normalized)
        return api.getPublicUsers()
    }

    suspend fun quickSignIn(serverAddress: String, userId: String): LoginResult {
        val normalized = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        val entry = rememberedUsersStore.getRememberedUsers(normalized)[userId]
            ?: return LoginResult.Failure(LoginFailure.NO_REMEMBERED_SIGNIN)
        // The server dropped this sign-in earlier: the profile stays, the password is needed.
        if (entry.accessToken.isBlank()) return LoginResult.Failure(LoginFailure.SESSION_EXPIRED)
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
                LoginResult.Failure(LoginFailure.SAVED_SIGNIN_EXPIRED)
            } else {
                LoginResult.Failure(LoginFailure.SWITCH_PROFILE_ERROR)
            }
        } catch (err: Exception) {
            LoginResult.Failure(LoginFailure.SERVER_TRY_AGAIN)
        }
    }

    fun userImageUrl(serverAddress: String, userId: String, tag: String?, maxWidth: Int = 300, accessToken: String? = null): String =
        imageUrls.userImageUrl(serverAddress, userId, tag, maxWidth, accessToken)

    fun bannerUrl(serverAddress: String, accessToken: String, userId: String): String =
        imageUrls.bannerUrl(serverAddress, accessToken, userId)

    suspend fun requestPasswordReset(serverAddress: String, username: String): Boolean {
        sessionManager.saveServerAddress(serverAddress)
        return try {
            api.requestPasswordReset(ForgotPasswordRequest(username))
            true
        } catch (err: Exception) {
            false
        }
    }

    suspend fun redeemPasswordReset(serverAddress: String, username: String, pin: String, newPassword: String): LoginResult {
        return try {
            val redeemed = api.redeemPasswordResetPin(ForgotPasswordPinRequest(pin))
            if (!redeemed.Success) {
                return LoginResult.Failure(LoginFailure.RESET_CODE_INVALID)
            }
            val loginResult = connectAndLogin(serverAddress, username, "")
            if (loginResult is LoginResult.Failure) return loginResult
            val userId = sessionManager.sessionFlow.first()?.userId
                ?: return LoginResult.Failure(LoginFailure.RESET_FINISH_ERROR)
            updatePassword(userId, "", newPassword)
            LoginResult.Success
        } catch (err: Exception) {
            LoginResult.Failure(LoginFailure.RESET_TRY_AGAIN)
        }
    }

    suspend fun logout() {
        clearCache()
        sessionManager.clearSession()
    }

    // Signs out a session the server no longer accepts, keeping its profile listed.
    suspend fun logoutExpired(serverAddress: String, userId: String) {
        rememberedUsersStore.clearToken(com.jellio.tv.di.normalizeServerAddress(serverAddress), userId)
        logout()
    }

    private suspend fun getLibraries(userId: String): List<BaseItemDto> =
        cache.get("views:$userId", CACHE_TTL_MS) { api.getUserViews(userId).Items }

    suspend fun getLibraryNavEntries(userId: String): List<BaseItemDto> {
        val views = try {
            getLibraries(userId)
        } catch (err: Exception) {
            emptyList()
        }

        val moviesView = views.firstOrNull { it.CollectionType == "movies" }
        val tvView = views.firstOrNull { it.CollectionType == "tvshows" }
        val realAnimeView = views.firstOrNull { ANIME_VIEW_NAME.containsMatchIn(it.Name ?: "") }

        val animeEntry = realAnimeView ?: tvView?.copy(Name = com.jellio.tv.data.i18n.Strings.get("library_anime_title"))
        val liveTvView = views.firstOrNull { it.CollectionType == "livetv" }

        return listOfNotNull(moviesView, tvView, animeEntry, liveTvView)
    }

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

    suspend fun getJellioNotifications(): List<com.jellio.tv.data.model.JellioNotificationDto> = api.getJellioNotifications()

    // An announcement's picture; adds ApiKey query param so Jellyfin authorizes it.
    suspend fun announcementImageUrl(imageId: String): String? = jellioImageUrl("notifications", imageId)

    suspend fun getPendingOverlays(after: Long): com.jellio.tv.data.model.PendingOverlaysDto = api.getPendingOverlays(after)

    suspend fun overlayImageUrl(imageId: String): String? = jellioImageUrl("overlays", imageId)

    // A full URL is used as is, a server path is put on the server, and a
    // bare id goes through the plugin's image route with the token.
    private suspend fun jellioImageUrl(feature: String, imageId: String): String? {
        val server = sessionManager.serverAddress() ?: return null
        if (imageId.startsWith("http://", ignoreCase = true) || imageId.startsWith("https://", ignoreCase = true)) {
            return imageId
        }
        if (imageId.startsWith("/")) {
            return "$server$imageId"
        }
        val token = sessionManager.accessToken()
        val base = "$server/Jellio/$feature/image/" + java.net.URLEncoder.encode(imageId, "UTF-8")
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
            "Authorization" to com.jellio.tv.data.network.buildEmbyAuthorizationHeader(sessionManager.getCachedDeviceId(), com.jellio.tv.data.network.APP_VERSION, token),
        )
    }

    suspend fun getAnimeIds(): Set<String> =
        catching {
            cache.get("anime-ids", CACHE_TTL_MS) { api.getAnimeIds().Ids.map { it.replace("-", "").lowercase() }.toSet() }
        }.getOrDefault(emptySet())

    fun isAnimeCollection(collection: BaseItemDto): Boolean {
        val stremio = collection.ProviderIds?.get("Stremio") ?: collection.ProviderIds?.get("stremio")
        if (!stremio.isNullOrEmpty()) {
            return stremio.substringBefore('.').lowercase() == "anime"
        }
        return ANIME_COLLECTION_NAME.containsMatchIn(collection.Name ?: "")
    }

    fun collectionKind(collection: BaseItemDto): String {
        if (isAnimeCollection(collection)) return "tvshows"
        val stremio = collection.ProviderIds?.get("Stremio") ?: collection.ProviderIds?.get("stremio")
        if (!stremio.isNullOrEmpty()) {
            return if (stremio.substringBefore('.').lowercase() == "movie") "movies" else "tvshows"
        }
        return "movies"
    }

    fun isAnimeLibrary(library: BaseItemDto): Boolean = ANIME_VIEW_NAME.containsMatchIn(library.Name ?: "")

    // Not recursive, like the web client: a collection's titles are its
    // direct children, and a recursive walk of a large one is much slower.
    suspend fun getCollectionItems(userId: String, collectionId: String, kind: String, limit: Int = 24, startIndex: Int? = null): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            parentId = collectionId,
            includeItemTypes = if (kind == "movies") "Movie" else "Series",
            recursive = false,
            limit = limit,
            startIndex = startIndex,
            sortBy = "SortName",
            fields = "PrimaryImageAspectRatio,ProductionYear,CommunityRating,Genres,BackdropImageTags,Overview",
        ).Items

    suspend fun getContinueWatching(userId: String): List<BaseItemDto> =
        api.getResumeItems(userId, fields = "$ITEM_FIELDS,RunTimeTicks").Items.also { seedPreviews(it) }

    suspend fun hasValidStreamForTodayItem(userId: String, item: BaseItemDto): Boolean {
        if (!isReleasedToday(item)) return true
        if (isUnreleasedByDate(item)) return false
        if (isTodayItemStreamVerified(item.Id)) return true
        val lastEmptyAt = emptyTodayStreamCheckAt[item.Id]
        if (lastEmptyAt != null && System.currentTimeMillis() - lastEmptyAt < 120_000L) {
            return false
        }
        val sources = catching {
            getMediaSources(userId, item.Id, reason = "today release check")
        }.getOrDefault(emptyList())
        val hasValid = sources.isNotEmpty()
        markTodayItemHasStream(item.Id, hasValid)
        if (!hasValid) {
            emptyTodayStreamCheckAt[item.Id] = System.currentTimeMillis()
        } else {
            emptyTodayStreamCheckAt.remove(item.Id)
        }
        return hasValid
    }

    private suspend fun filterReadyToWatchItems(userId: String, items: List<BaseItemDto>): List<BaseItemDto> = coroutineScope {
        // Today's episodes stay in until their stream check says otherwise.
        val released = items.filterNot { isUnreleasedByDate(it) }
        if (released.none { isReleasedToday(it) }) {
            return@coroutineScope released
        }
        val checked = released.map { item ->
            if (isReleasedToday(item)) {
                async { if (hasValidStreamForTodayItem(userId, item)) item else null }
            } else {
                null
            }
        }
        val results = checked.mapIndexed { index, deferred ->
            if (deferred != null) deferred.await() else released[index]
        }
        results.filterNotNull()
    }

    suspend fun getNextUp(userId: String, limit: Int = 20): List<BaseItemDto> {
        val fetchLimit = maxOf(limit * 2, 40)
        val items = api.getNextUp(userId, fetchLimit, fields = "$ITEM_FIELDS,Genres,People,RunTimeTicks,PremiereDate,EndDate", enableResumable = false).Items
        val released = filterReadyToWatchItems(userId, items)
        seedPreviews(released)
        val hidden = catching { api.getHiddenNextUpSeries() }.getOrDefault(emptyList())
        val filtered = if (hidden.isEmpty()) released else released.filterNot { hidden.contains(it.SeriesId) }
        return filtered.take(limit)
    }

    suspend fun hideSeriesFromNextUp(seriesId: String) {
        api.hideSeriesFromNextUp(seriesId)
    }

    suspend fun getSeriesNextUp(seriesId: String, userId: String): BaseItemDto? {
        val items = api.getNextUp(userId, limit = 5, fields = "PrimaryImageAspectRatio,RunTimeTicks,PremiereDate,EndDate", seriesId = seriesId).Items
        return filterReadyToWatchItems(userId, items).firstOrNull()
    }

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

    suspend fun discoverGenres(userId: String, parentId: String?, itemType: String, limit: Int = 6, minCount: Int = 8): List<String> {
        // The server's cached genre list first; sampling items is the fallback.
        catching { api.getGenreCounts(itemType, parentId).Genres }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { counts ->
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

    suspend fun getSubtitleLanguagePreference(userId: String): String? =
        catching {
            val config = getUser(userId)?.Configuration
            if (config?.SubtitleMode.equals("None", ignoreCase = true)) null else config?.SubtitleLanguagePreference
        }.getOrNull()?.trim()?.ifBlank { null }

    suspend fun getAudioLanguagePreference(userId: String): String? =
        catching { getUser(userId)?.Configuration?.AudioLanguagePreference }.getOrNull()?.trim()?.ifBlank { null }

    suspend fun getAllGenres(userId: String, parentId: String?, itemType: String): List<String> =
        api.getGenres(userId = userId, parentId = parentId, includeItemTypes = itemType).Items
            .mapNotNull { it.Name?.trim()?.ifBlank { null } }
            .distinct()

    suspend fun searchGenreOrCategory(query: String): com.jellio.tv.data.model.GenreSearchResultDto? =
        catching { api.searchGenreOrCategory(query) }.getOrNull()

    // From the server's few-minute cache of the row, else asked directly.
    suspend fun getGenreItems(userId: String, parentId: String?, itemType: String, genre: String, limit: Int = 20): List<BaseItemDto> =
        cachedUnlessEmpty("genre:$userId:$parentId:$itemType:$genre:$limit") { fetchGenreItems(userId, parentId, itemType, genre, limit) }

    // One page of a genre, for grids that keep loading as they are scrolled.
    suspend fun getGenreItemsPage(userId: String, parentId: String?, itemType: String, genre: String, startIndex: Int, limit: Int): List<BaseItemDto> =
        fetchGenreItems(userId, parentId, itemType, genre, limit, startIndex)

    private suspend fun fetchGenreItems(
        userId: String,
        parentId: String?,
        itemType: String,
        genre: String,
        limit: Int,
        startIndex: Int? = null,
    ): List<BaseItemDto> =
        distinctTitles(
            catching {
                api.getCachedGenreItems(
                    genre = genre,
                    types = itemType,
                    parentId = parentId,
                    limit = limit,
                    startIndex = startIndex,
                    enableVirtual = true,
                ).Items
            }.getOrNull() ?: api.getItems(
                userId = userId,
                parentId = parentId,
                includeItemTypes = itemType,
                recursive = true,
                limit = limit,
                startIndex = startIndex,
                genres = genre,
                sortBy = "CommunityRating",
                sortOrder = "Descending",
                fields = "$ITEM_FIELDS,ProductionYear,CommunityRating",
            ).Items,
        )

    private val showcaseCache = lruMap<String, Pair<Long, BaseItemDto>>(400)

    suspend fun getShowcaseItems(userId: String, ids: List<String>): Map<String, BaseItemDto> {
        val now = System.currentTimeMillis()
        val fresh = ids.mapNotNull { id -> showcaseCache[id]?.takeIf { now - it.first < PRELOAD_TTL_MS }?.second }.associateBy { it.Id }
        val missing = ids.filter { it !in fresh }.distinct()
        if (missing.isEmpty()) return fresh
        val fetched = api.getItemsByIds(userId, missing.joinToString(","), SHOWCASE_FIELDS).Items
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
                    catching {
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
                    catching {
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

    suspend fun getSearchSuggestions(query: String): com.jellio.tv.data.model.SearchSuggestionsDto = api.getSearchSuggestions(query)

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
            ).Items.map {
                if (it.UserData?.IsFavorite != true) {
                    it.copy(UserData = (it.UserData ?: UserItemDataDto()).copy(IsFavorite = true))
                } else {
                    it
                }
            }
        }

    suspend fun getItemDetails(userId: String, itemId: String): BaseItemDto =
        cache.get("details:$itemId", SHORT_CACHE_TTL_MS) {
            fetchItemInfo(userId, itemId, DETAIL_FIELDS)
        }.also {
            previews[it.Id] = it
            preloaded[itemId] = System.currentTimeMillis() to it
        }

    // Full details fetched while the viewer rests on a card, so the title
    // page can open complete; fresh for a minute, the page still refreshes.
    private val preloaded = lruMap<String, Pair<Long, BaseItemDto>>(200)
    private val preloadSlots = java.util.concurrent.Semaphore(2)

    fun preloadDetails(itemId: String) {
        val now = System.currentTimeMillis()
        val existing = preloaded[itemId]?.takeIf { now - it.first < PRELOAD_TTL_MS }?.second
        if (existing != null) {
            val seriesId = existing.SeriesId ?: existing.Id.takeIf { existing.Type == "Series" }
            if (seriesId == null || recentSeasons(seriesId) != null) return
        }
        if (!preloadSlots.tryAcquire()) return
        prefetch.scope.launch {
            try {
                val userId = sessionManager.sessionFlow.first()?.userId ?: return@launch
                val item = existing ?: fetchItemInfo(userId, itemId, DETAIL_FIELDS).also {
                    preloaded[itemId] = System.currentTimeMillis() to it
                    previews[it.Id] = it
                }
                if (item.Type == "Episode" && item.SeriesId != null) {
                    coroutineScope {
                        launch { catching { getSeasons(item.SeriesId, userId) } }
                        if (item.SeasonId != null) {
                            launch { catching { getEpisodes(item.SeriesId, userId, item.SeasonId) } }
                        }
                    }
                } else if (item.Type == "Series") {
                    val seasons = catching { getSeasons(item.Id, userId) }.getOrNull()
                    val firstSeason = seasons?.sortedBy { if (isSpecialsSeason(it)) 1 else 0 }?.firstOrNull()
                    if (firstSeason != null) {
                        catching { getEpisodes(item.Id, userId, firstSeason.Id) }
                    }
                }
            } catch (_: Exception) {
            } finally {
                preloadSlots.release()
            }
        }
    }

    fun preloadEpisodeSeasonsInTurn(userId: String, episodes: List<BaseItemDto>) {
        if (episodes.isEmpty()) return
        prefetch.scope.launch {
            val seenSeasons = HashSet<String>()
            for (ep in episodes) {
                val seriesId = ep.SeriesId ?: continue
                val seasonId = ep.SeasonId
                val key = "$seriesId:${seasonId.orEmpty()}"
                if (!seenSeasons.add(key)) continue
                coroutineScope {
                    if (recentSeasons(seriesId) == null) {
                        launch { catching { getSeasons(seriesId, userId) } }
                    }
                    if (seasonId != null && recentEpisodes(seriesId, seasonId) == null) {
                        launch { catching { getEpisodes(seriesId, userId, seasonId) } }
                    }
                }
            }
        }
    }

    fun preloadedDetails(itemId: String): BaseItemDto? {
        val entry = preloaded[itemId] ?: return null
        return entry.second.takeIf { System.currentTimeMillis() - entry.first < PRELOAD_TTL_MS }
    }

    // Items already seen in a row or list, so a title page can draw them
    // at once while the full details load.
    private val previews = lruMap<String, BaseItemDto>(600)

    fun seedPreviews(items: List<BaseItemDto>) {
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

    private suspend fun fetchItemInfo(userId: String, itemId: String, fields: String?): BaseItemDto =
        withRetry {
            catching {
                api.getItemsByIds(userId, itemId, fields).Items.firstOrNull {
                    it.Id.equals(itemId, ignoreCase = true) ||
                        it.Id.replace("-", "").equals(itemId.replace("-", ""), ignoreCase = true)
                }
            }.getOrNull() ?: api.getItem(userId, itemId, fields)
        }

    suspend fun getPerson(userId: String, personId: String): BaseItemDto = fetchItemInfo(userId, personId, null)

    suspend fun getRichPersonDetails(session: Session, personId: String): RichPersonDetails =
        cache.get("rich_person:$personId", RECOMMENDATION_CACHE_TTL_MS) {
            val person = getPerson(session.userId, personId)
            val localFilmography = catching {
                api.getItems(
                    userId = session.userId,
                    includeItemTypes = "Movie,Series",
                    recursive = true,
                    limit = 300,
                    sortBy = "PremiereDate",
                    sortOrder = "Descending",
                    fields = "$ITEM_FIELDS,ProviderIds,ProductionYear",
                    personIds = personId,
                ).Items
            }.getOrDefault(emptyList())

            var tmdbId = person.ProviderIds?.get("Tmdb")?.toIntOrNull()
            if (tmdbId == null && !person.Name.isNullOrBlank()) {
                tmdbId = tmdbPeople.searchTmdbPersonId(person.Name)
            }

            if (tmdbId != null) {
                val tmdbDetails = tmdbPeople.fetchTmdbPersonDetails(tmdbId)
                if (tmdbDetails != null) {
                    val result = tmdbPeople.buildRichPersonDetails(personId, person, tmdbDetails, localFilmography)
                    seedPreviews(result.allCredits.map { it.baseItem })
                    return@get result
                }
            }

            val fallback = tmdbPeople.buildFallbackPersonDetails(personId, person, localFilmography)
            seedPreviews(fallback.allCredits.map { it.baseItem })
            fallback
        }

    suspend fun getSeasons(seriesId: String, userId: String): List<BaseItemDto> =
        cache.get("seasons:$seriesId", SHORT_CACHE_TTL_MS) { withRetry { api.getSeasons(seriesId, userId).Items } }
            .also {
                recentLists["seasons:$seriesId"] = System.currentTimeMillis() to it
            }

    // Seasons and episodes as last seen, to draw a series page at once.
    private val recentLists = lruMap<String, Pair<Long, List<BaseItemDto>>>(300)

    fun recentSeasons(seriesId: String): List<BaseItemDto>? = recentList("seasons:$seriesId")

    fun recentEpisodes(seriesId: String, seasonId: String): List<BaseItemDto>? = recentList("episodes:$seriesId:$seasonId")

    private fun recentList(key: String): List<BaseItemDto>? {
        val entry = recentLists[key] ?: return null
        return entry.second.takeIf { System.currentTimeMillis() - entry.first < PRELOAD_TTL_MS }
    }

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

    suspend fun getRecommendationCandidates(userId: String, seed: BaseItemDto, limit: Int): List<CandidateEntry> =
        cachedUnlessEmpty("reccand:$userId:${seed.Id}:$limit") { fetchRecommendationCandidates(userId, seed, limit) }

    // An empty answer is usually a failed request, so it isn't kept.
    private suspend fun <T> cachedUnlessEmpty(key: String, fetch: suspend () -> List<T>): List<T> =
        cache.get(key, RECOMMENDATION_CACHE_TTL_MS, fetch).also { if (it.isEmpty()) cache.invalidate(key) }

    private suspend fun fetchRecommendationCandidates(userId: String, seed: BaseItemDto, limit: Int): List<CandidateEntry> {
        val genres = seed.Genres.orEmpty()
        val people = seed.People.orEmpty().filter { it.Id.isNotEmpty() && (it.Type == "Actor" || it.Type == "Director") }.take(5)
        if (genres.isEmpty() && people.isEmpty()) return emptyList()

        val byId = linkedMapOf<String, CandidateEntry>()

        if (genres.isNotEmpty()) {
            catching {
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
            catching {
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
            withRetry {
                api.getEpisodes(seriesId, userId, seasonId, fields = "Overview,PrimaryImageAspectRatio,PremiereDate,EndDate").Items
            }
        }.also {
            seedPreviews(it)
            recentLists["episodes:$seriesId:$seasonId"] = System.currentTimeMillis() to it
        }

    suspend fun getNextEpisode(userId: String, item: BaseItemDto): BaseItemDto? {
        if (item.Type != "Episode") return null
        val seriesId = item.SeriesId ?: return null
        val seasonId = item.SeasonId
        if (seasonId != null) {
            val episodes = getEpisodes(seriesId, userId, seasonId)
            val index = episodes.indexOfFirst { it.Id == item.Id }
            if (index != -1 && index + 1 < episodes.size) {
                val next = episodes[index + 1]
                return if (isUnreleasedByDate(next) || (isReleasedToday(next) && !hasValidStreamForTodayItem(userId, next))) null else next
            }
        }
        val seasons = getSeasons(seriesId, userId)
        val seasonIndex = seasons.indexOfFirst { it.Id == seasonId }
        val nextSeason = if (seasonIndex != -1) seasons.getOrNull(seasonIndex + 1) else null
        nextSeason ?: return null
        val next = getEpisodes(seriesId, userId, nextSeason.Id).firstOrNull() ?: return null
        return if (isUnreleasedByDate(next) || (isReleasedToday(next) && !hasValidStreamForTodayItem(userId, next))) null else next
    }

    private val previousEpisodes = lruMap<String, BaseItemDto>(200)

    // The episode before this one, remembered per episode; null for the first.
    suspend fun getPreviousEpisode(userId: String, item: BaseItemDto): BaseItemDto? {
        if (item.Type != "Episode") return null
        previousEpisodes[item.Id]?.let { return it }
        val seriesId = item.SeriesId ?: return null
        val seasonId = item.SeasonId
        if (seasonId != null) {
            val episodes = getEpisodes(seriesId, userId, seasonId)
            val index = episodes.indexOfFirst { it.Id == item.Id }
            if (index > 0) return episodes[index - 1].also { previousEpisodes[item.Id] = it }
        }
        val seasons = getSeasons(seriesId, userId)
        val seasonIndex = seasons.indexOfFirst { it.Id == seasonId }
        for (i in earlierSeasonIndexes(seasons.map { isSpecialsSeason(it) }, seasonIndex)) {
            val last = getEpisodes(seriesId, userId, seasons[i].Id).lastOrNull() ?: continue
            previousEpisodes[item.Id] = last
            return last
        }
        return null
    }

    suspend fun getCommunitySkipSegments(itemId: String): SkipSegments? =
        catching { api.getCommunityIntroCredits(itemId).toSkipSegments() }.getOrNull()

    // Jellyfin's own Media Segments first, the Intro Skipper's older
    // Timestamps endpoint only when those have neither intro nor credits.
    suspend fun getIntroSkipperSegments(itemId: String): SkipSegments? {
        // Plugins with the merged endpoint answer here; older ones fall through.
        val merged = catching { api.getJellioSegments(itemId) }.getOrNull()
        if (merged != null && merged.isSuccessful) return merged.body()?.toSkipSegments()
        val native = catching { nativeSkipSegments(api.getMediaSegments(itemId).Items) }.getOrNull()
        if (native != null) return native
        return catching { api.getIntroSkipperSegments(itemId).toSkipSegments() }.getOrNull()
    }

    suspend fun getChapterSkipSegments(userId: String, itemId: String, runTimeTicks: Long?): SkipSegments? =
        catching {
            val item = api.getItemsByIds(userId, itemId, "Chapters").Items.firstOrNull()
            chapterSkipSegments(item?.Chapters, runTimeTicks ?: item?.RunTimeTicks)
        }.getOrNull()

    suspend fun getJellioIntroCredits(itemId: String): SkipSegments? =
        catching { api.getJellioIntroCredits(itemId).toSkipSegments() }.getOrNull()

    suspend fun resolveSeriesPlayTarget(seriesId: String, userId: String): SeriesPlayTarget? {
        val continueWatchingEp = catching {
            getContinueWatching(userId).firstOrNull { it.SeriesId == seriesId && it.UserData?.Played != true }
        }.getOrNull()
        val seriesNextUp = catching {
            getSeriesNextUp(seriesId, userId)
        }.getOrNull()
        fun epOrder(item: BaseItemDto) = (item.ParentIndexNumber ?: 0) * 10_000 + (item.IndexNumber ?: 0)
        val nextUp = when {
            continueWatchingEp != null && (seriesNextUp == null || epOrder(continueWatchingEp) >= epOrder(seriesNextUp)) -> continueWatchingEp
            else -> seriesNextUp
        }
        val episode = nextUp ?: try {
            val seasons = getSeasons(seriesId, userId)
            val ordered = seasons.sortedBy { if (isSpecialsSeason(it)) 1 else 0 }
            val firstSeason = ordered.firstOrNull()
            firstSeason?.let {
                val eps = getEpisodes(seriesId, userId, it.Id)
                eps.firstOrNull { ep -> !isUnreleasedByDate(ep) && (!isReleasedToday(ep) || hasValidStreamForTodayItem(userId, ep)) } ?: eps.firstOrNull()
            }
        } catch (err: Exception) {
            null
        } ?: return null

        val isFirstEpisode = episode.ParentIndexNumber == 1 && episode.IndexNumber == 1
        val hasProgress = (episode.UserData?.PlaybackPositionTicks ?: 0) > 0
        val resume = hasProgress || !isFirstEpisode
        return SeriesPlayTarget(episode, resume)
    }

    private fun isEpisodicAnthology(series: BaseItemDto?): Boolean {
        if (series == null) return false
        val genres = series.Genres.orEmpty()
        val tags = series.Tags.orEmpty()
        if (genres.any { it.contains("anthology", ignoreCase = true) }) return true
        if (tags.any { it.contains("anthology", ignoreCase = true) }) return true
        val name = series.Name?.lowercase() ?: ""
        return KNOWN_ANTHOLOGIES.any { name.contains(it) }
    }

    suspend fun autoCompletePreviousEpisodesIfSkipped(userId: String, episode: BaseItemDto) {
        val seriesId = episode.SeriesId ?: return
        val currentSeasonNum = episode.ParentIndexNumber ?: return
        val currentEpNum = episode.IndexNumber ?: return
        if (currentSeasonNum <= 0) return

        val series = catching { getItemDetails(userId, seriesId) }.getOrNull()
        if (isEpisodicAnthology(series)) {
            android.util.Log.d("JellioProgress", "Skipping auto-complete for anthology series: ${series?.Name}")
            return
        }

        try {
            val allEpisodes = api.getEpisodes(
                seriesId = seriesId,
                userId = userId,
                fields = "UserData,PremiereDate,EndDate,ParentIndexNumber,IndexNumber",
            ).Items
            val toMark = allEpisodes.filter { ep ->
                if (isUnreleasedItem(ep)) return@filter false
                val sNum = ep.ParentIndexNumber ?: return@filter false
                val eNum = ep.IndexNumber ?: return@filter false
                if (sNum <= 0) return@filter false
                val isBefore = sNum < currentSeasonNum || (sNum == currentSeasonNum && eNum < currentEpNum)
                isBefore && (ep.UserData?.Played != true || (ep.UserData?.PlaybackPositionTicks ?: 0L) > 0L)
            }

            if (toMark.isNotEmpty()) {
                android.util.Log.i("JellioProgress", "Auto-completing ${toMark.size} previous skipped episodes for series ${series?.Name ?: seriesId}")
                for (prevEp in toMark) {
                    catching {
                        api.markPlayed(userId, prevEp.Id)
                    }
                }
                cache.clear()
            }
        } catch (err: Exception) {
            android.util.Log.w("JellioProgress", "Failed to auto-complete previous episodes: ${err.message}")
        }
    }

    fun isSpecialsSeason(season: BaseItemDto): Boolean {
        if (season.IndexNumber == 0) return true
        return SPECIALS_SEASON_NAME.containsMatchIn(season.Name ?: "")
    }

    suspend fun getMediaSources(
        userId: String,
        itemId: String,
        reason: String = "play",
        forceFresh: Boolean = false,
    ): List<MediaSourceDto> = streams.getMediaSources(userId, itemId, reason, forceFresh)

    suspend fun getFailoverSources(userId: String, itemId: String, fresh: Boolean = false): com.jellio.tv.data.streams.FailoverLoad<MediaSourceDto> =
        streams.getFailoverSources(userId, itemId, fresh)

    suspend fun logFailover(body: com.jellio.tv.data.model.FailoverLogBody) = streams.logFailover(body)

    // Skips the short cache: a friend or another device may have changed the list.
    suspend fun getFreshWatchlistItems(userId: String, limit: Int = 100): List<BaseItemDto> {
        cache.invalidatePrefix("watchlist:$userId:")
        return getWatchlistItems(userId, limit)
    }

    suspend fun toggleFavorite(userId: String, item: BaseItemDto): Boolean {
        val isFavorite = item.UserData?.IsFavorite ?: false
        val result = if (isFavorite) {
            api.removeFavorite(userId, item.Id).IsFavorite
        } else {
            api.addFavorite(userId, item.Id).IsFavorite
        }
        cache.invalidatePrefix("watchlist:$userId:")
        // Back on the Watchlist means back in Coming Soon if it was taken out.
        if (result) catching { api.unhideFromComingSoon(item.Id) }
        return result
    }

    suspend fun hideFromComingSoon(itemId: String): Boolean =
        catching { api.hideFromComingSoon(itemId).isSuccessful }.getOrDefault(false)

    suspend fun setPlayed(userId: String, itemId: String, played: Boolean): UserItemDataDto {
        val result = if (played) api.markPlayed(userId, itemId) else api.markUnplayed(userId, itemId)
        cache.clear()
        return result
    }

    suspend fun clearResumePosition(userId: String, itemId: String): UserItemDataDto {
        val result = catching { api.updateUserData(itemId, userId, com.jellio.tv.data.model.UserDataUpdate(0L)) }
            .getOrElse {
                api.markPlayed(userId, itemId)
                api.markUnplayed(userId, itemId)
            }
        cache.clear()
        return result
    }

    suspend fun setRating(userId: String, itemId: String, likes: Boolean?): UserItemDataDto =
        if (likes == null) api.clearRating(userId, itemId) else api.setRating(userId, itemId, likes)

    fun prefetchStreams(itemId: String) = streams.prefetchStreams(itemId)

    fun prefetchStreamsBatch(itemIds: List<String>): kotlinx.coroutines.Job? = streams.prefetchStreamsBatch(itemIds)

    suspend fun getWarmSources(itemId: String): Map<String, String>? = streams.getWarmSources(itemId)

    fun recentWarmSources(itemId: String): Set<String>? = streams.recentWarmSources(itemId)

    suspend fun getQualityTiers(itemId: String): Map<String, String> = streams.getQualityTiers(itemId)

    fun probeSource(userId: String, itemId: String, mediaSourceId: String) = streams.probeSource(userId, itemId, mediaSourceId)

    fun prefetchStreamsInTurn(itemIds: List<String>) = streams.prefetchStreamsInTurn(itemIds)

    fun prewarmStreamUrl(streamUrl: String) = playback.prewarmStreamUrl(streamUrl)

    suspend fun getCalendarEntries(): List<CalendarEntryDto> = withRetry { api.getCalendarEntries() }

    suspend fun getFeed(): List<FeedEntryDto> = api.getFeed()

    suspend fun getProfile(userId: String): ProfileDto = api.getProfile(userId)

    suspend fun getAchievements(userId: String): AchievementsDto = api.getAchievements(userId)

    suspend fun setProfileBio(bio: String?) {
        api.setProfileBio(SetBioRequest(bio))
    }

    suspend fun getProfileSettings(): ProfileDto = api.getProfileSettings().also { profile ->
        syncRemoveFromWatchlistAfterWatching(profile.RemoveFromWatchlistAfterWatching)
        profile.subtitleStyle?.let { subtitleStylePreferences.applyServerStyle(it) }
    }

    suspend fun saveSubtitleStyle(style: com.jellio.tv.data.model.SubtitleStyleDto): ProfileDto =
        api.saveSubtitleStyle(style)

    suspend fun saveProfileRecommendations(
        selectedGenres: List<String>?,
        customGenres: List<com.jellio.tv.data.model.CustomGenreItem>?,
    ): ProfileDto = api.saveProfileRecommendations(
        com.jellio.tv.data.model.ProfileRecommendationsRequest(
            selectedGenres = selectedGenres,
            customGenres = customGenres,
        )
    )

    suspend fun setProfilePrivacy(isPrivate: Boolean) {
        api.setProfilePrivacy(SetPrivacyRequest(isPrivate))
    }

    suspend fun creditRealWatch(itemId: String) = api.creditRealWatch(RealWatchRequest(itemId))

    suspend fun reportRealDuration(itemId: String, durationTicks: Long) =
        api.reportRealDuration(ReportDurationRequest(itemId, durationTicks))

    suspend fun deleteProfileBanner() = api.deleteProfileBanner()

    suspend fun getJellioConfig(): ClientConfigDto? = config.getJellioConfig()

    suspend fun loadVideoTranscodePolicy(): String = config.loadVideoTranscodePolicy()

    suspend fun logPlaybackDecision(itemId: String, mediaSource: MediaSourceDto, directPlay: Boolean) =
        playback.logPlaybackDecision(itemId, mediaSource, directPlay)

    suspend fun startSleepTimer(minutes: Int): SleepTimerStatusDto = api.startSleepTimer(SleepTimerStartRequest(minutes))

    suspend fun cancelSleepTimer() {
        catching { api.cancelSleepTimer() }
    }

    suspend fun getSleepTimerStatus(): SleepTimerStatusDto? = catching { api.getSleepTimerStatus() }.getOrNull()

    suspend fun getUser(userId: String): UserDto =
        cache.get("user:$userId", CACHE_TTL_MS) { api.getUser(userId) }

    suspend fun getUserFresh(userId: String): UserDto {
        cache.invalidate("user:$userId")
        return getUser(userId)
    }

    suspend fun updateLanguagePreferences(userId: String, audioLanguage: String?, subtitleLanguage: String?) {
        val configuration = org.json.JSONObject(api.getUserJson(userId).string()).optJSONObject("Configuration")
            ?: org.json.JSONObject()
        val isOff = subtitleLanguage == "off" || subtitleLanguage == "none"
        configuration.put("AudioLanguagePreference", audioLanguage.orEmpty())
        configuration.put("SubtitleLanguagePreference", if (isOff) "" else subtitleLanguage.orEmpty())
        if (isOff) {
            configuration.put("SubtitleMode", "None")
        } else if (configuration.optString("SubtitleMode") == "None") {
            configuration.put("SubtitleMode", "Default")
        }
        api.updateUserConfiguration(userId, configuration.toString().toRequestBody("application/json".toMediaTypeOrNull()))
        cache.invalidate("user:$userId")
    }

    suspend fun updatePassword(userId: String, currentPassword: String, newPassword: String) {
        api.updatePassword(userId, UpdatePasswordRequest(CurrentPw = currentPassword, NewPw = newPassword))
    }

    suspend fun isQuickConnectEnabled(): Boolean = catching { api.isQuickConnectEnabled() }.getOrDefault(false)

    suspend fun authorizeQuickConnect(code: String): Boolean = api.authorizeQuickConnect(code)

    suspend fun initiateQuickConnect(serverAddress: String): com.jellio.tv.data.model.QuickConnectResultDto? = catching {
        sessionManager.saveServerAddress(com.jellio.tv.di.normalizeServerAddress(serverAddress))
        api.initiateQuickConnect()
    }.getOrNull()

    suspend fun checkQuickConnect(secret: String): com.jellio.tv.data.model.QuickConnectResultDto? = catching {
        api.checkQuickConnect(secret)
    }.getOrNull()

    suspend fun authenticateWithQuickConnect(serverAddress: String, secret: String): LoginResult {
        return try {
            val normalized = resolveServerAddress(serverAddress).getOrElse {
                return LoginResult.Failure(loginFailureOf(it, LoginFailure.SERVER_UNREACHABLE))
            }
            val result = api.authenticateWithQuickConnect(com.jellio.tv.data.model.QuickConnectAuthBody(secret))
            val user = result.User ?: return LoginResult.Failure(LoginFailure.INVALID_USER_RESPONSE)
            val token = result.AccessToken ?: return LoginResult.Failure(LoginFailure.MISSING_TOKEN)
            clearCache()
            sessionManager.saveSession(normalized, token, user.Id, user.Name)
            rememberUser(normalized, user.Id, token, user.Name, user.PrimaryImageTag)
            LoginResult.Success
        } catch (err: Exception) {
            android.util.Log.w("JellioAuth", "Quick Connect sign in failed", err)
            LoginResult.Failure(loginFailureOf(err, LoginFailure.QUICK_CONNECT_FAILED, rejected = LoginFailure.QUICK_CONNECT_FAILED))
        }
    }

    suspend fun getAvatarPresets(): List<AvatarPresetDto> = api.getAvatarPresets()

    fun avatarPresetUrl(serverAddress: String, accessToken: String, id: String): String =
        imageUrls.avatarPresetUrl(serverAddress, accessToken, id)

    suspend fun setUserAvatarFromPreset(userId: String, presetId: String) {
        val response = api.getAvatarPresetImage(encodeAvatarId(presetId))
        val (bytes, contentType) = withContext(Dispatchers.IO) {
            response.bytes() to (response.contentType()?.toString() ?: "image/png")
        }
        uploadUserAvatarBlob(userId, bytes, contentType)
    }

    suspend fun setUserAvatarFromBytes(userId: String, bytes: ByteArray, contentType: String) {
        uploadUserAvatarBlob(userId, bytes, contentType)
    }

    private suspend fun uploadUserAvatarBlob(userId: String, bytes: ByteArray, contentType: String) {
        val body = withContext(Dispatchers.Default) {
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            base64.toRequestBody(contentType.toMediaTypeOrNull())
        }
        api.uploadUserAvatar(userId, body)
        cache.invalidate("user:$userId")
    }

    fun prefetchPlayback(userId: String, itemId: String, mediaSourceId: String?, startTicks: Long) =
        playback.prefetchPlayback(userId, itemId, mediaSourceId, startTicks)

    suspend fun awaitPrefetchedPlayback(itemId: String, mediaSourceId: String?, startTicks: Long): PlaybackTarget? =
        playback.awaitPrefetchedPlayback(itemId, mediaSourceId, startTicks)

    suspend fun warmFirstBytes(itemId: String, streamUrl: String) = playback.warmFirstBytes(itemId, streamUrl)

    fun claimFirstBytesWarm(itemId: String) = playback.claimFirstBytesWarm(itemId)

    suspend fun takePrefetchedPlayback(itemId: String, mediaSourceId: String?, startTicks: Long): PlaybackTarget? =
        playback.takePrefetchedPlayback(itemId, mediaSourceId, startTicks)

    suspend fun resolvePlayback(
        userId: String,
        itemId: String,
        mediaSourceId: String?,
        startTimeTicks: Long,
        burnInSubtitleStreamIndex: Int? = null,
        audioStreamIndex: Int? = null,
        forceTranscode: Boolean = false,
        forceDirectPlay: Boolean = false,
    ): PlaybackTarget = playback.resolvePlayback(
        userId,
        itemId,
        mediaSourceId,
        startTimeTicks,
        burnInSubtitleStreamIndex,
        audioStreamIndex,
        forceTranscode,
        forceDirectPlay,
    )

    fun getAudioStreams(mediaSource: MediaSourceDto): List<MediaStreamDto> = playback.getAudioStreams(mediaSource)

    suspend fun buildSubtitleUrl(itemId: String, mediaSourceId: String, stream: MediaStreamDto, offsetTicks: Long = 0): String =
        playback.buildSubtitleUrl(itemId, mediaSourceId, stream, offsetTicks)

    internal fun canDirectPlayAudio(codec: String?): Boolean = playback.canDirectPlayAudio(codec)

    internal fun canDirectPlay(mediaSource: MediaSourceDto, audioStreamIndex: Int? = null, ignoreAudio: Boolean = false): Boolean =
        playback.canDirectPlay(mediaSource, audioStreamIndex, ignoreAudio)

    suspend fun reportPlaybackStart(itemId: String, mediaSourceId: String?, positionTicks: Long) =
        playback.reportPlaybackStart(itemId, mediaSourceId, positionTicks)

    fun reportPlayStart(record: com.jellio.tv.data.model.PlayStartRecord) = playback.reportPlayStart(record)

    suspend fun reportPlaybackProgress(itemId: String, mediaSourceId: String?, positionTicks: Long, isPaused: Boolean) =
        playback.reportPlaybackProgress(itemId, mediaSourceId, positionTicks, isPaused)

    suspend fun reportPlaybackStopped(itemId: String, mediaSourceId: String?, positionTicks: Long) =
        playback.reportPlaybackStopped(itemId, mediaSourceId, positionTicks)

    suspend fun reportCapabilities(): Result<Unit> = playback.reportCapabilities()

    fun imageUrl(
        serverAddress: String,
        itemId: String,
        tag: String?,
        imageType: String = "Primary",
        maxWidth: Int = 400,
        accessToken: String? = null,
    ): String = imageUrls.imageUrl(serverAddress, itemId, tag, imageType, maxWidth, accessToken)

}

internal fun normalizeStreamContainer(rawContainer: String?): String {
    val first = rawContainer?.lowercase()?.split(",")?.firstOrNull()?.trim().orEmpty()
    return when (first) {
        "", "mov" -> "mp4"
        "matroska" -> "mkv"
        else -> first
    }
}

internal fun generateDeterministicVirtualId(kind: String, tmdbId: String): String {
    val md = java.security.MessageDigest.getInstance("MD5")
    val bytes = md.digest("jellio:virtual:$kind:$tmdbId".toByteArray(Charsets.UTF_8))
    val buffer = java.nio.ByteBuffer.wrap(bytes)
    buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val d1 = buffer.getInt().toLong() and 0xFFFFFFFFL
    val d2 = buffer.getShort().toInt() and 0xFFFF
    val d3 = buffer.getShort().toInt() and 0xFFFF
    val d4 = ByteArray(8)
    buffer.get(d4)
    val d4Hex = d4.joinToString("") { "%02x".format(it) }
    return "%08x-%04x-%04x-%s-%s".format(d1, d2, d3, d4Hex.substring(0, 4), d4Hex.substring(4))
}

