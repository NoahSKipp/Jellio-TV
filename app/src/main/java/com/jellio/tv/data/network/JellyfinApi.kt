package com.jellio.tv.data.network

import com.jellio.tv.data.model.AuthenticateByNameRequest
import com.jellio.tv.data.model.AuthenticationResultDto
import com.jellio.tv.data.model.AvatarPresetDto
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.data.model.ClientConfigDto
import com.jellio.tv.data.model.CreateSyncPlayGroupRequest
import com.jellio.tv.data.model.ForgotPasswordPinRequest
import com.jellio.tv.data.model.ForgotPasswordRequest
import com.jellio.tv.data.model.GroupWatchMessageDto
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.JoinSyncPlayGroupRequest
import com.jellio.tv.data.model.ItemsResultDto
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.data.model.PinRedeemResultDto
import com.jellio.tv.data.model.PlaybackInfoRequest
import com.jellio.tv.data.model.PlaybackInfoResponseDto
import com.jellio.tv.data.model.PlaybackReportRequest
import com.jellio.tv.data.model.PublicSystemInfoDto
import com.jellio.tv.data.model.SendGroupWatchMessageRequest
import com.jellio.tv.data.model.SleepTimerStartRequest
import com.jellio.tv.data.model.SleepTimerStatusDto
import com.jellio.tv.data.model.SyncPlayGroupDto
import com.jellio.tv.data.model.UpdatePasswordRequest
import com.jellio.tv.data.model.UserConfigurationDto
import com.jellio.tv.data.model.UserDto
import com.jellio.tv.data.model.UserItemDataDto
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

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

    // Real endpoint, GET /Users/Public (Jellyfin's own UserController.cs
    // GetPublicUsers), confirmed against runtime/auth.js's own
    // getPublicUsers() before porting this: unauthenticated, a real
    // admin's own per-user "Display this user on the login screen"
    // toggle already enforced server side.
    @GET("Users/Public")
    suspend fun getPublicUsers(): List<UserDto>

    // Same real GET /Users/{userId} getUser() above already calls, a
    // second real Retrofit method only because this one carries an
    // explicit candidate token rather than trusting NetworkModule's own
    // auth interceptor (which has nothing to attach yet on the login
    // screen this is called from): the same real verification
    // runtime/auth.js's own quickSignIn() does before trusting a
    // remembered token at all.
    @GET("Users/{userId}")
    suspend fun getUserWithToken(
        @Header("X-Emby-Token") token: String,
        @Path("userId") userId: String,
    ): UserDto

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

    // Real Controllers/ConfigController.cs endpoint: one real server
    // side, admin controlled source components/seasonalEffects.js's
    // own real overlay reads, confirmed against that controller's own
    // real ClientConfig shape before porting this.
    @GET("Jellio/config")
    suspend fun getJellioConfig(): ClientConfigDto

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

    // Real endpoint, POST /Users/ForgotPassword, confirmed against
    // UserController.cs's own ForgotPassword action before porting
    // this: the same real generic response every request gets back
    // regardless of whether the username exists at all, nothing this
    // app acts on, hence no meaningful return type.
    @POST("Users/ForgotPassword")
    suspend fun requestPasswordReset(@Body body: ForgotPasswordRequest)

    @POST("Users/ForgotPassword/Pin")
    suspend fun redeemPasswordResetPin(@Body body: ForgotPasswordPinRequest): PinRedeemResultDto

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

    // Real Controllers/AvatarsController.cs endpoints: real preset
    // images an admin dropped into Jellio's own plugin data directory,
    // confirmed against components/avatarPicker.js's own real
    // getAvatarPresets()/getAvatarPresetUrl() before porting this.
    @GET("Jellio/avatars")
    suspend fun getAvatarPresets(): List<AvatarPresetDto>

    @Streaming
    @GET("Jellio/avatars/{id}")
    suspend fun getAvatarPresetImage(@Path("id") id: String): ResponseBody

    // Real endpoint, POST /Users/{id}/Images/Primary, confirmed against
    // runtime/api.js's own uploadUserAvatarBlob(): body is the image's
    // own real bytes base64 encoded as plain text, Content-Type set to
    // the image's own real mime type rather than application/json, the
    // same real shape jellyfin-apiclient-javascript's own
    // uploadUserImage already sends. body is built as a raw RequestBody
    // here specifically so Retrofit's own Moshi converter never touches
    // it.
    @POST("Users/{userId}/Images/Primary")
    suspend fun uploadUserAvatar(@Path("userId") userId: String, @Body body: RequestBody)

    // Real Jellyfin SyncPlay endpoints (SyncPlayController.cs): the
    // exact same real REST surface native jellyfin-web's own hidden
    // .headerSyncButton menu already drove, confirmed against
    // components/groupWatch.js's own real
    // getSyncPlayGroups/createSyncPlayGroup/joinSyncPlayGroup/
    // leaveSyncPlayGroup before porting this. Leave takes no body: a
    // signed in session can only ever be in one real group at a time,
    // so there is nothing to target.
    @GET("SyncPlay/List")
    suspend fun getSyncPlayGroups(): List<SyncPlayGroupDto>

    @POST("SyncPlay/New")
    suspend fun createSyncPlayGroup(@Body body: CreateSyncPlayGroupRequest)

    @POST("SyncPlay/Join")
    suspend fun joinSyncPlayGroup(@Body body: JoinSyncPlayGroupRequest)

    @POST("SyncPlay/Leave")
    suspend fun leaveSyncPlayGroup()

    // Real Controllers/GroupWatchChatController.cs endpoints: Jellio's
    // own small in memory chat room per real SyncPlay GroupId, polled
    // rather than pushed, see GroupWatchMessageDto's own header comment
    // for why this app opens no WebSocket of its own to drive it live
    // the way real Jellyfin SyncPlay's own playback lockstep would.
    @GET("Jellio/groupwatch/{groupId}/messages")
    suspend fun getGroupWatchMessages(
        @Path("groupId") groupId: String,
        @Query("after") after: Long = 0,
    ): List<GroupWatchMessageDto>

    @POST("Jellio/groupwatch/{groupId}/messages")
    suspend fun sendGroupWatchMessage(
        @Path("groupId") groupId: String,
        @Body body: SendGroupWatchMessageRequest,
    ): GroupWatchMessageDto
}

// Real Jellyfin auth convention every client sends, confirmed against
// the same real shape jellyfin-apiclient-javascript's own boot line
// already relies on (IndexHtmlPatchService.cs's own header explains
// that real log line, this is the equally real request side of it).
fun buildEmbyAuthorizationHeader(deviceId: String, appVersion: String): String =
    "MediaBrowser Client=\"Jellio TV\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"$appVersion\""
