package com.jellio.tv.ui.player

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndRecsTest {
    private fun item(id: String, played: Boolean? = null) =
        BaseItemDto(Id = id, UserData = played?.let { UserItemDataDto(Played = it) })

    private fun rec(id: String, backdrop: String? = null, overview: String? = null) =
        EndRec(id, id, overview, null, backdrop, null)

    @Test fun episodesUseTheirSeries() {
        assertEquals("s1", recommendationSourceId("e1", "Episode", "s1"))
        assertEquals("e1", recommendationSourceId("e1", "Episode", null))
        assertEquals("m1", recommendationSourceId("m1", "Movie", "s1"))
    }

    @Test fun dropsWatchedRepeatsAndTheTitleJustWatched() {
        val items = listOf(item("a"), item("b", played = true), item("a"), item("c", played = false), item("self"), item("d"))
        val kept = keepRecommendations(items, setOf("self"))
        assertEquals(listOf("a", "c", "d"), kept.map { it.Id })
    }

    @Test fun keepsUnwatchedWhenDataMissingAndHonoursLimit() {
        val items = (1..20).map { item("i$it") }
        assertEquals(12, keepRecommendations(items, emptySet()).size)
        assertEquals(3, keepRecommendations(items, emptySet(), limit = 3).size)
    }

    @Test fun previewNeedsASettledCardWithSomethingToShow() {
        val recs = listOf(rec("a", backdrop = "u"), rec("b"), rec("c", overview = "text"))
        assertNull(previewRec(recs, null))
        assertEquals("a", previewRec(recs, 0)?.itemId)
        assertNull(previewRec(recs, 1))
        assertEquals("c", previewRec(recs, 2)?.itemId)
        assertNull(previewRec(recs, 9))
    }
}
