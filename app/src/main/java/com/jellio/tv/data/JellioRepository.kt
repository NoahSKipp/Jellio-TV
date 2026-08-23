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

    suspend fun getContinueWatching(userId: String): List<BaseItemDto> =
        api.getResumeItems(userId, fields = ITEM_FIELDS).Items

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
