package com.jellio.tv.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

// Jellyfin and the plugin send "2024-05-01T20:00:00.0000000Z", sometimes
// without the Z (still UTC), with an offset, or as a bare date (midnight UTC).
object JellyfinDates {
    fun parseInstant(raw: String?): Instant? {
        val value = raw?.trim()
        if (value.isNullOrEmpty()) return null
        runCatching { return OffsetDateTime.parse(value).toInstant() }
        runCatching { return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
        runCatching { return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() }
        return null
    }

    fun parseEpochMs(raw: String?): Long? = parseInstant(raw)?.toEpochMilli()
}
