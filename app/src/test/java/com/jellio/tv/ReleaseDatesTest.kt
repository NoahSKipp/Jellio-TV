package com.jellio.tv

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.ui.common.clearVerifiedTodayStreamIds
import com.jellio.tv.ui.common.episodeReleaseLabel
import com.jellio.tv.ui.common.filterReleasedItems
import com.jellio.tv.ui.common.isPendingTodayRelease
import com.jellio.tv.ui.common.isReleasedToday
import com.jellio.tv.ui.common.isTodayItemStreamVerified
import com.jellio.tv.ui.common.isUnreleasedByDate
import com.jellio.tv.ui.common.isUnreleasedItem
import com.jellio.tv.ui.common.markTodayItemHasStream
import com.jellio.tv.ui.common.seasonEpisodeReleaseLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ReleaseDatesTest {
    @Before
    fun setUp() {
        clearVerifiedTodayStreamIds()
    }

    private fun utcMs(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private val nowMs = utcMs(2026, 5, 15, 12, 0)

    @Test
    fun pastDateIsReleased() {
        val item = BaseItemDto(Id = "e1", PremiereDate = "2026-05-10T14:00:00.0000000Z")
        assertFalse(isUnreleasedItem(item, nowMs))
        assertNull(episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun todayDateWithVerifiedStreamIsReleased() {
        val item = BaseItemDto(Id = "e2", PremiereDate = "2026-05-15T00:00:00Z")
        markTodayItemHasStream(item.Id, true)
        assertFalse(isUnreleasedItem(item, nowMs))
        assertNull(episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun todayDateWithoutStreamIsPendingRelease() {
        val item = BaseItemDto(Id = "e2-pending", PremiereDate = "2026-05-15T00:00:00Z")
        assertTrue(isUnreleasedItem(item, nowMs))
        assertTrue(isReleasedToday(item, nowMs))
        assertTrue(isPendingTodayRelease(item, nowMs))
        assertEquals("Today", episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun futureAirTimeLaterTodayIsUnreleasedAndLabeledToday() {
        val now = utcMs(2026, 4, 10, 12, 0)
        val item = BaseItemDto(Id = "ep-later", PremiereDate = "2026-04-10T20:00:00.000Z")
        assertTrue(isUnreleasedItem(item, now))
        assertEquals("Today", episodeReleaseLabel(item, now, Locale.US))
    }

    @Test
    fun futureAirTimeLaterTodayWithTimezoneOffsetIsUnreleasedAndLabeledToday() {
        val now = utcMs(2026, 4, 10, 12, 0)
        // 22:00 +02:00 is 20:00:00 UTC, which is later than 12:00:00 UTC
        val item = BaseItemDto(Id = "ep-later-tz", PremiereDate = "2026-04-10T22:00:00+02:00")
        assertTrue(isUnreleasedItem(item, now))
        assertEquals("Today", episodeReleaseLabel(item, now, Locale.US))
    }

    @Test
    fun pastAirTimeEarlierTodayIsReleasedItem() {
        val now = utcMs(2026, 4, 10, 12, 0)
        val item = BaseItemDto(Id = "ep-earlier", PremiereDate = "2026-04-10T08:00:00.000Z")
        assertTrue(isUnreleasedItem(item, now))
        assertFalse(isUnreleasedByDate(item, now))
        assertTrue(isReleasedToday(item, now))
        assertTrue(isPendingTodayRelease(item, now))
        assertEquals("Today", episodeReleaseLabel(item, now, Locale.US))

        markTodayItemHasStream("ep-earlier", true)
        assertFalse(isUnreleasedItem(item, now))
        assertFalse(isPendingTodayRelease(item, now))
        assertNull(episodeReleaseLabel(item, now, Locale.US))
    }

    @Test
    fun playedTodayEpisodeIsNotPendingAndHasNoReleaseLabel() {
        val now = utcMs(2026, 4, 10, 12, 0)
        val item = BaseItemDto(
            Id = "ep-played-today",
            PremiereDate = "2026-04-10T20:00:00.000Z",
            UserData = UserItemDataDto(Played = true),
        )
        assertFalse(isUnreleasedItem(item, now))
        assertFalse(isReleasedToday(item, now))
        assertFalse(isPendingTodayRelease(item, now))
        assertNull(episodeReleaseLabel(item, now, Locale.US))
    }

    @Test
    fun inProgressTodayEpisodeIsNotPendingAndHasNoReleaseLabel() {
        val now = utcMs(2026, 4, 10, 12, 0)
        val item = BaseItemDto(
            Id = "ep-prog-today",
            PremiereDate = "2026-04-10T20:00:00.000Z",
            UserData = UserItemDataDto(PlaybackPositionTicks = 120_000_000L),
        )
        assertFalse(isUnreleasedItem(item, now))
        assertFalse(isReleasedToday(item, now))
        assertFalse(isPendingTodayRelease(item, now))
        assertNull(episodeReleaseLabel(item, now, Locale.US))
    }

    @Test
    fun markTodayItemHasStreamCanBeRevoked() {
        markTodayItemHasStream("ep-test", true)
        assertTrue(isTodayItemStreamVerified("ep-test"))
        markTodayItemHasStream("ep-test", false)
        assertFalse(isTodayItemStreamVerified("ep-test"))
    }

    @Test
    fun missingPremiereDateWithTodayEndDateIsDetectedAsToday() {
        val now = utcMs(2026, 4, 10, 12, 0)
        val item = BaseItemDto(Id = "ep-end-today", PremiereDate = null, EndDate = "2026-04-10T00:00:00Z")
        assertTrue(isUnreleasedItem(item, now))
        assertTrue(isReleasedToday(item, now))
        assertTrue(isPendingTodayRelease(item, now))
    }

    @Test
    fun todayReleaseWithoutStreamIsPendingAndLabeledToday() {
        val now = utcMs(2026, 4, 10, 12, 0)
        val item = BaseItemDto(Id = "ep-today", PremiereDate = "2026-04-10T00:00:00.000Z")
        assertTrue(isUnreleasedItem(item, now))
        assertTrue(isReleasedToday(item, now))
        assertTrue(isPendingTodayRelease(item, now))
        assertEquals("Today", episodeReleaseLabel(item, now, Locale.US))
        assertEquals(
            mapOf("ep-today" to "Today"),
            seasonEpisodeReleaseLabels(listOf(item), now, Locale.US),
        )
    }

    @Test
    fun todayReleaseWithVerifiedStreamIsReadyAndUnlabeled() {
        val now = utcMs(2026, 4, 10, 12, 0)
        val item = BaseItemDto(Id = "ep-today-verified", PremiereDate = "2026-04-10T00:00:00.000Z")
        markTodayItemHasStream(item.Id, true)
        assertFalse(isUnreleasedItem(item, now))
        assertTrue(isReleasedToday(item, now))
        assertFalse(isPendingTodayRelease(item, now))
        assertNull(episodeReleaseLabel(item, now, Locale.US))
        assertEquals(
            emptyMap<String, String>(),
            seasonEpisodeReleaseLabels(listOf(item), now, Locale.US),
        )
    }

    @Test
    fun tomorrowDateIsUnreleasedAndLabeledTomorrow() {
        val item = BaseItemDto(Id = "e3", PremiereDate = "2026-05-16T21:00:00Z")
        assertTrue(isUnreleasedItem(item, nowMs))
        assertEquals("Tomorrow", episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun inFiveDaysIsUnreleasedAndLabeled() {
        val item = BaseItemDto(Id = "e4", PremiereDate = "2026-05-20T19:30:00.000Z")
        assertTrue(isUnreleasedItem(item, nowMs))
        assertEquals("In 5 days", episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun futureDateAtLeastSevenDaysUsesFormattedDate() {
        val item = BaseItemDto(Id = "e5", PremiereDate = "2026-05-25T00:00:00Z")
        assertTrue(isUnreleasedItem(item, nowMs))
        assertEquals("May 25", episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun yearSentinelIsUnreleasedAndComingSoon() {
        val item = BaseItemDto(Id = "e6", PremiereDate = "9999-01-01T00:00:00Z")
        assertTrue(isUnreleasedItem(item, nowMs))
        assertEquals("Coming soon", episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun missingPremiereDateWithSentinelEndDateIsUnreleased() {
        val item = BaseItemDto(Id = "e7", PremiereDate = null, EndDate = "9999-01-01T00:00:00Z")
        assertTrue(isUnreleasedItem(item, nowMs))
        assertEquals("Coming soon", episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun missingPremiereDateWithFutureEndDateIsUnreleased() {
        val item = BaseItemDto(Id = "e8", PremiereDate = null, EndDate = "2026-05-20T00:00:00Z")
        assertTrue(isUnreleasedItem(item, nowMs))
        assertEquals("Coming soon", episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun missingDatesIsReleased() {
        val item = BaseItemDto(Id = "e9", PremiereDate = null, EndDate = null)
        assertFalse(isUnreleasedItem(item, nowMs))
        assertNull(episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun playedItemIsNeverUnreleasedEvenWithFutureDate() {
        val item = BaseItemDto(
            Id = "e10",
            PremiereDate = "2026-05-20T00:00:00Z",
            UserData = UserItemDataDto(Played = true),
        )
        assertFalse(isUnreleasedItem(item, nowMs))
        assertNull(episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun itemWithPlaybackPositionIsNeverUnreleased() {
        val item = BaseItemDto(
            Id = "e11",
            PremiereDate = "2026-05-20T00:00:00Z",
            UserData = UserItemDataDto(PlaybackPositionTicks = 50_000_000L),
        )
        assertFalse(isUnreleasedItem(item, nowMs))
        assertNull(episodeReleaseLabel(item, nowMs, Locale.US))
    }

    @Test
    fun filterReleasedItemsRemovesUnreleasedItems() {
        val past = BaseItemDto(Id = "p", PremiereDate = "2026-05-10T00:00:00Z")
        val today = BaseItemDto(Id = "t", PremiereDate = "2026-05-15T00:00:00Z")
        val tomorrow = BaseItemDto(Id = "tom", PremiereDate = "2026-05-16T00:00:00Z")
        val sentinel = BaseItemDto(Id = "s", PremiereDate = "9999-01-01T00:00:00Z")
        val playedFuture = BaseItemDto(
            Id = "pf",
            PremiereDate = "2026-05-20T00:00:00Z",
            UserData = UserItemDataDto(Played = true),
        )

        val all = listOf(past, today, tomorrow, sentinel, playedFuture)
        assertEquals(listOf("p", "pf"), filterReleasedItems(all, nowMs).map { it.Id })
        markTodayItemHasStream("t", true)
        assertEquals(listOf("p", "t", "pf"), filterReleasedItems(all, nowMs).map { it.Id })
    }

    @Test
    fun seasonEpisodeReleaseLabelsHandlesAiringShowWithTrailingEpisodes() {
        val ep1 = BaseItemDto(
            Id = "ep1",
            PremiereDate = "2026-05-01T00:00:00Z",
            UserData = UserItemDataDto(Played = true),
            RunTimeTicks = 30_000_000_000L,
        )
        val ep2 = BaseItemDto(
            Id = "ep2",
            PremiereDate = "2026-05-15T00:00:00Z",
            UserData = UserItemDataDto(Played = false),
            RunTimeTicks = 30_000_000_000L,
        )
        val ep3 = BaseItemDto(
            Id = "ep3",
            PremiereDate = "2026-05-20T00:00:00Z",
            UserData = UserItemDataDto(Played = false),
            RunTimeTicks = 0L,
        )
        val ep4 = BaseItemDto(
            Id = "ep4",
            PremiereDate = "2026-05-27T00:00:00Z",
            UserData = UserItemDataDto(Played = false),
            RunTimeTicks = 0L,
        )
        val ep5TrailingUnannounced = BaseItemDto(
            Id = "ep5",
            PremiereDate = null,
            EndDate = null,
            UserData = UserItemDataDto(Played = false),
            RunTimeTicks = 0L,
        )

        val labels = seasonEpisodeReleaseLabels(listOf(ep1, ep2, ep3, ep4, ep5TrailingUnannounced), nowMs, Locale.US)
        assertEquals(
            mapOf(
                "ep2" to "Today",
                "ep3" to "In 5 days",
                "ep4" to "May 27",
                "ep5" to "Coming soon",
            ),
            labels,
        )

        markTodayItemHasStream("ep2", true)
        val verifiedLabels = seasonEpisodeReleaseLabels(listOf(ep1, ep2, ep3, ep4, ep5TrailingUnannounced), nowMs, Locale.US)
        assertEquals(
            mapOf(
                "ep3" to "In 5 days",
                "ep4" to "May 27",
                "ep5" to "Coming soon",
            ),
            verifiedLabels,
        )
    }

    // Ported from tests/frontend/releaseDate.test.mjs.
    private val webNow = utcMs(2026, 11, 5, 12, 0)

    @Test
    fun webTodayEpisodeFollowsItsStreamCheck() {
        val item = BaseItemDto(Id = "ep-today-past-airtime", PremiereDate = "2026-11-05T00:00:00Z")
        assertTrue(isUnreleasedItem(item, webNow))
        assertEquals("Today", episodeReleaseLabel(item, webNow, Locale.US))

        markTodayItemHasStream(item.Id, true)
        assertFalse(isUnreleasedItem(item, webNow))
        assertNull(episodeReleaseLabel(item, webNow, Locale.US))

        markTodayItemHasStream(item.Id, false)
        assertTrue(isUnreleasedItem(item, webNow))
        assertEquals("Today", episodeReleaseLabel(item, webNow, Locale.US))
    }

    @Test
    fun webIsReleasedTodayCases() {
        assertFalse(isReleasedToday(BaseItemDto(Id = "a", PremiereDate = "2026-11-05T00:00:00Z", UserData = UserItemDataDto(Played = true)), webNow))
        assertFalse(isReleasedToday(BaseItemDto(Id = "b", PremiereDate = "2026-11-05T00:00:00Z", UserData = UserItemDataDto(PlaybackPositionTicks = 5000)), webNow))
        assertTrue(isReleasedToday(BaseItemDto(Id = "c", PremiereDate = "2026-11-05T20:00:00Z"), webNow))
        assertTrue(isReleasedToday(BaseItemDto(Id = "d", EndDate = "2026-11-05T10:00:00Z"), webNow))
        assertFalse(isReleasedToday(BaseItemDto(Id = "e", PremiereDate = "2026-11-06T00:00:00Z"), webNow))
        assertFalse(isReleasedToday(BaseItemDto(Id = "f", PremiereDate = "2026-11-04T23:59:59Z"), webNow))
        assertFalse(isReleasedToday(BaseItemDto(Id = "g", PremiereDate = "9999-11-05T00:00:00Z"), webNow))
    }

    @Test
    fun webReleasedEpisodeWithoutPremiereDate() {
        val item = BaseItemDto(Id = "ep-normal", RunTimeTicks = 25_000_000_000L, LocationType = "FileSystem")
        assertFalse(isUnreleasedItem(item, webNow))
        assertNull(episodeReleaseLabel(item, webNow, Locale.US))
    }

    @Test
    fun webVirtualItemWithoutRuntimeIsUnreleased() {
        val item = BaseItemDto(Id = "ep-virtual", LocationType = "Virtual")
        assertTrue(isUnreleasedItem(item, webNow))
        assertEquals("Coming soon", episodeReleaseLabel(item, webNow, Locale.US))
        assertFalse(isUnreleasedItem(item.copy(RunTimeTicks = 1200L), webNow))
    }

    @Test
    fun aMovieDatedTodayIsNotHeldBackByTheStreamCheck() {
        val movie = BaseItemDto(Id = "m-today", Type = "Movie", PremiereDate = "2026-11-05T00:00:00Z")
        assertFalse(isUnreleasedItem(movie, webNow))
    }

    @Test
    fun webFilterReleasedItemsDropsUnverifiedTodayEpisodes() {
        val items = listOf(
            BaseItemDto(Id = "ep-1", PremiereDate = "2026-10-01T00:00:00Z"),
            BaseItemDto(Id = "ep-2", PremiereDate = "2026-11-05T00:00:00Z"),
            BaseItemDto(Id = "ep-3", PremiereDate = "2026-11-06T00:00:00Z"),
            BaseItemDto(Id = "ep-4", PremiereDate = "2026-11-12T00:00:00Z"),
            BaseItemDto(Id = "ep-5", EndDate = "9999-01-01T00:00:00Z"),
            BaseItemDto(Id = "ep-6", LocationType = "Virtual"),
        )
        assertEquals(listOf("ep-1"), filterReleasedItems(items, webNow).map { it.Id })
        markTodayItemHasStream("ep-2", true)
        assertEquals(listOf("ep-1", "ep-2"), filterReleasedItems(items, webNow).map { it.Id })
    }

    @Test
    fun webSeasonLabelsForUnverifiedTodayAndTrailingEpisodes() {
        val episodes = listOf(
            BaseItemDto(Id = "ep-1", IndexNumber = 1, PremiereDate = "2026-10-25T00:00:00Z", RunTimeTicks = 1000),
            BaseItemDto(Id = "ep-2", IndexNumber = 2, PremiereDate = "2026-11-05T00:00:00Z", RunTimeTicks = 1000),
            BaseItemDto(Id = "ep-3", IndexNumber = 3, PremiereDate = "2026-11-06T00:00:00Z"),
            BaseItemDto(Id = "ep-4", IndexNumber = 4),
            BaseItemDto(Id = "ep-5", IndexNumber = 5),
            BaseItemDto(Id = "ep-6", IndexNumber = 6, RunTimeTicks = 1200),
            BaseItemDto(Id = "ep-7", IndexNumber = 7, UserData = UserItemDataDto(Played = true)),
        )
        val unverified = seasonEpisodeReleaseLabels(episodes, webNow, Locale.US)
        assertNull(unverified["ep-1"])
        assertEquals("Today", unverified["ep-2"])
        assertEquals("Tomorrow", unverified["ep-3"])
        assertEquals("Coming soon", unverified["ep-4"])

        markTodayItemHasStream("ep-2", true)
        assertEquals(
            mapOf("ep-3" to "Tomorrow", "ep-4" to "Coming soon", "ep-5" to "Coming soon"),
            seasonEpisodeReleaseLabels(episodes, webNow, Locale.US),
        )
    }
}
