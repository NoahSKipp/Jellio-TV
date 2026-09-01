package com.jellio.tv.data

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistAutoRemoveTest {

    @Test
    fun movieWatched_eligibleForRemovalWhenFavorite() {
        val favoriteMovie = BaseItemDto(
            Id = "m1",
            Type = "Movie",
            UserData = UserItemDataDto(IsFavorite = true, Played = true),
        )
        assertTrue(isMovieWatchlistRemovalEligible(favoriteMovie))

        val nonFavoriteMovie = BaseItemDto(
            Id = "m2",
            Type = "Movie",
            UserData = UserItemDataDto(IsFavorite = false, Played = true),
        )
        assertFalse(isMovieWatchlistRemovalEligible(nonFavoriteMovie))
    }

    @Test
    fun continuingAiringSeries_keptOnWatchlistEvenIfAllEpisodesWatched() {
        val continuingSeries = BaseItemDto(
            Id = "s1",
            Type = "Series",
            Status = "Continuing",
            UserData = UserItemDataDto(IsFavorite = true),
        )
        val episodes = listOf(
            BaseItemDto(Id = "e1", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 1, UserData = UserItemDataDto(Played = true)),
            BaseItemDto(Id = "e2", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 2, UserData = UserItemDataDto(Played = true)),
        )
        assertFalse(isSeriesWatchlistRemovalEligible(continuingSeries, episodes))
    }

    @Test
    fun endedSeries_withUnwatchedRegularEpisodes_keptOnWatchlist() {
        val endedSeries = BaseItemDto(
            Id = "s2",
            Type = "Series",
            Status = "Ended",
            UserData = UserItemDataDto(IsFavorite = true),
        )
        val episodes = listOf(
            BaseItemDto(Id = "e1", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 1, UserData = UserItemDataDto(Played = true)),
            BaseItemDto(Id = "e2", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 2, UserData = UserItemDataDto(Played = false)),
        )
        assertFalse(isSeriesWatchlistRemovalEligible(endedSeries, episodes, watchedItemId = "e1"))
    }

    @Test
    fun endedSeries_withUnreleasedEpisodes_keptOnWatchlist() {
        val nowMs = 1700000000000L
        val endedSeries = BaseItemDto(
            Id = "s3",
            Type = "Series",
            Status = "Ended",
            UserData = UserItemDataDto(IsFavorite = true),
        )
        val episodes = listOf(
            BaseItemDto(Id = "e1", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 1, UserData = UserItemDataDto(Played = true)),
            BaseItemDto(Id = "e2", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 2, PremiereDate = "2099-01-01T00:00:00Z", UserData = UserItemDataDto(Played = false)),
        )
        assertFalse(isSeriesWatchlistRemovalEligible(endedSeries, episodes, nowMs = nowMs))
    }

    @Test
    fun endedSeries_withAllReleasedRegularEpisodesWatched_eligibleForRemoval() {
        val endedSeries = BaseItemDto(
            Id = "s4",
            Type = "Series",
            Status = "Ended",
            UserData = UserItemDataDto(IsFavorite = true),
        )
        val episodes = listOf(
            BaseItemDto(Id = "e1", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 1, UserData = UserItemDataDto(Played = true)),
            BaseItemDto(Id = "e2", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 2, UserData = UserItemDataDto(Played = true)),
        )
        assertTrue(isSeriesWatchlistRemovalEligible(endedSeries, episodes))
    }

    @Test
    fun endedSeries_currentlyWatchingFinalEpisode_eligibleForRemoval() {
        val endedSeries = BaseItemDto(
            Id = "s4",
            Type = "Series",
            Status = "Ended",
            UserData = UserItemDataDto(IsFavorite = true),
        )
        val episodes = listOf(
            BaseItemDto(Id = "e1", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 1, UserData = UserItemDataDto(Played = true)),
            BaseItemDto(Id = "e2", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 2, UserData = UserItemDataDto(Played = false)),
        )
        assertTrue(isSeriesWatchlistRemovalEligible(endedSeries, episodes, watchedItemId = "e2"))
    }

    @Test
    fun endedSeries_withUnwatchedSpecialsS00_stillEligibleForRemoval() {
        val endedSeries = BaseItemDto(
            Id = "s5",
            Type = "Series",
            Status = "Ended",
            UserData = UserItemDataDto(IsFavorite = true),
        )
        val episodes = listOf(
            BaseItemDto(Id = "sp1", Type = "Episode", ParentIndexNumber = 0, IndexNumber = 1, UserData = UserItemDataDto(Played = false)),
            BaseItemDto(Id = "e1", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 1, UserData = UserItemDataDto(Played = true)),
            BaseItemDto(Id = "e2", Type = "Episode", ParentIndexNumber = 1, IndexNumber = 2, UserData = UserItemDataDto(Played = true)),
        )
        assertTrue(isSeriesWatchlistRemovalEligible(endedSeries, episodes))
    }
}
