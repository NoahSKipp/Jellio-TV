package com.jellio.tv.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.ui.theme.JellioText

// A title loading: its backdrop with the logo bouncing gently, or its
// name pulsing when it has no logo. Used by the player and title pages.
@Composable
fun TitleLoadingArt(art: List<ArtCandidate>, logoUrl: String?, title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        if (art.isNotEmpty()) {
            TitleArtBackdrop(art, Modifier.fillMaxSize())
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        startY = 0f,
                        endY = 1080f,
                    ),
                ),
            )
        }
        val transition = rememberInfiniteTransition(label = "titleLoading")
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "titleLoadingPulse",
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(0.4f).fillMaxHeight(0.4f).scale(0.95f + 0.1f * pulse),
                )
            } else {
                Text(
                    text = title.ifBlank { "Loading" },
                    color = JellioText.copy(alpha = 0.55f + 0.45f * pulse),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
        }
    }
}
