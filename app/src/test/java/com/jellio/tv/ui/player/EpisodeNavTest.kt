package com.jellio.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeNavTest {
    @Test fun movieShowsNothing() {
        assertEquals(EpisodeNav(false, false, false), episodeNav(false, false, "p", "n"))
    }

    @Test fun episodeWithBothNeighbours() {
        assertEquals(EpisodeNav(true, true, true), episodeNav(true, false, "p", "n"))
    }

    @Test fun missingNeighbourKeepsItsSlot() {
        assertEquals(EpisodeNav(true, false, true), episodeNav(true, false, null, "n"))
        assertEquals(EpisodeNav(true, true, false), episodeNav(true, false, "p", null))
    }

    @Test fun hiddenForGuestsWhoMustAsk() {
        assertEquals(EpisodeNav(false, false, false), episodeNav(true, true, "p", "n"))
    }
}
