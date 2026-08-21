package com.jellio.tv.ui.seasonal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Real port of components/seasonalEffects.js's own buildFilmNoir(), in
// spirit from CodeDevMLH/Jellyfin-Seasonals' own real filmnoir.js/
// filmnoir.css: a real sepia mix-blend-mode: color tint
// (BlendMode.Color, the same real Skia/CSS blend both share), a real
// vignette, and a real screen-blend scratch streak that sweeps across
// with its own real flicker. css/app.css's own real grain layer (an
// injected SVG feTurbulence data URI, animated in discrete steps) is
// deliberately left out, the same real corner cut FrostOverlay.kt's
// own header already makes for frost.css's own SVG turbulence filter:
// procedurally reproducing fractal noise is real extra complexity
// neither this nor that layer is worth taking on for a background
// texture.
@Composable
fun FilmNoirOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "filmnoir")
    val sweep by transition.animateFloat(
        initialValue = -0.1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 4000, easing = LinearEasing)),
        label = "filmnoir-scratch-sweep",
    )
    val flicker by transition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "filmnoir-flicker",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(color = Color(0xFF8C7355), blendMode = BlendMode.Color) },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f),
                    ),
                ),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color.White.copy(alpha = flicker),
                topLeft = Offset(sweep * size.width, 0f),
                size = Size(width = size.width * 0.008f, height = size.height),
                blendMode = BlendMode.Screen,
            )
        }
    }
}
