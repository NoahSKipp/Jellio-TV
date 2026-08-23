package com.jellio.tv.data.network

import com.jellio.tv.data.model.AuthenticateByNameRequest
import com.jellio.tv.data.model.AuthenticationResultDto
import com.jellio.tv.data.model.ItemsResultDto
import com.jellio.tv.data.model.PublicSystemInfoDto
import com.jellio.tv.data.model.UserItemDataDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Every call here is a relative path against the Jellyfin server
// NetworkModule's own base-url interceptor swaps in per request: the
// same real REST surface frontend/runtime/api.js already calls from
// the web build, called from Kotlin instead of JS.
interface JellyfinApi {
    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): PublicSystemInfoDto

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Header("X-Emby-Authorization") authHeader: String,
        @Body body: AuthenticateByNameRequest,
    ): AuthenticationResultDto

    @GET("Users/{userId}/Views")
    suspend fun getUserViews(@Path("userId") userId: String): ItemsResultDto

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Limit") limit: Int? = null,
        @Query("SortBy") sortBy: String? = null,
        @Query("SortOrder") sortOrder: String? = null,
        @Query("Fields") fields: String? = null,
    ): ItemsResultDto

    @GET("Users/{userId}/Items/Resume")
    suspend fun getResumeItems(
        @Path("userId") userId: String,
        @Query("Limit") limit: Int = 20,
        @Query("Fields") fields: String? = null,
    ): ItemsResultDto

    @GET("Shows/NextUp")
    suspend fun getNextUp(
        @Query("userId") userId: String,
        @Query("Limit") limit: Int = 20,
    ): ItemsResultDto

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun addFavorite(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun removeFavorite(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto
}

// Real Jellyfin auth convention every client sends, confirmed against
// the same real shape jellyfin-apiclient-javascript's own boot line
// already relies on (IndexHtmlPatchService.cs's own header explains
// that real log line, this is the equally real request side of it).
fun buildEmbyAuthorizationHeader(deviceId: String, appVersion: String): String =
    "MediaBrowser Client=\"Jellio TV\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"$appVersion\""
