package com.jellio.tv.ui.seasonal

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.jellio.tv.ui.perf.MotionLevel
import com.jellio.tv.ui.perf.MotionPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

// The seasonal effects of Jellio web (components/seasons.js), drawn with
// Compose: a static layer (washes and decorations, drawn once), an
// animated layer (creatures and particles, positions worked out from
// the clock each frame) and, above the page, the sky (Halloween
// lightning, the New Year title). With the system's animations turned
// off only the washes stay.

private fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

private object Art {
    val batWingR by lazy { svg("M33 16C38 7 49 3 63 8C59 10 58 14 59 20C55 16 51 17 49 23C46 18 42 19 39 25C37 21 35 20 33 22Z") }
    val batWingL by lazy { svg("M31 16C26 7 15 3 1 8C5 10 6 14 5 20C9 16 13 17 15 23C18 18 22 19 25 25C27 21 29 20 31 22Z") }
    val batBody by lazy { svg("M27.5 9L26 1.5L30.5 6L32 4.5L33.5 6L38 1.5L36.5 9C37.5 12 37.5 17 35.5 22C34.5 26 33.2 29 32 33C30.8 29 29.5 26 28.5 22C26.5 17 26.5 12 27.5 9Z") }
    val ghostBody by lazy { svg("M30 2C14 2 5 15 5 32V68L13 59L21 69L30 59L39 69L47 59L55 68V32C55 15 46 2 30 2Z") }
    val ghostArms by lazy { svg("M5 40C-3 44 -5 52 1 58C6 55 7 50 5 46Z M55 40C63 44 65 52 59 58C54 55 53 50 55 46Z") }
    val spiderLegs by lazy { svg("M22 20L10 8L2 14 M22 23L7 18L1 28 M22 26L8 30L4 39 M23 28L14 36L12 40 M26 20L38 8L46 14 M26 23L41 18L47 28 M26 26L40 30L44 39 M25 28L34 36L36 40") }
    val spiderMark by lazy { svg("M21 28L24 24L27 28L24 31Z") }
    val pumpkinStem by lazy { svg("M37 14C36 8 38 4 43 1L46 4C43 6 43 9 44 14Z") }
    val pumpkinRibs by lazy { svg("M40 16C33 28 33 58 40 70M24 20C14 32 14 56 24 66M56 20C66 32 66 56 56 66") }
    val pumpkinFace by lazy { svg("M23 34L33 40L21 44Z M57 34L47 40L59 44Z M40 46L36 53H44Z M22 53L26 58L31 55L35 61L40 56L45 61L49 55L54 58L58 53C54 66 26 66 22 53Z") }
    val garland by lazy { svg("M-4 18C60 40 120 30 190 62C230 80 262 92 284 118") }
    val drift1 by lazy { svg("M0 120V70C80 40 160 38 260 62C360 86 440 36 560 40C680 44 740 84 860 66C960 50 1060 28 1200 58V120Z") }
    val drift2 by lazy { svg("M0 120V92C120 70 220 86 340 82C480 78 560 58 700 70C840 82 960 62 1200 84V120Z") }
    val sparkle by lazy { svg("M10 0C11 6 14 9 20 10C14 11 11 14 10 20C9 14 6 11 0 10C6 9 9 6 10 0Z") }
    val heart by lazy { svg("M12 21.35L10.55 20.03C5.4 15.36 2 12.28 2 8.5C2 5.42 4.42 3 7.5 3C9.24 3 10.91 3.81 12 5.09C13.09 3.81 14.76 3 16.5 3C19.58 3 22 5.42 22 8.5C22 12.28 18.6 15.36 13.45 20.04L12 21.35Z") }
}

private fun Random.range(min: Float, max: Float) = min + nextFloat() * (max - min)

private fun cycle(t: Float, duration: Float, offset: Float): Float {
    val v = (t + offset) / duration
    return v - floor(v)
}

// Off when the system or the reader turned motion off (Settings, or the
// device check in MotionPolicy picked it).
@Composable
private fun animationsOff(): Boolean = MotionPolicy.level == MotionLevel.STILL

// The scenes' time. Every frame on capable devices; on REDUCED it only
// moves on about 24 times a second, so the full-screen redraw behind the
// page costs well under half as much.
@Composable
private fun rememberClock(): MutableLongState {
    val clock = remember { mutableLongStateOf(0L) }
    val level = MotionPolicy.level
    LaunchedEffect(level) {
        val start = withFrameNanos { it }
        val stepNanos = if (level == MotionLevel.REDUCED) 1_000_000_000L / 24 else 0L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val t = now - start
                if (t - last >= stepNanos) {
                    last = t
                    clock.longValue = t
                }
            }
        }
    }
    return clock
}

// Shared between the layer behind the page and the sky above it.
private object SeasonSignals {
    var newYearTitleAt by mutableStateOf(0L)
}

@Composable
fun SeasonalEffectsOverlay(themeKey: String?, modifier: Modifier = Modifier) {
    val spec = themeKey?.let { SEASONAL_THEMES[it] } ?: return
    val still = animationsOff()
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawWash(spec) }
        if (still) return@Box
        when (themeKey) {
            "halloween" -> HalloweenScene()
            "christmas" -> ChristmasScene()
            "newyear" -> NewYearScene()
            "valentine" -> ValentineScene()
        }
    }
}

// Above the page: only what has to light or speak over everything.
@Composable
fun SeasonalSkyOverlay(themeKey: String?, modifier: Modifier = Modifier) {
    if (themeKey == null || animationsOff()) return
    when (themeKey) {
        "halloween" -> Lightning(modifier)
        "newyear" -> NewYearTitle(modifier)
    }
}

private fun DrawScope.drawWash(spec: SeasonalWashSpec) {
    drawRect(
        Brush.radialGradient(
            0f to spec.wash1,
            0.55f to Color.Transparent,
            center = Offset(size.width * 0.15f, -size.height * 0.10f),
            radius = maxOf(size.width * 1.2f, size.height * 0.9f),
        ),
    )
    drawRect(
        Brush.radialGradient(
            0f to spec.wash2,
            0.6f to Color.Transparent,
            center = Offset(size.width, size.height * 1.1f),
            radius = maxOf(size.width * 1.1f, size.height),
        ),
    )
}

