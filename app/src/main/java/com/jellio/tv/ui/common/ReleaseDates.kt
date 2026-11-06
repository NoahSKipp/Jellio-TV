package com.jellio.tv.ui.common

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.i18n.Strings
import com.jellio.tv.ui.i18n.displayLocale
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val ISO_DATE_PREFIX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")

private data class ParsedUtcDay(val year: Int, val epochMs: Long)

private fun parseUtcDay(raw: String?): ParsedUtcDay? {
    if (raw.isNullOrBlank()) return null
    val match = ISO_DATE_PREFIX.find(raw.trim()) ?: return null
    val (yearStr, monthStr, dayStr) = match.destructured
    val year = yearStr.toIntOrNull() ?: return null
    val month = monthStr.toIntOrNull() ?: return null
    val day = dayStr.toIntOrNull() ?: return null
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(year, month - 1, day, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return ParsedUtcDay(year, cal.timeInMillis)
}

private fun utcMidnightMs(nowMs: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun isUnreleasedItem(item: BaseItemDto, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (item.UserData?.Played == true || (item.UserData?.PlaybackPositionTicks ?: 0L) > 0L) return false
    val todayUtcMs = utcMidnightMs(nowMs)
    if (!item.PremiereDate.isNullOrBlank()) {
        val parsed = parseUtcDay(item.PremiereDate) ?: return false
        if (parsed.year >= 9000) return true
        return parsed.epochMs > todayUtcMs
    }
    if (!item.EndDate.isNullOrBlank()) {
        val parsed = parseUtcDay(item.EndDate) ?: return false
        return parsed.year >= 9000 || parsed.epochMs > todayUtcMs
    }
    return false
}

fun episodeReleaseLabel(
    item: BaseItemDto,
    nowMs: Long = System.currentTimeMillis(),
    locale: Locale = displayLocale(),
): String? {
    if (!isUnreleasedItem(item, nowMs)) return null
    val todayUtcMs = utcMidnightMs(nowMs)
    if (!item.PremiereDate.isNullOrBlank()) {
        val parsed = parseUtcDay(item.PremiereDate)
        if (parsed != null && parsed.year < 9000) {
            val diffDays = ((parsed.epochMs - todayUtcMs) / (24L * 60L * 60L * 1000L)).toInt()
            return when {
                diffDays <= 0 -> Strings.get("calendar_today")
                diffDays == 1 -> Strings.get("calendar_tomorrow")
                diffDays < 7 -> Strings.format("coming_soon_in_days", diffDays)
                else -> SimpleDateFormat("MMM d", locale).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(parsed.epochMs))
            }
        }
    }
    return Strings.get("coming_soon_badge")
}

fun filterReleasedItems(items: List<BaseItemDto>, nowMs: Long = System.currentTimeMillis()): List<BaseItemDto> {
    return items.filterNot { isUnreleasedItem(it, nowMs) }
}

fun seasonEpisodeReleaseLabels(
    episodes: List<BaseItemDto>,
    nowMs: Long = System.currentTimeMillis(),
    locale: Locale = displayLocale(),
): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    var seenUnreleased = false
    for (ep in episodes) {
        val label = episodeReleaseLabel(ep, nowMs, locale)
        if (label != null) {
            result[ep.Id] = label
            seenUnreleased = true
        } else if (seenUnreleased &&
            ep.PremiereDate.isNullOrBlank() &&
            ep.UserData?.Played != true &&
            (ep.UserData?.PlaybackPositionTicks ?: 0L) <= 0L &&
            (ep.RunTimeTicks ?: 0L) <= 0L
        ) {
            result[ep.Id] = Strings.get("coming_soon_badge")
        }
    }
    return result
}
