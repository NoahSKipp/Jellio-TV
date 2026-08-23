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
data class CalendarEntryDto(
    val ItemId: String,
    val Name: String? = null,
    val Kind: String? = null,
    val Detail: String? = null,
    val Date: String? = null,
)
