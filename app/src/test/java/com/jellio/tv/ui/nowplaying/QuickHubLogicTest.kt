package com.jellio.tv.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickHubLogicTest {
    @Test
    fun onlySelectedOrFocusedPillShowsItsLabel() {
        assertFalse(quickHubPillExpanded(selected = false, focused = false))
        assertTrue(quickHubPillExpanded(selected = true, focused = false))
        assertTrue(quickHubPillExpanded(selected = false, focused = true))
        assertTrue(quickHubPillExpanded(selected = true, focused = true))
    }

    @Test
    fun expandedPillTakesMoreWidth() {
        assertEquals(PILL_WEIGHT_COMPACT, quickHubPillWeight(selected = false, focused = false), 0f)
        assertEquals(PILL_WEIGHT_EXPANDED, quickHubPillWeight(selected = true, focused = false), 0f)
        assertEquals(PILL_WEIGHT_EXPANDED, quickHubPillWeight(selected = false, focused = true), 0f)
        assertTrue(PILL_WEIGHT_EXPANDED > PILL_WEIGHT_COMPACT)
    }
}
