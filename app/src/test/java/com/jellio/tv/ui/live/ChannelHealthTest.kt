package com.jellio.tv.ui.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelHealthTest {
    private fun channel(id: String, name: String, favorite: Boolean = false) = LiveChannel(id, name, "", null, favorite, null)

    @Test
    fun brokenChannelsAreMarkedAndGroupedLast() {
        val channels = listOf(channel("a", "Alpha", favorite = true), channel("b", "Bravo"), channel("c", "Charlie", favorite = true))
        val marked = applyHealth(channels, setOf("b", "c"))
        assertEquals(listOf(false, true, true), marked.map { it.failing })
        val arranged = arrangeChannels(marked)
        assertEquals(listOf("a"), arranged.favorites.map { it.id })
        assertTrue(arranged.others.isEmpty())
        assertEquals(listOf("b", "c"), arranged.broken.map { it.id })
        assertFalse(applyHealth(marked, emptySet())[1].failing)
    }

    @Test
    fun zappingStepsOverBrokenChannels() {
        val list = listOf(channel("1", "One"), channel("2", "Two").copy(failing = true), channel("3", "Three"))
        assertEquals("3", playableNeighbour(list, "1", 1)?.id)
        assertEquals("1", playableNeighbour(list, "3", -1)?.id)
        assertEquals("3", playableNeighbour(list, "2", 1)?.id)
    }

    @Test
    fun busyTunersDoNotCount() {
        assertFalse(countsAsChannelFailure("No available tuner"))
        assertTrue(countsAsChannelFailure("Timeout of 25000 ms"))
        assertTrue(countsAsChannelFailure(null))
    }
}