// ---------------------------------------------------------------- Halloween

private class Bat(val x: Float, val dx: Float, val duration: Float, val offset: Float, val width: Float, val alpha: Float, val flap: Float)
private class Ghost(val x: Float, val size: Float, val alpha: Float, val duration: Float, val offset: Float)
private class Spider(val x: Float, val drop: Float, val size: Float, val duration: Float, val offset: Float)
private class Ember(val x: Float, val size: Float, val sway: Float, val duration: Float, val offset: Float)
private class Eyes(val x: Float, val y: Float, val color: Color, val duration: Float, val offset: Float)

@Composable
private fun HalloweenScene() {
    // On REDUCED the fog, dread and moon glow hold still: an infinite
    // transition asks for every frame even when the scenes don't.
    val reduced = MotionPolicy.level == MotionLevel.REDUCED
    val fogShiftState: androidx.compose.runtime.State<Float>
    val dreadState: androidx.compose.runtime.State<Float>
    val moonGlowState: androidx.compose.runtime.State<Float>
    if (reduced) {
        fogShiftState = remember { mutableStateOf(0f) }
        dreadState = remember { mutableStateOf(0.76f) }
        moonGlowState = remember { mutableStateOf(0.82f) }
    } else {
        val fog = rememberInfiniteTransition(label = "fog")
        fogShiftState = fog.animateFloat(-0.03f, 0.03f, infiniteRepeatable(tween(24000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fogShift")
        dreadState = fog.animateFloat(0.65f, 0.88f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dread")
        moonGlowState = fog.animateFloat(0.72f, 0.9f, infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "moon")
    }
    val fogShift by fogShiftState
    val dread by dreadState
    val moonGlow by moonGlowState

    // The moon and the cobwebs never move: drawn once.
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = moonGlow }) { drawMoon() }
    Canvas(Modifier.fillMaxSize()) {
        drawCobweb(Offset.Zero, 1f)
        drawCobweb(Offset(size.width, 0f), -1f)
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * fogShift }) {
        drawRect(
            Brush.radialGradient(
                0f to Color(0x427C3AED), 1f to Color.Transparent,
                center = Offset(size.width * 0.2f, size.height * 1.05f), radius = size.width * 0.45f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                0f to Color(0x2EFF7518), 1f to Color.Transparent,
                center = Offset(size.width * 0.75f, size.height * 1.05f), radius = size.width * 0.38f,
            ),
        )
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = dread }) {
        drawRect(
            Brush.radialGradient(
                0.45f to Color.Transparent, 1f to Color(0x80000000),
                center = Offset(size.width / 2, size.height * 0.4f), radius = maxOf(size.width, size.height) * 0.75f,
            ),
        )
    }

    val rng = remember { Random(31) }
    val bats = remember { List(12) { i -> val near = i < 4; Bat(rng.nextFloat() * 0.9f, -rng.range(0.3f, 0.6f), if (near) rng.range(9f, 13f) else rng.range(15f, 24f), rng.range(0f, 20f), if (near) rng.range(40f, 56f) else rng.range(20f, 30f), if (near) 0.6f else 0.34f, rng.range(0.26f, 0.4f)) } }
    val ghosts = remember { List(3) { Ghost(rng.range(0.08f, 0.88f), rng.range(34f, 62f), rng.range(0.18f, 0.34f), rng.range(20f, 32f), rng.range(0f, 28f)) } }
    val spiders = remember { List(2) { Spider(rng.range(0.14f, 0.88f), rng.range(0.22f, 0.55f), rng.range(22f, 32f), rng.range(15f, 24f), rng.range(0f, 20f)) } }
    val embers = remember { List(20) { Ember(rng.nextFloat(), rng.range(2f, 4.5f), rng.range(-60f, 60f), rng.range(8f, 15f), rng.range(0f, 15f)) } }
    val eyes = remember { listOf(Color(0xFFFF7518), Color(0xFF7CFF6B), Color(0xFFFF3B3B)).map { Eyes(rng.range(0.15f, 0.8f), rng.range(0.34f, 0.74f), it, rng.range(12f, 19f), rng.range(0f, 14f)) } }
    val clock = rememberClock()

    Canvas(Modifier.fillMaxSize()) {
        val t = clock.longValue / 1_000_000_000f
        val dp = density
        val w = size.width
        val h = size.height

        eyes.forEach { e ->
            val p = cycle(t, e.duration, e.offset)
            val on = (p in 0.42f..0.52f) || (p in 0.56f..0.60f)
            if (on) {
                val cx = e.x * w
                val cy = e.y * h
                for (dx in listOf(-9f, 9f)) {
                    drawCircle(e.color.copy(alpha = 0.25f), 9f * dp, Offset(cx + dx * dp, cy))
                    drawOval(e.color.copy(alpha = 0.9f), Offset(cx + dx * dp - 4.5f * dp, cy - 2.5f * dp), Size(9f * dp, 5f * dp))
                }
            }
        }

        embers.forEach { e ->
            val p = cycle(t, e.duration, e.offset)
            val alpha = if (p < 0.12f) p / 0.12f * 0.9f else (1f - p) / 0.88f * 0.9f
            val x = e.x * w + e.sway * dp * p
            val y = h * 1.02f - p * h * 1.05f
            drawCircle(Color(0xFFFF8C28).copy(alpha = alpha * 0.35f), e.size * dp * 2.4f, Offset(x, y))
            drawCircle(Color(0xFFFFB066).copy(alpha = alpha), e.size * dp / 2f, Offset(x, y))
        }

        ghosts.forEach { g ->
            val p = cycle(t, g.duration, g.offset)
            val alpha = when {
                p < 0.1f -> p / 0.1f * g.alpha
                p > 0.75f -> (1f - p) / 0.25f * g.alpha
                else -> g.alpha
            }
            val sizePx = g.size * dp
            val x = g.x * w + sin(p * 4f * PI.toFloat()) * 32f * dp
            val y = h - p * h * 1.12f
            translate(x, y) {
                rotate(sin(p * 4f * PI.toFloat() + 0.5f) * 5f, Offset(sizePx / 2, sizePx * 0.6f)) {
                    scale(sizePx / 60f, Offset.Zero) {
                        drawPath(Art.ghostArms, Color(0xFFD8CCFF), alpha = alpha * 0.6f)
                        drawPath(
                            Art.ghostBody,
                            Brush.verticalGradient(listOf(Color(0xFFF4F0FF), Color(0x40B9A8FF)), 0f, 72f),
                            alpha = alpha,
                        )
                        drawOval(Color(0xFF1B1230), Offset(18f, 22f), Size(8f, 12f), alpha = alpha)
                        drawOval(Color(0xFF1B1230), Offset(34f, 22f), Size(8f, 12f), alpha = alpha)
                        drawOval(Color(0xFF1B1230), Offset(25f, 35.5f), Size(10f, 15f), alpha = alpha)
                    }
                }
            }
        }

        bats.forEach { b ->
            val p = cycle(t, b.duration, b.offset)
            val x = b.x * w + b.dx * w * p
            val y = -0.06f * h + p * 1.14f * h
            val wing = 0.69f + 0.31f * cos(2f * PI.toFloat() * t / b.flap)
            val s = b.width * dp / 64f
            translate(x, y) {
                scale(s, Offset.Zero) {
                    drawBat(wing, Color(0xFFFF7518).copy(alpha = b.alpha * 0.22f), 1.12f)
                    drawBat(wing, Color(0xFF050308).copy(alpha = b.alpha), 1f)
                }
            }
        }

        spiders.forEach { sp ->
            val p = cycle(t, sp.duration, sp.offset)
            val full = sp.drop * h
            val len = when {
                p < 0.08f -> 0f
                p < 0.28f -> full * (p - 0.08f) / 0.2f
                p < 0.34f -> full - 30f * dp * (p - 0.28f) / 0.06f
                p < 0.40f -> full - 30f * dp * (0.40f - p) / 0.06f
                p < 0.62f -> full
                p < 0.80f -> full * (0.80f - p) / 0.18f
                else -> 0f
            }
            if (len > 1f) {
                val x = sp.x * w
                drawLine(Color.White.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, len), 1f * dp)
                val sizePx = sp.size * dp
                translate(x - sizePx / 2, len - 3f * dp) {
                    scale(sizePx / 48f, Offset.Zero) {
                        val legs = 1f - 0.12f * abs(sin(t * PI.toFloat() * 2f))
                        scale(legs, 1f, Offset(24f, 24f)) {
                            drawPath(Art.spiderLegs, Color(0xFF050308), style = Stroke(width = 2f, cap = StrokeCap.Round))
                        }
                        drawOval(Color(0xFF050308), Offset(15.5f, 16f), Size(17f, 20f))
                        drawCircle(Color(0xFF050308), 5f, Offset(24f, 15f))
                        drawPath(Art.spiderMark, Color(0xFFC1121F))
                        drawCircle(Color(0xFFFF3B3B), 1f, Offset(22f, 14f))
                        drawCircle(Color(0xFFFF3B3B), 1f, Offset(26f, 14f))
                    }
                }
            }
        }

        listOf(Triple(0.10f, 70f, 0f), Triple(0.92f, 56f, 1.4f)).forEach { (fx, sizeDp, off) ->
            val sizePx = sizeDp * dp
            val left = if (fx < 0.5f) fx * w else fx * w - sizePx
            val top = h * 0.98f - sizePx * 0.9f
            val flicker = 0.75f + 0.25f * sin((t + off) * 2f * PI.toFloat() / 3.4f) * sin((t + off) * 7.3f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0x8CFF8C1E), Color(0x2EFF7518), Color.Transparent),
                    center = Offset(left + sizePx / 2, top + sizePx * 0.5f), radius = sizePx * 1.1f,
                ),
                sizePx * 1.1f, Offset(left + sizePx / 2, top + sizePx * 0.5f), alpha = flicker,
            )
            translate(left, top) {
                scale(sizePx / 80f, Offset.Zero) { drawPumpkin(flicker) }
            }
        }

        // A slow cloud across the moon.
        val moonR = 75f * dp
        val mc = Offset(w - w * 0.07f - moonR, h * 0.04f + moonR)
        val cloudX = mc.x + sin(t * 2f * PI.toFloat() / 52f) * moonR * 0.6f
        translate(cloudX, mc.y + moonR * 0.15f) {
            scale(3f, 0.45f, Offset.Zero) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xD90C0812), Color.Transparent), center = Offset.Zero, radius = moonR * 0.5f),
                    moonR * 0.5f, Offset.Zero,
                )
            }
        }
    }
}

