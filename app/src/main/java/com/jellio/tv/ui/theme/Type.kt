package com.jellio.tv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

val JellioFontFamily = FontFamily.Default

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
    labelSmall = TextStyle(
        fontFamily = JellioFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    ),
)
