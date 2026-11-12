package com.jellio.tv.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistRefreshTest {
    @Test
    fun sameTitlesInTheSameOrderStay() {
        assertFalse(watchlistIdsChanged(listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun anAddedOrRemovedTitleRedraws() {
        assertTrue(watchlistIdsChanged(listOf("a"), listOf("b", "a")))
        assertTrue(watchlistIdsChanged(listOf("a", "b"), listOf("a")))
        assertTrue(watchlistIdsChanged(emptyList(), listOf("a")))
    }

    @Test
    fun aReorderedListRedraws() {
        assertTrue(watchlistIdsChanged(listOf("a", "b"), listOf("b", "a")))
    }
}
