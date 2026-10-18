package com.jellio.tv.ui.common

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

// Horizontal rows: the focused card stays at the row's start edge, so the
// cards after it keep sliding in from the right. Near the end the row can't
// scroll any further and focus moves along to the last item (View All).
@OptIn(ExperimentalFoundationApi::class)
internal class TvHorizontalBringIntoViewSpec(private val leadingPx: Float) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 200, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val target = if (containerSize - leadingPx < size) containerSize - size else leadingPx
        return offset - target
    }
}

@Composable
internal fun rememberTvHorizontalBringIntoViewSpec(leading: Dp = 48.dp): BringIntoViewSpec {
    val density = LocalDensity.current
    return remember(density, leading) { TvHorizontalBringIntoViewSpec(with(density) { leading.toPx() }) }
}

// Vertical list of rows under Home's showcase: the focused card always lands
// topInsetPx below the top of the list, which leaves room for its row title.
@OptIn(ExperimentalFoundationApi::class)
internal class TvVerticalRowBringIntoViewSpec(
    private val topInsetPx: Float = 0f,
    private val canScrollBackward: () -> Boolean = { true },
) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> =
        tween(durationMillis = 180, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val distance = offset - topInsetPx
        if (distance < 0f && !canScrollBackward()) return 0f
        return distance
    }
}


