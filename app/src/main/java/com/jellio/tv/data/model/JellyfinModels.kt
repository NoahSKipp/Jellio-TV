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
)

@JsonClass(generateAdapter = true)
data class BaseItemDto(
    val Id: String,
    val Name: String? = null,
    val Type: String? = null,
    val CollectionType: String? = null,
    val SeriesName: String? = null,
    val ProductionYear: Int? = null,
    val Overview: String? = null,
    val ImageTags: Map<String, String>? = null,
    val BackdropImageTags: List<String>? = null,
    val UserData: UserItemDataDto? = null,
    val RunTimeTicks: Long? = null,
    val ProviderIds: Map<String, String>? = null,
)

@JsonClass(generateAdapter = true)
data class ItemsResultDto(
    val Items: List<BaseItemDto> = emptyList(),
    val TotalRecordCount: Int = 0,
)
