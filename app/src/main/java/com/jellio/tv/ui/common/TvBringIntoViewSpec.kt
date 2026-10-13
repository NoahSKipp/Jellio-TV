package com.jellio.tv.ui.common

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

// Replaces Compose Foundation's heavy spring() physics animation with a
// lightweight, crisp 200ms tween for TV D-pad focus scrolling.
// On 4K Android TV devices (such as Google TV Streamer), spring physics
// compute continuous sub-pixel oscillations across large viewports, causing
// frame drops during rapid navigation.
@OptIn(ExperimentalFoundationApi::class)
internal object TvBringIntoViewSpec : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 200, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Child is above the visible viewport: scroll up to bring it into view with margin
        if (offset < 0f) {
            return offset - 32f
        }
        // Child is below the visible viewport: scroll down to bring it into view with margin
        if (offset + size > containerSize) {
            return (offset + size) - containerSize + 32f
        }
        // Child is already fully inside the viewport
        return 0f
    }
}
