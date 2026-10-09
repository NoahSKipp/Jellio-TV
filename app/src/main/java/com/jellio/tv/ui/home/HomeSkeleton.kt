package com.jellio.tv.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Grey placeholders for rows still on their way, like the web Home's
// skeleton cards: a soft pulse, never focusable.
@Composable
private fun skeletonColor(): Color {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.05f,
        targetValue = 0.11f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    return Color.White.copy(alpha = pulse)
}

@Composable
fun SkeletonRow(landscape: Boolean = false, modifier: Modifier = Modifier) {
    val color = skeletonColor()
    Column(modifier = modifier.fillMaxWidth().clipToBounds().padding(start = 48.dp, top = 20.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.width(160.dp).height(16.dp).clip(RoundedCornerShape(6.dp)).background(color),
        ) {}
        // Laid out at full size and cut off at the screen edge, never squeezed.
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState(), enabled = false),
        ) {
            repeat(if (landscape) 4 else 7) {
                val cardWidth = if (landscape) 220.dp else 110.dp
                Row(
                    modifier = Modifier
                        .width(cardWidth)
                        .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color),
                ) {}
            }
        }
    }
}

// The whole Home while its first rows load: a banner and a few rows.
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonColor()
    Column(modifier = modifier.fillMaxSize().clipToBounds()) {
        Row(
            modifier = Modifier
                .padding(start = 48.dp, end = 48.dp, top = 28.dp)
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color),
        ) {}
        SkeletonRow(landscape = true)
        SkeletonRow()
    }
}
