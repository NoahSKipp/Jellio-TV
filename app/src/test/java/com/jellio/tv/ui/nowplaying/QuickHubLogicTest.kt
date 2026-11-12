package com.jellio.tv.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun friendsTabIsHiddenWithoutThePlugin() {
        assertTrue(QuickHubTab.FRIENDS in visibleQuickHubTabs(true))
        assertFalse(QuickHubTab.FRIENDS in visibleQuickHubTabs(false))
        assertEquals(3, visibleQuickHubTabs(false).size)
        assertEquals(QuickHubTab.NOW_PLAYING, resolveQuickHubTab(QuickHubTab.FRIENDS, false))
        assertEquals(QuickHubTab.FRIENDS, resolveQuickHubTab(QuickHubTab.FRIENDS, true))
        assertEquals(QuickHubTab.GROUP_WATCH, resolveQuickHubTab(QuickHubTab.GROUP_WATCH, false))
    }

    @Test
    fun backLeavesTheAddListBeforeClosing() {
        assertTrue(quickHubBackLeavesAddList(QuickHubTab.FRIENDS, true))
        assertFalse(quickHubBackLeavesAddList(QuickHubTab.FRIENDS, false))
        assertFalse(quickHubBackLeavesAddList(QuickHubTab.NOTIFICATIONS, true))
    }

    @Test
    fun tabSelectionIsBlockedOnlyDuringTheGuard() {
        assertTrue(quickHubMaySelectTab(nowMs = 1_000, blockedUntilMs = 0))
        assertFalse(quickHubMaySelectTab(nowMs = 1_000, blockedUntilMs = 1_800))
        assertTrue(quickHubMaySelectTab(nowMs = 1_800, blockedUntilMs = 1_800))
    }

    @Test
    fun focusIsRecoveredOnlyAfterItWasInTheHub() {
        assertFalse(quickHubShouldRecoverFocus(hadFocus = false, hasFocus = false))
        assertFalse(quickHubShouldRecoverFocus(hadFocus = true, hasFocus = true))
        assertTrue(quickHubShouldRecoverFocus(hadFocus = true, hasFocus = false))
    }

    @Test
    fun leftAndRightStepToTheNeighbouringVisibleTab() {
        val all = visibleQuickHubTabs(true)
        assertEquals(QuickHubTab.NOTIFICATIONS, quickHubNeighbourTab(all, QuickHubTab.NOW_PLAYING, 1))
        assertEquals(QuickHubTab.GROUP_WATCH, quickHubNeighbourTab(all, QuickHubTab.FRIENDS, -1))
        assertNull(quickHubNeighbourTab(all, QuickHubTab.NOW_PLAYING, -1))
        assertNull(quickHubNeighbourTab(all, QuickHubTab.FRIENDS, 1))
        val noFriends = visibleQuickHubTabs(false)
        assertNull(quickHubNeighbourTab(noFriends, QuickHubTab.GROUP_WATCH, 1))
        assertNull(quickHubNeighbourTab(noFriends, QuickHubTab.FRIENDS, -1))
    }

    @Test
    fun onlyTheSelectedPillKeepsFocus() {
        assertFalse(quickHubPillHandsBack(QuickHubTab.GROUP_WATCH, QuickHubTab.GROUP_WATCH))
        assertTrue(quickHubPillHandsBack(QuickHubTab.NOW_PLAYING, QuickHubTab.GROUP_WATCH))
    }

    @Test
    fun removalFocusesNextThenPreviousThenNothing() {
        val keys = listOf("a", "b", "c")
        assertEquals("b", quickHubFocusAfterRemoval(keys, "a"))
        assertEquals("c", quickHubFocusAfterRemoval(keys, "b"))
        assertEquals("b", quickHubFocusAfterRemoval(keys, "c"))
        assertNull(quickHubFocusAfterRemoval(listOf("a"), "a"))
        assertNull(quickHubFocusAfterRemoval(emptyList<String>(), "a"))
        assertNull(quickHubFocusAfterRemoval(keys, "gone"))
    }

    @Test
    fun recoveryTriesTheBodyBeforeTheSelectedPill() {
        assertEquals(QuickHubRecoveryTarget.BODY, quickHubRecoveryTarget(0))
        assertEquals(QuickHubRecoveryTarget.BODY, quickHubRecoveryTarget(QUICK_HUB_RECOVERY_BODY_ATTEMPTS - 1))
        assertEquals(QuickHubRecoveryTarget.SELECTED_PILL, quickHubRecoveryTarget(QUICK_HUB_RECOVERY_BODY_ATTEMPTS))
        assertEquals(QuickHubRecoveryTarget.SELECTED_PILL, quickHubRecoveryTarget(QUICK_HUB_RECOVERY_ATTEMPTS - 1))
        assertTrue(QUICK_HUB_RECOVERY_ATTEMPTS > QUICK_HUB_RECOVERY_BODY_ATTEMPTS)
    }

    @Test
    fun recoveryLimiterStopsARunawayLoopAndReopensAfterTheWindow() {
        val limiter = QuickHubRecoveryLimiter(maxRuns = 3, windowMs = 1_000)
        assertTrue(limiter.tryAcquire(0))
        assertTrue(limiter.tryAcquire(100))
        assertTrue(limiter.tryAcquire(200))
        assertFalse(limiter.tryAcquire(300))
        assertFalse(limiter.tryAcquire(999))
        assertTrue(limiter.tryAcquire(1_000))
    }
}
