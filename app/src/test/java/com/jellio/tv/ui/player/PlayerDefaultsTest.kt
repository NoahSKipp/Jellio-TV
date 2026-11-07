package com.jellio.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDefaultsTest {
    @Test
    fun seekDeltaReadsInSecondsThenMinutes() {
        assertEquals("+10s", formatSeekDelta(10_000L))
        assertEquals("-30s", formatSeekDelta(-30_000L))
        assertEquals("+0s", formatSeekDelta(0L))
        assertEquals("+1m", formatSeekDelta(60_000L))
        assertEquals("-1m 30s", formatSeekDelta(-90_000L))
    }

    @Test
    fun seekTargetStaysInsideTheTitle() {
        assertEquals(0L, seekTarget(5_000L, -10_000L, 60_000L))
        assertEquals(60_000L, seekTarget(55_000L, 10_000L, 60_000L))
        assertEquals(70_000L, seekTarget(60_000L, 10_000L, 0L))
    }

    @Test
    fun speedDropsTrailingZero() {
        assertEquals("1x", formatSpeed(1f))
        assertEquals("1.25x", formatSpeed(1.25f))
    }
}
