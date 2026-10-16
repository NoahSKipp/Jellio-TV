package com.jellio.tv.data

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.home.posterImageCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailFieldsTest {
    @Test
    fun titleInfoNeverAsksForStreams() {
        for (fields in listOf(DETAIL_FIELDS, PLAYER_DETAIL_FIELDS)) {
            assertFalse(fields, fields.contains("MediaSources"))
            assertFalse(fields, fields.contains("MediaStreams"))
        }
    }

    @Test
    fun posterImageCandidatesStepsThroughFallbacks() {
        val item = BaseItemDto(
            Id = "item-1",
            ImageTags = mapOf("Primary" to "ptag", "Thumb" to "ttag"),
            BackdropImageTags = listOf("btag"),
            SeriesId = "series-1",
            SeriesPrimaryImageTag = "sptag",
        )
        val urls = posterImageCandidates(item, imageUrl = { dto, type, width ->
            val tag = if (type == "Backdrop") dto.BackdropImageTags?.firstOrNull() else dto.ImageTags?.get(type)
            if (tag != null) "https://srv/Items/${dto.Id}/Images/$type?maxWidth=$width&tag=$tag"
            else "https://srv/Items/${dto.Id}/Images/$type?maxWidth=$width"
        })
        assertEquals("https://srv/Items/item-1/Images/Primary?maxWidth=400&tag=ptag", urls[0])
        assertEquals("https://srv/Items/item-1/Images/Primary?maxWidth=400", urls[1])
        assertEquals("https://srv/Items/series-1/Images/Primary?maxWidth=400&tag=sptag", urls[2])
        assertEquals("https://srv/Items/series-1/Images/Primary?maxWidth=400", urls[3])
        assertTrue(urls.contains("https://srv/Items/item-1/Images/Thumb?maxWidth=400&tag=ttag"))
        assertTrue(urls.contains("https://srv/Items/item-1/Images/Backdrop?maxWidth=400&tag=btag"))
    }
}