private fun DrawScope.drawBat(wing: Float, color: Color, grow: Float) {
    scale(grow, Offset(32f, 17f)) {
        scale(1f, wing, Offset(33f, 20f)) { drawPath(Art.batWingR, color) }
        scale(1f, wing, Offset(31f, 20f)) { drawPath(Art.batWingL, color) }
        drawPath(Art.batBody, color)
    }
}

private fun DrawScope.drawPumpkin(faceLight: Float) {
    val fill = Brush.radialGradient(
        0f to Color(0xFFFF9A2E), 0.7f to Color(0xFFE2560A), 1f to Color(0xFF8F2A04),
        center = Offset(40f, 30f), radius = 50f,
    )
    drawPath(Art.pumpkinStem, Color(0xFF3B4A14))
    drawOval(fill, Offset(3f, 18f), Size(38f, 50f))
    drawOval(fill, Offset(39f, 18f), Size(38f, 50f))
    drawOval(fill, Offset(18f, 16f), Size(44f, 54f))
    drawPath(Art.pumpkinRibs, Color(0xFF7A2503), alpha = 0.55f, style = Stroke(width = 1.6f))
    drawPath(Art.pumpkinFace, Color(0xFFFFD36B), alpha = faceLight.coerceIn(0.4f, 1f))
}

