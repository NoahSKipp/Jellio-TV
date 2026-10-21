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
    if (minutes < 1) return com.jellio.tv.ui.i18n.Strings.get("time_just_now")
    if (minutes < 60) return if (minutes == 1L) com.jellio.tv.ui.i18n.Strings.get("time_minute_ago") else com.jellio.tv.ui.i18n.Strings.format("time_minutes_ago", minutes)
    val hours = Math.round(minutes / 60.0)
    if (hours < 24) return if (hours == 1L) com.jellio.tv.ui.i18n.Strings.get("time_hour_ago") else com.jellio.tv.ui.i18n.Strings.format("time_hours_ago", hours)
    val days = Math.round(hours / 24.0)
    if (days < 30) return if (days == 1L) com.jellio.tv.ui.i18n.Strings.get("time_day_ago") else com.jellio.tv.ui.i18n.Strings.format("time_days_ago", days)
    val months = Math.round(days / 30.0)
    if (months < 12) return if (months == 1L) com.jellio.tv.ui.i18n.Strings.get("time_month_ago") else com.jellio.tv.ui.i18n.Strings.format("time_months_ago", months)
    val years = Math.round(months / 12.0)
    return if (years == 1L) com.jellio.tv.ui.i18n.Strings.get("time_year_ago") else com.jellio.tv.ui.i18n.Strings.format("time_years_ago", years)
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
// server side.
fun watchActivityText(
    itemType: String?,
    seriesName: String?,
    episodeCount: Int,
    seasonNumber: Int?,
    firstEpisodeNumber: Int?,
    lastEpisodeNumber: Int?,
    itemName: String?,
): AnnotatedString = buildAnnotatedString {
    val lang = com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage.code.lowercase()
    val isEpisode = itemType == "Episode" && !seriesName.isNullOrEmpty()
    val isGerman = lang == "de"

    if (isGerman) {
        if (isEpisode) {
            val season = if (seasonNumber != null) com.jellio.tv.ui.i18n.Strings.format("feed_season_single", seasonNumber) + ", " else ""
            if (episodeCount > 1) {
                val range = if (firstEpisodeNumber != null && lastEpisodeNumber != null) {
                    com.jellio.tv.ui.i18n.Strings.format("feed_episodes_range", firstEpisodeNumber, lastEpisodeNumber)
                } else {
                    com.jellio.tv.ui.i18n.Strings.format("feed_episodes_count", episodeCount)
                }
                append("$season$range ")
            } else if (firstEpisodeNumber != null) {
                append(season + com.jellio.tv.ui.i18n.Strings.format("feed_episode_single", firstEpisodeNumber) + " ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(seriesName) }
            append(" " + com.jellio.tv.ui.i18n.Strings.get("activity_watched").lowercase())
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(itemName ?: "") }
            append(" " + com.jellio.tv.ui.i18n.Strings.get("activity_watched").lowercase())
        }
    } else {
        append(com.jellio.tv.ui.i18n.Strings.get("activity_watched") + " ")
        if (isEpisode) {
            val season = if (seasonNumber != null) com.jellio.tv.ui.i18n.Strings.format("feed_season_single", seasonNumber) + ", " else ""
            if (episodeCount > 1) {
                val range = if (firstEpisodeNumber != null && lastEpisodeNumber != null) {
                    com.jellio.tv.ui.i18n.Strings.format("feed_episodes_range", firstEpisodeNumber, lastEpisodeNumber)
                } else {
                    com.jellio.tv.ui.i18n.Strings.format("feed_episodes_count", episodeCount)
                }
                append("$season$range ")
            } else if (firstEpisodeNumber != null) {
                append(season + com.jellio.tv.ui.i18n.Strings.format("feed_episode_single", firstEpisodeNumber) + " ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(seriesName) }
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(itemName ?: "") }
        }
    }
}

// Real port of screens/feed.js's own appendBadgeDescription(): Feed
// rows only (Kind == "Badge"), Profile's own badge grid uses a
// different tile layout for the same real data, not this description
// line. Real color on the badge title itself, same as that file's own
// title.style.color, not a plain SemiBold like watchActivityText's own
// series/item name.
fun badgeActivityText(badgeName: String?, rarity: String?): AnnotatedString = buildAnnotatedString {
    append(com.jellio.tv.ui.i18n.Strings.get("activity_unlocked") + " ")
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = rarityColor(rarity))) {
        append(badgeName ?: "")
    }
}
