package com.jellio.tv.ui.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewNotificationsLogicTest {
    private val sig = notificationSignature(listOf("a", "b"), listOf("g"))

    @Test fun badgeCountAddsInvites() {
        assertEquals(3, notificationBadgeCount(2, 1))
        assertEquals(0, notificationBadgeCount(0, 0))
        assertEquals(2, notificationBadgeCount(-1, 2))
    }

    @Test fun showsWhenNewAndFree() {
        assertEquals(PillDecision.SHOW, decidePill(3, sig, null, immersive = false, toastShowing = false))
    }

    @Test fun skipsWhenNothingUnread() {
        assertEquals(PillDecision.SKIP, decidePill(0, emptySet(), null, false, false))
    }

    @Test fun skipsWhenAlreadyAnnounced() {
        assertEquals(PillDecision.SKIP, decidePill(3, sig, sig, false, false))
    }

    @Test fun showsAgainWhenSetChanges() {
        val next = notificationSignature(listOf("a", "b", "c"), listOf("g"))
        assertEquals(PillDecision.SHOW, decidePill(4, next, sig, false, false))
    }

    @Test fun skipsInPlayerAndWaitsForToast() {
        assertEquals(PillDecision.SKIP, decidePill(3, sig, null, immersive = true, toastShowing = false))
        assertEquals(PillDecision.WAIT, decidePill(3, sig, null, immersive = false, toastShowing = true))
    }

    @Test fun reopensOnlyAfterLongAway() {
        assertFalse(reopensAfterBackground(null, 1_000_000))
        assertFalse(reopensAfterBackground(0, PILL_AWAY_MS))
        assertTrue(reopensAfterBackground(0, PILL_AWAY_MS + 1))
    }

    @Test fun textKeyFollowsCount() {
        assertEquals("new_notifications_pill_one", pillTextKey(1))
        assertEquals("new_notifications_pill_many", pillTextKey(3))
    }
}
