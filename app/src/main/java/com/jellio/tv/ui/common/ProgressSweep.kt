package com.jellio.tv.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jellio.tv.ui.theme.JellioSecondary

@Composable
fun ProgressSweep(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "progressSweep")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progressSweepOffset",
    )
    Box(modifier = modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.06f))) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.38f)
                .height(3.dp)
                .graphicsLayer { translationX = progress * size.width }
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.25f to JellioSecondary.copy(alpha = 0.35f),
                        0.5f to JellioSecondary,
                        0.75f to JellioSecondary.copy(alpha = 0.35f),
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}
