package com.jellio.tv.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveProgramDto(
    val Id: String? = null,
    val ChannelId: String? = null,
    val Name: String? = null,
    val EpisodeTitle: String? = null,
    val StartDate: String? = null,
    val EndDate: String? = null,
)

@JsonClass(generateAdapter = true)
data class LiveChannelDto(
    val Id: String,
    val Name: String? = null,
    val ChannelNumber: String? = null,
    val ImageTags: Map<String, String>? = null,
    val UserData: UserItemDataDto? = null,
    val CurrentProgram: LiveProgramDto? = null,
)

@JsonClass(generateAdapter = true)
data class LiveChannelsResponse(val Items: List<LiveChannelDto> = emptyList())

@JsonClass(generateAdapter = true)
data class LiveProgramsResponse(val Items: List<LiveProgramDto> = emptyList())

@JsonClass(generateAdapter = true)
data class LiveProgramsRequest(
    val UserId: String,
    val ChannelIds: List<String>,
    val MinEndDate: String,
    val MaxStartDate: String,
    val SortBy: List<String> = listOf("StartDate"),
    val Limit: Int = 500,
    val EnableTotalRecordCount: Boolean = false,
    val EnableImages: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class LiveDirectPlayProfile(
    val Container: String,
    val Type: String = "Video",
    val VideoCodec: String? = null,
    val AudioCodec: String? = null,
)

@JsonClass(generateAdapter = true)
data class LiveDeviceProfile(
    val MaxStreamingBitrate: Long = 40_000_000,
    val MaxStaticBitrate: Long = 40_000_000,
    val DirectPlayProfiles: List<LiveDirectPlayProfile> = emptyList(),
    val TranscodingProfiles: List<LiveDirectPlayProfile> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LivePlaybackInfoRequest(
    val UserId: String,
    val AutoOpenLiveStream: Boolean = true,
    val DeviceProfile: LiveDeviceProfile,
)

@JsonClass(generateAdapter = true)
data class LiveMediaSourceDto(
    val Id: String? = null,
    val LiveStreamId: String? = null,
    val Container: String? = null,
    val TranscodingUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class LivePlaybackInfoResponse(
    val MediaSources: List<LiveMediaSourceDto> = emptyList(),
    val PlaySessionId: String? = null,
    val ErrorCode: String? = null,
)

// Jellio/StreamLinks: streams a user added by link.
@JsonClass(generateAdapter = true)
data class StreamLinkEntryDto(
    val Name: String? = null,
    val Url: String? = null,
    val Logo: String? = null,
    val Group: String? = null,
)

@JsonClass(generateAdapter = true)
data class StreamLinkDto(
    val Id: String,
    val Name: String? = null,
    val SourceUrl: String? = null,
    val Kind: String? = null,
    val Entries: List<StreamLinkEntryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class StreamLinkPlayDto(
    val Name: String? = null,
    val Format: String? = null,
    val Mime: String? = null,
    val DirectUrl: String? = null,
    val Referer: String? = null,
    val UserAgent: String? = null,
    val ProxyPath: String? = null,
)

@JsonClass(generateAdapter = true)
data class StreamPairingDto(
    val Code: String,
    val ExpiresAt: String? = null,
    val Path: String? = null,
)

@JsonClass(generateAdapter = true)
data class StreamPairingStatusDto(
    val Status: String? = null,
    val LinkId: String? = null,
    val LinkName: String? = null,
)
