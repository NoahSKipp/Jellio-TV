package com.jellio.tv.ui.update

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

// Real, full port of jellio-mark-animated.svg, not an approximation of
// it: the same real triangle+gradient, the same real radial teal glow
// clipped to it, the same real caustic ripples and wake-trail bubbles,
// and critically the real bell/tentacle silhouette itself, morphing
// through this exact file's own 11 keyframe path variants rather than
// staying a single static silhouette that only ever drifts and rotates
// as one rigid whole. Real feedback's own explicit ask: this should
// look identical to the real one, not "close enough" - the previous
// android-animated-vector port (ic_jellio_mark_animated.xml,
// ic_jellio_mark.xml) could drift/rotate the whole jellyfish but had no
// real way to morph a path's own d attribute at all, an AVD's own real
// animator set only ever tweening plain numeric properties (translate,
// rotate, scale), never a path's own control points. Compose's Canvas
// needs no such real limit: JellioMarkData.kt's own 11 real keyframe
// frames (parsed straight off this exact file's own <animate
// attributeName="d"> values, not resampled or hand-perturbed) get
// linearly interpolated here the same real way calcMode="linear"
// already does on the web side.
private const val ViewBoxSize = 400f
private val TrianglePoints = listOf(98.1f to 89.2f, 303.5f to 200f, 98.1f to 318.6f)
private const val TriangleStrokeWidth = 100.2f

// Real jm-swim keyframes: a gentle drift+rotate loop on the jellyfish
// silhouette alone (not the triangle it swims inside), 3.2s, infinite,
// pivot (175,200) in the real 400x400 viewBox.
private val SwimTranslateX = listOf(0f to 0f, 0.20f to 6.6f, 0.46f to 3.4f, 0.72f to -1.2f, 1f to 0f)
private val SwimTranslateY = listOf(0f to 0f, 0.20f to -10f, 0.46f to -5.2f, 0.72f to 1.8f, 1f to 0f)
private val SwimRotation = listOf(0f to 0f, 0.20f to 1.5f, 0.46f to 0.4f, 0.72f to -0.8f, 1f to 0f)
private const val SwimDurationMs = 3200f

