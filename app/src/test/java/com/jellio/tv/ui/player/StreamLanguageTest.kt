package com.jellio.tv.ui.player

import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.MediaStreamDto
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamLanguageTest {
    private fun source(id: String, name: String = id, vararg audio: String) =
        MediaSourceDto(Id = id, Name = name, MediaStreams = audio.map { MediaStreamDto(Type = "Audio", Language = it) })

    @Test
    fun audioPreferenceWinsOverMenuLanguage() {
        assertEquals(normalizeLanguage("ger"), preferredStreamLanguage("ger", "en"))
        assertEquals(normalizeLanguage("de"), preferredStreamLanguage(null, "de"))
        assertEquals(normalizeLanguage("de"), normalizeLanguage("ger"))
    }

    @Test
    fun matchingLanguageThenMultiAudioAndNoOthers() {
        val english = source("en", "English", "eng")
        val german = source("de", "German", "ger")
        val multi = source("multi", "Film MULTI 1080p")
        val ordered = orderByLanguage(listOf(english, multi, german), "de")
        assertEquals(listOf("de", "multi"), ordered.map { it.Id })
    }

    @Test
    fun othersOnlyWhenNothingMatches() {
        val english = source("en", "English", "eng")
        val french = source("fr", "French", "fre")
        assertEquals(listOf("en", "fr"), orderByLanguage(listOf(english, french), "de").map { it.Id })
    }
}
