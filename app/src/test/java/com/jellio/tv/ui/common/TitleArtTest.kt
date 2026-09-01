package com.jellio.tv.ui.common

import com.jellio.tv.data.model.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleArtTest {
    private val url = { id: String, tag: String, type: String, width: Int -> "$id/$type/$tag/$width" }

    @Test
    fun movieWithBackdropsTriesEachBackdropThenThumbThenBlurredPoster() {
        val movie = BaseItemDto(
            Id = "m",
            Type = "Movie",
            BackdropImageTags = listOf("b0", "b1"),
            ImageTags = mapOf("Primary" to "p", "Thumb" to "t", "Logo" to "l"),
        )
        val art = titleArtCandidates(movie, url)
        assertEquals(
            listOf("m/Backdrop/b0/1920", "m/Backdrop/1/b1/1920", "m/Thumb/t/1920", "m/Primary/p/$BLURRED_POSTER_WIDTH"),
            art.map { it.url },
        )
        assertEquals(listOf(false, false, false, true), art.map { it.blurred })
    }

    @Test
    fun movieWithoutBackdropFallsBackToBlurredPoster() {
        val movie = BaseItemDto(Id = "m", Type = "Movie", ImageTags = mapOf("Primary" to "p"))
        assertEquals(listOf(ArtCandidate("m/Primary/p/$BLURRED_POSTER_WIDTH", true)), titleArtCandidates(movie, url))
    }

    @Test
    fun itemWithNoArtGivesAnEmptyList() {
        assertTrue(titleArtCandidates(BaseItemDto(Id = "m", Type = "Movie"), url).isEmpty())
    }

    @Test
    fun episodeUsesSeriesBackdropThenItsStillThenSeriesPoster() {
        val episode = BaseItemDto(
            Id = "e",
            Type = "Episode",
            SeriesId = "s",
            SeriesPrimaryImageTag = "sp",
            ParentBackdropItemId = "s",
            ParentBackdropImageTags = listOf("sb"),
            ImageTags = mapOf("Primary" to "still"),
        )
        assertEquals(
            listOf("s/Backdrop/sb/1920", "e/Primary/still/1920", "s/Primary/sp/$BLURRED_POSTER_WIDTH"),
            titleArtCandidates(episode, url).map { it.url },
        )
    }

    @Test
    fun seasonBackdropComesBeforeTheSeries() {
        val series = BaseItemDto(Id = "s", Type = "Series", BackdropImageTags = listOf("sb"))
        val season = BaseItemDto(Id = "se", Type = "Season", BackdropImageTags = listOf("seb"))
        assertEquals(
            listOf("se/Backdrop/seb/1920", "s/Backdrop/sb/1920"),
            titleArtCandidates(series, url, season = season).map { it.url },
        )
    }

    @Test
    fun seriesFirstPutsTheShowsThumbBeforeTheEpisodeStill() {
        val episode = BaseItemDto(
            Id = "e",
            Type = "Episode",
            ParentThumbItemId = "s",
            ParentThumbImageTag = "st",
            ImageTags = mapOf("Primary" to "still"),
        )
        assertEquals(
            listOf("s/Thumb/st/1600", "e/Primary/still/1600"),
            titleArtCandidates(episode, url, seriesFirst = true, backdropWidth = 1600).map { it.url },
        )
    }

    @Test
    fun logoPrefersItsOwnThenTheShows() {
        val episode = BaseItemDto(Id = "e", ParentLogoItemId = "s", ParentLogoImageTag = "sl", ImageTags = mapOf("Logo" to "l"))
        assertEquals(listOf("e/Logo/l/800", "s/Logo/sl/800"), titleLogoCandidates(episode, url))
    }
}