private fun DrawScope.drawMoon() {
    val r = 75f * density
    val c = Offset(size.width - size.width * 0.07f - r, size.height * 0.04f + r)
    drawCircle(Brush.radialGradient(listOf(Color(0x597A0F1A), Color.Transparent), center = c, radius = r * 3.2f), r * 3.2f, c)
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFFFB88A), 0.38f to Color(0xFFE0562C), 0.72f to Color(0xFF7A1C10), 1f to Color(0xFF3A0C0A),
            center = Offset(c.x - r * 0.24f, c.y - r * 0.28f), radius = r * 1.5f,
        ),
        r, c,
    )
    listOf(Triple(0.24f, -0.4f, 0.18f), Triple(-0.28f, 0.16f, 0.24f), Triple(0.4f, 0.32f, 0.14f), Triple(-0.52f, -0.44f, 0.12f)).forEach { (dx, dy, cr) ->
        drawCircle(Color(0x663C0806), r * cr, Offset(c.x + r * dx, c.y + r * dy))
    }
}

private fun DrawScope.drawCobweb(corner: Offset, flip: Float) {
    val reach = 170f * density
    val path = Path()
    val spokes = 6
    fun point(r: Float, i: Int): Offset {
        val a = i.toFloat() / (spokes - 1) * (PI.toFloat() / 2f)
        return Offset(corner.x + flip * r * cos(a), corner.y + r * sin(a))
    }
    for (i in 0 until spokes) {
        val p = point(reach, i)
        path.moveTo(corner.x, corner.y)
        path.lineTo(p.x, p.y)
    }
    listOf(0.2f, 0.4f, 0.6f, 0.8f, 0.99f).forEach { f ->
        val r = reach * f
        val first = point(r, 0)
        path.moveTo(first.x, first.y)
        for (i in 1 until spokes) {
            val a = point(r, i - 1)
            val b = point(r, i)
            val mid = Offset(corner.x + ((a.x + b.x) / 2f - corner.x) * 0.8f, corner.y + ((a.y + b.y) / 2f - corner.y) * 0.8f)
            path.quadraticTo(mid.x, mid.y, b.x, b.y)
        }
    }
    drawPath(path, Color(0xFFE9E4FF), alpha = 0.45f, style = Stroke(width = 1f * density, cap = StrokeCap.Round))
}

private class Strike(val lines: List<List<Offset>>, val x: Float)

@Composable
private fun Lightning(modifier: Modifier) {
    var strike by remember { mutableStateOf<Strike?>(null) }
    val flash = remember { Animatable(0f) }
    val bolt = remember { Animatable(0f) }
    var area by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(Unit) {
        val rng = Random(System.currentTimeMillis())
        delay(rng.range(6000f, 12000f).toLong())
        while (true) {
            val w = area.width
            val h = area.height
            if (w > 0f && h > 0f) {
                strike = makeStrike(rng, w, h)
                for ((at, f, b) in listOf(Triple(0L, 0.62f, 1f), Triple(70L, 0f, 0f), Triple(60L, 0.35f, 0.8f), Triple(60L, 0f, 0f), Triple(70L, 0.22f, 0.5f), Triple(120L, 0f, 0f))) {
                    delay(at)
                    flash.snapTo(f)
                    bolt.snapTo(b)
                }
                strike = null
            }
            delay(rng.range(12000f, 26000f).toLong())
        }
    }

    Canvas(modifier.fillMaxSize()) {
        area = size
        val s = strike ?: return@Canvas
        if (flash.value > 0f) {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFFF3ECFF), Color(0xFFB79BFF), Color(0xFF4B2A9A)),
                    center = Offset(s.x, 0f), radius = size.width * 0.9f,
                ),
                alpha = flash.value, blendMode = BlendMode.Screen,
            )
        }
        if (bolt.value > 0f) {
            s.lines.forEachIndexed { i, line ->
                val wide = if (i == 0) 6f else 3f
                val thin = if (i == 0) 2.4f else 1.2f
                for (k in 1 until line.size) {
                    drawLine(Color(0xFF966EFF), line[k - 1], line[k], wide * density, StrokeCap.Round, alpha = bolt.value * 0.45f)
                }
                for (k in 1 until line.size) {
                    drawLine(Color(0xFFEBE1FF), line[k - 1], line[k], thin * density, StrokeCap.Round, alpha = bolt.value)
                }
            }
        }
    }
}

private fun makeStrike(rng: Random, w: Float, h: Float): Strike {
    fun route(x1: Float, y1: Float, x2: Float, y2: Float, spread: Float): List<Offset> {
        val out = mutableListOf(Offset(x1, y1))
        fun fork(ax: Float, ay: Float, bx: Float, by: Float, s: Float) {
            if (s < 3f) {
                out.add(Offset(bx, by))
                return
            }
            val mx = (ax + bx) / 2f + rng.range(-s, s)
            val my = (ay + by) / 2f + rng.range(-s * 0.3f, s * 0.3f)
            fork(ax, ay, mx, my, s / 2f)
            fork(mx, my, bx, by, s / 2f)
        }
        fork(x1, y1, x2, y2, spread)
        return out
    }
    val x = rng.range(w * 0.25f, w * 0.8f)
    val main = route(x, 0f, x + rng.range(-120f, 120f), rng.range(h * 0.45f, h * 0.8f), rng.range(70f, 110f))
    val lines = mutableListOf(main)
    repeat(3) {
        val from = main[rng.range(main.size * 0.25f, main.size * 0.7f).toInt().coerceIn(0, main.size - 1)]
        lines.add(route(from.x, from.y, from.x + rng.range(-170f, 170f), from.y + rng.range(80f, 220f), 50f))
    }
    return Strike(lines, x)
}

// ---------------------------------------------------------------- Christmas

private class Flake(val depth: Int, val x: Float, val y: Float, val r: Float, val v: Float, val phase: Float, val alpha: Float, val spin: Float)
private class Twinkle(val x: Float, val y: Float, val size: Float, val duration: Float, val offset: Float, val color: Color)

private val BULBS = listOf(Color(0xFFFF4D4D), Color(0xFFFFD36B), Color(0xFF4DFF88), Color(0xFF5AA8FF), Color(0xFFFF8FD6))

private fun wirePath(w: Float, dp: Float): Path {
    val swags = maxOf(3, (w / (280f * dp)).toInt())
    val seg = w / swags
    val path = Path()
    path.moveTo(0f, 0f)
    for (i in 0 until swags) {
        path.quadraticTo(seg * i + seg / 2f, (58f + (i % 2) * 8f) * dp, seg * (i + 1), 6f * dp)
    }
    return path
}

