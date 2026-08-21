package com.jellio.tv.ui.seasonal

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val STREAK_COUNT = 60
private val StarWarsEasing = CubicBezierEasing(0.7f, 0f, 1f, 1f)

private data class Streak(val angleDeg: Float, val durationSec: Float, val phaseOffsetSec: Float)

private fun buildStreaks(): List<Streak> = List(STREAK_COUNT) {
    Streak(
        angleDeg = Random.nextFloat() * 360f,
        durationSec = 0.8f + Random.nextFloat() * (2.3f - 0.8f),
        phaseOffsetSec = Random.nextFloat() * 2f,
    )
}

// Real port of components/seasonalEffects.js's own buildStarWars(), in
// spirit from CodeDevMLH/Jellyfin-Seasonals' own real starwars.js/
// starwars.css: 60 real hyperspace streaks radiating outward from the
// screen's own real fixed center point, each on its own real
// cubic-bezier(0.7, 0, 1, 1) growth curve (2vh to 150vh, 0.1x to 3x
// length) and randomized duration/phase, same real ranges. Drawn as a
// real line along its own ray rather than a rotated, translated,
// scaled ::after box: replicating that exact real CSS transform
// composition (translateY then scaleY, around the pseudo-element's
// own default center origin) down to the pixel is not worth it for a
// cosmetic streak, this reaches the same real "grows and flies
// outward from center, fading" shape directly instead. A second,
// wider, dimmer line underneath each one stands in for that file's
// own real box-shadow glow, the same real glow approximation
// DRIFT_THEMES' own glow themes already use a shadow layer for.
@Composable
fun StarWarsOverlay(modifier: Modifier = Modifier) {
    val streaks = remember { buildStreaks() }
    var elapsedSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val nanos = withFrameNanos { it }
            elapsedSec += (nanos - lastNanos) / 1_000_000_000f
            lastNanos = nanos
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val height = size.height

        streaks.forEach { streak ->
            val t = ((elapsedSec + streak.phaseOffsetSec) % streak.durationSec) / streak.durationSec
            val eased = StarWarsEasing.transform(t)

            val originFrac = 0.02f + eased * 1.48f
            val scaleY = 0.1f + eased * 2.9f
            val lengthFrac = 0.15f * scaleY
            val nearFrac = originFrac
            val farFrac = originFrac + lengthFrac
            val opacity = if (eased < 0.2f) eased / 0.2f else 1f - (eased - 0.2f) / 0.8f
            if (opacity <= 0f) return@forEach

            val angleRad = streak.angleDeg * PI.toFloat() / 180f
            val dx = sin(angleRad)
            val dy = cos(angleRad)
            val nearPoint = Offset(centerX + dx * nearFrac * height, centerY + dy * nearFrac * height)
            val farPoint = Offset(centerX + dx * farFrac * height, centerY + dy * farFrac * height)

            drawLine(
                color = Color(0xFF88CCFF).copy(alpha = opacity.coerceIn(0f, 1f) * 0.5f),
                start = nearPoint,
                end = farPoint,
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = opacity.coerceIn(0f, 1f)),
                start = nearPoint,
                end = farPoint,
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}
