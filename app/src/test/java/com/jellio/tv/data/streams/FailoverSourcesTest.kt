package com.jellio.tv.data.streams

import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailoverSourcesTest {
    private data class Src(val id: String, val lang: String = "en", val reencode: Boolean = false)

    private val all = (1..5).map { Src("s$it") }

    private fun plan(sources: List<Src>?, tried: List<String>, bad: Set<String> = emptySet()) = planFailover(
        sources,
        tried,
        id = { it.id },
        isBad = { if (it.id in bad) BadKind.HARD else null },
        byLanguage = { list -> list.sortedBy { if (it.lang == "de") 0 else 1 } },
    )

    @Test
    fun aTimedOutListIsRetriedWithTheLongerTimeout() = runBlocking {
        val timeouts = mutableListOf<Int?>()
        val loaded = loadFailoverSources<Src>(null, { 0L }) { timeout ->
            timeouts.add(timeout)
            if (timeout == null) throw SocketTimeoutException("timeout")
            all
        }
        assertEquals(listOf(null, RETRY_TIMEOUT_SECONDS), timeouts)
        assertEquals(ListOrigin.RETRY, loaded.origin)
        assertEquals("timeout", loaded.error?.kind)
        assertTrue(loaded.error!!.recovered)
        assertEquals(FailoverOutcome.Navigate("s2"), failoverOutcome(loaded, plan(loaded.sources, listOf("s1")), { it.id }))
    }

    @Test
    fun twoFailuresFallBackToTheLastLoadedList() = runBlocking {
        val loaded = loadFailoverSources(LastList(all, at = 0L), { 10 * RECENT_LIST_MS }) { throw SocketTimeoutException() }
        assertEquals(ListOrigin.FALLBACK, loaded.origin)
        assertEquals(all, loaded.sources)
        assertFalse(loaded.error!!.recovered)
    }

    @Test
    fun noListAtAllSaysTheListFailedNotThatNoneAreLeft() = runBlocking {
        val loaded = loadFailoverSources<Src>(null, { 0L }) { throw SocketTimeoutException() }
        assertEquals(ListOrigin.FAILED, loaded.origin)
        assertNull(loaded.sources)
        assertEquals(FailoverOutcome.ListFailed("timeout"), failoverOutcome(loaded, plan(null, listOf("s1")), { it.id }))
    }

    @Test
    fun aRecentListIsUsedWithoutAsking() = runBlocking {
        var asked = false
        val loaded = loadFailoverSources(LastList(all, at = 0L), { RECENT_LIST_MS - 1 }) { asked = true; emptyList() }
        assertEquals(ListOrigin.RECENT, loaded.origin)
        assertFalse(asked)
    }

    @Test
    fun badMarksLeaveNoneAndSaySo() = runBlocking {
        val loaded = loadFailoverSources<Src>(null, { 0L }) { all }
        val p = plan(loaded.sources, listOf("s1"), bad = setOf("s2", "s3", "s4", "s5"))
        assertEquals(FailoverCounts(5, 1, 4, 0, 0, 0), p.counts)
        assertEquals(FailoverOutcome.NoneLeft, failoverOutcome(loaded, p, { it.id }))
    }

    @Test
    fun theNextCandidateFollowsLanguageAndTranscodeRules() {
        val list = listOf(Src("a"), Src("b", lang = "de", reencode = true), Src("c", lang = "de"))
        val p = planFailover(
            list,
            emptyList(),
            id = { it.id },
            isBad = { null },
            byLanguage = { l -> l.sortedBy { if (it.lang == "de") 0 else 1 } },
            needsReencode = { it.reencode },
            blockReencode = true,
        )
        assertEquals(listOf("c", "a"), p.ordered.map { it.id })
        assertEquals(3, p.counts.afterLanguage)
        assertEquals(2, p.counts.afterTranscode)
    }

    @Test
    fun theLogLineHasCountsAndNoUrls() = runBlocking {
        val loaded = loadFailoverSources<Src>(null, { 0L }) { all }
        val p = plan(loaded.sources, listOf("s1"))
        val line = describeFailover("error before the first frame", p.counts, loaded, failoverOutcome(loaded, p, { it.id }))
        assertTrue(line.contains("sources 5, tried 1"))
        assertTrue(line.endsWith("next s2"))
        assertFalse(line.contains("http"))
    }
}
