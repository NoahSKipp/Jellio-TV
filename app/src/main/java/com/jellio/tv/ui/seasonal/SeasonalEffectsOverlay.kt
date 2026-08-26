package com.jellio.tv.ui.seasonal

import android.graphics.Paint
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class SeasonalParticle(
    val isDot: Boolean,
    val glyph: String,
    val color: Color,
    val glow: Boolean,
    val sizePx: Float,
    val opacity: Float,
    val xFraction: Float,
    val durationSec: Float,
    val phaseOffsetSec: Float,
    val swayPx: Float,
    val direction: DriftDirection,
)

// Real port of components/seasonalEffects.js's own buildDrift(): one
// real random draw per particle for size/opacity/duration/sway, the
// same real ranges DriftThemeSpec above carries over from that file's
// own DRIFT_THEMES table. phaseOffsetSec mirrors that file's own
// negative animation-delay (rand(0, maxDuration)), starting each
// particle already partway through its own cycle rather than every
// one beginning at the very top at once.
private fun buildParticles(spec: DriftThemeSpec): List<SeasonalParticle> = List(spec.count) {
    SeasonalParticle(
        isDot = spec.isDot,
        glyph = if (!spec.isDot && spec.glyphs.isNotEmpty()) spec.glyphs.random() else "",
        color = if (spec.isDot && spec.dotColors.isNotEmpty()) Color(spec.dotColors.random()) else Color.White,
        glow = spec.glow,
        sizePx = spec.minSizePx + Random.nextFloat() * (spec.maxSizePx - spec.minSizePx),
        opacity = spec.minOpacity + Random.nextFloat() * (spec.maxOpacity - spec.minOpacity),
        xFraction = Random.nextFloat(),
        durationSec = spec.minDurationSec + Random.nextFloat() * (spec.maxDurationSec - spec.minDurationSec),
        phaseOffsetSec = Random.nextFloat() * spec.maxDurationSec,
        swayPx = (Random.nextFloat() * 2f - 1f) * 40f,
        direction = spec.direction,
    )
}

// Real port of components/seasonalEffects.js's own applyTheme()
// dispatch: most real keys share buildDrift() below, a few (Friday
// the 13th so far, more to follow) are each their own bespoke real
// builder, ported to their own composable rather than folded in here.
@Composable
fun SeasonalEffectsOverlay(themeKey: String?, modifier: Modifier = Modifier) {
    when {
        themeKey == null -> Unit
        themeKey == "friday13" -> Friday13Overlay(modifier)
        DRIFT_THEMES.containsKey(themeKey) -> DriftOverlay(themeKey, modifier)
    }
}

// Real port of css/app.css's own jellio-seasonal-fall/-rise keyframes:
// y runs linearly from -5vh to 115vh (or the mirrored 110vh to -10vh
// for a rising theme) over one real cycle, x sways out to
// --jellio-seasonal-sway and back twice, the same real 0/+1/0/-1/0
// shape at the quarter marks a sine wave already passes through
// exactly, used here directly rather than a piecewise match to those
// same four keyframe stops.
@Composable
private fun DriftOverlay(themeKey: String, modifier: Modifier = Modifier) {
    val spec = DRIFT_THEMES[themeKey] ?: return
    val particles = remember(themeKey) { buildParticles(spec) }
    var elapsedSec by remember(themeKey) { mutableFloatStateOf(0f) }

    LaunchedEffect(themeKey) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val nanos = withFrameNanos { it }
            elapsedSec += (nanos - lastNanos) / 1_000_000_000f
            lastNanos = nanos
        }
    }

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.WHITE
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        particles.forEach { particle ->
            val cycle = ((elapsedSec + particle.phaseOffsetSec) % particle.durationSec) / particle.durationSec
            val yFraction = if (particle.direction == DriftDirection.DOWN) {
                -0.05f + cycle * 1.2f
            } else {
                1.10f - cycle * 1.2f
            }
            val xOffset = sin(cycle * 2f * PI.toFloat()) * particle.swayPx
            val x = particle.xFraction * width + xOffset
            val y = yFraction * height

            if (particle.isDot) {
                drawCircle(
                    color = particle.color.copy(alpha = particle.opacity),
                    radius = particle.sizePx / 2f,
                    center = Offset(x, y),
                )
            } else {
                drawIntoCanvas { canvas ->
                    textPaint.textSize = particle.sizePx
                    textPaint.alpha = (particle.opacity * 255).toInt().coerceIn(0, 255)
                    if (particle.glow) {
                        textPaint.setShadowLayer(particle.sizePx * 0.3f, 0f, 0f, android.graphics.Color.WHITE)
                    } else {
                        textPaint.clearShadowLayer()
                    }
                    canvas.nativeCanvas.drawText(particle.glyph, x, y, textPaint)
                }
            }
        }
    }
}
