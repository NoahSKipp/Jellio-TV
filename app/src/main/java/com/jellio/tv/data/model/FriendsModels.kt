package com.jellio.tv.data.model

import androidx.compose.runtime.Immutable
import com.squareup.moshi.JsonClass

// Friends and watchlist sharing (Controllers/FriendsController.cs).
@Immutable
@JsonClass(generateAdapter = true)
data class FriendEntryDto(
    val UserId: String = "",
    val UserName: String = "",
    val PrimaryImageTag: String? = null,
    val Since: String? = null,
    val Muted: Boolean = false,
)

@Immutable
@JsonClass(generateAdapter = true)
data class FriendRequestDto(
    val UserId: String = "",
    val UserName: String = "",
    val PrimaryImageTag: String? = null,
    val RequestId: String = "",
    val At: String? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class FriendsListDto(
    val Friends: List<FriendEntryDto> = emptyList(),
    val Incoming: List<FriendRequestDto> = emptyList(),
    val Outgoing: List<FriendRequestDto> = emptyList(),
)

@Immutable
@JsonClass(generateAdapter = true)
data class FriendStatusDto(
    val Status: String = "none",
    val RequestId: String? = null,
    val CanRequest: Boolean = false,
)

// One public user in "Find people".
@Immutable
@JsonClass(generateAdapter = true)
data class FriendPersonDto(
    val UserId: String = "",
    val UserName: String = "",
    val PrimaryImageTag: String? = null,
    val Status: String = "none",
)

@JsonClass(generateAdapter = true)
data class FriendRequestBody(val UserId: String)

@JsonClass(generateAdapter = true)
data class FriendStatusResultDto(val Status: String = "none")

@JsonClass(generateAdapter = true)
data class FriendMuteBody(val Muted: Boolean)

@JsonClass(generateAdapter = true)
data class FriendMuteResultDto(val Muted: Boolean = false)

@JsonClass(generateAdapter = true)
data class WatchlistFriendsBody(val ItemId: String, val UserIds: List<String>)

@JsonClass(generateAdapter = true)
data class WatchlistFriendResultDto(val UserId: String = "", val Result: String = "")

@JsonClass(generateAdapter = true)
data class WatchlistFriendsResponseDto(val Results: List<WatchlistFriendResultDto> = emptyList())

@JsonClass(generateAdapter = true)
data class AddedByEntryDto(
    val UserId: String = "",
    val UserName: String = "",
    val PrimaryImageTag: String? = null,
    val At: String? = null,
)

@JsonClass(generateAdapter = true)
data class AddedByResponseDto(val Items: Map<String, List<AddedByEntryDto>> = emptyMap())

@JsonClass(generateAdapter = true)
data class FriendsSettingsBody(
    val FriendsCanAddToWatchlist: Boolean? = null,
    val FriendRequestsFrom: String? = null,
)

@JsonClass(generateAdapter = true)
data class FriendsSettingsDto(
    val FriendsCanAddToWatchlist: Boolean = true,
    val FriendRequestsFrom: String = "everyone",
)
