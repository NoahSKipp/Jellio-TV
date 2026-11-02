package com.jellio.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import kotlin.random.Random

private const val SHATTER_COLUMNS = 7
private const val SHATTER_ROWS = 10
private const val SHATTER_DURATION_MS = 550

private val ShatterEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

private data class ShardSpec(
    val col: Int,
    val row: Int,
    val delayMs: Float,
    val driftX: Float,
    val driftY: Float,
    val rotateDeg: Float,
)

private fun buildShardSpecs(): List<ShardSpec> = buildList {
    for (row in 0 until SHATTER_ROWS) {
        for (col in 0 until SHATTER_COLUMNS) {
            add(
                ShardSpec(
                    col = col,
                    row = row,
                    delayMs = col * 45f + Random.nextFloat() * 90f,
                    driftX = (Random.nextFloat() - 0.3f) * 40f,
                    driftY = -(30f + Random.nextFloat() * 50f),
                    rotateDeg = (Random.nextFloat() - 0.5f) * 50f,
                ),
            )
        }
    }
}

@Composable
fun CardShatterOverlay(
    imageUrl: String,
    cardWidth: Dp,
    cardHeight: Dp,
    onFinished: () -> Unit,
) {
    val shards = remember { buildShardSpecs() }
    val totalMs = remember(shards) { shards.maxOf { it.delayMs } + SHATTER_DURATION_MS }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(totalMs) {
        progress.animateTo(targetValue = totalMs, animationSpec = tween(durationMillis = totalMs.toInt(), easing = LinearEasing))
        onFinished()
    }

    val shardWidth = cardWidth / SHATTER_COLUMNS
    val shardHeight = cardHeight / SHATTER_ROWS

    Box(modifier = Modifier.size(cardWidth, cardHeight)) {
        shards.forEach { shard ->
            Box(
                modifier = Modifier
                    .offset(x = shardWidth * shard.col, y = shardHeight * shard.row)
                    .size(shardWidth, shardHeight)
                    .graphicsLayer {
                        val localProgress = ((progress.value - shard.delayMs) / SHATTER_DURATION_MS).coerceIn(0f, 1f)
                        val eased = ShatterEasing.transform(localProgress)
                        alpha = 1f - eased
                        translationX = eased * shard.driftX
                        translationY = eased * shard.driftY
                        rotationZ = eased * shard.rotateDeg
                        val scale = 1f - eased * 0.6f
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RectangleShape),
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(cardWidth, cardHeight)
                        .offset(x = -(shardWidth * shard.col), y = -(shardHeight * shard.row)),
                )
            }
        }
    }
}
