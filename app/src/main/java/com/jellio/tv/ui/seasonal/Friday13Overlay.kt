package com.jellio.tv.ui.seasonal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlin.math.roundToInt

// Real port of components/seasonalEffects.js's own buildFriday13(): a
// real vignette (css/app.css's own jellio-seasonal-flicker keyframe,
// flat at full opacity except for a brief real double dip around the
// 93-95% mark of every 7 real seconds) plus a real black cat walking
// left to right across the bottom of the screen every 22 real seconds,
// the exact same real jellio-seasonal-cat-walk timing.
@Composable
fun Friday13Overlay(modifier: Modifier = Modifier) {
    val flickerTransition = rememberInfiniteTransition(label = "friday13-flicker")
    val flickerAlpha by flickerTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 7000
                1f at 0
                1f at 6440
                0.6f at 6510
                1f at 6580
                0.6f at 6650
                1f at 7000
            },
        ),
        label = "friday13-flicker-value",
    )

    val walkTransition = rememberInfiniteTransition(label = "friday13-cat")
    val walkFraction by walkTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 22000, easing = LinearEasing)),
        label = "friday13-cat-x",
    )

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = flickerAlpha }
                .background(
                    Brush.radialGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )

        Text(
            text = "🐈‍⬛",
            fontSize = 28.sp,
            modifier = Modifier
                .offset { IntOffset(x = (-0.10f * widthPx + walkFraction * 1.20f * widthPx).roundToInt(), y = (heightPx * 0.94f).roundToInt()) }
                .graphicsLayer { scaleX = -1f },
        )
    }
}
