package com.jellio.tv.ui.seasonal

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

// Real CSS ease-in-out, cubic-bezier(0.42, 0, 0.58, 1): Compose ships
// no named curve matching that exactly, the closest of its own three
// (FastOutSlowIn/LinearOutSlowIn/FastOutLinearIn) all being visibly
// off center for a real back-and-forth shimmer this subtle.
private val FrostShimmerEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

// Real port of components/seasonalEffects.js's own buildFrost(), in
// spirit from CodeDevMLH/Jellyfin-Seasonals' own real frost.js/
// frost.css: a real vignette plus shimmer, that real plugin's own SVG
// feTurbulence displacement filter left out, the same real corner cut
// that file's own header already documents (an injected
// <svg><filter> id reference is real extra complexity neither port
// takes on for a corner effect). Two stacked radial gradients stand
// in for css/app.css's own real outer radial-gradient plus its own
// real inset box-shadow glow, since Compose has no inset shadow
// equivalent to paint directly.
@Composable
fun FrostOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "frost")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FrostShimmerEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "frost-progress",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val scale = 1f + progress * 0.02f
                scaleX = scale
                scaleY = scale
                alpha = 0.5f + progress * 0.35f
            }
            .background(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.25f),
                    0.6f to Color.Transparent,
                    1f to Color(0xFFB4DCFF).copy(alpha = 0.5f),
                ),
            )
            .background(
                Brush.radialGradient(
                    0.6f to Color.Transparent,
                    1f to Color(0xFFB4DCFF).copy(alpha = 0.4f),
                ),
            ),
    )
}