@Composable
private fun ChristmasScene() {
    val glow = rememberInfiniteTransition(label = "hearth")
    val hearth by glow.animateFloat(0.65f, 1f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hearthAlpha")

    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = hearth }) {
        val c = Offset(size.width / 2f, size.height + 120f * density)
        drawRect(
            Brush.radialGradient(listOf(Color(0x52FF9632), Color(0x1AFF6E1E), Color.Transparent), center = c, radius = size.width * 0.38f),
        )
    }
    // Garland, the drift, the frost and the wire never move: drawn once.
    Canvas(Modifier.fillMaxSize()) {
        val dp = density
        drawFrost(Offset(0f, size.height), 1f)
        drawFrost(Offset(size.width, size.height), -1f)
        val driftH = 120f * dp
        translate(-size.width * 0.03f, size.height - driftH) {
            scale(size.width * 1.06f / 1200f, driftH / 120f, Offset.Zero) {
                drawPath(Art.drift1, Brush.verticalGradient(listOf(Color(0xFFF4F9FF), Color(0xFF9FB8D6)), 40f, 120f), alpha = 0.92f)
                drawPath(Art.drift2, Color.White, alpha = 0.55f)
            }
        }
        drawGarland(Offset.Zero, 1f)
        drawGarland(Offset(size.width, 0f), -1f)
        drawPath(wirePath(size.width, dp), Color(0xFF0B1F14), style = Stroke(width = 2.2f * dp))
    }

    val rng = remember { Random(25) }
    val flakes = remember {
        List(46) { Flake(0, rng.nextFloat(), rng.nextFloat(), rng.range(0.8f, 1.6f), rng.range(0.25f, 0.5f), rng.range(0f, 7f), rng.range(0.4f, 0.8f), 0f) } +
            List(24) { Flake(1, rng.nextFloat(), rng.nextFloat(), rng.range(1.8f, 3f), rng.range(0.6f, 1.1f), rng.range(0f, 7f), rng.range(0.4f, 0.8f), 0f) } +
            List(7) { Flake(2, rng.nextFloat(), rng.nextFloat(), rng.range(5f, 9f), rng.range(1.3f, 2f), rng.range(0f, 7f), 0.85f, rng.range(0.2f, 0.6f)) }
    }
    val twinkles = remember {
        listOf(Color.White, Color(0xFFFFE08A), Color(0xFFBFE3FF)).let { colors ->
            List(9) { Twinkle(rng.range(0.03f, 0.96f), rng.range(0.06f, 0.92f), rng.range(8f, 16f), rng.range(3f, 7f), rng.range(0f, 7f), colors[it % 3]) }
        }
    }
    val shootStart = remember { Offset(rng.range(0.1f, 0.4f), rng.range(0.08f, 0.25f)) }
    val clock = rememberClock()
    var bulbs by remember { mutableStateOf<Pair<Float, List<Offset>>?>(null) }

    Canvas(Modifier.fillMaxSize()) {
        val t = clock.longValue / 1_000_000_000f
        val dp = density
        val w = size.width
        val h = size.height

        // Shooting star.
        val sp = cycle(t, 17f, 3f)
        if (sp in 0.90f..0.99f) {
            val k = (sp - 0.90f) / 0.09f
            val head = Offset(shootStart.x * w + 380f * dp * k, shootStart.y * h + 170f * dp * k)
            val tail = Offset(head.x - 128f * dp, head.y - 57f * dp)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White), tail, head), tail, head, 2f * dp, StrokeCap.Round, alpha = 1f - k)
        }
        twinkles.forEach { tw ->
            val p = cycle(t, tw.duration, tw.offset)
            val v = when {
                p < 0.7f -> 0f
                p < 0.82f -> (p - 0.7f) / 0.12f
                else -> (1f - p) / 0.18f
            }
            if (v > 0f) {
                val s = tw.size * dp * (0.4f + 0.6f * v) / 20f
                translate(tw.x * w, tw.y * h) {
                    rotate(45f * v, Offset.Zero) {
                        scale(s, Offset.Zero) { translate(-10f, -10f) { drawPath(Art.sparkle, tw.color, alpha = v) } }
                    }
                }
            }
        }

        flakes.forEach { f ->
            val travel = h + 24f * dp
            val y = ((f.y * travel + f.v * 60f * dp * t) % travel) - 12f * dp
            val drift = sin(t * (1f + f.depth * 0.3f) + f.phase) * (0.3f + f.depth * 0.25f) * 30f * dp +
                sin(t * 0.18f + f.phase) * (0.4f + f.depth * 0.5f) * 40f * dp
            val x = ((f.x * w + drift) % w + w) % w
            if (f.depth == 2) {
                drawCrystal(Offset(x, y), f.r * dp, t * f.spin + f.phase)
            } else {
                drawCircle(Color.White, f.r * dp, Offset(x, y), alpha = f.alpha)
            }
        }

        // Baubles swinging from the top.
        listOf(Triple(0.14f, Color(0xFFC0392B), 120f), Triple(0.86f, Color(0xFFD4AF37), 150f), Triple(0.5f, Color(0xFF3B82F6), 96f)).forEachIndexed { i, (fx, color, len) ->
            val anchor = Offset(fx * w, 0f)
            val angle = 4f * sin(t * 2f * PI.toFloat() / (4.5f + i) + i * 1.7f)
            rotate(angle, anchor) {
                val end = Offset(anchor.x, len * dp)
                drawLine(Color.White.copy(alpha = 0.35f), anchor, end, 1f * dp)
                val r = (if (i == 2) 15f else 17f) * dp
                val center = Offset(end.x, end.y + r * 1.2f)
                drawCircle(color.copy(alpha = 0.25f), r * 1.6f, center)
                drawRect(Color(0xFFD4AF37), Offset(end.x - 4f * dp, end.y - 1f * dp), Size(8f * dp, 6f * dp))
                drawCircle(
                    Brush.radialGradient(
                        0f to Color(0xE6FFFFFF), 0.25f to color, 1f to Color(0x8C000000),
                        center = Offset(center.x - r * 0.35f, center.y - r * 0.4f), radius = r * 1.6f,
                    ),
                    r, center,
                )
            }
        }

        // Bulbs along the wire, chasing.
        val cached = bulbs
        val points = if (cached != null && cached.first == w) cached.second else {
            val measure = PathMeasure()
            measure.setPath(wirePath(w, dp), false)
            val length = measure.length
            val count = (length / (38f * dp)).toInt().coerceAtLeast(1)
            val list = List(count) { b -> measure.getPosition((b + 0.5f) * length / count) }
            bulbs = w to list
            list
        }
        points.forEachIndexed { b, p ->
            val color = BULBS[b % BULBS.size]
            val a = 0.35f + 0.65f * (0.5f + 0.5f * cos(2f * PI.toFloat() * (t / 3.2f + (b % 5) * 0.2f)))
            drawCircle(color.copy(alpha = 0.28f * a), 13f * dp, Offset(p.x, p.y + 9f * dp))
            drawRect(Color(0xFF10261A), Offset(p.x - 2.5f * dp, p.y - 1f * dp), Size(5f * dp, 5f * dp))
            drawOval(color.copy(alpha = 0.55f + 0.45f * a), Offset(p.x - 5f * dp, p.y + 3f * dp), Size(10f * dp, 14f * dp))
            drawOval(Color.White.copy(alpha = 0.55f * a), Offset(p.x - 3f * dp, p.y + 6f * dp), Size(2.6f * dp, 5f * dp))
        }
    }
}

