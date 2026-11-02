package com.jellio.tv.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferWatchTest {
    private fun started() = BufferWatch().apply { firstFrameAt = 1 }

    @Test
    fun ignoresStallsBeforeFirstFrameAndRightAfterSeek() {
        val watch = BufferWatch()
        assertFalse(watch.counts(10_000, playing = true, inGroup = false, switching = false))
        watch.firstFrameAt = 1
        watch.lastSeekAt = 9_000
        assertFalse(watch.counts(10_000, playing = true, inGroup = false, switching = false))
        assertTrue(watch.counts(20_000, playing = true, inGroup = false, switching = false))
        assertFalse(watch.counts(20_000, playing = true, inGroup = true, switching = false))
    }

    @Test
    fun offersAfterLongStall() {
        val watch = started()
        watch.stallStarted(10_000)
        assertFalse(watch.shouldOffer(15_000))
        assertTrue(watch.shouldOffer(22_000))
    }

    @Test
    fun offersAfterRepeatedShortStallsInWindow() {
        val watch = started()
        listOf(10_000L, 30_000L, 50_000L).forEach { watch.stallStarted(it); watch.stallEnded(it + 1_000) }
        assertFalse(watch.shouldOffer(60_000))
        watch.stallStarted(70_000)
        assertTrue(watch.shouldOffer(70_000))
    }

    @Test
    fun snoozeHoldsOffer() {
        val watch = started()
        watch.snooze(0, 600_000)
        watch.stallStarted(10_000)
        assertFalse(watch.shouldOffer(30_000))
        assertFalse(watch.counts(30_000, playing = true, inGroup = false, switching = false))
    }
}
