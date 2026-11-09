package com.jellio.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeterministicVirtualIdTest {

    @Test
    fun generatesConsistentAndDistinctGuids() {
        val movie1 = generateDeterministicVirtualId("movie", "550")
        val movie2 = generateDeterministicVirtualId("movie", "550")
        val tv1 = generateDeterministicVirtualId("tv", "550")
        val movieOther = generateDeterministicVirtualId("movie", "551")

        assertEquals(movie1, movie2)
        assertNotEquals(movie1, tv1)
        assertNotEquals(movie1, movieOther)

        // Matches .NET Guid MD5 layout: 849a39f8-2cef-f512-7276-b3307ccae383
        assertEquals("849a39f8-2cef-f512-7276-b3307ccae383", movie1)
    }
}
