package com.jellio.tv.ui.home

import com.jellio.tv.data.model.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CardImagesTest {
    private val imageUrl = { dto: BaseItemDto, type: String, width: Int ->
        val tag = if (type == "Backdrop") dto.BackdropImageTags?.firstOrNull() else dto.ImageTags?.get(type)
        if (tag != null) "${dto.Id}/$type?tag=$tag" else "${dto.Id}/$type"
    }

    private val episode = BaseItemDto(
        Id = "ep",
        Type = "Episode",
        ImageTags = mapOf("Primary" to "still"),
        SeriesId = "show",
        SeriesPrimaryImageTag = "poster",
        ParentBackdropItemId = "show",
        ParentBackdropImageTags = listOf("backdrop"),
        ParentLogoItemId = "show",
        ParentLogoImageTag = "logo",
        ParentIndexNumber = 1,
        IndexNumber = 3,
    )

    @Test
    fun progressCardShowsTheShowPosterFirst() {
        assertEquals("show/Primary?tag=poster", posterImageCandidates(episode, imageUrl, seriesFirst = true).first())
        assertEquals("ep/Primary?tag=still", posterImageCandidates(episode, imageUrl).first())
    }

    @Test
    fun episodeBackdropUsesTheShowUnlessTheStillIsWanted() {
        assertEquals("show/Backdrop?tag=backdrop", posterBackdropCandidates(episode, imageUrl).first())
        assertEquals("ep/Primary?tag=still", posterBackdropCandidates(episode, imageUrl, episodeStill = true).first())
    }

    @Test
    fun showcaseUsesTheLoadedShowsBackdropAndLogo() {
        val show = BaseItemDto(Id = "show", ImageTags = mapOf("Logo" to "showlogo"), BackdropImageTags = listOf("showbd"))
        val bare = episode.copy(ParentBackdropItemId = null, ParentBackdropImageTags = null, ParentLogoItemId = null, ParentLogoImageTag = null)
        assertEquals("show/Backdrop?tag=showbd", posterBackdropCandidates(bare, imageUrl, series = show).first())
        assertEquals("show/Logo?tag=showlogo", posterLogoCandidates(bare, imageUrl, series = show).first())
    }

    @Test
    fun episodeLogoNeverTriesTheEpisodeItself() {
        val logos = posterLogoCandidates(episode, imageUrl)
        assertEquals("show/Logo?tag=logo", logos.first())
        assertFalse(logos.any { it.startsWith("ep/") })
    }

    @Test
    fun episodeLabelNeedsSeasonAndEpisode() {
        assertEquals("S1 E3", episodeLabel(episode))
        assertEquals("", episodeLabel(episode.copy(IndexNumber = null)))
        assertEquals("", episodeLabel(BaseItemDto(Id = "m", Type = "Movie")))
    }
}
