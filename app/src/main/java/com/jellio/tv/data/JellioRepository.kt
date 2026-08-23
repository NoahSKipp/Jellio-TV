package com.jellio.tv.data

import com.jellio.tv.data.model.AuthenticateByNameRequest
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private const val APP_VERSION = "0.1.0"

// BackdropImageTags is not included by default (real bug found live:
// the hero backdrop silently never loaded because this app never
// asked the server whether one even existed), same real field
// screens/home.js's own hero already knows to ask for.
private const val ITEM_FIELDS = "PrimaryImageAspectRatio,BackdropImageTags"

// Two distinct real patterns, same distinction navShared.js's own
// getPrimaryNavLinks()/isAnimeCollection() draw: a real hand-made
// Anime library is only ever literally named that, but a collection
// with no Stremio provider id to fall back on (anything imported
// before Gelato started writing one, or made by hand) is matched
// more loosely.
private val ANIME_VIEW_NAME = Regex("anime", RegexOption.IGNORE_CASE)
private val ANIME_COLLECTION_NAME = Regex("anime|anilist|kitsu", RegexOption.IGNORE_CASE)

sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(val message: String) : LoginResult
}

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

    suspend fun getCollections(userId: String): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            includeItemTypes = "BoxSet",
            recursive = true,
            limit = 200,
            sortBy = "SortName",
            fields = "ProviderIds,ChildCount",
        ).Items

    // Gelato's own GetOrCreateBoxSetAsync writes a collection's
    // ProviderIds.Stremio as "{catalogType}.{catalogId}", catalogType
    // being the literal type string configured on that catalog in
    // AIOStreams: "movie", "series", or "anime". A real signal
    // straight from Gelato, not a guess off a name a reader can
    // rename freely.
    private fun isAnimeCollection(collection: BaseItemDto): Boolean {
        val stremio = collection.ProviderIds?.get("Stremio") ?: collection.ProviderIds?.get("stremio")
        if (!stremio.isNullOrEmpty()) {
            return stremio.substringBefore('.').lowercase() == "anime"
        }
        return ANIME_COLLECTION_NAME.containsMatchIn(collection.Name ?: "")
    }

    suspend fun getContinueWatching(userId: String): List<BaseItemDto> =
        api.getResumeItems(userId, fields = ITEM_FIELDS).Items

    suspend fun getNextUp(userId: String, limit: Int = 20): List<BaseItemDto> =
        api.getNextUp(userId, limit, fields = ITEM_FIELDS).Items

    // Mirrors runtime/api.js's own getHeroCandidates(): a real random
    // Movie/Series, never an Episode (the web hero never once asks for
    // one), so there is no episode title/art to ever land in a hero in
    // the first place, same real reasoning as the fix itself rather
    // than a patch bolted onto whatever a Continue Watching item
    // happened to be.
    suspend fun getHeroCandidates(userId: String, limit: Int = 8): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            includeItemTypes = "Movie,Series",
            recursive = true,
            limit = limit,
            sortBy = "Random",
            fields = ITEM_FIELDS,
        ).Items

    suspend fun getLibraryItems(userId: String, parentId: String, limit: Int = 24): List<BaseItemDto> =
        api.getItems(
            userId = userId,
            parentId = parentId,
            recursive = true,
            limit = limit,
            sortBy = "DateCreated",
            sortOrder = "Descending",
            fields = ITEM_FIELDS,
        ).Items

    suspend fun toggleFavorite(userId: String, item: BaseItemDto): Boolean {
        val isFavorite = item.UserData?.IsFavorite ?: false
        return if (isFavorite) {
            api.removeFavorite(userId, item.Id).IsFavorite
        } else {
            api.addFavorite(userId, item.Id).IsFavorite
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
