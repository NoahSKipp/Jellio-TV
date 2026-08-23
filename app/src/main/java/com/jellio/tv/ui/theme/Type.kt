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

val JellioTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
)
