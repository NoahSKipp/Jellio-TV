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
