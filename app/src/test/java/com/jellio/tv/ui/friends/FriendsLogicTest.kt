package com.jellio.tv.ui.friends

import com.jellio.tv.data.FriendFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsLogicTest {
    @Test
    fun statusMapsToButton() {
        assertEquals(FriendButtonState.Add, buttonStateOf(relationOf("none"), true))
        assertEquals(FriendButtonState.Hidden, buttonStateOf(relationOf("none"), false))
        assertEquals(FriendButtonState.RequestSent, buttonStateOf(relationOf("outgoing"), false))
        assertEquals(FriendButtonState.Respond, buttonStateOf(relationOf("incoming"), false))
        assertEquals(FriendButtonState.Friends, buttonStateOf(relationOf("friends"), false))
        for (s in listOf("self", "private", "closed", "weird", null)) {
            assertEquals(FriendButtonState.Hidden, buttonStateOf(relationOf(s), true))
        }
    }

    @Test
    fun resultsMapToKeys() {
        assertEquals("friends_result_added", addResultKey("added"))
        assertEquals("friends_result_already_on_list", addResultKey("already_on_list"))
        assertEquals("friends_result_not_visible", addResultKey("not_visible"))
        assertEquals("friends_result_failed", addResultKey("something_new"))
        assertEquals("friends_result_failed", addResultKey(null))
        assertTrue(isAddSuccess("added"))
        assertTrue(isAddSuccess("already_on_list"))
        assertFalse(isAddSuccess("muted"))
    }

    @Test
    fun failuresMapToKeys() {
        assertEquals("friends_err_cooldown", failureKey(FriendFailure.Cooldown))
        assertEquals("friends_err_gone", failureKey(FriendFailure.Gone))
        assertEquals(FriendFailure.entries.size, FriendFailure.entries.map { failureKey(it) }.toSet().size)
    }

    @Test
    fun watchlistNoticeCountAndTarget() {
        assertEquals(1, watchlistAddedCount(null))
        assertEquals(1, watchlistAddedCount("x"))
        assertEquals(1, watchlistAddedCount("0"))
        assertEquals(3, watchlistAddedCount(" 3 "))
        assertEquals(NoticeTarget.Item, watchlistNoticeTarget(1, true))
        assertEquals(NoticeTarget.None, watchlistNoticeTarget(1, false))
        assertEquals(NoticeTarget.Watchlist, watchlistNoticeTarget(3, true))
    }

    @Test
    fun kindsAndTypes() {
        assertTrue(isFriendKind("friend_request"))
        assertTrue(isFriendKind("watchlist_added"))
        assertFalse(isFriendKind("episode"))
        assertTrue(canShareType("Movie"))
        assertTrue(canShareType("Series"))
        assertFalse(canShareType("Episode"))
        assertTrue(badgeVisible(1))
        assertFalse(badgeVisible(0))
        assertTrue(nextMuted(false))
    }
}