private fun DrawScope.drawCrystal(c: Offset, r: Float, rot: Float) {
    val color = Color(0xFFF2F8FF)
    val width = 1.1f * density
    for (k in 0 until 6) {
        val a = rot + k * PI.toFloat() / 3f
        val tip = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
        val mid = Offset(c.x + cos(a) * r * 0.55f, c.y + sin(a) * r * 0.55f)
        drawLine(color, c, tip, width, alpha = 0.85f)
        drawLine(color, mid, Offset(c.x + cos(a + 0.6f) * r * 0.8f, c.y + sin(a + 0.6f) * r * 0.8f), width, alpha = 0.85f)
        drawLine(color, mid, Offset(c.x + cos(a - 0.6f) * r * 0.8f, c.y + sin(a - 0.6f) * r * 0.8f), width, alpha = 0.85f)
    }
}

private fun DrawScope.drawGarland(corner: Offset, flip: Float) {
    val s = density
    translate(corner.x, corner.y - 6f * s) {
        scale(flip * s, s, Offset.Zero) {
            drawPath(Art.garland, Color(0xFF0F3D22), style = Stroke(width = 16f, cap = StrokeCap.Round))
            val rng = Random(7)
            for (i in 0 until 30) {
                val t = i / 30f
                val x = -4f + t * 288f
                val y = 18f + 100f * Math.pow(t.toDouble(), 1.4).toFloat() + sin(t * 9f) * 5f
                drawLine(
                    listOf(Color(0xFF1D6B3A), Color(0xFF2A8A4C), Color(0xFF0F3D22))[i % 3],
                    Offset(x, y), Offset(x + rng.range(-9f, 9f), y + rng.range(8f, 16f)), 2f, StrokeCap.Round,
                )
            }
            drawPath(Art.garland, Color(0xFF1D6B3A), style = Stroke(width = 7f, cap = StrokeCap.Round))
            listOf(Triple(60f, 34f, Color(0xFFC0392B)), Triple(130f, 40f, Color(0xFFD4AF37)), Triple(205f, 70f, Color(0xFFC0392B))).forEach { (x, y, c) ->
                drawCircle(c, 5f, Offset(x, y))
            }
        }
    }
}

private fun DrawScope.drawFrost(corner: Offset, flip: Float) {
    val path = Path()
    val s = density
    fun branch(x: Float, y: Float, a: Float, len: Float, depth: Int) {
        if (depth == 0 || len < 6f) return
        val x2 = x + cos(a) * len
        val y2 = y + sin(a) * len
        path.moveTo(corner.x + flip * x * s, corner.y + y * s)
        path.lineTo(corner.x + flip * x2 * s, corner.y + y2 * s)
        branch(x2, y2, a - 0.55f, len * 0.62f, depth - 1)
        branch(x2, y2, a + 0.55f, len * 0.62f, depth - 1)
        branch(x2, y2, a, len * 0.74f, depth - 1)
    }
    listOf(-1.2f, -0.95f, -0.7f, -0.45f, -0.2f).forEach { branch(0f, 0f, it, 70f, 4) }
    drawPath(path, Color(0xFFCFE6FF), alpha = 0.4f, style = Stroke(width = 1f * s, cap = StrokeCap.Round))
}

// ---------------------------------------------------------------- New Year

private val NY_MIX = listOf(Color(0xFFD4AF37), Color(0xFFEEF0F5), Color(0xFFFFE08A), Color(0xFFFF7A59), Color(0xFF7FB2FF))
private val NY_CONFETTI = listOf(Color(0xFFD4AF37), Color(0xFFEEF0F5), Color(0xFFFFE08A), Color(0xFFC9A227), Color(0xFFF6E7B4))

private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, val g: Float, var life: Float, val decay: Float, val color: Color, val size: Float, val trail: Int) {
    val tail = ArrayDeque<Offset>()
}
private class Rocket(var x: Float, var y: Float, val targetY: Float, var vy: Float, val type: String, val color: Color) {
    val tail = ArrayDeque<Offset>()
}
private class Glow(val x: Float, val y: Float, val color: Color, val born: Float)
private class Confetto(val x: Float, val y: Float, val w: Float, val h: Float, val v: Float, val sway: Float, val phase: Float, val spin: Float, val color: Color, val alpha: Float)
private class Bubble(val x: Float, val size: Float, val sway: Float, val duration: Float, val offset: Float)

private class Fireworks(seed: Int) {
    val rng = Random(seed)
    val rockets = mutableListOf<Rocket>()
    val sparks = mutableListOf<Spark>()
    val glows = mutableListOf<Glow>()
    val pending = mutableListOf<Pair<Float, () -> Unit>>()
    var nextLaunch = 0f
    var now = 0f
    var lastW = 1920f
    var lastH = 1080f

