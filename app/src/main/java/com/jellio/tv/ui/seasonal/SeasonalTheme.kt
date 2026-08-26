package com.jellio.tv.ui.seasonal

import com.jellio.tv.data.model.ClientConfigDto
import com.jellio.tv.data.model.SeasonalRangeDto
import java.util.Calendar

// Real port of components/seasonalEffects.js's own real THEME_ORDER:
// priority when more than one real range matches at once (Halloween
// week sitting inside Autumn's own much wider window, New Year's own
// week sitting inside winter's), so only one real particle system ever
// renders at a time rather than stacking unrelated ones over each
// other. Kept as the full real 34 entry list, not trimmed to what
// DRIFT_THEMES below actually knows how to draw: activeSeasonalTheme()
// skips any key this app has no real renderer for the same way it
// skips a disabled one, so admin priority among the themes this app
// does support stays exactly real, and an admin-enabled theme this
// app does not yet draw simply shows nothing rather than the wrong
// substitute.
val THEME_ORDER = listOf(
    "friday13", "birthday", "eid", "resurrection", "hearts", "carnival", "oscar",
    "marioday", "filmnoir", "space", "cherryblossom", "earthday", "starwars",
    "eurovision", "pride", "underwater", "oktoberfest", "spooky", "halloween",
    "santa", "newyear", "christmas", "snowflakes", "snowfall", "nightsky",
    "matrix", "frost", "storm", "rain", "sports", "snowstorm", "winter",
    "autumn", "summer", "spring",
)

enum class DriftDirection { DOWN, UP }

// Real port of components/seasonalEffects.js's own buildDrift() options
// shape: a shared particle engine every falling/rising theme uses,
// glyphs (or, for earthday's own real coloured petals, a plain dot
// with no glyph at all) drifting down or up. sizePx/durationSec below
// are ranges, one real random draw per particle the same way that
// file's own rand() calls are.
data class DriftThemeSpec(
    val glyphs: List<String> = emptyList(),
    val isDot: Boolean = false,
    val dotColors: List<Long> = emptyList(),
    val count: Int,
    val minSizePx: Float,
    val maxSizePx: Float,
    val minOpacity: Float,
    val maxOpacity: Float,
    val minDurationSec: Float,
    val maxDurationSec: Float,
    val direction: DriftDirection,
    val glow: Boolean = false,
)

