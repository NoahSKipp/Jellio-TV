package com.jellio.tv.data.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceCacheTest {
    private val minute = 60_000L

    @Test
    fun streamsAreReusedForEightMinutes() {
        val cache = SourceCache<String>()
        cache.put("a", listOf("1080p"), 0)
        assertEquals(listOf("1080p"), cache.get("a", 7 * minute))
        assertNull(cache.get("a", 9 * minute))
    }

    @Test
    fun anEmptyAnswerIsReusedForTwoMinutes() {
        val cache = SourceCache<String>()
        cache.put("a", emptyList(), 0)
        assertEquals(emptyList<String>(), cache.get("a", minute))
        assertNull(cache.get("a", 3 * minute))
    }

    @Test
    fun clearDropsEverything() {
        val cache = SourceCache<String>()
        cache.put("a", listOf("x"), 0)
        cache.clear()
        assertNull(cache.get("a", 0))
    }
}
