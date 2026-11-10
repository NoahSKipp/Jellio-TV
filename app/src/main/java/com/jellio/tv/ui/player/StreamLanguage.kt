package com.jellio.tv.ui.player

import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.normalizeLanguageCode
import com.jellio.tv.ui.detail.sourceAudioLanguages

private const val MULTI_AUDIO_CODE = "__multi__"

private val MULTI_AUDIO = Regex("\\b(multi|dual)([-.\\s]?audio)?\\b", RegexOption.IGNORE_CASE)

// A preferred audio language set by the user wins; otherwise the menu
// language stands in for it.
internal fun preferredStreamLanguage(audioPreference: String?, displayLanguage: String?): String? =
    normalizeLanguage(audioPreference) ?: normalizeLanguage(displayLanguage)

// Streams with the language first, then multi-audio ones; other
// languages only when neither is left.
internal fun orderByLanguage(sources: List<MediaSourceDto>, language: String?): List<MediaSourceDto> =
    orderByLanguage(sources, language) { source ->
        sourceAudioLanguages(source).ifEmpty {
            if (MULTI_AUDIO.containsMatchIn(source.Name.orEmpty())) listOf(MULTI_AUDIO_CODE) else emptyList()
        }
    }

internal fun <T> orderByLanguage(sources: List<T>, language: String?, languagesOf: (T) -> List<String>): List<T> {
    val wanted = normalizeLanguage(language) ?: return sources
    val matches = mutableListOf<T>()
    val multi = mutableListOf<T>()
    val others = mutableListOf<T>()
    sources.forEach { source ->
        val languages = languagesOf(source).mapNotNull { normalizeLanguage(it) }
        when {
            wanted in languages -> matches += source
            MULTI_AUDIO_CODE in languages -> multi += source
            else -> others += source
        }
    }
    return if (matches.isEmpty() && multi.isEmpty()) others else matches + multi
}

internal fun sameLanguage(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    if (a.equals(b, ignoreCase = true)) return true
    fun short(code: String): String = runCatching { java.util.Locale.forLanguageTag(code).language }.getOrDefault(code).lowercase()
    val left = short(a)
    val right = short(b)
    return left.isNotEmpty() && left == right ||
        runCatching { java.util.Locale(a).isO3Language.equals(java.util.Locale(b).isO3Language, ignoreCase = true) }.getOrDefault(false)
}

// "ger", "deu", "de" and "German" all mean the same language: compared as
// ISO 639-2/B codes, the same table as the web client's runtime/languages.js.
internal fun normalizeLanguage(code: String?): String? = normalizeLanguageCode(code).ifEmpty { null }