// Real jm-caustic: opacity has its own real 0%/16%/74%/100% stops,
// translate just the one real 0%-to-100% linear drift the whole way
// (only those two real keyframes define it at all), same real
// per-property independence JellioMarkOcean's own wake-dot keyframes
// already lean on.
private val CausticOpacity = listOf(0f to 0f, 0.16f to 0.17f, 0.74f to 0.13f, 1f to 0f)
private const val CausticDurationMs = 7500f
private const val CausticTranslateX = -231f
private const val CausticTranslateY = 353f

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
// list on the CSS side, so each interpolates independently here too.
private fun wakeOpacity(kind: WakeKind): List<Pair<Float, Float>> = when (kind) {
    WakeKind.S -> listOf(0f to 0f, 0.14f to 0.5f, 0.58f to 0.34f, 1f to 0f)
    WakeKind.M -> listOf(0f to 0f, 0.12f to 0.55f, 0.58f to 0.36f, 1f to 0f)
    WakeKind.L -> listOf(0f to 0f, 0.10f to 0.5f, 0.58f to 0.3f, 1f to 0f)
}
private fun wakeScale(kind: WakeKind): List<Pair<Float, Float>> = when (kind) {
    WakeKind.S -> listOf(0f to 0.45f, 0.58f to 1f, 1f to 1.1f)
    WakeKind.M -> listOf(0f to 0.45f, 0.58f to 1f, 1f to 1.12f)
    WakeKind.L -> listOf(0f to 0.4f, 0.58f to 1f, 1f to 1.15f)
}
private fun wakeTranslateX(kind: WakeKind): List<Pair<Float, Float>> = when (kind) {
    WakeKind.S -> listOf(0f to 0f, 0.58f to -31f, 1f to -53f)
    WakeKind.M -> listOf(0f to 0f, 0.58f to -46f, 1f to -79f)
    WakeKind.L -> listOf(0f to 0f, 0.58f to -61f, 1f to -105f)
}
private fun wakeTranslateY(kind: WakeKind): List<Pair<Float, Float>> = when (kind) {
    WakeKind.S -> listOf(0f to 0f, 0.58f to 47f, 1f to 81f)
    WakeKind.M -> listOf(0f to 0f, 0.58f to 70f, 1f to 120f)
    WakeKind.L -> listOf(0f to 0f, 0.58f to 93f, 1f to 160f)
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

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

@Composable
fun JellioMarkOcean(modifier: Modifier = Modifier) {
    // Real jm-pop: a one-shot 0.85s pop-in (scale .66->1.03->1, opacity
    // 0->1->1), cubic-bezier(.2,1.2,.35,1), not looping - the whole
    // real mark's own entrance, triangle and jellyfish alike, so this
    // wraps the entire real Canvas below rather than just one piece of
    // it.
    val popScale = remember { Animatable(0.66f) }
    val popAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val easing = CubicBezierEasing(0.2f, 1.2f, 0.35f, 1f)
        launch {
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 850
                    0.66f at 0
                    1.03f at 527 using easing
                    1f at 850
                },
            )
        }
        launch {
            popAlpha.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 850
                    0f at 0
                    1f at 527
                    1f at 850
                },
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "jellioMark")
    // A plain real millisecond counter: every animation in this mark
    // (swim, path morph, caustics, wake dots) needs its own real modulo
    // against its own real duration off the same one real clock, not a
    // shared 0..1 progress every one of them would otherwise have to
    // rescale against its own real period anyway. 1,000,000ms (a bit
    // over 16 real minutes) comfortably outlasts this splash's own real
    // on-screen time and every one of their real durations (11.2s at
    // most), a real wraparound glitch this splash will never actually
    // stay open long enough to hit.
    val clockMs by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1_000_000f,
        animationSpec = infiniteRepeatable(animation = tween(1_000_000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "jellioMarkClock",
    )

    val path = remember { Path() }
    val trianglePath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = popScale.value
                scaleY = popScale.value
                alpha = popAlpha.value
            },
    ) {
        val scale = size.width / ViewBoxSize

        trianglePath.reset()
        trianglePath.moveTo(TrianglePoints[0].first * scale, TrianglePoints[0].second * scale)
        trianglePath.lineTo(TrianglePoints[1].first * scale, TrianglePoints[1].second * scale)
        trianglePath.lineTo(TrianglePoints[2].first * scale, TrianglePoints[2].second * scale)
        trianglePath.close()

        val linearBrush = Brush.linearGradient(
            colorStops = *JellioMarkLinearGradientStops.map { it.offset to Color(it.color) }.toTypedArray(),
            start = Offset(48f * scale, 36f * scale),
            end = Offset(306f * scale, 264f * scale),
        )
        // Real polygon's own fill AND stroke both the same real
        // gradient, round join: the round join is what actually
        // produces this shape's own rounded corners, not a separate
        // corner radius anywhere, same real reason ic_jellio_mark.xml's
        // own header already documented for the AVD version this
        // replaces.
        drawPath(trianglePath, brush = linearBrush)
        drawPath(
            trianglePath,
            brush = linearBrush,
            style = Stroke(width = TriangleStrokeWidth * scale, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )

        clipPath(trianglePath) {
            val radialBrush = Brush.radialGradient(
                colorStops = *JellioMarkRadialGradientStops.map { it.offset to Color(it.color).copy(alpha = it.alpha) }.toTypedArray(),
                center = Offset(112f * scale, 104f * scale),
                radius = 104f * scale,
            )
            drawRect(brush = radialBrush)

            drawCaustics(clockMs, scale)
            drawWakeDots(clockMs, scale)
        }

        drawJellyfish(clockMs, scale, path)
    }
}

