package com.jellio.tv.ui.common

import android.view.KeyEvent
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Adapted from Nuvio's DpadFastScrollModifier. Holding Up or Down glides the
// list at a steady speed instead of queueing one scroll animation per key
// repeat, which is what made a held key stutter. Releasing the key (or no
// repeat for END_TIMEOUT_MS) stops it and onLand puts focus on the row that
// ended up at the top. Held Left/Right repeats are spaced out so a row
// doesn't fall behind the key.
private const val VELOCITY_DP_PER_SEC = 3200f
private const val END_TIMEOUT_MS = 160L
private const val MAX_FRAME_DT_SEC = 0.048f
private const val HORIZONTAL_REPEAT_GATE_MS = 80L

fun Modifier.dpadFastScroll(
    state: ScrollableState,
    onLand: (direction: Int) -> Unit,
): Modifier = composed {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val holder = remember { FastScrollHolder() }
    DisposableEffect(Unit) { onDispose { holder.cancel() } }

    onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        val code = native.keyCode
        val horizontal = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
        val vertical = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
        if (!horizontal && !vertical) return@onPreviewKeyEvent false

        fun end() {
            val direction = holder.direction
            if (holder.cancel()) onLand(direction)
        }

        when {
            native.action == KeyEvent.ACTION_UP -> {
                end()
                false
            }
            native.action != KeyEvent.ACTION_DOWN || native.repeatCount == 0 -> false
            horizontal -> {
                end()
                val now = native.eventTime
                if (now - holder.lastHorizontalAt < HORIZONTAL_REPEAT_GATE_MS) {
                    true
                } else {
                    holder.lastHorizontalAt = now
                    false
                }
            }
            else -> {
                val sign = if (code == KeyEvent.KEYCODE_DPAD_UP) -1 else 1
                if (holder.job?.isActive != true || holder.direction != sign) {
                    holder.job?.cancel()
                    val atEdge = (sign > 0 && !state.canScrollForward) || (sign < 0 && !state.canScrollBackward)
                    if (atEdge) {
                        end()
                        return@onPreviewKeyEvent true
                    }
                    holder.direction = sign
                    val velocity = with(density) { VELOCITY_DP_PER_SEC.dp.toPx() }
                    holder.job = scope.launch {
                        try {
                            state.scroll {
                                var last = withFrameNanos { it }
                                while (true) {
                                    val now = withFrameNanos { it }
                                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(MAX_FRAME_DT_SEC)
                                    last = now
                                    val delta = sign * velocity * dt
                                    if (scrollBy(delta) == 0f && delta != 0f) break
                                }
                            }
                            end()
                        } catch (_: CancellationException) {
                        }
                    }
                }
                holder.endTimer?.cancel()
                holder.endTimer = scope.launch {
                    delay(END_TIMEOUT_MS)
                    end()
                }
                true
            }
        }
    }
}

private class FastScrollHolder {
    var job: Job? = null
    var endTimer: Job? = null
    var direction = 0
    var lastHorizontalAt = 0L

    // True when a glide was running.
    fun cancel(): Boolean {
        val wasRunning = direction != 0
        job?.cancel()
        endTimer?.cancel()
        job = null
        endTimer = null
        direction = 0
        return wasRunning
    }
}
