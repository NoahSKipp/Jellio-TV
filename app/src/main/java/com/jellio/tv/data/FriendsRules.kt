package com.jellio.tv.data

import com.jellio.tv.data.model.AddedByResponseDto
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CommunityWatcherDto

internal const val ADDED_BY_MAX = 3

// Ids come with or without dashes; the map is keyed the way Jellyfin lists items.
internal fun plainId(id: String): String = id.replace("-", "").lowercase()

// "Added by" per title, newest first and at most three, shaped like the
// watchers badge's people.
internal fun addedByMap(dto: AddedByResponseDto): Map<String, List<CommunityWatcherDto>> {
    val result = HashMap<String, List<CommunityWatcherDto>>()
    for ((itemId, entries) in dto.Items) {
        val people = entries
            .filter { it.UserId.isNotBlank() }
            .take(ADDED_BY_MAX)
            .map { CommunityWatcherDto(UserId = it.UserId, UserName = it.UserName, PrimaryImageTag = it.PrimaryImageTag) }
        if (people.isNotEmpty()) result[plainId(itemId)] = people
    }
    return result
}

internal fun List<BaseItemDto>.withAddedBy(byItem: Map<String, List<CommunityWatcherDto>>): List<BaseItemDto> {
    if (byItem.isEmpty()) return this
    return this.map { item ->
        val by = byItem[plainId(item.Id)]
        if (by == null) item else item.copy(AddedBy = by)
    }
}

// Why a friend action was refused, for a calm message instead of raw errors.
enum class FriendFailure { Private, Closed, Cooldown, FriendLimit, Gone, Failed }

class FriendRequestException(val failure: FriendFailure) : Exception(failure.name)

private val STATUS_IN_BODY = Regex("\"Status\"\\s*:\\s*\"([A-Za-z]+)\"")

internal fun statusFromBody(body: String?): String? =
    body?.let { STATUS_IN_BODY.find(it)?.groupValues?.get(1)?.lowercase() }

// 403 {Status: private|closed}, 429 cooldown or too many pending, 409 friend
// limit, 404 the request is gone.
internal fun friendFailureOf(code: Int, status: String?): FriendFailure = when (code) {
    403 -> when (status) {
        "private" -> FriendFailure.Private
        "closed" -> FriendFailure.Closed
        else -> FriendFailure.Failed
    }
    429 -> FriendFailure.Cooldown
    409 -> FriendFailure.FriendLimit
    404 -> FriendFailure.Gone
    else -> FriendFailure.Failed
}
