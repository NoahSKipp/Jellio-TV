package com.jellio.tv.data.model

import com.squareup.moshi.JsonClass

// Field names match the real Jellyfin server JSON verbatim (PascalCase),
// the same real shape frontend/runtime/api.js already reads: no separate
// translation layer between what the server actually sends and what this
// app's own models hold.

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
    // Real fields runtime/auth.js's own buildPublicUserTile() checks
    // (a formally obsolete pair Jellyfin's own stock login page still
    // reads the same way) before deciding whether a public user's own
    // tile can sign in passwordless or has to ask first.
    val HasPassword: Boolean? = null,
    val HasConfiguredPassword: Boolean? = null,
)

// Real Jellyfin UserDto.Policy shape: IsAdministrator is the one real
// gate every native admin link already uses, confirmed against
// screens/settings.js's own real user.Policy.IsAdministrator check
// before porting this. EnableContentDeletion alongside it, the same
// real second half of the gate components/cardOptionsMenu.js's own
// Remove from Library option checks: a non-admin user can still carry
// this real permission on their own account.
@JsonClass(generateAdapter = true)
data class UserPolicyDto(
    val IsAdministrator: Boolean = false,
    val EnableContentDeletion: Boolean = false,
)

// Real fields screens/settings.js's own Language section reads/writes,
// UserConfiguration.cs confirmed before porting this: Jellyfin's own
// PlaybackInfo negotiation already reads these server side to pick a
// MediaSource's own real DefaultAudioStreamIndex/
// DefaultSubtitleStreamIndex, no client side track selection needed
// for a saved preference to take effect on the next stream negotiated.
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

