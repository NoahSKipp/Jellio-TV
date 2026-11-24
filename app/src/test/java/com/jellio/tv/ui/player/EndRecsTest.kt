package com.jellio.tv.ui.player

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.SuggestionRowDto
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

    private fun tmdbItem(id: String, tmdb: String) = BaseItemDto(Id = id, ProviderIds = mapOf("Tmdb" to tmdb))

    @Test fun tmdbRowIsPrimaryAndJellyfinOnlyFallsBack() {
        val tmdb = listOf(item("t1"), item("t2"))
        val jf = listOf(item("j1"))
        assertEquals(listOf("t1", "t2"), chooseRecommendations(tmdb, jf, emptySet()).map { it.Id })
        assertEquals(listOf("j1"), chooseRecommendations(emptyList(), jf, emptySet()).map { it.Id })
        assertEquals(listOf("j1"), chooseRecommendations(listOf(item("t1", played = true)), jf, emptySet()).map { it.Id })
    }

    @Test fun excludesTheTitleByTmdbIdAndCapsAtTwelve() {
        val row = listOf(tmdbItem("v1", "10"), tmdbItem("v2", "11")) + (1..20).map { item("i$it") }
        val kept = chooseRecommendations(row, emptyList(), emptySet(), excludeTmdbId = "10")
        assertEquals(12, kept.size)
        assertEquals("v2", kept.first().Id)
    }

    @Test fun picksTheSimilarRowAndMediaType() {
        val rows = listOf(SuggestionRowDto("genre", null, listOf(item("g"))), SuggestionRowDto("Similar", null, listOf(item("s"))))
        assertEquals(listOf("s"), similarRowItems(rows).map { it.Id })
        assertEquals(emptyList<BaseItemDto>(), similarRowItems(emptyList()))
        assertEquals("tv", recommendationMediaType("Episode"))
        assertEquals("tv", recommendationMediaType("Series"))
        assertEquals("movie", recommendationMediaType("Movie"))
        assertEquals("5", tmdbIdOf(tmdbItem("a", "5")))
        assertNull(tmdbIdOf(item("a")))
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
