package com.jellio.tv.data.watchnext

import com.jellio.tv.data.model.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchNextTopPicksTest {

    @Test
    fun googleTvTopPicksSizeIsSix() {
        assertEquals(6, GOOGLE_TV_TOP_PICKS_SIZE)
        assertEquals(6, WatchNextSyncer.GOOGLE_TV_TOP_PICKS_SIZE)
    }

    @Test
    fun topPicksExportCappedToSixItems() {
        val largePicksList = (1..20).map { i ->
            BaseItemDto(Id = "item_$i", Name = "Top Pick $i")
        }
        val exported = largePicksList.take(GOOGLE_TV_TOP_PICKS_SIZE)
        assertEquals(6, exported.size)
        assertEquals("item_1", exported.first().Id)
        assertEquals("item_6", exported.last().Id)
    }

    @Test
    fun topPicksWeightsDescendCorrectly() {
        val picks = (1..10).map { i ->
            BaseItemDto(Id = "item_$i", Name = "Pick $i")
        }
        val exported = picks.take(GOOGLE_TV_TOP_PICKS_SIZE)
        val weights = exported.mapIndexed { index, _ -> GOOGLE_TV_TOP_PICKS_SIZE - index }

        assertEquals(listOf(6, 5, 4, 3, 2, 1), weights)
        assertTrue(weights.all { it > 0 })
    }

    @Test
    fun topPicksWithFewerThanSixItemsRetainsAll() {
        val shortList = (1..3).map { i ->
            BaseItemDto(Id = "item_$i", Name = "Pick $i")
        }
        val exported = shortList.take(GOOGLE_TV_TOP_PICKS_SIZE)
        assertEquals(3, exported.size)
        val weights = exported.mapIndexed { index, _ -> GOOGLE_TV_TOP_PICKS_SIZE - index }
        assertEquals(listOf(6, 5, 4), weights)
    }
}
