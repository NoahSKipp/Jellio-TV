package com.jellio.tv.ui.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Modern Jellio seasonal ambient overlay: draws dual radial gradient washes
// matching css/app.css's .jellio-seasons-wash behind page content.
// Eliminates the CPU-heavy 60 FPS particle loop that caused severe lag on TV.
@Composable
fun SeasonalEffectsOverlay(themeKey: String?, modifier: Modifier = Modifier) {
    val spec = themeKey?.let { SEASONAL_THEMES[it] } ?: return

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Radial gradient 1: radial-gradient(120% 90% at 15% -10%, wash1 0%, transparent 55%)
        val center1 = Offset(width * 0.15f, height * -0.10f)
        val radius1 = maxOf(width * 1.2f, height * 0.9f)
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to spec.wash1,
                    0.55f to Color.Transparent,
                ),
                center = center1,
                radius = radius1,
            )
        )

        // Radial gradient 2: radial-gradient(110% 100% at 100% 110%, wash2 0%, transparent 60%)
        val center2 = Offset(width * 1.0f, height * 1.10f)
        val radius2 = maxOf(width * 1.1f, height * 1.0f)
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to spec.wash2,
                    0.60f to Color.Transparent,
                ),
                center = center2,
                radius = radius2,
            )
        )
    }
}
