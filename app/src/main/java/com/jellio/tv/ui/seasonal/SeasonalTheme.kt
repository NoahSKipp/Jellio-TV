package com.jellio.tv.ui.seasonal

import androidx.compose.ui.graphics.Color
import com.jellio.tv.data.model.ClientConfigDto
import com.jellio.tv.data.model.SeasonalRangeDto
import java.util.Calendar

// Four real occasions (Halloween, New Year's, Valentine's, Christmas),
// matching Jellio Web's modern seasonal system and replacing the old
// catalogue of three dozen themes ported from Jellyfin-Seasonals.
// Heavy 60 FPS falling emoji particles caused severe UI lag on TV hardware;
// the modern system uses ambient radial gradient washes behind the page.
val THEME_ORDER = listOf(
    "halloween",
    "newyear",
    "valentine",
    "christmas",
)

data class SeasonalWashSpec(
    val wash1: Color,
    val wash2: Color,
    val accent: Color,
    val accent2: Color,
)

// Matches css/app.css's own seasonal theme color tokens:
// #jellioRoot[data-jellio-season="halloween"] {
//   --jellio-season-accent: #ff7518;
//   --jellio-season-accent-2: #7c3aed;
//   --jellio-season-wash-1: #180a24;
//   --jellio-season-wash-2: #1c0f04;
// }
val SEASONAL_THEMES: Map<String, SeasonalWashSpec> = mapOf(
    "halloween" to SeasonalWashSpec(
        wash1 = Color(0xFF180A24),
        wash2 = Color(0xFF1C0F04),
        accent = Color(0xFFFF7518),
        accent2 = Color(0xFF7C3AED),
    ),
    "newyear" to SeasonalWashSpec(
        wash1 = Color(0xFF05070F),
        wash2 = Color(0xFF0B0D16),
        accent = Color(0xFFD4AF37),
        accent2 = Color(0xFFEEF0F5),
    ),
    "valentine" to SeasonalWashSpec(
        wash1 = Color(0xFF210510),
        wash2 = Color(0xFF160308),
        accent = Color(0xFFFF4D6D),
        accent2 = Color(0xFFFFB3C6),
    ),
    "christmas" to SeasonalWashSpec(
        wash1 = Color(0xFF031A10),
        wash2 = Color(0xFF160603),
        accent = Color(0xFFC0392B),
        accent2 = Color(0xFFD4AF37),
    ),
)

// Real port of components/seasons.js's own inRange(): a day-of-year range
// comparison that wraps New Year's (December into January).
fun inRange(month: Int, day: Int, range: SeasonalRangeDto?): Boolean {
    if (range == null) return false
    val now = month * 100 + day
    val start = range.StartMonth * 100 + range.StartDay
    val end = range.EndMonth * 100 + range.EndDay
    return if (start <= end) now in start..end else now >= start || now <= end
}

val SUPPORTED_SEASONAL_THEMES: Set<String> = SEASONAL_THEMES.keys

fun activeSeasonalTheme(calendar: Calendar, config: ClientConfigDto?): String? {
    if (config == null || !config.SeasonalEffectsEnabled) return null
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    for (key in THEME_ORDER) {
        val effect = config.SeasonalEffects[key] ?: continue
        if (!effect.Enabled) continue
        if (inRange(month, day, effect.Range)) return key
    }
    return null
}
