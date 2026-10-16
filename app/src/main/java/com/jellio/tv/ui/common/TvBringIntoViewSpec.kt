package com.jellio.tv.ui.common

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

// Replaces Compose Foundation's heavy spring() physics animation with a
// lightweight, snappy 90ms tween for TV D-pad focus scrolling so scrolling
// keeps direct pace with remote button presses and key repeats without lag.
@OptIn(ExperimentalFoundationApi::class)
internal object TvBringIntoViewSpec : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 90, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Child is taller/wider than the viewport: align to leading edge
        if (size >= containerSize) {
            return offset
        }

        // Buffer margin keeps the focused item from hugging the edge of the TV screen
        // and ensures the next item is already in view ahead of navigation.
        val margin = (containerSize * 0.12f).coerceIn(60f, 120f)

        // Child is at or beyond the leading edge (top or left)
        if (offset < margin) {
            return offset - margin
        }
        // Child is at or beyond the trailing edge (bottom or right)
        if (offset + size > containerSize - margin) {
            return (offset + size) - (containerSize - margin)
        }
        // Child is comfortably inside the viewport
        return 0f
    }
}
