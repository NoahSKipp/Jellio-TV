package com.jellio.tv

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.ui.common.episodeReleaseLabel
import com.jellio.tv.ui.common.filterReleasedItems
import com.jellio.tv.ui.common.isUnreleasedItem
import com.jellio.tv.ui.common.seasonEpisodeReleaseLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ReleaseDatesTest {
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
    fun todayDateIsReleased() {
        val item = BaseItemDto(Id = "e2", PremiereDate = "2026-05-15T00:00:00Z")
        assertFalse(isUnreleasedItem(item, nowMs))
        assertNull(episodeReleaseLabel(item, nowMs, Locale.US))
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

        val result = filterReleasedItems(listOf(past, today, tomorrow, sentinel, playedFuture), nowMs)
        assertEquals(listOf("p", "t", "pf"), result.map { it.Id })
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
                "ep3" to "In 5 days",
                "ep4" to "May 27",
                "ep5" to "Coming soon",
            ),
            labels,
        )
    }
}
