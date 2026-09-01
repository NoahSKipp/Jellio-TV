package com.jellio.tv.ui.player

import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.ui.detail.sourceAudioLanguages

private val MULTI_AUDIO = Regex("\\b(multi|dual)([-.\\s]?audio)?\\b", RegexOption.IGNORE_CASE)

// A preferred audio language set by the user wins; otherwise the menu
// language stands in for it.
internal fun preferredStreamLanguage(audioPreference: String?, displayLanguage: String?): String? =
    normalizeLanguage(audioPreference) ?: normalizeLanguage(displayLanguage)

// Streams with the language first, then multi-audio ones; other
// languages only when neither is left.
internal fun orderByLanguage(sources: List<MediaSourceDto>, language: String?): List<MediaSourceDto> {
    val wanted = normalizeLanguage(language) ?: return sources
    val matches = mutableListOf<MediaSourceDto>()
    val multi = mutableListOf<MediaSourceDto>()
    val others = mutableListOf<MediaSourceDto>()
    sources.forEach { source ->
        val languages = sourceAudioLanguages(source).mapNotNull { normalizeLanguage(it) }
        when {
            wanted in languages -> matches += source
            languages.isEmpty() && MULTI_AUDIO.containsMatchIn(source.Name.orEmpty()) -> multi += source
            else -> others += source
        }
    }
    return if (matches.isEmpty() && multi.isEmpty()) others else matches + multi
}
