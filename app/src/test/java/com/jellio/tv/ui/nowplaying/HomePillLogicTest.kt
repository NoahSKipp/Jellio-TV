package com.jellio.tv.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomePillLogicTest {
    @Test
    fun quickHubWhenNobodyIsWatching() {
        assertEquals(HomePill.QUICK_HUB, homePillFor(0, false))
    }

    @Test
    fun nowPlayingWhenStreamsRun() {
        assertEquals(HomePill.NOW_PLAYING, homePillFor(1, false))
        assertEquals(HomePill.NOW_PLAYING, homePillFor(3, true))
    }

    @Test
    fun nowPlayingWhenThisTvIsPaused() {
        assertEquals(HomePill.NOW_PLAYING, homePillFor(0, true))
    }

    @Test
    fun countAddsAllThreeSources() {
        assertEquals(6, quickHubPillCount(3, 2, 1))
        assertEquals(0, quickHubPillCount(0, 0, 0))
    }

    @Test
    fun countIgnoresNegativeInputs() {
        assertEquals(2, quickHubPillCount(-4, 2, -1))
    }

    @Test
    fun badgeHiddenWhenNothingWaits() {
        assertNull(quickHubPillBadgeText(0))
        assertNull(quickHubPillBadgeText(-1))
    }

    @Test
    fun badgeShowsNumberUpToNine() {
        assertEquals("1", quickHubPillBadgeText(1))
        assertEquals("9", quickHubPillBadgeText(9))
    }

    @Test
    fun badgeCapsAtNinePlus() {
        assertEquals("9+", quickHubPillBadgeText(10))
        assertEquals("9+", quickHubPillBadgeText(250))
    }
}
