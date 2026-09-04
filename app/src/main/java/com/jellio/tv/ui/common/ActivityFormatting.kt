package com.jellio.tv.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import com.jellio.tv.ui.theme.JellioRarityCommon
import com.jellio.tv.ui.theme.JellioRarityEpic
import com.jellio.tv.ui.theme.JellioRarityLegendary
import com.jellio.tv.ui.theme.JellioRarityRare
import java.time.Instant
import java.time.temporal.ChronoUnit

// Real port of runtime/format.js's own formatRelativeTime(), shared by
// screens/feed.js and screens/profile.js's own recent activity list.
fun formatRelativeTime(isoUtc: String?): String {
    val then = isoUtc?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    val minutes = ChronoUnit.MINUTES.between(then, Instant.now())
    if (minutes < 1) return "just now"
    if (minutes < 60) return "$minutes " + if (minutes == 1L) "minute ago" else "minutes ago"
    val hours = Math.round(minutes / 60.0)
    if (hours < 24) return "$hours " + if (hours == 1L) "hour ago" else "hours ago"
    val days = Math.round(hours / 24.0)
    if (days < 30) return "$days " + if (days == 1L) "day ago" else "days ago"
    val months = Math.round(days / 30.0)
    if (months < 12) return "$months " + if (months == 1L) "month ago" else "months ago"
    val years = Math.round(months / 12.0)
    return "$years " + if (years == 1L) "year ago" else "years ago"
}

// Real FeedController.cs/AchievementCatalog.cs shape: only these four
// tiers, "Legendary" the same real --jellio-trending-color the CSS
// theme's own header already reuses for it.
fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "rare" -> JellioRarityRare
    "epic" -> JellioRarityEpic
    "legendary" -> JellioRarityLegendary
    else -> JellioRarityCommon
}

// Real port of screens/feed.js's own appendWatchDescription(): shared
// by Feed rows (Kind == "Watch") and Profile's own recent activity
// list, both fed by the exact same real ActivityGrouping.Group output
// server side, just through two different real endpoints
// (Jellio/feed's merged FeedEntry vs Jellio/achievements/{userId}'s
// own GroupedActivityEntry). itemType/seriesName/episodeCount/
// seasonNumber/firstEpisodeNumber/lastEpisodeNumber/itemName are the
// exact fields both real DTOs share.
fun watchActivityText(
    itemType: String?,
    seriesName: String?,
    episodeCount: Int,
    seasonNumber: Int?,
    firstEpisodeNumber: Int?,
    lastEpisodeNumber: Int?,
    itemName: String?,
): AnnotatedString = buildAnnotatedString {
    append("Watched ")
    if (itemType == "Episode" && !seriesName.isNullOrEmpty()) {
        val season = if (seasonNumber != null) "Season $seasonNumber, " else ""
        if (episodeCount > 1) {
            val range = if (firstEpisodeNumber != null && lastEpisodeNumber != null) {
                "Episodes $firstEpisodeNumber-$lastEpisodeNumber"
            } else {
                "$episodeCount episodes"
            }
            append(season + range + " of ")
        } else if (firstEpisodeNumber != null) {
            append(season + "Episode " + firstEpisodeNumber + " of ")
        }
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(seriesName) }
    } else {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(itemName ?: "") }
    }
}

// Real port of screens/feed.js's own appendBadgeDescription(): Feed
// rows only (Kind == "Badge"), Profile's own badge grid uses a
// different tile layout for the same real data, not this description
// line. Real color on the badge title itself, same as that file's own
// title.style.color, not a plain SemiBold like watchActivityText's own
// series/item name.
fun badgeActivityText(badgeName: String?, rarity: String?): AnnotatedString = buildAnnotatedString {
    append("Unlocked ")
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = rarityColor(rarity))) {
        append(badgeName ?: "")
    }
}
