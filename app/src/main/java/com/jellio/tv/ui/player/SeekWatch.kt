package com.jellio.tv.ui.player

import kotlin.math.abs

internal const val SEEK_DRIFT_FALLBACK_MS = 30_000L
internal const val DIRECT_SEEK_SETTLE_MS = 1_500L
internal const val BACKWARD_JUMP_THRESHOLD_MS = 30_000L

internal data class BackwardJump(
    val fromMs: Long,
    val toMs: Long,
    val count: Int,
)

// Tracks Direct Play seek accuracy and spontaneous backward jumps on the current
// stream so a broken container seek index or PTS reset can fall back to server remux.
internal class SeekWatch(
    private val driftThresholdMs: Long = SEEK_DRIFT_FALLBACK_MS,
    private val settleDelayMs: Long = DIRECT_SEEK_SETTLE_MS,
    private val backwardJumpThresholdMs: Long = BACKWARD_JUMP_THRESHOLD_MS,
) {
    private var pendingTargetMs: Long? = null
    private var pendingRequestedAtMs: Long = 0L
    private var lastPositionMs: Long = 0L
    private var backwardJumpCount: Int = 0

    fun reset() {
        pendingTargetMs = null
        pendingRequestedAtMs = 0L
        lastPositionMs = 0L
        backwardJumpCount = 0
    }

    fun noteDirectSeek(targetMs: Long, nowMs: Long) {
        pendingTargetMs = targetMs
        pendingRequestedAtMs = nowMs
        lastPositionMs = targetMs
    }

    fun clearDirectSeek() {
        pendingTargetMs = null
        pendingRequestedAtMs = 0L
    }

    // Returns the missed target timestamp if a Direct Play seek settled far away
    // from where the viewer asked to go (e.g. truncated MKV Cues clamping seeks).
    fun checkDirectSeekResult(landedMs: Long, nowMs: Long): Long? {
        val target = pendingTargetMs ?: return null
        if (nowMs - pendingRequestedAtMs < settleDelayMs) return null
        pendingTargetMs = null
        pendingRequestedAtMs = 0L
        lastPositionMs = landedMs
        return if (abs(landedMs - target) > driftThresholdMs) target else null
    }

    // Flags large backward jumps that happen during normal playback without a
    // recent seek (e.g. a corrupt cluster timestamp reset in a container).
    fun notePosition(positionMs: Long, recentSeek: Boolean): BackwardJump? {
        val prev = lastPositionMs
        lastPositionMs = positionMs
        if (recentSeek || prev <= 0L) return null
        if (prev - positionMs > backwardJumpThresholdMs) {
            backwardJumpCount++
            return BackwardJump(fromMs = prev, toMs = positionMs, count = backwardJumpCount)
        }
        return null
    }
}
