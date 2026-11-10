package com.jellio.tv.data.recommend

import com.jellio.tv.data.model.BaseItemDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TopPicksPaginationTest {

    @Test
    fun topPicksRowIncludesFetchPageWhenAvailable() = runBlocking {
        val history = listOf(
            BaseItemDto(Id = "h1", Name = "History Movie 1", Genres = listOf("Sci-Fi")),
            BaseItemDto(Id = "h2", Name = "History Movie 2", Genres = listOf("Sci-Fi")),
            BaseItemDto(Id = "h3", Name = "History Movie 3", Genres = listOf("Sci-Fi")),
        )
        val initialItems = listOf(
            BaseItemDto(Id = "item1", Name = "Movie 1", ProductionYear = 2020),
            BaseItemDto(Id = "item2", Name = "Movie 2", ProductionYear = 2021),
        )
        val pagedItems = listOf(
            BaseItemDto(Id = "item3", Name = "Movie 3", ProductionYear = 2022),
            BaseItemDto(Id = "item4", Name = "Movie 4", ProductionYear = 2023),
        )

        var pagedRequestedStart = -1
        var pagedRequestedLimit = -1
        var pagedRequestedGenre = ""

        val source = RecommendationDataSource(
            getRecentlyCompleted = { history },
            getNextUp = { emptyList() },
            getRecommendationCandidates = { _, _ -> emptyList() },
            getGenreItems = { genre, _ -> if (genre == "Sci-Fi") initialItems else emptyList() },
            getGenreItemsPage = { genre, start, limit ->
                pagedRequestedGenre = genre
                pagedRequestedStart = start
                pagedRequestedLimit = limit
                pagedItems
            },
        )

        val exclude = mutableSetOf<String>()
        val sections = buildRecommendationRows(source, exclude)
        val topPicksSection = sections.firstOrNull { it.title == com.jellio.tv.data.i18n.Strings.get("home_top_picks") }

        assertNotNull(topPicksSection)
        assertEquals(2, topPicksSection!!.items.size)
        assertNotNull(topPicksSection.fetchPage)

        val fetched = topPicksSection.fetchPage!!.invoke(2, 20)
        assertEquals(2, fetched.size)
        assertEquals("Sci-Fi", pagedRequestedGenre)
        assertEquals(2, pagedRequestedStart)
        assertEquals(20, pagedRequestedLimit)
        assertEquals(listOf("item3", "item4"), fetched.map { it.Id })
    }

    @Test
    fun deduplicatesItemsByIdAndTitleYear() {
        val currentItems = mutableListOf(
            BaseItemDto(Id = "1", Name = "Inception", ProductionYear = 2010),
            BaseItemDto(Id = "2", Name = "Interstellar", ProductionYear = 2014),
        )
        val newItems = listOf(
            BaseItemDto(Id = "1", Name = "Inception", ProductionYear = 2010),
            BaseItemDto(Id = "3", Name = "Inception ", ProductionYear = 2010),
            BaseItemDto(Id = "4", Name = "Tenet", ProductionYear = 2020),
        )

        val existingIds = currentItems.map { it.Id }.toHashSet()
        val existingKeys = currentItems.map { titleKey(it) }.toHashSet()
        val toAdd = newItems.filter { item ->
            existingIds.add(item.Id) && existingKeys.add(titleKey(item))
        }

        assertEquals(1, toAdd.size)
        assertEquals("4", toAdd.first().Id)
    }
}