private fun DrawScope.drawCaustics(clockMs: Float, scale: Float) {
    JellioMarkCaustics.forEach { c ->
        val t = ((clockMs + c.delayMs) % CausticDurationMs) / CausticDurationMs
        val opacity = lerpStops(CausticOpacity, t)
        if (opacity <= 0f) return@forEach
        val tx = CausticTranslateX * scale * t
        val ty = CausticTranslateY * scale * t
        translate(left = tx, top = ty) {
            rotate(degrees = c.rotationDeg, pivot = Offset(c.cx * scale, c.cy * scale)) {
                drawOval(
                    color = Color.White.copy(alpha = opacity),
                    topLeft = Offset((c.cx - c.rx) * scale, (c.cy - c.ry) * scale),
                    size = Size(c.rx * 2 * scale, c.ry * 2 * scale),
                )
            }
        }
    }
}

private fun DrawScope.drawWakeDots(clockMs: Float, scale: Float) {
    wakeDots.forEach { dot ->
        val t = ((clockMs + dot.delayMs) % dot.durationMs) / dot.durationMs
        val opacity = lerpStops(wakeOpacity(dot.kind), t)
        if (opacity <= 0f) return@forEach
        val dotScale = lerpStops(wakeScale(dot.kind), t)
        val tx = lerpStops(wakeTranslateX(dot.kind), t)
        val ty = lerpStops(wakeTranslateY(dot.kind), t)
        drawCircle(
            color = Color.White.copy(alpha = opacity),
            radius = dot.r * scale * dotScale,
            center = Offset((dot.cx + tx) * scale, (dot.cy + ty) * scale),
        )
    }
}

// Real jm-swim wrapper (transform-origin 175,200) around the
// jellyfish's own static translate(58,113.5) scale(0.31) position, and
// the real path-morph itself: both driven off the same real 3.2s
// period, same real reason both stayed in phase on the web build too
// (neither ever declared its own begin offset, so both started
// together at real document load and never drifted apart since).
private fun DrawScope.drawJellyfish(clockMs: Float, scale: Float, path: Path) {
    val t = (clockMs % SwimDurationMs) / SwimDurationMs
    val swimX = lerpStops(SwimTranslateX, t)
    val swimY = lerpStops(SwimTranslateY, t)
    val swimRotation = lerpStops(SwimRotation, t)

    val frameFloat = t * (JellioMarkFrames.size - 1)
    val frameIndex = frameFloat.toInt().coerceIn(0, JellioMarkFrames.size - 2)
    val frameT = frameFloat - frameIndex
    val a = JellioMarkFrames[frameIndex]
    val b = JellioMarkFrames[frameIndex + 1]

    path.reset()
    var idx = 0
    for (type in JellioMarkCommandTypes) {
        when (type) {
            'M' -> {
                val x = lerp(a[idx], b[idx], frameT) * 0.31f * scale + 58f * scale
                val y = lerp(a[idx + 1], b[idx + 1], frameT) * 0.31f * scale + 113.5f * scale
                path.moveTo(x, y)
                idx += 2
            }
            'C' -> {
                val x1 = lerp(a[idx], b[idx], frameT) * 0.31f * scale + 58f * scale
                val y1 = lerp(a[idx + 1], b[idx + 1], frameT) * 0.31f * scale + 113.5f * scale
                val x2 = lerp(a[idx + 2], b[idx + 2], frameT) * 0.31f * scale + 58f * scale
                val y2 = lerp(a[idx + 3], b[idx + 3], frameT) * 0.31f * scale + 113.5f * scale
                val x3 = lerp(a[idx + 4], b[idx + 4], frameT) * 0.31f * scale + 58f * scale
                val y3 = lerp(a[idx + 5], b[idx + 5], frameT) * 0.31f * scale + 113.5f * scale
                path.cubicTo(x1, y1, x2, y2, x3, y3)
                idx += 6
            }
            'Z' -> path.close()
        }
    }

    withTransform({
        translate(left = swimX * scale, top = swimY * scale)
        rotate(degrees = swimRotation, pivot = Offset(175f * scale, 200f * scale))
    }) {
        drawPath(path, color = Color.White)
    }
}
