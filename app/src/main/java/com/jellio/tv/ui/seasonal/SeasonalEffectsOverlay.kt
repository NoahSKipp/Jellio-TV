package com.jellio.tv.ui.seasonal

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class SeasonalParticle(
    val xFraction: Float,
    val speed: Float,
    val phase: Float,
    val swayPx: Float,
    val sizePx: Float,
    val seed: Float,
    val color: Color,
)

private data class SeasonalCreature(
    val yFraction: Float,
    val speed: Float,
    val phase: Float,
    val scale: Float,
    val seed: Float,
    val isBat: Boolean,
)

private fun buildSeasonalParticles(themeKey: String, accent: Color, accent2: Color): List<SeasonalParticle> {
    val rng = Random(themeKey.hashCode())
    val count = when (themeKey) {
        "halloween" -> 16
        "christmas" -> 18
        "newyear" -> 20
        "valentine" -> 14
        else -> 12
    }
    return List(count) {
        val color = when (themeKey) {
            "halloween" -> listOf(
                Color(0xFFFF7518),
                Color(0xFFFF9500),
                Color(0xFFFFD36B),
                Color(0xFFE2560A),
            )[rng.nextInt(4)]
            "christmas" -> listOf(
                Color(0xFFFFFFFF),
                Color(0xFFE8F4F8),
                Color(0xFFD6EFFF),
                Color(0xFFFFE08A),
            )[rng.nextInt(4)]
            "newyear" -> listOf(
                Color(0xFFD4AF37),
                Color(0xFFEEF0F5),
                Color(0xFFFFE08A),
                Color(0xFF7FB2FF),
            )[rng.nextInt(4)]
            "valentine" -> listOf(
                Color(0xFFFF4D6D),
                Color(0xFFFF758F),
                Color(0xFFFFB3C6),
            )[rng.nextInt(3)]
            else -> accent
        }
        val size = when (themeKey) {
            "halloween" -> rng.nextFloat() * 3.5f + 2.0f
            "christmas" -> rng.nextFloat() * 4.0f + 2.5f
            "newyear" -> rng.nextFloat() * 4.5f + 2.0f
            "valentine" -> rng.nextFloat() * 5.0f + 3.0f
            else -> 3.0f
        }
        SeasonalParticle(
            xFraction = rng.nextFloat(),
            speed = rng.nextFloat() * 0.7f + 0.65f,
            phase = rng.nextFloat(),
            swayPx = (rng.nextFloat() * 2f - 1f) * 36f,
            sizePx = size,
            seed = rng.nextFloat() * 10f,
            color = color,
        )
    }
}

private fun buildCreatures(themeKey: String): List<SeasonalCreature> {
    if (themeKey != "halloween") return emptyList()
    val rng = Random(themeKey.hashCode() + 42)
    return listOf(
        SeasonalCreature(yFraction = 0.18f, speed = 0.5f, phase = 0.05f, scale = 1.0f, seed = rng.nextFloat() * 10f, isBat = true),
        SeasonalCreature(yFraction = 0.32f, speed = 0.42f, phase = 0.48f, scale = 0.75f, seed = rng.nextFloat() * 10f, isBat = true),
        SeasonalCreature(yFraction = 0.46f, speed = 0.58f, phase = 0.82f, scale = 0.85f, seed = rng.nextFloat() * 10f, isBat = true),
        SeasonalCreature(yFraction = 0.65f, speed = 0.3f, phase = 0.25f, scale = 1.1f, seed = rng.nextFloat() * 10f, isBat = false),
        SeasonalCreature(yFraction = 0.78f, speed = 0.28f, phase = 0.72f, scale = 0.9f, seed = rng.nextFloat() * 10f, isBat = false),
    )
}

