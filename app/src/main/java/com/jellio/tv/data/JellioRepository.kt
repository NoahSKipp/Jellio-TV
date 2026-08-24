package com.jellio.tv.data

import com.jellio.tv.data.model.AuthenticateByNameRequest
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.data.model.MediaSourceDto
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
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.session.SessionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
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
    "BackdropImageTags,OfficialRating,CommunityRating,ParentBackdropItemId,ParentBackdropImageTags"

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

// The one real place both auth and every other Jellyfin call go
// through, mirroring runtime/auth.js and runtime/api.js's own
// combined real job on the web side.
@Singleton
class JellioRepository @Inject constructor(
    private val api: JellyfinApi,
    private val sessionManager: SessionManager,
) {
    val sessionFlow: Flow<Session?> = sessionManager.sessionFlow

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
            sessionManager.saveSession(normalized, result.AccessToken, result.User.Id, result.User.Name)
            LoginResult.Success
        } catch (err: Exception) {
            LoginResult.Failure(err.message ?: "Could not reach that server")
        }
    }

    suspend fun logout() = sessionManager.clearSession()

    suspend fun getLibraries(userId: String): List<BaseItemDto> = api.getUserViews(userId).Items

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
    suspend fun getCollections(userId: String): List<BaseItemDto> {
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
        return collected
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

    suspend fun getContinueWatching(userId: String): List<BaseItemDto> =
        api.getResumeItems(userId, fields = ITEM_FIELDS).Items

    suspend fun getNextUp(userId: String, limit: Int = 20): List<BaseItemDto> =
        api.getNextUp(userId, limit, fields = ITEM_FIELDS, enableResumable = false).Items

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
        api.getItems(
            userId = userId,
            includeItemTypes = "Movie,Series",
            recursive = true,
            limit = limit,
            filters = "IsFavorite",
            fields = ITEM_FIELDS,
        ).Items

    suspend fun getItemDetails(userId: String, itemId: String): BaseItemDto =
        api.getItem(userId, itemId, fields = DETAIL_FIELDS)

    suspend fun getItem(userId: String, itemId: String): BaseItemDto = api.getItem(userId, itemId)

    // screens/person.js's own real getPerson(): a plain item lookup,
    // real Jellyfin Person items share the same BaseItemDto shape as
    // everything else this app already reads.
    suspend fun getPerson(userId: String, personId: String): BaseItemDto = api.getItem(userId, personId)

    // Real endpoint, GET /Items?personIds=X (Jellyfin.Api's own
    // ItemsController, confirmed against screens/person.js's own
    // getPersonFilmography() before porting this): every real Movie/
    // Series this person is credited on.
    suspend fun getPersonFilmography(userId: String, personId: String, limit: Int = 50): List<BaseItemDto> =
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

    suspend fun getSeasons(seriesId: String, userId: String): List<BaseItemDto> =
        api.getSeasons(seriesId, userId).Items

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
        api.getEpisodes(seriesId, userId, seasonId, fields = "Overview,PrimaryImageAspectRatio").Items

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

    suspend fun getCalendarEntries(): List<CalendarEntryDto> = api.getCalendarEntries()

    suspend fun getUser(userId: String): UserDto = api.getUser(userId)

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
    }

    suspend fun updatePassword(userId: String, currentPassword: String, newPassword: String) {
        api.updatePassword(userId, UpdatePasswordRequest(CurrentPw = currentPassword, NewPw = newPassword))
    }

    suspend fun isQuickConnectEnabled(): Boolean = runCatching { api.isQuickConnectEnabled() }.getOrDefault(false)

    suspend fun authorizeQuickConnect(code: String): Boolean = api.authorizeQuickConnect(code)

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
    ): PlaybackTarget {
        val response = api.getPlaybackInfo(
            itemId,
            PlaybackInfoRequest(UserId = userId, StartTimeTicks = startTimeTicks, MediaSourceId = mediaSourceId),
        )
        val mediaSource = response.MediaSources.firstOrNull { it.Id == mediaSourceId }
            ?: response.MediaSources.firstOrNull()
            ?: throw IllegalStateException("No playable source for this title")

        // Mirrors runtime/api.js's own buildStreamUrl() exactly: always
        // this app's own /Videos/{id}/stream.{container} endpoint, never
        // MediaSourceInfo.TranscodingUrl (that file never once reads
        // that field either, a forced transcode is this same endpoint
        // with VideoCodec/AudioCodec params instead of Static=true).
        val directPlay = burnInSubtitleStreamIndex == null && canDirectPlay(mediaSource)
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
            if (burnInSubtitleStreamIndex != null) {
                append("&SubtitleStreamIndex=").append(burnInSubtitleStreamIndex)
                append("&SubtitleMethod=Encode")
            }
        }

        return PlaybackTarget(streamUrl, mediaSource, response.PlaySessionId, startTimeTicks)
    }

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