    fun launch(w: Float, h: Float, type: String? = null) {
        val tx = rng.range(w * 0.1f, w * 0.9f)
        rockets.add(
            Rocket(
                tx + rng.range(-40f, 40f), h + 10f, rng.range(h * 0.1f, h * 0.45f), -rng.range(5.5f, 7.5f),
                type ?: listOf("peony", "willow", "ring", "double")[rng.nextInt(4)], NY_MIX[rng.nextInt(NY_MIX.size)],
            ),
        )
    }

    fun explode(type: String, x: Float, y: Float, color: Color, dp: Float) {
        glows.add(Glow(x, y, color, now))
        when (type) {
            "peony" -> repeat(80) { i ->
                val a = 2f * PI.toFloat() * i / 80f
                val sp = rng.range(1.4f, 4.1f) * dp
                sparks.add(Spark(x, y, cos(a) * sp, sin(a) * sp, 0.028f * dp, 1f, rng.range(0.011f, 0.016f), color, 1.8f, 4))
            }
            "willow" -> repeat(64) { i ->
                val a = 2f * PI.toFloat() * i / 64f
                val sp = rng.range(0.9f, 2.6f) * dp
                sparks.add(Spark(x, y, cos(a) * sp, sin(a) * sp, 0.04f * dp, 1f, rng.range(0.0055f, 0.008f), Color(0xFFFFD36B), 1.5f, 9))
            }
            "ring" -> {
                val tilt = rng.range(0.35f, 1f)
                repeat(48) { i ->
                    val a = 2f * PI.toFloat() * i / 48f
                    sparks.add(Spark(x, y, cos(a) * 3.1f * dp, sin(a) * 3.1f * dp * tilt, 0.012f * dp, 1f, 0.012f, color, 2f, 4))
                }
            }
            else -> {
                explode("peony", x, y, color, dp)
                pending.add(now + 0.24f to { explode("ring", x, y, NY_MIX[rng.nextInt(NY_MIX.size)], dp) })
            }
        }
    }

    // One step per 1/60 s, like the web version.
    fun step(w: Float, h: Float, dp: Float) {
        now += 1f / 60f
        if (now > nextLaunch) {
            launch(w, h)
            if (rng.nextFloat() < 0.4f) pending.add(now + rng.range(0.15f, 0.4f) to { launch(w, h) })
            nextLaunch = now + rng.range(1.1f, 2.3f) / 1.3f
        }
        val due = pending.filter { it.first <= now }
        pending.removeAll(due)
        due.forEach { it.second() }
        val burst = mutableListOf<Rocket>()
        rockets.forEach { r ->
            r.y += r.vy * dp
            r.vy *= 0.985f
            r.tail.addLast(Offset(r.x, r.y))
            if (r.tail.size > 12) r.tail.removeFirst()
            if (r.y <= r.targetY || r.vy > -1.2f) burst.add(r)
        }
        rockets.removeAll(burst)
        burst.forEach { explode(it.type, it.x, it.y, it.color, dp) }
        sparks.forEach { p ->
            p.tail.addLast(Offset(p.x, p.y))
            if (p.tail.size > p.trail) p.tail.removeFirst()
            p.x += p.vx
            p.y += p.vy
            p.vy += p.g
            p.vx *= 0.992f
            p.life -= p.decay
        }
        sparks.removeAll { it.life <= 0f }
        glows.removeAll { now - it.born > 0.9f }
    }
}

