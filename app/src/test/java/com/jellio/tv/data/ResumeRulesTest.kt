package com.jellio.tv.data

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.ui.home.withoutStaleEpisodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRulesTest {
    private val runtime = 36_000_000_000L // one hour

    private fun episode(id: String, season: Int, number: Int, played: Boolean, position: Long, series: String = "show") =
        BaseItemDto(
            Id = id,
            Type = "Episode",
            SeriesId = series,
            ParentIndexNumber = season,
            IndexNumber = number,
            RunTimeTicks = runtime,
            UserData = UserItemDataDto(Played = played, PlaybackPositionTicks = position),
        )

    @Test
    fun playedWithAPartPositionCountsAsInProgress() {
        val item = episode("e22x7", 22, 7, played = true, position = runtime * 27 / 100)
        assertTrue(isPartlyWatched(item))
        assertFalse(isFinished(item))
    }

    @Test
    fun playedNearTheEndStaysFinished() {
        val item = episode("e3", 1, 3, played = true, position = runtime * 99 / 100)
        assertFalse(isPartlyWatched(item))
        assertTrue(isFinished(item))
    }

    @Test
    fun playedWithoutAPositionIsFinished() {
        assertTrue(isFinished(episode("e1", 1, 1, played = true, position = 0L)))
    }

    @Test
    fun unknownRuntimeTrustsTheSavedPosition() {
        val item = episode("e3", 1, 3, played = true, position = 1_000L).copy(RunTimeTicks = null)
        assertTrue(isPartlyWatched(item))
    }

    @Test
    fun continueWatchingKeepsARewatchedEpisode() {
        val kept = listOf(episode("e22x7", 22, 7, played = true, position = runtime * 27 / 100)).withoutStaleEpisodes(emptyList())
        assertEquals(listOf("e22x7"), kept.map { it.Id })
    }

    @Test
    fun continueWatchingStillDropsAFinishedEpisodeBehindNextUp() {
        val finished = episode("e3", 1, 3, played = true, position = runtime * 99 / 100)
        val leftover = episode("e2", 1, 2, played = false, position = runtime / 2)
        val next = episode("e4", 1, 4, played = false, position = 0L)
        assertTrue(listOf(finished, leftover).withoutStaleEpisodes(listOf(next)).isEmpty())
    }

    @Test
    fun continueWatchingKeepsAnUnplayedEpisodeAheadOfNextUp() {
        val ahead = episode("e5", 1, 5, played = false, position = runtime / 4)
        val next = episode("e4", 1, 4, played = false, position = 0L)
        assertEquals(listOf("e5"), listOf(ahead).withoutStaleEpisodes(listOf(next)).map { it.Id })
    }

    @Test
    fun recentlyPlayedComesFirstAndUndatedLast() {
        fun dated(id: String, date: String?) =
            BaseItemDto(Id = id, UserData = UserItemDataDto(LastPlayedDate = date))
        val sorted = listOf(dated("old", "2026-11-01T10:00:00.0000000Z"), dated("none", null), dated("new", "2026-11-22T20:00:00.0000000Z"))
            .recentlyPlayedFirst()
        assertEquals(listOf("new", "old", "none"), sorted.map { it.Id })
    }
}
