package com.jellio.tv.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jellio.tv.ui.theme.JellioSecondary

// Real port of css/app.css's own .jellio-screen-spinner: a plain
// rotating ring (a translucent full circle, border-top recolored to
// the real accent and the whole thing spun 360deg/800ms linear
// infinite), the real generic per-screen loading indicator every
// renderLoading() caller on the web build actually shows. This app's
// own ProgressSweep.kt ported a real, but different, CSS class
// instead (.jellio-progress-bar, that file's own header covers what
// it actually is: a real long-running-import sweep, not this),
// borrowed here as a stand-in that never matched what a reader
// actually sees on web for the exact same "Loading..." state, real
// feedback's own explicit ask. tv-material3's own CircularProgressIndicator
// is avoided deliberately (this session already hit a real CI failure
// against this repo's own pinned version once before), a plain Canvas
// arc needs no such trust.
@Composable
fun ScreenSpinner(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val transition = rememberInfiniteTransition(label = "screenSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "screenSpinnerRotation",
    )
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawArc(
            color = Color.White.copy(alpha = 0.16f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
        )
        rotate(rotation) {
            drawArc(
                color = JellioSecondary,
                startAngle = -90f,
                sweepAngle = 100f,
                useCenter = false,
                style = stroke,
            )
        }
    }
}
