package com.jellio.tv.data.network

import com.jellio.tv.data.model.AchievementsDto
import com.jellio.tv.data.model.AuthenticateByNameRequest
import com.jellio.tv.data.model.AuthenticationResultDto
import com.jellio.tv.data.model.AvatarPresetDto
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.data.model.ClientConfigDto
import com.jellio.tv.data.model.FeedEntryDto
import com.jellio.tv.data.model.ForgotPasswordPinRequest
import com.jellio.tv.data.model.ForgotPasswordRequest
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.ItemsResultDto
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.data.model.PinRedeemResultDto
import com.jellio.tv.data.model.PlaybackInfoRequest
import com.jellio.tv.data.model.PlaybackInfoResponseDto
import com.jellio.tv.data.model.PlaybackReportRequest
import com.jellio.tv.data.model.ProfileDto
import com.jellio.tv.data.model.PublicSystemInfoDto
import com.jellio.tv.data.model.RealWatchRequest
import com.jellio.tv.data.model.ReportDurationRequest
import com.jellio.tv.data.model.SetBioRequest
import com.jellio.tv.data.model.SetPrivacyRequest
import com.jellio.tv.data.model.SleepTimerStartRequest
import com.jellio.tv.data.model.SleepTimerStatusDto
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

    // Real ItemsController.cs own DeleteItem, the same one
    // runtime/api.js's own deleteItem() calls: removes the item's own
    // record outright, not a per-user hide, gated server side on the
    // real signed in user's own Policy.IsAdministrator/
    // EnableContentDeletion, not something this app duplicates client
    // side beyond deciding whether to even show the option.
    @DELETE("Items/{itemId}")
    suspend fun deleteItem(@Path("itemId") itemId: String)

    @GET("Jellio/calendar")
    suspend fun getCalendarEntries(): List<CalendarEntryDto>

    // Real Controllers/FeedController.cs endpoint: server wide, every
    // non-private user's own watch activity and badge unlocks merged
    // and re-sorted by OccurredAtUtc, that controller's own header
    // confirmed before porting this.
    @GET("Jellio/feed")
    suspend fun getFeed(): List<FeedEntryDto>

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

    // Real Controllers/NextUpHiddenController.cs endpoints: the real
    // per user series id list Shows/NextUp's own results get filtered
    // against, and the real call that adds one, see
    // JellioRepository.getNextUp()'s own header for why this exists at
    // all.
    @GET("Jellio/next-up-hidden")
    suspend fun getHiddenNextUpSeries(): List<String>

    @POST("Jellio/next-up-hidden/{seriesId}")
    suspend fun hideSeriesFromNextUp(@Path("seriesId") seriesId: String)

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

    // encoded = true: id can carry a real "/" (a grouped preset's own
    // subfolder), the caller (JellioRepository's own encodeAvatarId())
    // already percent-encodes each real path segment on its own, this
    // just stops Retrofit from also encoding the literal "/" itself
    // into %2F, which AvatarsController.cs's own {**id} catch-all route
    // reads as real path segments, not one.
    @Streaming
    @GET("Jellio/avatars/{id}")
    suspend fun getAvatarPresetImage(@Path(value = "id", encoded = true) id: String): ResponseBody

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

    // Real Controllers/ProfileController.cs endpoints, confirmed
    // against that controller's own source before porting this: the
    // {userId} route is the one public read (IsPrivate/Bio, real
    // Steam-style split, badges/activity go dark separately via
    // AchievementsController below, not this one), settings/bio are
    // self only.
    @GET("Jellio/profile/{userId}")
    suspend fun getProfile(@Path("userId") userId: String): ProfileDto

    @GET("Jellio/profile/settings")
    suspend fun getProfileSettings(): ProfileDto

    @POST("Jellio/profile/bio")
    suspend fun setProfileBio(@Body body: SetBioRequest)

    @POST("Jellio/profile/privacy")
    suspend fun setProfilePrivacy(@Body body: SetPrivacyRequest)

    // Real Controllers/AchievementsController.cs's own {userId} route:
    // the caller's own stats/badges/activity when userId is their own,
    // a locked {IsPrivate: true} shell for anyone else's private
    // profile, confirmed against that controller's own source.
    @GET("Jellio/achievements/{userId}")
    suspend fun getAchievements(@Path("userId") userId: String): AchievementsDto

    // Real Controllers/ProfileBannerController.cs endpoints: upload
    // mirrors uploadUserAvatar above exactly (same real base64 body/
    // Content-Type convention, that controller's own header confirms
    // runtime/api.js's own uploadUserAvatarBlob already works
    // unchanged against it), no native Jellyfin image slot exists for
    // a banner at all.
    @POST("Jellio/profile/banner")
    suspend fun uploadProfileBanner(@Body body: RequestBody)

    @DELETE("Jellio/profile/banner")
    suspend fun deleteProfileBanner()

    // Real Controllers/AchievementsController.cs's own real-watch
    // endpoint: item.RunTimeTicks is the library's own metadata
    // runtime, not whatever Gelato actually resolved and streamed, so
    // ui/player's own real ExoPlayer duration/position is what decides
    // when to call this, not this file, same real reason
    // screens/player.js's own creditRealWatch() call site never trusts
    // item.RunTimeTicks either.
    @POST("Jellio/achievements/real-watch")
    suspend fun creditRealWatch(@Body body: RealWatchRequest)

    // Real Controllers/RealDurationController.cs endpoints: a title's
    // own real observed duration, not per user, fed by ui/player's own
    // real trustworthy signals (ExoPlayer's own real duration, or the
    // real position 'ended'/Up Next confirm a genuine full watch at),
    // same real three signals screens/player.js's own
    // reportRealDurationIfUseful() already uses.
    @POST("Jellio/real-duration")
    suspend fun reportRealDuration(@Body body: ReportDurationRequest)
}

// Real Jellyfin auth convention every client sends, confirmed against
// the same real shape jellyfin-apiclient-javascript's own boot line
// already relies on (IndexHtmlPatchService.cs's own header explains
// that real log line, this is the equally real request side of it).
fun buildEmbyAuthorizationHeader(deviceId: String, appVersion: String): String =
    "MediaBrowser Client=\"Jellio TV\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"$appVersion\""