// Modern Jellio seasonal ambient overlay:
// 1. Dual radial gradient washes matching css/app.css's .jellio-seasons-wash.
// 2. GPU-accelerated animated particles (rising glowing embers, bats, ghosts, falling snow).
// Strictly hardware accelerated with zero CPU blur layers to maintain 60 FPS on TV.
@Composable
fun SeasonalEffectsOverlay(themeKey: String?, modifier: Modifier = Modifier) {
    val spec = themeKey?.let { SEASONAL_THEMES[it] } ?: return
    val particles = remember(themeKey) { buildSeasonalParticles(themeKey, spec.accent, spec.accent2) }
    val creatures = remember(themeKey) { buildCreatures(themeKey) }

    val infiniteTransition = rememberInfiniteTransition(label = "SeasonalAnimation")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "SeasonalProgress",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Radial gradient 1: top-left wash (e.g. purple in Halloween, deep pine in Christmas)
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

        // Radial gradient 2: bottom-right wash (e.g. warm amber in Halloween, hearth red in Christmas)
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

        // Animated particles layer
        val isFalling = themeKey == "christmas"
        for (particle in particles) {
            val cycle = (progress * particle.speed + particle.phase) % 1f
            val yFraction = if (isFalling) {
                -0.08f + cycle * 1.16f
            } else {
                1.08f - cycle * 1.16f
            }
            val sway = sin((cycle * 4f + particle.seed) * (2f * PI.toFloat())) * particle.swayPx
            val x = (particle.xFraction * width + sway).mod(width)
            val y = yFraction * height

            val fade = sin(cycle * PI.toFloat()).coerceIn(0f, 1f)
            val baseAlpha = if (themeKey == "halloween") 0.65f else 0.55f
            val alpha = fade * baseAlpha

            if (alpha > 0.02f) {
                if (themeKey == "halloween") {
                    // Glowing floating ember: soft outer glow + bright core
                    drawCircle(
                        color = particle.color.copy(alpha = alpha * 0.3f),
                        radius = particle.sizePx * 2.2f,
                        center = Offset(x, y),
                    )
                    drawCircle(
                        color = particle.color.copy(alpha = alpha),
                        radius = particle.sizePx,
                        center = Offset(x, y),
                    )
                } else if (themeKey == "christmas") {
                    // Soft falling snowflake
                    drawCircle(
                        color = particle.color.copy(alpha = alpha * 0.25f),
                        radius = particle.sizePx * 1.6f,
                        center = Offset(x, y),
                    )
                    drawCircle(
                        color = particle.color.copy(alpha = alpha),
                        radius = particle.sizePx,
                        center = Offset(x, y),
                    )
                } else {
                    // Gentle rising sparkles / bubbles
                    drawCircle(
                        color = particle.color.copy(alpha = alpha * 0.35f),
                        radius = particle.sizePx * 1.8f,
                        center = Offset(x, y),
                    )
                    drawCircle(
                        color = particle.color.copy(alpha = alpha),
                        radius = particle.sizePx,
                        center = Offset(x, y),
                    )
                }
            }
        }

        // Creatures layer (Halloween bats & rising ghost wisps)
        if (creatures.isNotEmpty()) {
            for (c in creatures) {
                val cycle = (progress * c.speed + c.phase) % 1f
                if (c.isBat) {
                    // Bat flies right-to-left across screen
                    val batX = width * (1.15f - cycle * 1.3f)
                    val batY = height * c.yFraction + sin((cycle * 5f + c.seed) * (2f * PI.toFloat())) * 24f
                    val flap = sin((cycle * 24f + c.seed) * (2f * PI.toFloat())) * 0.45f
                    val alpha = sin(cycle * PI.toFloat()).coerceIn(0f, 1f) * 0.45f

                    if (alpha > 0.02f) {
                        val wingTipY = -10f * c.scale + flap * 14f * c.scale
                        val batPath = Path().apply {
                            moveTo(batX, batY - 4f * c.scale)
                            // Left wing
                            quadraticTo(batX - 14f * c.scale, batY + wingTipY, batX - 26f * c.scale, batY + wingTipY + 4f)
                            quadraticTo(batX - 16f * c.scale, batY + 4f * c.scale, batX - 4f * c.scale, batY + 2f * c.scale)
                            // Tail
                            lineTo(batX, batY + 8f * c.scale)
                            // Right wing
                            lineTo(batX + 4f * c.scale, batY + 2f * c.scale)
                            quadraticTo(batX + 16f * c.scale, batY + 4f * c.scale, batX + 26f * c.scale, batY + wingTipY + 4f)
                            quadraticTo(batX + 14f * c.scale, batY + wingTipY, batX, batY - 4f * c.scale)
                            close()
                        }
                        drawPath(path = batPath, color = Color(0xFF09040E).copy(alpha = alpha))
                    }
                } else {
                    // Translucent ghost rises gently
                    val ghostY = height * (1.1f - cycle * 1.25f)
                    val sway = sin((cycle * 3f + c.seed) * (2f * PI.toFloat())) * 30f
                    val ghostX = (width * 0.25f + c.seed * 60f + sway).mod(width)
                    val alpha = sin(cycle * PI.toFloat()).coerceIn(0f, 1f) * 0.22f

                    if (alpha > 0.02f) {
                        val ghostPath = Path().apply {
                            moveTo(ghostX - 12f * c.scale, ghostY)
                            cubicTo(
                                ghostX - 12f * c.scale, ghostY - 18f * c.scale,
                                ghostX + 12f * c.scale, ghostY - 18f * c.scale,
                                ghostX + 12f * c.scale, ghostY,
                            )
                            lineTo(ghostX + 12f * c.scale, ghostY + 16f * c.scale)
                            quadraticTo(ghostX + 6f * c.scale, ghostY + 12f * c.scale, ghostX, ghostY + 18f * c.scale)
                            quadraticTo(ghostX - 6f * c.scale, ghostY + 12f * c.scale, ghostX - 12f * c.scale, ghostY + 16f * c.scale)
                            close()
                        }
                        drawPath(path = ghostPath, color = Color(0xFFE2D6FF).copy(alpha = alpha))
                    }
                }
            }
        }
    }
}
