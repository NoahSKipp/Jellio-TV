package com.jellio.tv.data.streams

import org.junit.Assert.assertEquals
import org.junit.Test

class WarmRulesTest {
    private data class Src(val id: String, val tier: String?, val ready: Boolean, val rank: Int = 0)

    private fun pick(vararg sources: Src) =
        preferReadySource(sources.toList(), { it.ready }, { it.tier }, { it.rank }).map { it.id }

    @Test
    fun readySourceOfTheSameTierGoesFirst() {
        assertEquals(
            listOf("c", "a", "b"),
            pick(Src("a", "2160p-hdr", false), Src("b", "1080p-sdr", true), Src("c", "2160p-hdr", true)),
        )
    }

    @Test
    fun neverSwapsAcrossTiers() {
        assertEquals(listOf("a", "b"), pick(Src("a", "2160p-sdr", false), Src("b", "720p-sdr", true)))
    }

    @Test
    fun noSwapWhenLeaderIsReadyTierUnknownOrReadyOneRanksWorse() {
        assertEquals(listOf("a", "b"), pick(Src("a", "1080p-sdr", true), Src("b", "1080p-sdr", true)))
        assertEquals(listOf("a", "b"), pick(Src("a", "unknown", false), Src("b", "unknown", true)))
        assertEquals(listOf("a", "b"), pick(Src("a", null, false), Src("b", null, true)))
        assertEquals(listOf("a", "b"), pick(Src("a", "1080p-sdr", false, 0), Src("b", "1080p-sdr", true, 2)))
    }

    @Test
    fun normalisesIds() {
        assertEquals("0a1b2c3d000000000000000000000001", normalizeSourceId("0A1B2C3D-0000-0000-0000-000000000001"))
    }

    private class FakeClock : WarmScheduler {
        private var now = 0L
        private val timers = mutableListOf<Triple<Long, () -> Unit, BooleanArray>>()
        override fun schedule(delayMs: Long, block: () -> Unit): Cancellable {
            val cancelled = BooleanArray(1)
            timers += Triple(now + delayMs, block, cancelled)
            return Cancellable { cancelled[0] = true }
        }
        fun advance(ms: Long) {
            now += ms
            timers.sortedBy { it.first }.filter { it.first <= now }.forEach { timer ->
                timers.remove(timer)
                if (!timer.third[0]) timer.second()
            }
        }
    }

    @Test
    fun warmsOnceResultsStandStill() {
        val clock = FakeClock()
        val settled = mutableListOf<List<String>>()
        val warmer = SearchWarmer<String>(clock, onSettled = { settled += it })
        warmer.query()
        warmer.results(listOf("a"))
        clock.advance(500)
        warmer.results(listOf("a", "b"))
        clock.advance(500)
        assertEquals(emptyList<List<String>>(), settled)
        clock.advance(250)
        assertEquals(listOf(listOf("a", "b")), settled)
    }

    @Test
    fun newQueryCancelsPendingWork() {
        val clock = FakeClock()
        val settled = mutableListOf<List<String>>()
        val focused = mutableListOf<String>()
        var cancelled = 0
        val warmer = SearchWarmer<String>(
            clock,
            onSettled = { settled += it },
            onFocused = { focused += it },
            onQueryChanged = { cancelled++ },
        )
        warmer.results(listOf("old"))
        warmer.focus("old-card")
        clock.advance(300)
        warmer.query()
        clock.advance(2_000)
        assertEquals(emptyList<List<String>>(), settled)
        assertEquals(emptyList<String>(), focused)
        assertEquals(1, cancelled)
    }

    @Test
    fun focusWarmsAfterFocusTimeBlurBeforeItDoesNot() {
        val clock = FakeClock()
        val focused = mutableListOf<String>()
        val warmer = SearchWarmer<String>(clock, onSettled = {}, onFocused = { focused += it })
        warmer.focus("a")
        clock.advance(400)
        warmer.blur()
        clock.advance(500)
        warmer.focus("b")
        clock.advance(550)
        assertEquals(listOf("b"), focused)
    }
}