@JsonClass(generateAdapter = true)
data class UserItemDataDto(
    val IsFavorite: Boolean = false,
    val Played: Boolean = false,
    val PlaybackPositionTicks: Long = 0,
    val PlayedPercentage: Double? = null,
    // Real Jellyfin like/dislike, absent (null) rather than false when
    // never set, screens/detail.js's own three-way paintThumbs() reads
    // the same real distinction.
    val Likes: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class PersonDto(
    val Id: String,
    val Name: String? = null,
    val Type: String? = null,
    val Role: String? = null,
    val PrimaryImageTag: String? = null,
)

@JsonClass(generateAdapter = true)
data class TrailerDto(
    val Name: String? = null,
    val Url: String? = null,
)

@JsonClass(generateAdapter = true)
data class MediaStreamDto(
    val Type: String? = null,
    val Codec: String? = null,
    val Language: String? = null,
    val Height: Int? = null,
    val BitRate: Long? = null,
    val Channels: Int? = null,
    // Real field screens/player.js's own audioStreamLabel() reads
    // (e.g. "5.1", "stereo"), distinct from the plain Channels count
    // above.
    val ChannelLayout: String? = null,
    val Index: Int? = null,
    // Real fields screens/player.js's own subtitle track list reads:
    // an image based track (PGS, VobSub) has no WebVTT form, nothing
    // a side-loaded text track can render, only a burned in transcode
    // can show one at all.
    val IsTextSubtitleStream: Boolean? = null,
    val DisplayTitle: String? = null,
    val DeliveryMethod: String? = null,
    val DeliveryUrl: String? = null,
    val IsExternalUrl: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class MediaSourceDto(
    val Id: String? = null,
    val Name: String? = null,
    val Container: String? = null,
    val Size: Long? = null,
    val Bitrate: Long? = null,
    val SupportsDirectPlay: Boolean? = null,
    val SupportsDirectStream: Boolean? = null,
    val DefaultAudioStreamIndex: Int? = null,
    val TranscodingUrl: String? = null,
    val MediaStreams: List<MediaStreamDto>? = null,
)

// Real BaseItemDto.Trickplay shape, confirmed against
// TrickplayController.cs before porting this: width keyed (a source
// can carry more than one real generated resolution), each entry one
// real sheet's own layout, several packed thumbnails per sheet rather
// than one image per thumbnail.
@JsonClass(generateAdapter = true)
data class TrickplayInfoDto(
    val Width: Int = 0,
    val Height: Int = 0,
    val TileWidth: Int = 0,
    val TileHeight: Int = 0,
    val Interval: Int = 0,
)

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
    val Overview: String? = null,
    val OfficialRating: String? = null,
    val CommunityRating: Double? = null,
    val Genres: List<String>? = null,
    val People: List<PersonDto>? = null,
    val RemoteTrailers: List<TrailerDto>? = null,
    val ImageTags: Map<String, String>? = null,
    val BackdropImageTags: List<String>? = null,
    val ParentBackdropItemId: String? = null,
    val ParentBackdropImageTags: List<String>? = null,
    val ParentThumbItemId: String? = null,
    val ParentThumbImageTag: String? = null,
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val UserData: UserItemDataDto? = null,
    val RunTimeTicks: Long? = null,
    val ProviderIds: Map<String, String>? = null,
    val MediaSources: List<MediaSourceDto>? = null,
    // Width keyed within each real MediaSourceId key, real runtime/
    // api.js's own pickTrickplayInfo() shape: only ever real for a
    // title Jellyfin's own background task already generated one for,
    // a real local ffmpeg pass over the whole file, so this stays
    // quietly null on most titles this runtime plays (every one a live
    // Gelato proxy in front of a debrid/usenet host, never a local
    // file), no broken preview shown.
    val Trickplay: Map<String, Map<String, TrickplayInfoDto>>? = null,
    // Not part of a BoxSet's own default field set, real bug
    // runtime/api.js's own getAllCollections() comment documents:
    // without asking for this explicitly every collection reads back
    // as 0 children, dropping every real catalog row and hub tile
    // both silently.
    val ChildCount: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ItemsResultDto(
    val Items: List<BaseItemDto> = emptyList(),
    val TotalRecordCount: Int = 0,
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
    // Real fields on Jellyfin.Api's own PlaybackInfoDto, confirmed
    // against runtime/api.js's own getPlaybackInfo() before writing
    // this: a bare stream URL query param change alone (no fresh
    // negotiation carrying these) was chased through a real server log
    // and never once produced a genuinely new transcode job server
    // side, real feedback that file's own comment already documents.
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

// Real endpoint, POST /Users/ForgotPassword, confirmed against
// UserController.cs's own ForgotPassword action before porting this.
@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(val EnteredUsername: String)

@JsonClass(generateAdapter = true)
data class ForgotPasswordPinRequest(val Pin: String)

// Real Jellyfin PinRedeemResult shape: a real redeem clears the
// account's own password server side rather than setting the one the
// reader asked for, real Jellyfin behaviour JellioRepository.kt's own
// redeemPasswordReset() header comment covers the two follow up real
// calls this app makes for the same reason screens/login.js's own
// forgot password flow does.
@JsonClass(generateAdapter = true)
data class PinRedeemResultDto(val Success: Boolean = false)

@JsonClass(generateAdapter = true)
data class UpdatePasswordRequest(
    val CurrentPw: String,
    val NewPw: String,
)

@JsonClass(generateAdapter = true)
data class CalendarEntryDto(
    val ItemId: String,
    val Name: String? = null,
    val Kind: String? = null,
    val Detail: String? = null,
    val Date: String? = null,
)

// Real shape the community Intro Skipper plugin's own
// SkipIntroController.cs returns (github.com/intro-skipper/intro-
// skipper): a segment with no real detection comes back as
// Start: 0, End: 0, that plugin's own Segment.Valid rule is End > 0,
// not something invented client side.
@JsonClass(generateAdapter = true)
data class SkipSegmentDto(
    val Start: Double = 0.0,
    val End: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class IntroSkipperSegmentsDto(
    val Introduction: SkipSegmentDto? = null,
    val Credits: SkipSegmentDto? = null,
)

// Real shape Controllers/NowPlayingController.cs's own anonymous
// projection returns, off the server's own real ISessionManager: only
// a session with a real NowPlayingItem at all is included there, so
// this is never a placeholder/idle session client side either.
@JsonClass(generateAdapter = true)
data class NowPlayingItemDto(
    val Id: String,
    val Name: String? = null,
    val Type: String? = null,
    val SeriesId: String? = null,
    val SeriesName: String? = null,
    val ParentIndexNumber: Int? = null,
    val IndexNumber: Int? = null,
    val ProductionYear: Int? = null,
    val RunTimeTicks: Long? = null,
)

@JsonClass(generateAdapter = true)
data class NowPlayingSessionDto(
    val Id: String? = null,
    val UserName: String? = null,
    val DeviceName: String? = null,
    val IsPaused: Boolean = false,
    val PositionTicks: Long? = null,
    val Item: NowPlayingItemDto? = null,
)

@JsonClass(generateAdapter = true)
data class SleepTimerStartRequest(val Minutes: Int)

// Real Controllers/SleepTimerController.cs shape. Server side, backed
// by SleepTimerService's own background loop and a real
// ISessionManager.SendPlaystateCommand(Stop) delivered over that
// session's own WebSocket, real feedback confirmed there is no
// equivalent connection open from this app at all (Retrofit/OkHttp
// only, no session socket), so that real Stop command has nowhere to
// land here; PlayerScreen's own local countdown is what actually
// enforces it for this client, this status only what real cross-client
// (a signed in web tab watching the same account) parity needs.
@JsonClass(generateAdapter = true)
data class SleepTimerStatusDto(
    val Active: Boolean = false,
    val EndTimeUtc: String? = null,
)

// Real Controllers/AvatarsController.cs shape: Id is a real path
// relative to Jellio's own plugin data directory ("preset.png" for a
// loose one, "Kids/preset.png" for one an admin grouped into a real
// subfolder), not a synthetic id this app invents. Category is that
// subfolder's own name, null for a loose preset.
@JsonClass(generateAdapter = true)
data class AvatarPresetDto(val Id: String, val Category: String? = null)

// Real Controllers/ConfigController.cs shape: one real server side,
// admin controlled source components/seasonalEffects.js's own real
// overlay reads (not a client only localStorage toggle), starting
// with SeasonalEffectsEnabled/SeasonalEffects, confirmed against that
// controller's own real ClientConfig/SeasonalEffectConfig/
// SeasonalRange records before porting this.
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

@JsonClass(generateAdapter = true)
data class ClientConfigDto(
    val SeasonalEffectsEnabled: Boolean = false,
    val SeasonalEffects: Map<String, SeasonalEffectConfigDto> = emptyMap(),
)

// Real Controllers/FeedController.cs shape, its own FeedEntry record:
// Kind ("Watch" | "Badge") is a flat discriminator on one real merged,
// re-sorted list, not two separate response arrays, that controller's
// own header explains why. Watch fields (ItemId..LastEpisodeNumber)
// are null on a Badge entry and vice versa.
// Real Controllers/ProfileController.cs shape: the same {IsPrivate,
// Bio} both its own public GET {userId} route and self only GET
// settings return (settings also carries GrouplistEnabled, not
// modeled here since Grouplist itself has no Android counterpart yet).
@JsonClass(generateAdapter = true)
data class ProfileDto(
    val IsPrivate: Boolean = false,
    val Bio: String? = null,
)

// Real Services/Achievements/GroupedActivityEntry.cs shape,
// ActivityGrouping.Group's own real output: CompletedAtUtc, not
// OccurredAtUtc (FeedEntryDto's own field for the same real concept,
// server side a different record entirely despite the same real data
// underneath it, confirmed against both real C# sources before typing
// either one differently here).
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

// Real Controllers/AchievementsController.cs's own Build(userId)
// shape, badge.Rarity.ToString() as the real BadgeDto.Rarity string
// (Common/Rare/Epic/Legendary).
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
)
