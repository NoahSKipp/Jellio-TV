package com.jellio.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class JellyfinDatesTest {
    private val at = Instant.parse("2024-05-01T20:15:30Z").toEpochMilli()

    @Test
    fun readsUtcWithZ() {
        assertEquals(at, JellyfinDates.parseEpochMs("2024-05-01T20:15:30Z"))
    }

    @Test
    fun readsSevenDigitFractions() {
        assertEquals(at + 123, JellyfinDates.parseEpochMs("2024-05-01T20:15:30.1234567Z"))
        assertEquals(at, JellyfinDates.parseEpochMs("2024-05-01T20:15:30.0000000Z"))
    }

    @Test
    fun treatsMissingZoneAsUtc() {
        assertEquals(at, JellyfinDates.parseEpochMs("2024-05-01T20:15:30"))
        assertEquals(at + 500, JellyfinDates.parseEpochMs("2024-05-01T20:15:30.5000000"))
    }

    @Test
    fun readsOffsets() {
        assertEquals(at, JellyfinDates.parseEpochMs("2024-05-01T22:15:30+02:00"))
    }

    @Test
    fun readsBareDateAsUtcMidnight() {
        assertEquals(Instant.parse("2024-05-01T00:00:00Z"), JellyfinDates.parseInstant("2024-05-01"))
    }

    @Test
    fun trimsAndRejectsJunk() {
        assertEquals(at, JellyfinDates.parseEpochMs("  2024-05-01T20:15:30Z "))
        assertNull(JellyfinDates.parseEpochMs(null))
        assertNull(JellyfinDates.parseEpochMs(""))
        assertNull(JellyfinDates.parseEpochMs("   "))
        assertNull(JellyfinDates.parseEpochMs("soon"))
        assertNull(JellyfinDates.parseEpochMs("2024-13-01"))
    }
}
