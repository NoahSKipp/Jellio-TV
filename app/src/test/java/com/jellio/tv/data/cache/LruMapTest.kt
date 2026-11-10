package com.jellio.tv.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LruMapTest {
    @Test
    fun evictsOnlyTheEldestPastTheCap() {
        val map = lruMap<String, Int>(3)
        map["a"] = 1
        map["b"] = 2
        map["c"] = 3
        map["d"] = 4
        assertEquals(3, map.size)
        assertFalse(map.containsKey("a"))
        assertEquals(listOf("b", "c", "d"), map.keys.toList())
    }

    @Test
    fun readingAnEntryKeepsIt() {
        val map = lruMap<String, Int>(3)
        map["a"] = 1
        map["b"] = 2
        map["c"] = 3
        assertEquals(1, map["a"])
        map["d"] = 4
        assertTrue(map.containsKey("a"))
        assertNull(map["b"])
    }

    @Test
    fun rewritingAnEntryKeepsIt() {
        val map = lruMap<String, Int>(2)
        map["a"] = 1
        map["b"] = 2
        map["a"] = 10
        map["c"] = 3
        assertEquals(10, map["a"])
        assertFalse(map.containsKey("b"))
    }

    @Test
    fun staysBoundedUnderConcurrentWrites() {
        val map = lruMap<Int, Int>(100)
        val threads = (0 until 8).map { t ->
            Thread {
                repeat(5_000) { i ->
                    val key = t * 10_000 + i
                    map[key] = i
                    map[key - 1]
                    map.remove(key - 7)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(map.size <= 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveCap() {
        lruMap<String, Int>(0)
    }
}
