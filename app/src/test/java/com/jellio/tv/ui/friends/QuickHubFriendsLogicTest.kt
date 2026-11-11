package com.jellio.tv.ui.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class QuickHubFriendsLogicTest {
    @Test
    fun idsMatchWithoutDashesAndCase() {
        assertEquals("abc123", friendIdKey("ABC-123"))
        assertEquals("", friendIdKey(null))
        assertEquals(friendIdKey("0A1B2C3D-0000-1111-2222-333344445555"), friendIdKey("0a1b2c3d000011112222333344445555"))
    }

    @Test
    fun episodesShowTheirSeries() {
        assertEquals("Severance", watchingTitle("Episode", "Hello, Ms. Cobel", "Severance"))
        assertEquals("Alien", watchingTitle("Movie", " Alien ", null))
        assertEquals("Pilot", watchingTitle("Episode", "Pilot", ""))
        assertNull(watchingTitle("Movie", "  ", null))
        assertNull(watchingTitle(null, null, null))
    }

    @Test
    fun watchingMapKeysAreNormalizedAndFirstWins() {
        val map = watchingByUser(
            listOf(
                "AA-BB" to "First",
                "aabb" to "Second",
                null to "Nobody",
                "cc" to null,
                "dd" to "  ",
                "EE" to "Other",
            ),
        )
        assertEquals(mapOf("aabb" to "First", "ee" to "Other"), map)
    }

    @Test
    fun subtitlePrefersWatching() {
        assertEquals(FriendSubtitle.Watching("Dune"), friendSubtitle("Dune", "March 2024"))
        assertEquals(FriendSubtitle.Since("March 2024"), friendSubtitle(null, "March 2024"))
        assertEquals(FriendSubtitle.Since("March 2024"), friendSubtitle(" ", "March 2024"))
        assertEquals(FriendSubtitle.Empty, friendSubtitle(null, null))
        assertEquals(FriendSubtitle.Empty, friendSubtitle("", ""))
    }

    @Test
    fun monthYearFollowsTheLocale() {
        assertEquals("March 2024", formatMonthYear("2024-03-15T20:00:00.0000000Z", "LLLL yyyy", Locale.US, ZoneOffset.UTC))
        assertEquals("März 2024", formatMonthYear("2024-03-15T20:00:00Z", "LLLL yyyy", Locale.GERMAN, ZoneOffset.UTC))
        assertEquals("March 2024", formatMonthYear("2024-03-15", "LLLL yyyy", Locale.US, ZoneOffset.UTC))
        assertNull(formatMonthYear(null, "LLLL yyyy", Locale.US, ZoneOffset.UTC))
        assertNull(formatMonthYear("not a date", "LLLL yyyy", Locale.US, ZoneOffset.UTC))
        assertNull(formatMonthYear("2024-03-15", "not a pattern {", Locale.US, ZoneOffset.UTC))
    }

    @Test
    fun findOffersOnlyPeopleWhoCanBeAsked() {
        assertTrue(isFindable("none"))
        assertTrue(isFindable("outgoing"))
        assertFalse(isFindable("friends"))
        assertFalse(isFindable("incoming"))
        assertFalse(isFindable("self"))
        assertFalse(isFindable("private"))
        assertFalse(isFindable(null))
    }
}
