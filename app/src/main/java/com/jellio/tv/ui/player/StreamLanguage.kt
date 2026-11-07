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
// ISO 639-2 codes so the server's and the player's spellings match.
internal fun normalizeLanguage(code: String?): String? {
    val raw = code?.trim()?.lowercase()?.ifBlank { null } ?: return null
    val aliases = mapOf("ger" to "deu", "fre" to "fra", "dut" to "nld", "chi" to "zho", "cze" to "ces", "gre" to "ell", "per" to "fas", "rum" to "ron", "slo" to "slk", "wel" to "cym", "arm" to "hye", "baq" to "eus", "geo" to "kat", "ice" to "isl", "mac" to "mkd", "may" to "msa", "bur" to "mya", "alb" to "sqi", "tib" to "bod")
    aliases[raw]?.let { return it }
    return runCatching { java.util.Locale.forLanguageTag(raw.substringBefore('-')).isO3Language }.getOrNull()?.ifBlank { null } ?: raw
}
