package com.jellio.tv.ui.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val FIREWORK_COLORS = listOf(
    Color(0xFFFF5E5E),
    Color(0xFFFFD45E),
    Color(0xFF5ECBFF),
    Color(0xFF8BFF5E),
    Color(0xFFFF8BF3),
)

private class FireworkParticle(var x: Float, var y: Float, val vx: Float, var vy: Float, var life: Float)

private class FireworkBurst(val color: Color, val particles: List<FireworkParticle>)

private fun rand(min: Float, max: Float) = min + Random.nextFloat() * (max - min)

private fun spawnBurst(width: Float, height: Float): FireworkBurst {
    val x = rand(width * 0.15f, width * 0.85f)
    val y = rand(height * 0.15f, height * 0.55f)
    val color = FIREWORK_COLORS.random()
    val count = 26
    val particles = List(count) { i ->
        val angle = (PI * 2 * i / count).toFloat()
        val speed = rand(1.5f, 4f)
        FireworkParticle(x = x, y = y, vx = cos(angle) * speed, vy = sin(angle) * speed, life = 1f)
    }
    return FireworkBurst(color, particles)
}

// Real port of components/seasonalEffects.js's own buildFireworks():
// the one real theme that file's own header says genuinely cannot be
// a CSS-only span, an expanding fading ring of real per-frame physics
// instead. Same real spawn timing (a first burst after 400-1200ms,
// every 1500-3200ms after that), same real 26-particle radial burst,
// gravity and fade rate. Physics scaled against a real elapsed-time
// delta rather than a bare per-callback increment, the same real
// reason DriftOverlay.kt/CardShatterOverlay.kt already drive their
// own animations off elapsed seconds rather than frame count:
// withFrameNanos fires once per real display refresh, which a real TV
// panel does not promise stays at 60Hz the way requestAnimationFrame's
// own implicit assumption here does.
@Composable
fun FireworksOverlay(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val bursts = remember { mutableListOf<FireworkBurst>() }
        var tick by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            delay(rand(400f, 1200f).toLong())
            while (isActive) {
                bursts.add(spawnBurst(widthPx, heightPx))
                delay(rand(1500f, 3200f).toLong())
            }
        }

        LaunchedEffect(Unit) {
            var lastNanos = withFrameNanos { it }
            while (isActive) {
                val nanos = withFrameNanos { it }
                val dtScale = ((nanos - lastNanos) / 1_000_000_000f) * 60f
                lastNanos = nanos

                val iterator = bursts.iterator()
                while (iterator.hasNext()) {
                    val burst = iterator.next()
                    var anyAlive = false
                    burst.particles.forEach { p ->
                        p.x += p.vx * dtScale
                        p.y += p.vy * dtScale
                        p.vy += 0.02f * dtScale
                        p.life -= 0.012f * dtScale
                        if (p.life > 0f) anyAlive = true
                    }
                    if (!anyAlive) iterator.remove()
                }
                tick++
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            tick
            bursts.forEach { burst ->
                burst.particles.forEach { p ->
                    if (p.life > 0f) {
                        drawCircle(color = burst.color.copy(alpha = p.life.coerceIn(0f, 1f)), radius = 2.2f, center = Offset(p.x, p.y))
                    }
                }
            }
        }
    }
}
