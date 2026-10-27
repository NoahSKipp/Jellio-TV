package com.jellio.tv.data.model

import androidx.compose.runtime.Immutable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PublicSystemInfoDto(
    val ServerName: String? = null,
    val Version: String? = null,
    val Id: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthenticateByNameRequest(
    val Username: String,
    val Pw: String,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val Id: String,
    val Name: String,
    val PrimaryImageTag: String? = null,
    val Configuration: UserConfigurationDto? = null,
    val Policy: UserPolicyDto? = null,
    val HasPassword: Boolean? = null,
    val HasConfiguredPassword: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class UserPolicyDto(
    val IsAdministrator: Boolean = false,
    val EnableContentDeletion: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class UserConfigurationDto(
    val AudioLanguagePreference: String? = null,
    val SubtitleLanguagePreference: String? = null,
    val PlayDefaultAudioTrack: Boolean? = null,
    val SubtitleMode: String? = null,
)

@JsonClass(generateAdapter = true)
data class AuthenticationResultDto(
    val User: UserDto,
    val AccessToken: String,
    val ServerId: String? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class UserItemDataDto(
    val IsFavorite: Boolean = false,
    val Played: Boolean = false,
    val PlaybackPositionTicks: Long = 0,
    val PlayedPercentage: Double? = null,
    val LastPlayedDate: String? = null,
    val Likes: Boolean? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class PersonDto(
    val Id: String,
    val Name: String? = null,
    val Type: String? = null,
    val Role: String? = null,
    val PrimaryImageTag: String? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class TrailerDto(
    val Name: String? = null,
    val Url: String? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class MediaStreamDto(
    val Type: String? = null,
    // Frames per second of a video stream, as Jellyfin's probe found it.
    val RealFrameRate: Float? = null,
    val AverageFrameRate: Float? = null,
    val Codec: String? = null,
    val Language: String? = null,
    val Height: Int? = null,
    val BitRate: Long? = null,
    val Channels: Int? = null,
    val ChannelLayout: String? = null,
    val Index: Int? = null,
    val Width: Int? = null,
    val VideoRange: String? = null,
    val VideoRangeType: String? = null,
    val Title: String? = null,
    val Profile: String? = null,
    val IsDefault: Boolean? = null,
    val IsTextSubtitleStream: Boolean? = null,
    val DisplayTitle: String? = null,
    val DeliveryMethod: String? = null,
    val DeliveryUrl: String? = null,
    val IsExternalUrl: Boolean? = null,
    val BitDepth: Int? = null,
    val IsInterlaced: Boolean? = null,
    val DvProfile: Int? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class MediaSourceDto(
    val Id: String? = null,
    val Name: String? = null,
    val Path: String? = null,
    val RunTimeTicks: Long? = null,
    val Container: String? = null,
    val Size: Long? = null,
    val Bitrate: Long? = null,
    val SupportsDirectPlay: Boolean? = null,
    val SupportsDirectStream: Boolean? = null,
    val DefaultAudioStreamIndex: Int? = null,
    val TranscodingUrl: String? = null,
    val MediaStreams: List<MediaStreamDto>? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class BaseItemDto(
    val Id: String,
    val Name: String? = null,
    val Type: String? = null,
    val CollectionType: String? = null,
    val SeriesId: String? = null,
    val SeriesName: String? = null,
    val SeriesPrimaryImageTag: String? = null,
    val SeasonId: String? = null,
    val ProductionYear: Int? = null,
    val PremiereDate: String? = null,
    val OriginalTitle: String? = null,
    val Overview: String? = null,
    val OfficialRating: String? = null,
    val CommunityRating: Double? = null,
    val Status: String? = null,
    val EndDate: String? = null,
    val Genres: List<String>? = null,
    val Tags: List<String>? = null,
    val People: List<PersonDto>? = null,
    val RemoteTrailers: List<TrailerDto>? = null,
    val ImageTags: Map<String, String>? = null,
    val PrimaryImageTag: String? = null,
    val BackdropImageTags: List<String>? = null,
    val ParentId: String? = null,
    val ParentPrimaryImageTag: String? = null,
    val ParentPrimaryImageItemId: String? = null,
    val ParentBackdropItemId: String? = null,
    val ParentBackdropImageTags: List<String>? = null,
    val ParentThumbItemId: String? = null,
    val ParentThumbImageTag: String? = null,
    val ThumbImageTag: String? = null,
    val AlbumId: String? = null,
    val AlbumPrimaryImageTag: String? = null,
    val ParentLogoItemId: String? = null,
    val ParentLogoImageTag: String? = null,
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val UserData: UserItemDataDto? = null,
    val RunTimeTicks: Long? = null,
    val ProviderIds: Map<String, String>? = null,
    val MediaSources: List<MediaSourceDto>? = null,
    val MediaStreams: List<MediaStreamDto>? = null,
    val ChildCount: Int? = null,
    val Watchers: List<CommunityWatcherDto>? = null,
    val Chapters: List<ChapterInfoDto>? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class CommunityWatcherDto(
    val UserId: String,
    val UserName: String,
    val PrimaryImageTag: String? = null,
    val Activity: String? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class CommunityWatchingItem(
    val Item: BaseItemDto,
    val Watchers: List<CommunityWatcherDto> = emptyList(),
)

// The plugin's cached genre list (Controllers/GenresController.cs).
@JsonClass(generateAdapter = true)
data class GenreCountDto(val Name: String = "", val Count: Int = 0)

@JsonClass(generateAdapter = true)
data class GenreCountsDto(val Genres: List<GenreCountDto> = emptyList())

@Immutable
@JsonClass(generateAdapter = true)
data class GenreSearchResultDto(
    val Found: Boolean = false,
    val Name: String? = null,
    val Category: String? = null,
    val IsStandard: Boolean = false,
    val TmdbGenreId: Int? = null,
    val KeywordId: Int? = null,
    val OriginalLanguage: String? = null,
    val MatchCount: Int? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class CustomGenreItem(
    val name: String,
    val category: String? = null,
    val enabled: Boolean = true,
)

@Immutable
@JsonClass(generateAdapter = true)
data class CommunityWatchingResponse(
    val Items: List<CommunityWatchingItem> = emptyList(),
)

@Immutable
@JsonClass(generateAdapter = true)
data class CommunityUserDto(
    val Id: String,
    val Name: String,
    val PrimaryImageTag: String? = null,
)

@JsonClass(generateAdapter = true)
data class ItemsResultDto(
    val Items: List<BaseItemDto> = emptyList(),
    val TotalRecordCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PrefetchBatchRequest(
    val ItemIds: List<String>,
    val Probe: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PrefetchBatchResponse(
    val Queued: List<String>? = null,
    val AlreadyWarm: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class PlaybackInfoRequest(
    val UserId: String,
    val StartTimeTicks: Long = 0,
    val EnableDirectPlay: Boolean = true,
    val EnableDirectStream: Boolean = true,
    val EnableTranscoding: Boolean = true,
    val AutoOpenLiveStream: Boolean = true,
    val MediaSourceId: String? = null,
    val AudioStreamIndex: Int? = null,
    val SubtitleStreamIndex: Int? = null,
)

@JsonClass(generateAdapter = true)
data class PlaybackInfoResponseDto(
    val MediaSources: List<MediaSourceDto> = emptyList(),
    val PlaySessionId: String? = null,
)

@JsonClass(generateAdapter = true)
data class PlaybackReportRequest(
    val ItemId: String,
    val MediaSourceId: String? = null,
    val PositionTicks: Long = 0,
    val IsPaused: Boolean = false,
    val CanSeek: Boolean = true,
    val PlayMethod: String = "DirectStream",
)

@JsonClass(generateAdapter = true)
data class ClientCapabilitiesDto(
    val PlayableMediaTypes: List<String> = listOf("Video", "Audio"),
    val SupportedCommands: List<String> = listOf(
        "Play",
        "Playstate",
        "PlayNext",
        "GeneralCommand",
        "VolumeUp",
        "VolumeDown",
        "Mute",
        "Unmute",
        "ToggleMute",
        "SetVolume",
        "SetAudioStreamIndex",
        "SetSubtitleStreamIndex",
        "DisplayMessage",
    ),
    val SupportsMediaControl: Boolean = true,
    val SupportsPersistentIdentifier: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(val EnteredUsername: String)

@JsonClass(generateAdapter = true)
data class ForgotPasswordPinRequest(val Pin: String)

@JsonClass(generateAdapter = true)
data class PinRedeemResultDto(val Success: Boolean = false)

@JsonClass(generateAdapter = true)
data class UpdatePasswordRequest(
    val CurrentPw: String,
    val NewPw: String,
)

// Controllers/NotificationsController.cs: a reader's notifications; an
// admin's announcement is Kind "announcement" with its text in Name.
@JsonClass(generateAdapter = true)
data class JellioNotificationDto(
    val Id: String,
    val Name: String? = null,
    val Kind: String? = null,
    val CreatedUtc: String? = null,
    val ImageId: String? = null,
    val ItemId: String? = null,
    val Detail: String? = null,
    val Read: Boolean = false,
    val Date: String? = null,
)

@JsonClass(generateAdapter = true)
data class UtcTimeDto(val RequestReceptionTime: String? = null, val ResponseTransmissionTime: String? = null)

@JsonClass(generateAdapter = true)
data class SyncPlayGroupDto(
    val GroupId: String = "",
    val GroupName: String? = null,
    val State: String? = null,
    val Participants: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SyncPlayNewBody(val GroupName: String)

@JsonClass(generateAdapter = true)
data class SyncPlayJoinBody(val GroupId: String)

@JsonClass(generateAdapter = true)
data class SyncPlaySeekBody(val PositionTicks: Long)

@JsonClass(generateAdapter = true)
data class SyncPlayStateBody(val When: String, val PositionTicks: Long, val IsPlaying: Boolean, val PlaylistItemId: String)

@JsonClass(generateAdapter = true)
data class SyncPlayQueueBody(val PlayingQueue: List<String>, val PlayingItemPosition: Int, val StartPositionTicks: Long)

@JsonClass(generateAdapter = true)
data class GroupWatchInviteDto(
    val Id: Long = 0,
    val GroupId: String = "",
    val GroupName: String? = null,
    val FromUserName: String? = null,
)

@JsonClass(generateAdapter = true)
data class GroupWatchMessageDto(
    val Id: Long = 0,
    val UserId: String? = null,
    val UserName: String? = null,
    val Text: String = "",
    val ItemId: String? = null,
    val ReplyToId: Long? = null,
    val ReplyToName: String? = null,
    val ReplyToText: String? = null,
)

@JsonClass(generateAdapter = true)
data class GroupWatchSendBody(val Text: String, val ItemId: String? = null, val ReplyToId: Long? = null)

// Watch parties (Controllers/PartiesController.cs).
@JsonClass(generateAdapter = true)
data class PartyItemDto(
    val Id: String = "",
    val Name: String? = null,
    val Type: String? = null,
    val SeriesName: String? = null,
    val ImageItemId: String? = null,
    val SeasonNumber: Int? = null,
    val EpisodeNumber: Int? = null,
    val RunTimeTicks: Long? = null,
)

@JsonClass(generateAdapter = true)
data class PartyPersonDto(val Name: String = "", val UserId: String? = null)

@JsonClass(generateAdapter = true)
data class LivePartyDto(
    val GroupId: String = "",
    val GroupName: String? = null,
    val State: String? = null,
    val Participants: List<PartyPersonDto> = emptyList(),
    val HostName: String? = null,
    val HostUserId: String? = null,
    val Private: Boolean = false,
    val HostOnly: Boolean = false,
    val Code: String? = null,
    val Item: PartyItemDto? = null,
    val PositionTicks: Long? = null,
    val IsPlaying: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ScheduledPartyDto(
    val Id: String = "",
    val Name: String? = null,
    val HostName: String? = null,
    val HostUserId: String? = null,
    val StartsAt: String? = null,
    val Private: Boolean = false,
    val HostOnly: Boolean = false,
    val Item: PartyItemDto? = null,
    val Going: List<String> = emptyList(),
    val IsGoing: Boolean = false,
    val IsHost: Boolean = false,
    val GroupId: String? = null,
    val CanStart: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class PartiesDto(
    val Live: List<LivePartyDto> = emptyList(),
    val Scheduled: List<ScheduledPartyDto> = emptyList(),
    val ServerNow: String? = null,
)

@JsonClass(generateAdapter = true)
data class PartyReadyDto(val UserId: String? = null, val Name: String? = null, val PlaylistItemId: String? = null)

@JsonClass(generateAdapter = true)
data class PartyRequestDto(val Id: Long = 0, val UserId: String? = null, val UserName: String? = null, val Kind: String = "", val PositionTicks: Long? = null)

@JsonClass(generateAdapter = true)
data class PartyStateDto(
    val GroupId: String = "",
    val HostUserId: String? = null,
    val HostName: String? = null,
    val IsHost: Boolean = false,
    val Private: Boolean = false,
    val HostOnly: Boolean = false,
    val Code: String? = null,
    val ItemId: String? = null,
    val CountdownEndsAt: String? = null,
    val CountdownPlaylistItemId: String? = null,
    val Ready: List<PartyReadyDto> = emptyList(),
    val Requests: List<PartyRequestDto> = emptyList(),
    val ServerNow: String? = null,
)

@JsonClass(generateAdapter = true)
data class PartySetupBody(val Private: Boolean? = null, val HostOnly: Boolean? = null, val ScheduleId: String? = null)

@JsonClass(generateAdapter = true)
data class PartyItemBody(val ItemId: String)

@JsonClass(generateAdapter = true)
data class PartyHostBody(val UserId: String)

@JsonClass(generateAdapter = true)
data class PartyControlBody(val Kind: String, val PositionTicks: Long? = null)

@JsonClass(generateAdapter = true)
data class PartyReadyBody(val PlaylistItemId: String, val Ready: Boolean)

@JsonClass(generateAdapter = true)
data class PartyCountdownBody(val PlaylistItemId: String, val Seconds: Int)

@JsonClass(generateAdapter = true)
data class PartyCountdownDto(val EndsAt: String? = null, val ServerNow: String? = null)

@JsonClass(generateAdapter = true)
data class PartyRsvpBody(val Going: Boolean)

@JsonClass(generateAdapter = true)
data class PartyCodeBody(val Code: String)

@JsonClass(generateAdapter = true)
data class PartyCodeDto(val GroupId: String = "")

@JsonClass(generateAdapter = true)
data class PartyStartedBody(val GroupId: String)

@JsonClass(generateAdapter = true)
data class PendingOverlayDto(
    val Seq: Long = 0,
    val Text: String? = null,
    val ImageId: String? = null,
    val Opacity: Int = 80,
    val Seconds: Int = 5,
)

@JsonClass(generateAdapter = true)
data class PendingOverlaysDto(val Latest: Long = 0, val Overlays: List<PendingOverlayDto> = emptyList())

@JsonClass(generateAdapter = true)
data class PendingSoundDto(
    val Seq: Long = 0,
    val SoundId: String = "",
    val Volume: Int = 100,
    val DuckVolume: Int = 30,
    val Direction: String = "both",
)

@JsonClass(generateAdapter = true)
data class PendingSoundsDto(val Latest: Long = 0, val Sounds: List<PendingSoundDto> = emptyList())

@JsonClass(generateAdapter = true)
data class AnimeIdsDto(val Ids: List<String> = emptyList())

@Immutable
@JsonClass(generateAdapter = true)
data class CalendarEntryDto(
    val ItemId: String,
    val Name: String? = null,
    val Kind: String? = null,
    val Detail: String? = null,
    val Date: String? = null,
)

@JsonClass(generateAdapter = true)
data class SkipSegmentDto(
    val Start: Double = 0.0,
    val End: Double = 0.0,
)

// Shared by the Intro Skipper's /Episode/{id}/Timestamps and the plugin's
// /Jellio/introcredits endpoints (IntroCreditsController.SegmentsResponse).
@JsonClass(generateAdapter = true)
data class IntroSkipperSegmentsDto(
    val Introduction: SkipSegmentDto? = null,
    val Credits: SkipSegmentDto? = null,
)

// Jellyfin's native /MediaSegments/{itemId}; Type is Intro, Outro, Recap,
// Preview, Commercial or Unknown.
@JsonClass(generateAdapter = true)
data class MediaSegmentDto(
    val Type: String? = null,
    val StartTicks: Long = 0,
    val EndTicks: Long = 0,
)

@JsonClass(generateAdapter = true)
data class MediaSegmentsResultDto(
    val Items: List<MediaSegmentDto> = emptyList(),
)

@Immutable
@JsonClass(generateAdapter = true)
data class ChapterInfoDto(
    val Name: String? = null,
    val StartPositionTicks: Long = 0,
)

@JsonClass(generateAdapter = true)
data class SleepTimerStartRequest(val Minutes: Int)

@JsonClass(generateAdapter = true)
data class SleepTimerStatusDto(
    val Active: Boolean = false,
    val EndTimeUtc: String? = null,
)

@JsonClass(generateAdapter = true)
data class AvatarPresetDto(val Id: String, val Category: String? = null)

@JsonClass(generateAdapter = true)
data class SeasonalRangeDto(
    val StartMonth: Int = 1,
    val StartDay: Int = 1,
    val EndMonth: Int = 1,
    val EndDay: Int = 1,
)

@JsonClass(generateAdapter = true)
data class SeasonalEffectConfigDto(
    val Enabled: Boolean = false,
    val Range: SeasonalRangeDto? = null,
)

// Controllers/PlaybackLogController.cs's DecisionBody.
@JsonClass(generateAdapter = true)
data class PlaybackDecisionBody(
    val ItemId: String,
    val MediaSourceId: String?,
    val PlayMethod: String,
    val Reasons: List<String>,
    val ServerTranscodeReasons: String?,
    val Policy: String,
    val Guessed: Boolean,
    val Client: String = "tv",
)

@JsonClass(generateAdapter = true)
data class FailoverLogBody(
    val ItemId: String,
    val FailedSourceId: String?,
    val Reason: String,
    val Total: Int,
    val Tried: Int,
    val BadHard: Int,
    val BadSoft: Int,
    val AfterLanguage: Int,
    val AfterTranscode: Int,
    val ListOrigin: String,
    val ListError: String?,
    val ListMs: Long?,
    val Outcome: String,
    val NextSourceId: String?,
    val Client: String = "tv",
)

@JsonClass(generateAdapter = true)
data class ClientConfigDto(
    val SeasonalEffectsEnabled: Boolean = false,
    val SeasonalEffects: Map<String, SeasonalEffectConfigDto> = emptyMap(),
    val VideoTranscodePolicy: String = "Allow",
)

@JsonClass(generateAdapter = true)
data class ProfileDto(
    val IsPrivate: Boolean = false,
    val Bio: String? = null,
    val RemoveFromWatchlistAfterWatching: Boolean = true,
    @Json(name = "selectedGenres") val selectedGenres: List<String>? = null,
    @Json(name = "customGenres") val customGenres: List<CustomGenreItem>? = null,
)

@JsonClass(generateAdapter = true)
data class ProfileRecommendationsRequest(
    @Json(name = "selectedGenres") val selectedGenres: List<String>?,
    @Json(name = "customGenres") val customGenres: List<CustomGenreItem>?,
)

@JsonClass(generateAdapter = true)
data class WatchlistAutoRemoveRequest(val RemoveFromWatchlistAfterWatching: Boolean)

@JsonClass(generateAdapter = true)
data class GroupedActivityEntryDto(
    val ItemId: String,
    val ItemName: String? = null,
    val ItemType: String? = null,
    val SeriesName: String? = null,
    val SeriesId: String? = null,
    val CompletedAtUtc: String? = null,
    val EpisodeCount: Int = 0,
    val SeasonNumber: Int? = null,
    val FirstEpisodeNumber: Int? = null,
    val LastEpisodeNumber: Int? = null,
)

@JsonClass(generateAdapter = true)
data class BadgeDto(
    val Id: String,
    val Name: String? = null,
    val Description: String? = null,
    val Rarity: String? = null,
    val Unlocked: Boolean = false,
    val UnlockedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class AchievementsDto(
    val IsPrivate: Boolean = false,
    val MoviesCompleted: Int = 0,
    val EpisodesCompleted: Int = 0,
    val TotalCompleted: Int = 0,
    val BestBingeStreak: Int = 0,
    val Badges: List<BadgeDto> = emptyList(),
    val RecentActivity: List<GroupedActivityEntryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SetBioRequest(val Bio: String?)

@JsonClass(generateAdapter = true)
data class SetPrivacyRequest(val IsPrivate: Boolean)

@JsonClass(generateAdapter = true)
data class RealWatchRequest(val ItemId: String)

@JsonClass(generateAdapter = true)
data class ReportDurationRequest(val ItemId: String, val DurationTicks: Long)

@JsonClass(generateAdapter = true)
data class FeedEntryDto(
    val UserId: String,
    val UserName: String? = null,
    val Kind: String,
    val OccurredAtUtc: String,
    val ItemId: String? = null,
    val ItemName: String? = null,
    val ItemType: String? = null,
    val SeriesName: String? = null,
    val SeriesId: String? = null,
    val EpisodeCount: Int = 0,
    val SeasonNumber: Int? = null,
    val FirstEpisodeNumber: Int? = null,
    val LastEpisodeNumber: Int? = null,
    val BadgeId: String? = null,
    val BadgeName: String? = null,
    val BadgeDescription: String? = null,
    val BadgeRarity: String? = null,
    // Already finished before: shown as "Rewatched".
    val Rewatch: Boolean = false,
    // Kind == "Party": a finished watch party, posted under its host.
    val PartyName: String? = null,
    val PartyMembers: List<String>? = null,
    val PartyReactions: List<String>? = null,
    val PartyTitleCount: Int = 0,
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class UserDataUpdate(val PlaybackPositionTicks: Long)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class QuickConnectResultDto(
    val Code: String? = null,
    val Secret: String? = null,
    val Authenticated: Boolean = false,
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class QuickConnectAuthBody(
    val Secret: String,
)

// Jellio/search/suggestions: related library titles for a search that
// found nothing (SearchSuggestionsController.cs).
data class SearchSuggestionsDto(
    val MatchedTitle: String? = null,
    val Rows: List<SuggestionRowDto> = emptyList(),
)

data class SuggestionRowDto(
    val Kind: String = "",
    val Name: String? = null,
    val Items: List<BaseItemDto> = emptyList(),
)
