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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFETCH_WINDOW_MS = 8 * 60_000L
private const val PREFETCH_SLOT_WAIT_MS = 3_000L
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

internal const val DETAIL_FIELDS = "Overview,Genres,People,ProductionYear,RunTimeTicks,PremiereDate,RemoteTrailers," +
    "BackdropImageTags,OfficialRating,CommunityRating,ParentBackdropItemId,ParentBackdropImageTags"
private const val SHOWCASE_FIELDS = "Overview,Genres,ProductionYear,RunTimeTicks,PremiereDate,CommunityRating,BackdropImageTags," +
    "ParentBackdropItemId,ParentBackdropImageTags"

private val ANIME_VIEW_NAME = Regex("anime", RegexOption.IGNORE_CASE)
private val ANIME_COLLECTION_NAME = Regex("anime|anilist|kitsu", RegexOption.IGNORE_CASE)
private const val ANIME_ITEM_ID_LIMIT = 500

sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(val message: String) : LoginResult
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

private val DIRECT_PLAY_CONTAINERS = setOf("mp4", "webm", "m4v", "mkv", "m2ts", "ts", "avi")
private val DIRECT_PLAY_VIDEO_CODECS = setOf("h264", "avc", "hevc", "h265", "vp8", "vp9", "av1", "mpeg4")
private val DIRECT_PLAY_AUDIO_CODECS = setOf("aac", "mp3", "opus", "vorbis", "flac", "ac3", "eac3", "dts", "truehd")

private const val FALLBACK_VIDEO_BITRATE = 20000000L
private const val COPY_RANGE_TYPES = "SDR,HDR10,HDR10Plus,HLG,DOVIWithHDR10,DOVIWithHLG,DOVIWithSDR,DOVIWithHDR10Plus"

private const val CACHE_TTL_MS = 60_000L
private const val SHORT_CACHE_TTL_MS = 8_000L
private const val PLAYBACK_PREFETCH_TTL_MS = 45_000L
// Same as the web row cache's genres/people class: a title's genre and
// cast neighbours barely change, so Home refreshes reuse them.
private const val RECOMMENDATION_CACHE_TTL_MS = 30 * 60_000L

