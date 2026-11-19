package com.jellio.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekWatchTest {
    @Test
    fun directSeekWithinThresholdSucceeds() {
        val watch = SeekWatch()
        watch.noteDirectSeek(targetMs = 14 * 60_000L, nowMs = 1_000L)
        // Too early to judge before settleDelayMs.
        assertNull(watch.checkDirectSeekResult(landedMs = 9 * 60_000L, nowMs = 2_000L))
        // Once settled near the target, returns null and clears pending state.
        assertNull(watch.checkDirectSeekResult(landedMs = 14 * 60_000L - 5_000L, nowMs = 3_000L))
        assertNull(watch.checkDirectSeekResult(landedMs = 0L, nowMs = 5_000L))
    }

    @Test
    fun directSeekClampedByTruncatedCuesReturnsMissedTarget() {
        val watch = SeekWatch()
        val target = 14 * 60_000L
        watch.noteDirectSeek(targetMs = target, nowMs = 1_000L)
        // ExoPlayer snapped back to 9:05 instead of 14:00.
        assertEquals(target, watch.checkDirectSeekResult(landedMs = 9 * 60_000L + 5_000L, nowMs = 3_000L))
        // Pending seek is consumed so it only triggers once.
        assertNull(watch.checkDirectSeekResult(landedMs = 9 * 60_000L + 5_000L, nowMs = 5_000L))
    }

    @Test
    fun spontaneousBackwardJumpIsCountedAndUserSeeksAreIgnored() {
        val watch = SeekWatch()
        assertNull(watch.notePosition(12 * 60_000L, recentSeek = false))
        // User-initiated backward seek is ignored.
        assertNull(watch.notePosition(9 * 60_000L, recentSeek = true))
        assertNull(watch.notePosition(12 * 60_000L, recentSeek = false))

        // First spontaneous jump back from 12:00 to 9:05.
        val first = watch.notePosition(9 * 60_000L + 5_000L, recentSeek = false)
        assertEquals(BackwardJump(fromMs = 12 * 60_000L, toMs = 9 * 60_000L + 5_000L, count = 1), first)

        // Progresses again to 12:00 and jumps back a second time.
        assertNull(watch.notePosition(12 * 60_000L, recentSeek = false))
        val second = watch.notePosition(9 * 60_000L + 5_000L, recentSeek = false)
        assertEquals(BackwardJump(fromMs = 12 * 60_000L, toMs = 9 * 60_000L + 5_000L, count = 2), second)
    }
}
