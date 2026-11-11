package com.jellio.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeNeighborsTest {
    // Specials, S1, S2, S3
    private val seasons = listOf(true, false, false, false)

    @Test fun walksBackSkippingSpecials() {
        assertEquals(listOf(2, 1), earlierSeasonIndexes(seasons, 3))
        assertEquals(emptyList<Int>(), earlierSeasonIndexes(seasons, 1))
    }

    @Test fun specialsLookBackThroughSpecialsOnly() {
        assertEquals(emptyList<Int>(), earlierSeasonIndexes(listOf(true, true, false), 2))
        assertEquals(listOf(0), earlierSeasonIndexes(listOf(true, true), 1))
    }

    @Test fun unknownSeasonGivesNothing() {
        assertEquals(emptyList<Int>(), earlierSeasonIndexes(seasons, -1))
    }
}