internal class TtlCache {
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
) {
    val sessionFlow: Flow<Session?> = sessionManager.sessionFlow

    // One device id on every request, so Jellyfin lists a single device and session per TV.
    val deviceId: String get() = sessionManager.getCachedDeviceId()
    internal val cache = TtlCache()

    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sourceCache = com.jellio.tv.data.streams.SourceCache<MediaSourceDto>()
    private val sourcesInFlight = ConcurrentHashMap<String, Deferred<List<MediaSourceDto>>>()
    private val prefetchedAt = ConcurrentHashMap<String, Long>()
    // One warm-up request to the server at a time, whoever asks.
    private val prefetchSlots = java.util.concurrent.Semaphore(1)
    // Titles the server said are already warm.
    private val knownWarm = ConcurrentHashMap.newKeySet<String>()

    fun clearCache() {
        cache.clear()
        preloaded.clear()
        recentLists.clear()
        sourceCache.clear()
        prefetchedAt.clear()
    }

    suspend fun knownServerAddress(): String? = sessionManager.serverAddress()

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
            val result = api.authenticateByName(AuthenticateByNameRequest(username, password))
            clearCache()
            sessionManager.saveSession(normalized, result.AccessToken, result.User.Id, result.User.Name)
            rememberUser(normalized, result.User.Id, result.AccessToken, result.User.Name, result.User.PrimaryImageTag)
            LoginResult.Success
        } catch (err: Exception) {
            LoginResult.Failure(err.message ?: "Could not reach that server")
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

    fun bannerUrl(serverAddress: String, accessToken: String, userId: String): String {
        val cleanServer = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        return "$cleanServer/Jellio/profile/banner/$userId?ApiKey=$accessToken&api_key=$accessToken&t=${System.currentTimeMillis()}"
    }

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

    suspend fun getLibraryNavEntries(userId: String): List<BaseItemDto> {
        val views = try {
            getLibraries(userId)
        } catch (err: Exception) {
            emptyList()
        }

        val moviesView = views.firstOrNull { it.CollectionType == "movies" }
        val tvView = views.firstOrNull { it.CollectionType == "tvshows" }
        val realAnimeView = views.firstOrNull { ANIME_VIEW_NAME.containsMatchIn(it.Name ?: "") }

        val animeEntry = realAnimeView ?: tvView?.copy(Name = "Anime")
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
            "Authorization" to com.jellio.tv.data.network.buildEmbyAuthorizationHeader(sessionManager.getCachedDeviceId(), com.jellio.tv.data.network.APP_VERSION, token),
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

    fun collectionKind(collection: BaseItemDto): String {
        if (isAnimeCollection(collection)) return "tvshows"
        val stremio = collection.ProviderIds?.get("Stremio") ?: collection.ProviderIds?.get("stremio")
        if (!stremio.isNullOrEmpty()) {
            return if (stremio.substringBefore('.').lowercase() == "movie") "movies" else "tvshows"
        }
        return "movies"
    }

    fun isAnimeLibrary(library: BaseItemDto): Boolean = ANIME_VIEW_NAME.containsMatchIn(library.Name ?: "")

    suspend fun getAnimeItemIds(userId: String): Set<String> = coroutineScope {
        val animeCollections = runCatching { getCollections(userId) }.getOrDefault(emptyList()).filter { isAnimeCollection(it) }
        if (animeCollections.isEmpty()) return@coroutineScope emptySet()
        animeCollections.map { collection ->
            async {
                runCatching { getCollectionItems(userId, collection.Id, "tvshows", ANIME_ITEM_ID_LIMIT) }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().mapTo(mutableSetOf()) { it.Id }
    }

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
        api.getResumeItems(userId, fields = "$ITEM_FIELDS,RunTimeTicks").Items

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

    suspend fun getSeriesNextUp(seriesId: String, userId: String): BaseItemDto? =
        api.getNextUp(userId, limit = 1, fields = "PrimaryImageAspectRatio,RunTimeTicks", seriesId = seriesId)
            .Items.firstOrNull()

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
        cachedUnlessEmpty("genre:$userId:$parentId:$itemType:$genre:$limit") { fetchGenreItems(userId, parentId, itemType, genre, limit) }

    // One page of a genre, for grids that keep loading as they are scrolled.
    suspend fun getGenreItemsPage(userId: String, parentId: String?, itemType: String, genre: String, startIndex: Int, limit: Int): List<BaseItemDto> =
        api.getItems(
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
        ).Items

    private suspend fun fetchGenreItems(userId: String, parentId: String?, itemType: String, genre: String, limit: Int): List<BaseItemDto> =
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
            ).Items
        }

    suspend fun getItemDetails(userId: String, itemId: String, forPlayer: Boolean = false): BaseItemDto =
        cache.get("details:$itemId", SHORT_CACHE_TTL_MS) {
            fetchItemInfo(userId, itemId, DETAIL_FIELDS)
        }.also {
            previews[it.Id] = it
            preloaded[itemId] = System.currentTimeMillis() to it
        }

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

    private suspend fun <T> withRetry(maxAttempts: Int = 3, initialDelayMs: Long = 1000L, block: suspend () -> T): T {
        var currentDelay = initialDelayMs
        repeat(maxAttempts - 1) {
            try {
                return block()
            } catch (e: Exception) {
                val isTransient = (e is HttpException && e.code() in 500..504) || e is IOException
                if (isTransient) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * 1.5).toLong()
                } else {
                    throw e
                }
            }
        }
        return block()
    }

    private suspend fun fetchItemInfo(userId: String, itemId: String, fields: String?): BaseItemDto =
        withRetry {
            runCatching { api.getItemsByIds(userId, itemId, fields).Items.firstOrNull() }.getOrNull()
                ?: api.getItem(userId, itemId, fields)
        }

    suspend fun getPerson(userId: String, personId: String): BaseItemDto = fetchItemInfo(userId, personId, null)

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
        cache.get("seasons:$seriesId", SHORT_CACHE_TTL_MS) { withRetry { api.getSeasons(seriesId, userId).Items } }
            .also { recentLists["seasons:$seriesId"] = System.currentTimeMillis() to it }

    // Seasons and episodes as last seen, to draw a series page at once.
    private val recentLists = ConcurrentHashMap<String, Pair<Long, List<BaseItemDto>>>()

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
            withRetry {
                api.getEpisodes(seriesId, userId, seasonId, fields = "Overview,PrimaryImageAspectRatio").Items
            }
        }.also {
            seedPreviews(it)
            if (recentLists.size > 300) recentLists.clear()
            recentLists["episodes:$seriesId:$seasonId"] = System.currentTimeMillis() to it
        }

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

    suspend fun getIntroSkipperSegments(itemId: String): IntroSkipperSegmentsDto? =
        runCatching { api.getIntroSkipperSegments(itemId) }.getOrNull()

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
        val item = withRetry { api.getItem(userId, itemId, fields = "MediaSources,OriginalTitle,PremiereDate,ProductionYear") }
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
        val result = if (isFavorite) {
            api.removeFavorite(userId, item.Id).IsFavorite
        } else {
            api.addFavorite(userId, item.Id).IsFavorite
        }
        cache.invalidatePrefix("watchlist:$userId:")
        // Back on the Watchlist means back in Coming Soon if it was taken out.
        if (result) runCatching { api.unhideFromComingSoon(item.Id) }
        return result
    }

    suspend fun hideFromComingSoon(itemId: String): Boolean =
        runCatching { api.hideFromComingSoon(itemId).isSuccessful }.getOrDefault(false)

    suspend fun setPlayed(userId: String, itemId: String, played: Boolean): UserItemDataDto {
        val result = if (played) api.markPlayed(userId, itemId) else api.markUnplayed(userId, itemId)
        cache.clear()
        return result
    }

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

    suspend fun deleteItem(itemId: String) = api.deleteItem(itemId)

    suspend fun canDeleteItems(userId: String): Boolean {
        val policy = runCatching { getUser(userId) }.getOrNull()?.Policy ?: return false
        return policy.IsAdministrator || policy.EnableContentDeletion
    }

    fun isKnownWarm(itemId: String): Boolean =
        knownWarm.contains(com.jellio.tv.data.streams.normalizeSourceId(itemId))

    fun prefetchStreams(itemId: String) {
        val now = System.currentTimeMillis()
        val last = prefetchedAt[itemId]
        if (last != null && now - last < PREFETCH_WINDOW_MS) return
        if (isKnownWarm(itemId)) return
        if (sourceCache.get(itemId, now) != null || sourcesInFlight.containsKey(itemId)) return
        prefetchedAt[itemId] = now
        prefetchScope.launch {
            // Waits its turn briefly; given up, a later dwell tries again.
            if (!acquirePrefetchSlot(PREFETCH_SLOT_WAIT_MS)) {
                prefetchedAt.remove(itemId)
                return@launch
            }
            com.jellio.tv.data.streams.StreamLookupLog.prefetch(itemId)
            try {
                runCatching { api.prefetchGelatoStreams(itemId) }
            } finally {
                prefetchSlots.release()
            }
        }
    }

    private suspend fun acquirePrefetchSlot(waitMs: Long): Boolean {
        val until = System.currentTimeMillis() + waitMs
        while (!prefetchSlots.tryAcquire()) {
            if (System.currentTimeMillis() >= until) return false
            delay(100)
        }
        return true
    }

    // Stream lists only, a few titles in one call; the job is cancelled
    // when the search that asked for it changes.
    fun prefetchStreamsBatch(itemIds: List<String>): kotlinx.coroutines.Job? {
        val ids = itemIds.filter { it.isNotBlank() && !isKnownWarm(it) }.distinct()
        if (ids.isEmpty()) return null
        return prefetchScope.launch {
            if (!acquirePrefetchSlot(PREFETCH_SLOT_WAIT_MS)) return@launch
            try {
                val response = runCatching {
                    api.prefetchGelatoBatch(com.jellio.tv.data.model.PrefetchBatchRequest(ids, Probe = false))
                }.getOrNull()
                response?.body()?.AlreadyWarm.orEmpty().forEach {
                    knownWarm.add(com.jellio.tv.data.streams.normalizeSourceId(it))
                }
            } finally {
                prefetchSlots.release()
            }
        }
    }

    // { sourceId: warmedAt } for sources that start fast now; null when the
    // server can't say.
    suspend fun getWarmSources(itemId: String): Map<String, String>? = runCatching {
        val response = api.getWarmSources(itemId)
        if (response.isSuccessful) response.body().orEmpty() else null
    }.getOrNull()

    suspend fun getQualityTiers(itemId: String): Map<String, String> = runCatching {
        val response = api.getQualityTiers(itemId)
        if (response.isSuccessful) response.body().orEmpty() else emptyMap()
    }.getOrDefault(emptyMap())

    // The probe Play would run for one particular source, so picking it
    // later starts fast.
    fun probeSource(userId: String, itemId: String, mediaSourceId: String) {
        prefetchScope.launch {
            if (!acquirePrefetchSlot(PREFETCH_SLOT_WAIT_MS)) return@launch
            try {
                runCatching {
                    api.getPlaybackInfo(itemId, PlaybackInfoRequest(UserId = userId, MediaSourceId = mediaSourceId))
                }
            } finally {
                prefetchSlots.release()
            }
        }
    }

    private var queuedPrefetch: kotlinx.coroutines.Job? = null

    // One title at a time, so a slot stays free for whatever gets focus.
    fun prefetchStreamsInTurn(itemIds: List<String>) {
        queuedPrefetch?.cancel()
        queuedPrefetch = prefetchScope.launch {
            for (itemId in itemIds) {
                val now = System.currentTimeMillis()
                val last = prefetchedAt[itemId]
                if (last != null && now - last < PREFETCH_WINDOW_MS) continue
                if (sourceCache.get(itemId, now) != null || sourcesInFlight.containsKey(itemId)) continue
                while (!prefetchSlots.tryAcquire()) delay(500)
                prefetchedAt[itemId] = System.currentTimeMillis()
                com.jellio.tv.data.streams.StreamLookupLog.prefetch(itemId)
                try {
                    runCatching { api.prefetchGelatoStreams(itemId) }
                } finally {
                    prefetchSlots.release()
                }
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

    suspend fun getFeed(): List<FeedEntryDto> = api.getFeed()

    suspend fun getProfile(userId: String): ProfileDto = api.getProfile(userId)

    suspend fun getAchievements(userId: String): AchievementsDto = api.getAchievements(userId)

    suspend fun setProfileBio(bio: String?) {
        api.setProfileBio(SetBioRequest(bio))
    }

    suspend fun getProfileSettings(): ProfileDto = api.getProfileSettings()

    suspend fun setProfilePrivacy(isPrivate: Boolean) {
        api.setProfilePrivacy(SetPrivacyRequest(isPrivate))
    }

    suspend fun creditRealWatch(itemId: String) = api.creditRealWatch(RealWatchRequest(itemId))

    suspend fun reportRealDuration(itemId: String, durationTicks: Long) =
        api.reportRealDuration(ReportDurationRequest(itemId, durationTicks))

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

    suspend fun cancelSleepTimer() {
        runCatching { api.cancelSleepTimer() }
    }

    suspend fun getSleepTimerStatus(): SleepTimerStatusDto? = runCatching { api.getSleepTimerStatus() }.getOrNull()

    suspend fun getUser(userId: String): UserDto =
        cache.get("user:$userId", CACHE_TTL_MS) { api.getUser(userId) }

    suspend fun getUserFresh(userId: String): UserDto {
        cache.invalidate("user:$userId")
        return getUser(userId)
    }

    suspend fun updateLanguagePreferences(userId: String, audioLanguage: String?, subtitleLanguage: String?) {
        val current = api.getUser(userId).Configuration ?: UserConfigurationDto()
        val isOff = subtitleLanguage == "off" || subtitleLanguage == "none"
        val configuration = current.copy(
            AudioLanguagePreference = audioLanguage.orEmpty(),
            SubtitleLanguagePreference = if (isOff) "" else subtitleLanguage.orEmpty(),
            SubtitleMode = if (isOff) "None" else if (current.SubtitleMode == "None") "Default" else current.SubtitleMode,
        )
        api.updateUserConfiguration(userId, configuration)
        cache.invalidate("user:$userId")
    }

    suspend fun updatePassword(userId: String, currentPassword: String, newPassword: String) {
        api.updatePassword(userId, UpdatePasswordRequest(CurrentPw = currentPassword, NewPw = newPassword))
    }

    suspend fun isQuickConnectEnabled(): Boolean = runCatching { api.isQuickConnectEnabled() }.getOrDefault(false)

    suspend fun authorizeQuickConnect(code: String): Boolean = api.authorizeQuickConnect(code)

    suspend fun initiateQuickConnect(serverAddress: String): com.jellio.tv.data.model.QuickConnectResultDto? = runCatching {
        sessionManager.saveServerAddress(com.jellio.tv.di.normalizeServerAddress(serverAddress))
        api.initiateQuickConnect()
    }.getOrNull()

    suspend fun checkQuickConnect(secret: String): com.jellio.tv.data.model.QuickConnectResultDto? = runCatching {
        api.checkQuickConnect(secret)
    }.getOrNull()

    suspend fun authenticateWithQuickConnect(serverAddress: String, secret: String): LoginResult {
        return try {
            val normalized = resolveServerAddress(serverAddress).getOrElse {
                return LoginResult.Failure(it.message ?: "Could not reach that server")
            }
            val result = api.authenticateWithQuickConnect(com.jellio.tv.data.model.QuickConnectAuthBody(secret))
            val user = result.User ?: return LoginResult.Failure("Invalid user response from server")
            val token = result.AccessToken ?: return LoginResult.Failure("Missing access token")
            clearCache()
            sessionManager.saveSession(normalized, token, user.Id, user.Name)
            rememberUser(normalized, user.Id, token, user.Name, user.PrimaryImageTag)
            LoginResult.Success
        } catch (err: Exception) {
            LoginResult.Failure(err.message ?: "Quick Connect authentication failed")
        }
    }

    suspend fun getAvatarPresets(): List<AvatarPresetDto> = api.getAvatarPresets()

    private fun encodeAvatarId(id: String): String =
        id.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }

    fun avatarPresetUrl(serverAddress: String, accessToken: String, id: String): String =
        "$serverAddress/Jellio/avatars/${encodeAvatarId(id)}?ApiKey=$accessToken&api_key=$accessToken"

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

    private class PrefetchedPlayback(val at: Long, val target: kotlinx.coroutines.Deferred<PlaybackTarget?>)
    private val playbackPrefetch = ConcurrentHashMap<String, PrefetchedPlayback>()

    private fun playbackKey(itemId: String, mediaSourceId: String?, startTicks: Long) = "$itemId|${mediaSourceId.orEmpty()}|$startTicks"

    fun prefetchPlayback(userId: String, itemId: String, mediaSourceId: String?, startTicks: Long) {
        val now = System.currentTimeMillis()
        playbackPrefetch.entries.removeIf { now - it.value.at > PLAYBACK_PREFETCH_TTL_MS }
        val key = playbackKey(itemId, mediaSourceId, startTicks)
        if (playbackPrefetch.containsKey(key)) return
        playbackPrefetch[key] = PrefetchedPlayback(
            now,
            prefetchScope.async { runCatching { resolvePlayback(userId, itemId, mediaSourceId, startTicks) }.getOrNull() },
        )
    }

    suspend fun takePrefetchedPlayback(itemId: String, mediaSourceId: String?, startTicks: Long): PlaybackTarget? {
        val now = System.currentTimeMillis()
        val keys = listOf(playbackKey(itemId, mediaSourceId, startTicks), playbackKey(itemId, null, startTicks)).distinct()
        for (key in keys) {
            val entry = playbackPrefetch.remove(key) ?: continue
            if (now - entry.at > PLAYBACK_PREFETCH_TTL_MS) continue
            val target = entry.target.await() ?: continue
            if (mediaSourceId == null || target.mediaSource.Id == mediaSourceId) return target
        }
        return null
    }

    suspend fun resolvePlayback(
        userId: String,
        itemId: String,
        mediaSourceId: String?,
        startTimeTicks: Long,
        burnInSubtitleStreamIndex: Int? = null,
        audioStreamIndex: Int? = null,
        forceTranscode: Boolean = false,
        forceDirectPlay: Boolean = false,
    ): PlaybackTarget {
        val response = api.getPlaybackInfo(
            itemId,
            PlaybackInfoRequest(
                UserId = userId,
                StartTimeTicks = startTimeTicks,
                MediaSourceId = mediaSourceId,
                AudioStreamIndex = audioStreamIndex,
                SubtitleStreamIndex = burnInSubtitleStreamIndex,
            ),
        )
        val mediaSource = response.MediaSources.firstOrNull { it.Id == mediaSourceId }
            ?: response.MediaSources.firstOrNull()
            ?: throw IllegalStateException("No playable source for this title")

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
        val directPlay = forceDirectPlay || (!shouldTranscode && canDirectPlay(mediaSource, audioStreamIndex))
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

    fun getAudioStreams(mediaSource: MediaSourceDto): List<MediaStreamDto> =
        mediaSource.MediaStreams?.filter { it.Type == "Audio" } ?: emptyList()

    private fun estimateVideoBitrate(mediaSource: MediaSourceDto): Long {
        val video = mediaSource.MediaStreams?.firstOrNull { it.Type == "Video" }
        return video?.BitRate ?: mediaSource.Bitrate ?: FALLBACK_VIDEO_BITRATE
    }

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

    // ignoreAudio: whether the picture and container alone can be played as is.
    internal fun canDirectPlay(mediaSource: MediaSourceDto, audioStreamIndex: Int? = null, ignoreAudio: Boolean = false): Boolean {
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
        if (!ignoreAudio && audio != null && !canDirectPlayAudio(audio.Codec)) return false
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
