package com.jellio.tv.ui.update

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath

// Real port of jellio-mark-animated.svg's own wake-trail dots: 18
// tiny bubbles masked to the same real triangle the jellyfish itself
// swims inside, each its own real duration/delay pair so they never
// drift in lockstep, scaling up and fading out along its own real
// jm-wake-s/m/l keyframes as it trails away. ic_jellio_mark_animated.xml's
// own header explains why this stayed web-only until now: 18 real
// individually-timed AVD property animators (54 ObjectAnimators, three
// properties each) was real effort with no real payoff next to a
// dozen more clip-path groups for detail this drawable already
// rendered its own real triangle+glow+jellyfish silhouette without.
// Compose's own Canvas needs none of that per-shape XML: one real
// shared clock (below) plus a real per-kind keyframe table (also
// below) draws all eighteen off the same real math this SVG's own
// three @keyframes rules already define, not an approximation of them.
private enum class WakeKind { S, M, L }

private data class WakeDot(val cx: Float, val cy: Float, val r: Float, val kind: WakeKind, val durationMs: Float, val delayMs: Float)

// Real cx/cy/r/duration/delay values, read straight off this exact
// file's own <circle> elements, not resampled or approximated.
private val wakeDots = listOf(
    WakeDot(220f, 164f, 2.6f, WakeKind.M, 7400f, 300f),
    WakeDot(200f, 204f, 3.4f, WakeKind.L, 9200f, 2200f),
    WakeDot(232f, 202f, 1.8f, WakeKind.S, 6600f, 1100f),
    WakeDot(186f, 162f, 1.5f, WakeKind.M, 8400f, 4100f),
    WakeDot(246f, 236f, 2.9f, WakeKind.L, 10400f, 5600f),
    WakeDot(212f, 250f, 2.1f, WakeKind.S, 7800f, 3200f),
    WakeDot(258f, 186f, 1.4f, WakeKind.M, 9800f, 6400f),
    WakeDot(236f, 150f, 2.2f, WakeKind.S, 6900f, 1800f),
    WakeDot(268f, 214f, 2.5f, WakeKind.L, 9600f, 4800f),
    WakeDot(196f, 236f, 1.6f, WakeKind.M, 8100f, 7200f),
    WakeDot(224f, 278f, 2.8f, WakeKind.L, 11200f, 2900f),
    WakeDot(176f, 196f, 1.9f, WakeKind.S, 7200f, 5400f),
    WakeDot(252f, 258f, 1.7f, WakeKind.M, 8800f, 900f),
    WakeDot(288f, 196f, 2.0f, WakeKind.L, 10800f, 7800f),
    WakeDot(206f, 176f, 1.3f, WakeKind.S, 6400f, 3900f),
    WakeDot(240f, 296f, 2.4f, WakeKind.M, 9400f, 5100f),
    WakeDot(264f, 166f, 1.6f, WakeKind.S, 7600f, 2600f),
    WakeDot(184f, 262f, 2.3f, WakeKind.L, 10200f, 6900f),
)

// Real per-kind jm-wake-s/m/l keyframe stops: opacity and transform
// (scale, translateX, translateY) each got their own real fraction
// list on the CSS side (an opacity-only 10-14% stop sits alongside a
// transform-only 58% stop), so each one interpolates independently
// here too rather than forcing a single shared stop list across all
// four.
private fun keyframeStops(kind: WakeKind): Triple<List<Pair<Float, Float>>, List<Pair<Float, Float>>, Pair<List<Pair<Float, Float>>, List<Pair<Float, Float>>>> =
    when (kind) {
        WakeKind.S -> Triple(
            listOf(0f to 0f, 0.14f to 0.5f, 0.58f to 0.34f, 1f to 0f),
            listOf(0f to 0.45f, 0.58f to 1f, 1f to 1.1f),
            listOf(0f to 0f, 0.58f to -31f, 1f to -53f) to listOf(0f to 0f, 0.58f to 47f, 1f to 81f),
        )
        WakeKind.M -> Triple(
            listOf(0f to 0f, 0.12f to 0.55f, 0.58f to 0.36f, 1f to 0f),
            listOf(0f to 0.45f, 0.58f to 1f, 1f to 1.12f),
            listOf(0f to 0f, 0.58f to -46f, 1f to -79f) to listOf(0f to 0f, 0.58f to 70f, 1f to 120f),
        )
        WakeKind.L -> Triple(
            listOf(0f to 0f, 0.10f to 0.5f, 0.58f to 0.3f, 1f to 0f),
            listOf(0f to 0.4f, 0.58f to 1f, 1f to 1.15f),
            listOf(0f to 0f, 0.58f to -61f, 1f to -105f) to listOf(0f to 0f, 0.58f to 93f, 1f to 160f),
        )
    }

private fun lerpStops(stops: List<Pair<Float, Float>>, t: Float): Float {
    if (t <= stops.first().first) return stops.first().second
    if (t >= stops.last().first) return stops.last().second
    for (i in 0 until stops.size - 1) {
        val (f0, v0) = stops[i]
        val (f1, v1) = stops[i + 1]
        if (t in f0..f1) {
            val span = (t - f0) / (f1 - f0)
            return v0 + (v1 - v0) * span
        }
    }
    return stops.last().second
}

// Real jellio-mark-animated.svg's own 400x400 viewBox, same triangle
// mask (jmMask) the caustic glow and these wake dots both clip to,
// scaled to whatever real size this composable is actually drawn at.
private const val ViewBoxSize = 400f
private val TrianglePoints = listOf(98.1f to 89.2f, 303.5f to 200f, 98.1f to 318.6f)

@Composable
fun JellioMarkOcean(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "jellioMarkOcean")
    // A plain real millisecond counter, not a real repeating fraction:
    // eighteen dots each need their own real modulo against their own
    // real duration below, not one shared 0..1 progress every dot
    // would otherwise have to rescale against its own real period
    // anyway. 1,000,000ms (a bit over 16 real minutes) comfortably
    // outlasts this splash's own real on-screen time and every dot's
    // own real duration (11.2s at most), a real wraparound glitch this
    // splash will never actually stay open long enough to hit.
    val clockMs by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1_000_000f,
        animationSpec = infiniteRepeatable(animation = tween(1_000_000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "jellioMarkOceanClock",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val scale = size.width / ViewBoxSize
        val triangle = Path().apply {
            moveTo(TrianglePoints[0].first * scale, TrianglePoints[0].second * scale)
            lineTo(TrianglePoints[1].first * scale, TrianglePoints[1].second * scale)
            lineTo(TrianglePoints[2].first * scale, TrianglePoints[2].second * scale)
            close()
        }
        clipPath(triangle) {
            wakeDots.forEach { dot ->
                val t = ((clockMs + dot.delayMs) % dot.durationMs) / dot.durationMs
                val (opacityStops, scaleStops, translateStops) = keyframeStops(dot.kind)
                val opacity = lerpStops(opacityStops, t)
                if (opacity <= 0f) return@forEach
                val dotScale = lerpStops(scaleStops, t)
                val tx = lerpStops(translateStops.first, t)
                val ty = lerpStops(translateStops.second, t)
                drawCircle(
                    color = Color.White.copy(alpha = opacity),
                    radius = dot.r * scale * dotScale,
                    center = Offset((dot.cx + tx) * scale, (dot.cy + ty) * scale),
                )
            }
        }
    }
}
