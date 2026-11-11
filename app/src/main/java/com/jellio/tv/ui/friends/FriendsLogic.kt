package com.jellio.tv.ui.friends

import com.jellio.tv.data.FriendFailure

enum class FriendRelation { Self, None, Outgoing, Incoming, Friends, Private, Closed, Unknown }

fun relationOf(status: String?): FriendRelation = when (status?.lowercase()) {
    "self" -> FriendRelation.Self
    "none" -> FriendRelation.None
    "outgoing" -> FriendRelation.Outgoing
    "incoming" -> FriendRelation.Incoming
    "friends" -> FriendRelation.Friends
    "private" -> FriendRelation.Private
    "closed" -> FriendRelation.Closed
    else -> FriendRelation.Unknown
}

// What the button next to a profile name shows.
enum class FriendButtonState { Hidden, Add, RequestSent, Respond, Friends }

fun buttonStateOf(relation: FriendRelation, canRequest: Boolean): FriendButtonState = when (relation) {
    FriendRelation.None -> if (canRequest) FriendButtonState.Add else FriendButtonState.Hidden
    FriendRelation.Outgoing -> FriendButtonState.RequestSent
    FriendRelation.Incoming -> FriendButtonState.Respond
    FriendRelation.Friends -> FriendButtonState.Friends
    FriendRelation.Self, FriendRelation.Private, FriendRelation.Closed, FriendRelation.Unknown -> FriendButtonState.Hidden
}

// One line per friend after "Add to a friend's watchlist".
fun addResultKey(result: String?): String = when (result) {
    "added" -> "friends_result_added"
    "already_on_list" -> "friends_result_already_on_list"
    "already_watched" -> "friends_result_already_watched"
    "not_visible" -> "friends_result_not_visible"
    "muted" -> "friends_result_muted"
    "disabled" -> "friends_result_disabled"
    "limit" -> "friends_result_limit"
    "not_friends" -> "friends_result_not_friends"
    "unsupported" -> "friends_result_unsupported"
    else -> "friends_result_failed"
}

fun isAddSuccess(result: String?): Boolean = result == "added" || result == "already_on_list"

fun failureKey(failure: FriendFailure): String = when (failure) {
    FriendFailure.Private -> "friends_err_private"
    FriendFailure.Closed -> "friends_err_closed"
    FriendFailure.Cooldown -> "friends_err_cooldown"
    FriendFailure.FriendLimit -> "friends_err_limit"
    FriendFailure.Gone -> "friends_err_gone"
    FriendFailure.Failed -> "friends_err_failed"
}

// Only films and shows can be sent to a friend.
fun canShareType(type: String?): Boolean = type == "Movie" || type == "Series"

// Friend notifications from the server.
const val KIND_FRIEND_REQUEST = "friend_request"
const val KIND_FRIEND_ACCEPTED = "friend_accepted"
const val KIND_WATCHLIST_ADDED = "watchlist_added"

fun isFriendKind(kind: String?): Boolean =
    kind == KIND_FRIEND_REQUEST || kind == KIND_FRIEND_ACCEPTED || kind == KIND_WATCHLIST_ADDED

// "Detail" carries the number of titles in a watchlist_added notice.
fun watchlistAddedCount(detail: String?): Int = detail?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1

enum class NoticeTarget { None, Item, Watchlist }

fun watchlistNoticeTarget(count: Int, hasItem: Boolean): NoticeTarget = when {
    count > 1 -> NoticeTarget.Watchlist
    hasItem -> NoticeTarget.Item
    else -> NoticeTarget.None
}

fun badgeVisible(incomingRequests: Int): Boolean = incomingRequests > 0

// Who is shown first when a person is already a friend: mute state is a plain toggle.
fun nextMuted(current: Boolean): Boolean = !current