// Real port of that file's own DRIFT_THEMES table, values carried over
// verbatim (glyphs, counts, size/opacity/duration ranges).
val DRIFT_THEMES: Map<String, DriftThemeSpec> = mapOf(
    "winter" to DriftThemeSpec(glyphs = listOf("❄"), count = 60, minSizePx = 10f, maxSizePx = 22f, minOpacity = 0.5f, maxOpacity = 0.95f, minDurationSec = 8f, maxDurationSec = 16f, direction = DriftDirection.DOWN),
    "autumn" to DriftThemeSpec(glyphs = listOf("🍂", "🍁"), count = 40, minSizePx = 14f, maxSizePx = 24f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 7f, maxDurationSec = 14f, direction = DriftDirection.DOWN),
    "spring" to DriftThemeSpec(glyphs = listOf("🌸", "🌷"), count = 35, minSizePx = 12f, maxSizePx = 20f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 9f, maxDurationSec = 17f, direction = DriftDirection.DOWN),
    "summer" to DriftThemeSpec(glyphs = listOf("✨"), count = 30, minSizePx = 8f, maxSizePx = 16f, minOpacity = 0.4f, maxOpacity = 0.85f, minDurationSec = 10f, maxDurationSec = 18f, direction = DriftDirection.UP),
    "halloween" to DriftThemeSpec(glyphs = listOf("🦇", "🎃"), count = 30, minSizePx = 16f, maxSizePx = 26f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 8f, maxDurationSec = 15f, direction = DriftDirection.DOWN),
    "hearts" to DriftThemeSpec(glyphs = listOf("❤️", "💕", "💞", "💓", "💗", "💖"), count = 25, minSizePx = 14f, maxSizePx = 24f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 12f, maxDurationSec = 18f, direction = DriftDirection.UP),
    "cherryblossom" to DriftThemeSpec(glyphs = listOf("🌸"), count = 25, minSizePx = 14f, maxSizePx = 22f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 9f, maxDurationSec = 16f, direction = DriftDirection.DOWN),
    "eid" to DriftThemeSpec(glyphs = listOf("🏮", "🌙", "⭐", "✨"), count = 12, minSizePx = 16f, maxSizePx = 26f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 10f, maxDurationSec = 17f, direction = DriftDirection.DOWN),
    "christmas" to DriftThemeSpec(glyphs = listOf("❆", "🎁", "❄️", "🎅", "🎊"), count = 30, minSizePx = 14f, maxSizePx = 24f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 8f, maxDurationSec = 16f, direction = DriftDirection.DOWN),
    "oktoberfest" to DriftThemeSpec(glyphs = listOf("🥨", "🍺", "🍻"), count = 25, minSizePx = 16f, maxSizePx = 26f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 8f, maxDurationSec = 15f, direction = DriftDirection.DOWN),
    "eurovision" to DriftThemeSpec(glyphs = listOf("♪", "♫", "♬", "♭", "♮", "♯"), count = 25, minSizePx = 14f, maxSizePx = 22f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 8f, maxDurationSec = 15f, direction = DriftDirection.DOWN, glow = true),
    "earthday" to DriftThemeSpec(isDot = true, dotColors = listOf(0xFFFF69B4, 0xFFFFD700, 0xFF87CEFA, 0xFFFF4500, 0xFFBA55D3, 0xFFFFA500, 0xFFFF1493), count = 45, minSizePx = 6f, maxSizePx = 12f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 9f, maxDurationSec = 16f, direction = DriftDirection.DOWN),
    "pride" to DriftThemeSpec(glyphs = listOf("❤️", "🧡", "💛", "💚", "💙", "💜"), count = 20, minSizePx = 16f, maxSizePx = 24f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 10f, maxDurationSec = 15f, direction = DriftDirection.UP),
    "resurrection" to DriftThemeSpec(glyphs = listOf("✝️", "🕊️", "🌿"), count = 12, minSizePx = 18f, maxSizePx = 28f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 10f, maxDurationSec = 17f, direction = DriftDirection.DOWN),
    "snowflakes" to DriftThemeSpec(glyphs = listOf("❅", "❆"), count = 25, minSizePx = 12f, maxSizePx = 22f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 8f, maxDurationSec = 16f, direction = DriftDirection.DOWN),
    "birthday" to DriftThemeSpec(glyphs = listOf("🎈", "🎉", "🎊"), count = 12, minSizePx = 20f, maxSizePx = 30f, minOpacity = 0.7f, maxOpacity = 1f, minDurationSec = 11f, maxDurationSec = 17f, direction = DriftDirection.UP),
    "spooky" to DriftThemeSpec(glyphs = listOf("👻", "🦇", "🎃"), count = 25, minSizePx = 18f, maxSizePx = 28f, minOpacity = 0.6f, maxOpacity = 1f, minDurationSec = 8f, maxDurationSec = 15f, direction = DriftDirection.DOWN, glow = true),
)

// Real port of that file's own inRange(): a day-of-year range
// comparison that wraps New Year's, the same real problem the
// server's own December to January windows (winter, New Year) both
// have.
fun inRange(month: Int, day: Int, range: SeasonalRangeDto?): Boolean {
    if (range == null) return false
    val now = month * 100 + day
    val start = range.StartMonth * 100 + range.StartDay
    val end = range.EndMonth * 100 + range.EndDay
    return if (start <= end) now in start..end else now >= start || now <= end
}

fun isFriday13(calendar: Calendar): Boolean =
    calendar.get(Calendar.DAY_OF_MONTH) == 13 && calendar.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY

// Real port of that file's own real, singular activeSeasonalTheme():
// "what's active right now" against ConfigController.cs's own real
// response shape.
fun activeSeasonalTheme(calendar: Calendar, config: ClientConfigDto?): String? {
    if (config == null || !config.SeasonalEffectsEnabled) return null
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    for (key in THEME_ORDER) {
        if (key !in DRIFT_THEMES) continue
        val effect = config.SeasonalEffects[key] ?: continue
        if (!effect.Enabled) continue
        val active = if (key == "friday13") isFriday13(calendar) else inRange(month, day, effect.Range)
        if (active) return key
    }
    return null
}
