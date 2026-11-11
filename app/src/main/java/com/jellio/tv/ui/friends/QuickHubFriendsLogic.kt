package com.jellio.tv.ui.friends

import com.jellio.tv.data.JellyfinDates
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Server ids come with or without dashes and in any case.
fun friendIdKey(id: String?): String = id.orEmpty().replace("-", "").lowercase()

// An episode is shown as its series.
fun watchingTitle(type: String?, name: String?, seriesName: String?): String? {
    val title = if (type == "Episode" && !seriesName.isNullOrBlank()) seriesName else name
    return title?.trim()?.takeIf { it.isNotEmpty() }
}

// Viewer id to the title they are watching, from (user id, title) pairs; the first session wins.
fun watchingByUser(sessions: List<Pair<String?, String?>>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for ((userId, title) in sessions) {
        val key = friendIdKey(userId)
        if (key.isEmpty() || title.isNullOrBlank()) continue
        if (key !in result) result[key] = title
    }
    return result
}

sealed interface FriendSubtitle {
    data class Watching(val title: String) : FriendSubtitle
    data class Since(val label: String) : FriendSubtitle
    data object Empty : FriendSubtitle
}

fun friendSubtitle(watching: String?, sinceLabel: String?): FriendSubtitle = when {
    !watching.isNullOrBlank() -> FriendSubtitle.Watching(watching)
    !sinceLabel.isNullOrBlank() -> FriendSubtitle.Since(sinceLabel)
    else -> FriendSubtitle.Empty
}

// "March 2024" in the reader's language; null when the date is missing or unreadable.
fun formatMonthYear(iso: String?, pattern: String, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String? {
    val instant = JellyfinDates.parseInstant(iso) ?: return null
    return runCatching { DateTimeFormatter.ofPattern(pattern, locale).withZone(zone).format(instant) }.getOrNull()
}

// Find people lists those who can still be asked; friends and others are not offered.
fun isFindable(status: String?): Boolean {
    val relation = relationOf(status)
    return relation == FriendRelation.None || relation == FriendRelation.Outgoing
}
