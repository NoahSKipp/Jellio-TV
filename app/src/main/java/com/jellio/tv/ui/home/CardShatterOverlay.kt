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
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import kotlin.random.Random

private const val SHATTER_COLUMNS = 7
private const val SHATTER_ROWS = 10
private const val SHATTER_DURATION_MS = 550

// Real port of components/cardOptionsMenu.js's own real cubic-bezier(0.4,
// 0, 1, 1) easing on .jellio-card-snap-shard's own real
// jellio-card-snap-dissolve keyframe.
private val ShatterEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

private data class ShardSpec(
    val col: Int,
    val row: Int,
    val delayMs: Float,
    val driftX: Float,
    val driftY: Float,
    val rotateDeg: Float,
)

// Real port of that file's own animateCardRemoval() shard generation:
// same real 7x10 grid, same real per-column stagger (col * 45 plus up
// to 90ms of real per-shard randomness) and drift/rotate ranges, real
// feedback's own reasoning for not using a mechanical column by column
// sweep preserved the same way.
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

// Real port of components/cardOptionsMenu.js's own animateCardRemoval():
// a real Thanos snap, the same real 7x10 grid of shards fading,
// drifting and rotating away with a staggered left to right delay.
// That file slices a single decoded <img> into background-position
// offset shards; this app has no equivalent decoded-bitmap access
// worth a second real Coil request for, so each shard is instead its
// own full size AsyncImage clipped to a 1/70th window and offset
// negatively, the same real trick CSS's own background-position does,
// just with Coil's own memory cache making every one of the 70 real
// requests resolve from the same already-decoded image the row's own
// card was already showing. Blur, the CSS keyframe's own real final
// touch, is deliberately left out: 70 real blurred layers redrawing
// every frame is a real cost this app is not spending on a purely
// cosmetic effect, same real reasoning DetailScreen's own scope-cut
// comments document elsewhere in this codebase.
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