@Composable
private fun NewYearScene() {
    val rng = remember { Random(11) }
    val stars = remember { List(44) { Triple(Offset(rng.nextFloat(), rng.nextFloat() * 0.62f), rng.range(1f, 2.4f), rng.range(2.5f, 6f) to rng.range(0f, 6f)) } }
    val bubbles = remember { List(22) { Bubble(rng.range(0.02f, 0.98f), rng.range(4f, 11f), rng.range(-14f, 14f), rng.range(8f, 14f), rng.range(0f, 14f)) } }
    val confetti = remember { List(80) { Confetto(rng.nextFloat(), rng.nextFloat(), rng.range(4f, 8f), rng.range(6f, 12f), rng.range(0.7f, 1.7f), rng.range(0.5f, 1.6f), rng.range(0f, 7f), rng.range(-0.05f, 0.05f), NY_CONFETTI[rng.nextInt(NY_CONFETTI.size)], rng.range(0.55f, 0.85f)) } }
    val fireworks = remember { Fireworks(5) }
    val clock = rememberClock()
    var stepped by remember { mutableStateOf(0L) }
    val context = LocalContext.current

    // Once a year, on 1 January: a finale.
    LaunchedEffect(Unit) {
        val today = Calendar.getInstance()
        if (today.get(Calendar.MONTH) != Calendar.JANUARY || today.get(Calendar.DAY_OF_MONTH) != 1) return@LaunchedEffect
        val prefs = context.getSharedPreferences("jellio_seasonal", android.content.Context.MODE_PRIVATE)
        val year = today.get(Calendar.YEAR)
        if (prefs.getInt("new_year_finale", 0) == year) return@LaunchedEffect
        prefs.edit().putInt("new_year_finale", year).apply()
        delay(2500)
        SeasonSignals.newYearTitleAt = System.currentTimeMillis()
        listOf("peony", "willow", "ring", "double", "peony", "willow", "double", "ring", "peony", "double").forEach { type ->
            fireworks.pending.add(fireworks.now to { fireworks.launch(fireworks.lastW, fireworks.lastH, type) })
            delay(260)
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val nanos = clock.longValue
        val t = nanos / 1_000_000_000f
        val dp = density
        val w = size.width
        val h = size.height
        fireworks.lastW = w
        fireworks.lastH = h
        val steps = ((nanos - stepped) / 16_666_667L).toInt().coerceIn(0, 4)
        if (steps > 0) {
            repeat(steps) { fireworks.step(w, h, dp) }
            stepped = nanos
        }

        stars.forEach { (pos, size, timing) ->
            val a = 0.15f + 0.75f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (t + timing.second) / timing.first))
            drawCircle(Color.White, size * dp / 2f, Offset(pos.x * w, pos.y * h), alpha = a)
        }
        bubbles.forEach { b ->
            val p = cycle(t, b.duration, b.offset)
            val alpha = if (p < 0.12f) p / 0.12f * 0.85f else (1f - p) / 0.88f * 0.85f
            val x = b.x * w + sin(p * PI.toFloat() * 2f) * b.sway * dp
            val y = h + 12f * dp - p * h * 1.02f
            val r = b.size * dp / 2f
            drawCircle(Color(0x8CFFECAA), r, Offset(x, y), alpha = alpha, style = Stroke(width = 1f * dp))
            drawCircle(Color.White.copy(alpha = 0.45f), r * 0.35f, Offset(x - r * 0.35f, y - r * 0.35f), alpha = alpha)
        }
        // Streamers from the top corners.
        listOf(0.02f to 1f, 0.98f to -1f).forEachIndexed { i, (fx, dir) ->
            val anchor = Offset(fx * w, 0f)
            rotate(3f * sin(t * 2f * PI.toFloat() / 12f + i * 2.2f), anchor) {
                listOf(Triple(Color(0xFFD4AF37), 18f, 0), Triple(Color(0xFFEEF0F5), 52f, 1), Triple(Color(0xFFC9A227), 86f, 2)).forEach { (color, ox, j) ->
                    val swing = (if (j % 2 == 1) 26f else -26f) * dp
                    val x0 = anchor.x + dir * (ox - 60f) * dp
                    val path = Path().apply {
                        moveTo(x0, 0f)
                        cubicTo(x0 + swing, 30f * dp, x0 - swing, 60f * dp, x0, 90f * dp)
                        cubicTo(x0 + swing, 120f * dp, x0 - swing, 150f * dp, x0, 180f * dp)
                        cubicTo(x0 - swing, 210f * dp, x0 + swing, 240f * dp, x0, (240f + j * 20f) * dp)
                    }
                    drawPath(path, color, alpha = 0.85f, style = Stroke(width = 4f * dp, cap = StrokeCap.Round))
                }
            }
        }
        confetti.forEach { c ->
            val travel = h + 28f * dp
            val y = ((c.y * travel + c.v * 60f * dp * t) % travel) - 14f * dp
            val x = c.x * w + sin(t * c.sway + c.phase) * 36f * dp
            val flip = abs(sin(t * 3f * c.sway + c.phase)) * 0.9f + 0.1f
            translate(x, y) {
                rotate((c.spin * 60f * t + c.phase) * 57.3f, Offset.Zero) {
                    scale(1f, flip, Offset.Zero) {
                        drawRect(c.color, Offset(-c.w * dp / 2f, -c.h * dp / 2f), Size(c.w * dp, c.h * dp), alpha = c.alpha)
                    }
                }
            }
        }
        fireworks.glows.forEach { g ->
            val k = ((fireworks.now - g.born) / 0.9f).coerceIn(0f, 1f)
            val r = 260f * dp * (0.5f + 0.65f * k)
            drawCircle(
                Brush.radialGradient(listOf(g.color.copy(alpha = 0.4f), g.color.copy(alpha = 0.13f), Color.Transparent), center = Offset(g.x, g.y), radius = r),
                r, Offset(g.x, g.y), alpha = 0.9f * (1f - k), blendMode = BlendMode.Screen,
            )
        }
        fireworks.rockets.forEach { r ->
            val tail = r.tail.toList()
            for (i in 1 until tail.size) {
                drawLine(Color(0xFFFFE9A8), tail[i - 1], tail[i], 1.6f * dp, StrokeCap.Round, alpha = i.toFloat() / tail.size * 0.8f, blendMode = BlendMode.Plus)
            }
        }
        fireworks.sparks.forEach { p ->
            val tail = p.tail.toList()
            for (i in 1 until tail.size) {
                drawLine(p.color, tail[i - 1], tail[i], p.size * dp, StrokeCap.Round, alpha = (p.life * i / tail.size).coerceIn(0f, 1f), blendMode = BlendMode.Plus)
            }
            drawCircle(p.color, p.size * dp * 0.7f, Offset(p.x, p.y), alpha = p.life.coerceIn(0f, 1f), blendMode = BlendMode.Plus)
        }
    }
}

@Composable
private fun NewYearTitle(modifier: Modifier) {
    val shownAt = SeasonSignals.newYearTitleAt
    if (shownAt == 0L) return
    val fade = remember(shownAt) { Animatable(0f) }
    LaunchedEffect(shownAt) {
        fade.animateTo(1f, tween(600))
        delay(3400)
        fade.animateTo(0f, tween(1000, easing = LinearEasing))
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Happy New Year",
            color = Color(0xFFFFF3C4),
            fontSize = 72.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = fade.value
                    val s = 0.85f + 0.15f * fade.value
                    scaleX = s
                    scaleY = s
                },
        )
    }
}

// ---------------------------------------------------------------- Valentine

@Composable
private fun ValentineScene() {
    val rng = remember { Random(14) }
    val hearts = remember { List(22) { i -> Bubble(rng.nextFloat(), if (i < 5) rng.range(34f, 54f) else rng.range(12f, 22f), rng.range(-28f, 28f), rng.range(12f, 22f), rng.range(0f, 22f)) } }
    val clock = rememberClock()
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.longValue / 1_000_000_000f
        val dp = density
        hearts.forEachIndexed { i, b ->
            val p = cycle(t, b.duration, b.offset)
            val soft = i < 5
            val peak = if (soft) 0.18f else 0.5f
            val alpha = if (p < 0.15f) p / 0.15f * peak else (1f - p) / 0.85f * peak
            val x = b.x * size.width + sin(p * PI.toFloat() * 2f) * b.sway * dp
            val y = size.height * 1.05f - p * size.height * 1.12f
            val s = b.size * dp / 24f
            translate(x, y) {
                scale(s, Offset.Zero) {
                    drawPath(Art.heart, if (soft) Color(0xFFFFB3C6) else Color(0xFFFF4D6D), alpha = alpha)
                }
            }
        }
    }
}
