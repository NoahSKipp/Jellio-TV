package com.jellio.tv.data.network

import com.jellio.tv.data.model.AuthenticateByNameRequest
import com.jellio.tv.data.model.AuthenticationResultDto
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.ItemsResultDto
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.data.model.PlaybackInfoRequest
import com.jellio.tv.data.model.PlaybackInfoResponseDto
import com.jellio.tv.data.model.PlaybackReportRequest
import com.jellio.tv.data.model.PublicSystemInfoDto
import com.jellio.tv.data.model.SleepTimerStartRequest
import com.jellio.tv.data.model.SleepTimerStatusDto
import com.jellio.tv.data.model.UpdatePasswordRequest
import com.jellio.tv.data.model.UserConfigurationDto
import com.jellio.tv.data.model.UserDto
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

    @GET("Users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): UserDto

    // Real endpoint, POST /Users/{id}/Configuration (UserController.cs's
    // own UpdateUserConfiguration): replaces the whole real
    // UserConfiguration object, not a single field patch.
    @POST("Users/{userId}/Configuration")
    suspend fun updateUserConfiguration(
        @Path("userId") userId: String,
        @Body body: UserConfigurationDto,
    )

    @GET("Users/{userId}/Views")
    suspend fun getUserViews(@Path("userId") userId: String): ItemsResultDto

    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Limit") limit: Int? = null,
        @Query("StartIndex") startIndex: Int? = null,
        @Query("SortBy") sortBy: String? = null,
        @Query("SortOrder") sortOrder: String? = null,
        @Query("Fields") fields: String? = null,
        @Query("Genres") genres: String? = null,
        @Query("Filters") filters: String? = null,
        @Query("searchTerm") searchTerm: String? = null,
        @Query("personIds") personIds: String? = null,
    ): ItemsResultDto

    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItem(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Query("Fields") fields: String? = null,
    ): BaseItemDto

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
        @Query("Fields") fields: String? = null,
        @Query("seriesId") seriesId: String? = null,
        @Query("enableResumable") enableResumable: Boolean? = null,
    ): ItemsResultDto

    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasons(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
    ): ItemsResultDto

    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("seasonId") seasonId: String,
        @Query("Fields") fields: String? = null,
    ): ItemsResultDto

    // Soft dependency on the community Intro Skipper plugin
    // (github.com/intro-skipper/intro-skipper): real route confirmed
    // against its own SkipIntroController.cs, works for both Episode
    // and Movie items despite the route name.
    @GET("Episode/{itemId}/Timestamps")
    suspend fun getIntroSkipperSegments(@Path("itemId") itemId: String): IntroSkipperSegmentsDto

    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Body body: PlaybackInfoRequest,
    ): PlaybackInfoResponseDto

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(@Body body: PlaybackReportRequest)

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(@Body body: PlaybackReportRequest)

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(@Body body: PlaybackReportRequest)

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun addFavorite(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun removeFavorite(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @POST("Users/{userId}/PlayedItems/{itemId}")
    suspend fun markPlayed(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @DELETE("Users/{userId}/PlayedItems/{itemId}")
    suspend fun markUnplayed(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @POST("Users/{userId}/Items/{itemId}/Rating")
    suspend fun setRating(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Query("likes") likes: Boolean,
    ): UserItemDataDto

    @DELETE("Users/{userId}/Items/{itemId}/Rating")
    suspend fun clearRating(@Path("userId") userId: String, @Path("itemId") itemId: String): UserItemDataDto

    @GET("Jellio/calendar")
    suspend fun getCalendarEntries(): List<CalendarEntryDto>

    // Real Controllers/NowPlayingController.cs endpoint: every real
    // active session on the server with a real NowPlayingItem, backed
    // by ISessionManager server side rather than a cron plus static
    // file the way a plugin-less community script would need.
    @GET("Jellio/now-playing")
    suspend fun getNowPlayingSessions(): List<NowPlayingSessionDto>

    // Real Controllers/SleepTimerController.cs endpoints: server side
    // duration timer, one per user/device. See SleepTimerStatusDto's
    // own header comment for why this app's own player also has to
    // enforce this locally rather than trusting the real
    // SendPlaystateCommand(Stop) that timer expiring server side sends.
    @POST("Jellio/sleep-timer/start")
    suspend fun startSleepTimer(@Body body: SleepTimerStartRequest): SleepTimerStatusDto

    @POST("Jellio/sleep-timer/cancel")
    suspend fun cancelSleepTimer()

    @GET("Jellio/sleep-timer/status")
    suspend fun getSleepTimerStatus(): SleepTimerStatusDto

    // Real endpoint, POST /Users/{id}/Password, body { CurrentPw, NewPw
    // }, confirmed against jellyfin-apiclient-javascript's own
    // updateUserPassword rather than guessed field names, the same
    // call the stock profile page's own password form uses.
    @POST("Users/{userId}/Password")
    suspend fun updatePassword(@Path("userId") userId: String, @Body body: UpdatePasswordRequest)

    // Real endpoint, GET /QuickConnect/Enabled: a server admin can turn
    // the whole real feature off, checked before this screen bothers
    // offering a code field nobody could ever actually use.
    @GET("QuickConnect/Enabled")
    suspend fun isQuickConnectEnabled(): Boolean

    // Real endpoint, POST /QuickConnect/Authorize?code=: the signed in
    // session's own token approving a real pending request another
    // device started, returns a real bool for whether the code
    // actually matched a pending request.
    @POST("QuickConnect/Authorize")
    suspend fun authorizeQuickConnect(@Query("code") code: String): Boolean
}

// Real Jellyfin auth convention every client sends, confirmed against
// the same real shape jellyfin-apiclient-javascript's own boot line
// already relies on (IndexHtmlPatchService.cs's own header explains
// that real log line, this is the equally real request side of it).
fun buildEmbyAuthorizationHeader(deviceId: String, appVersion: String): String =
    "MediaBrowser Client=\"Jellio TV\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"$appVersion\""
