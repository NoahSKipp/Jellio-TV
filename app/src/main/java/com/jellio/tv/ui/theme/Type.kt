package com.jellio.tv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// css/app.css's own --jellio-font-family leads with Inter; this app
// falls back to the system font until a real Inter font resource
// (res/font/) replaces FontFamily.Default, same real fallback stack
// the web build itself hits on a client with no path to Google Fonts.
val JellioFontFamily = FontFamily.Default

// Sized for a real 10-foot living room viewing distance, not a phone
// held in hand: real feedback live was that the first pass read as
// native Jellyfin's own default, cramped TV UI rather than Nuvio's
// own deliberately oversized one, and default/small Compose text
// sizes were exactly why.
val JellioTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    // css/app.css's own .jellio-mobile-nav-label: 0.66em against that
    // pill's own 22px base, the nav pill's own small icon-under label,
    // real feedback found the taller labelMedium default too big
    // there.
    labelSmall = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    ),
)
