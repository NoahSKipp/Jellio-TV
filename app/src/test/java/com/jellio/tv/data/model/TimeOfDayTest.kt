package com.jellio.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeOfDayTest {
    @Test
    fun keywordsCountDoubleGenres() {
        val isekai = TimeOfDayRow("Isekai", listOf("Fantasy"), listOf("another world", "reincarnat"))
        val hit = BaseItemDto(Id = "a", Name = "Mushoku Tensei", Genres = listOf("Fantasy"), Overview = "A man is reincarnated in another world.")
        val genreOnly = BaseItemDto(Id = "b", Name = "Frieren", Genres = listOf("fantasy"), Overview = "An elf mage travels.")
        val miss = BaseItemDto(Id = "c", Name = "Haikyu!!", Genres = listOf("Sports"), Overview = "Volleyball.")
        assertEquals(5, isekai.score(hit))
        assertEquals(1, isekai.score(genreOnly))
        assertEquals(0, isekai.score(miss))
    }

    @Test
    fun everyHourGetsARowWithGenres() {
        for (hour in 0..23) {
            for (row in listOf(moviesTimeOfDay(hour), animeTimeOfDay(hour), showsTimeOfDay(hour))) {
                assertTrue(row.title.isNotBlank())
                assertTrue(row.genres.isNotEmpty())
            }
        }
    }

    @Test
    fun lateNightAnimeIsANightTheme() {
        val nightTitles = setOf(
            "Isekai: diving into another world",
            "Supernatural after midnight",
            "Psychological late-night picks",
            "Neon and cyberpunk",
        )
        assertTrue(animeTimeOfDay(23).title in nightTitles)
        assertTrue(animeTimeOfDay(2).title in nightTitles)
    }
}
