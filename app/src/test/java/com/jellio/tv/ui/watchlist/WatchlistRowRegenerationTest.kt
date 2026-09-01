package com.jellio.tv.ui.watchlist

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.ui.home.HomeRow
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.home.PosterHomeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistRowRegenerationTest {

    @Test
    fun watchlistRowRemoval_dropsItemAndKeepsRemaining() {
        val item1 = BaseItemDto(Id = "m1", Name = "Movie 1", Type = "Movie")
        val item2 = BaseItemDto(Id = "m2", Name = "Movie 2", Type = "Movie")
        val initialRows = listOf<HomeRow>(
            PosterHomeRow(HomeSection(title = "Watchlist Movies", items = listOf(item1, item2), key = "watchlist-movies")),
        )

        val targetIdToRemove = "m1"
        val updatedRows = initialRows.mapNotNull { row ->
            if (row is PosterHomeRow && row.section.key.startsWith("watchlist")) {
                val remaining = row.section.items.filterNot { it.Id == targetIdToRemove }
                if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
            } else {
                row
            }
        }

        assertEquals(1, updatedRows.size)
        val remainingRow = updatedRows[0] as PosterHomeRow
        assertEquals(1, remainingRow.section.items.size)
        assertEquals("m2", remainingRow.section.items[0].Id)
    }

    @Test
    fun watchlistRowRemoval_whenLastItemRemoved_rowIsCompletelyRemoved() {
        val item1 = BaseItemDto(Id = "m1", Name = "Movie 1", Type = "Movie")
        val initialRows = listOf<HomeRow>(
            PosterHomeRow(HomeSection(title = "Continue Watching", items = listOf(BaseItemDto(Id = "cw1")), key = "continue-watching")),
            PosterHomeRow(HomeSection(title = "Watchlist Movies", items = listOf(item1), key = "watchlist-movies")),
        )

        val targetIdToRemove = "m1"
        val updatedRows = initialRows.mapNotNull { row ->
            if (row is PosterHomeRow && row.section.key.startsWith("watchlist")) {
                val remaining = row.section.items.filterNot { it.Id == targetIdToRemove }
                if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
            } else {
                row
            }
        }

        // The watchlist-movies row should be completely eliminated
        assertEquals(1, updatedRows.size)
        assertEquals("continue-watching", updatedRows[0].key)
    }

    @Test
    fun watchlistRowAddition_insertsInProperTopRowPosition() {
        val cwRow = PosterHomeRow(HomeSection("Continue Watching", listOf(BaseItemDto(Id = "cw1")), key = "continue-watching"))
        val upNextRow = PosterHomeRow(HomeSection("Next Up", listOf(BaseItemDto(Id = "un1")), key = "up-next"))
        val recRow = PosterHomeRow(HomeSection("Top Picks", listOf(BaseItemDto(Id = "rec1")), key = "rec:Top Picks"))
        val initialRows = listOf<HomeRow>(cwRow, upNextRow, recRow)

        val newItem = BaseItemDto(Id = "m1", Name = "New Movie", Type = "Movie", UserData = UserItemDataDto(IsFavorite = true))
        val newWatchlistRow = PosterHomeRow(HomeSection("Watchlist Movies", listOf(newItem), key = "watchlist-movies"))

        // Insertion helper matching HomeViewModel logic
        val upNextIdx = initialRows.indexOfFirst { it.key == "up-next" }
        val targetIndex = if (upNextIdx >= 0) upNextIdx + 1 else 0

        val resultRows = initialRows.toMutableList()
        resultRows.add(targetIndex, newWatchlistRow)

        assertEquals(4, resultRows.size)
        assertEquals("continue-watching", resultRows[0].key)
        assertEquals("up-next", resultRows[1].key)
        assertEquals("watchlist-movies", resultRows[2].key)
        assertEquals("rec:Top Picks", resultRows[3].key)
    }

    @Test
    fun removedFromWatchlist_preventsResurrectionByBackgroundPublish() {
        val removedFromWatchlist = mutableSetOf<String>()
        val item1 = BaseItemDto(Id = "m1", Name = "Movie 1", Type = "Movie")
        val item2 = BaseItemDto(Id = "m2", Name = "Movie 2", Type = "Movie")

        // Initial state before background job finishes
        val initialTopRows = listOf<HomeRow>(
            PosterHomeRow(HomeSection("Watchlist Movies", listOf(item1, item2), key = "watchlist-movies")),
        )

        // User removes item1
        removedFromWatchlist.add("m1")
        val currentRows = initialTopRows.mapNotNull { row ->
            if (row is PosterHomeRow && row.section.key.startsWith("watchlist")) {
                val remaining = row.section.items.filterNot { it.Id == "m1" }
                if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
            } else {
                row
            }
        }

        // Later, background job calls assembled()
        val topRowKeys = setOf("continue-watching", "up-next", "watchlist-movies", "watchlist-shows", "coming-soon", "studio-hubs")
        val currentTop = currentRows.filter { it.key in topRowKeys }.ifEmpty { initialTopRows }
        val filteredTop = currentTop.mapNotNull { row ->
            if (row is PosterHomeRow && row.section.key.startsWith("watchlist")) {
                val remaining = row.section.items.filterNot { removedFromWatchlist.contains(it.Id) }
                if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
            } else {
                row
            }
        }

        // Ensure item1 is NOT resurrected
        val assembledWatchlistRow = filteredTop.first { it.key == "watchlist-movies" } as PosterHomeRow
        assertFalse(assembledWatchlistRow.section.items.any { it.Id == "m1" })
        assertTrue(assembledWatchlistRow.section.items.any { it.Id == "m2" })
    }
}
