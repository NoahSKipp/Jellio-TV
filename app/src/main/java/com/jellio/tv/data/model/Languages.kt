package com.jellio.tv.data.model

// Real ISO 639-2/B codes, what MediaStream.Language actually carries
// (confirmed against Jellyfin's own MediaStream DTO), ported from
// runtime/languages.js verbatim rather than guessed at.
private val LANGUAGE_KEY_MAP = mapOf(
    "eng" to "lang_english", "ger" to "lang_german", "deu" to "lang_german",
    "fre" to "lang_french", "fra" to "lang_french", "spa" to "lang_spanish",
    "ita" to "lang_italian", "jpn" to "lang_japanese", "kor" to "lang_korean",
    "chi" to "lang_chinese", "zho" to "lang_chinese", "rus" to "lang_russian",
    "por" to "lang_portuguese", "dut" to "lang_dutch", "nld" to "lang_dutch",
    "ara" to "lang_arabic", "pol" to "lang_polish", "swe" to "lang_swedish",
    "tur" to "lang_turkish",
)

private val FALLBACK_ENGLISH_NAMES = mapOf(
    "eng" to "English", "ger" to "German", "deu" to "German", "fre" to "French", "fra" to "French",
    "spa" to "Spanish", "ita" to "Italian", "jpn" to "Japanese", "kor" to "Korean", "chi" to "Chinese",
    "zho" to "Chinese", "rus" to "Russian", "por" to "Portuguese", "dut" to "Dutch", "nld" to "Dutch",
    "ara" to "Arabic", "pol" to "Polish", "swe" to "Swedish", "tur" to "Turkish",
)

fun languageName(code: String?): String {
    if (code.isNullOrEmpty()) return com.jellio.tv.ui.i18n.Strings.get("lang_unknown")
    val lower = code.lowercase()
    val key = LANGUAGE_KEY_MAP[lower]
    return if (key != null) com.jellio.tv.ui.i18n.Strings.get(key) else (FALLBACK_ENGLISH_NAMES[lower] ?: code.uppercase())
}

data class LanguageOption(val code: String, val name: String)

// One real canonical code per real name (ger over deu, fre over fra,
// dut over nld), mirrors runtime/languages.js's own LANGUAGE_OPTIONS,
// screens/settings.js's own default audio/subtitle language pickers.
val LANGUAGE_OPTIONS = listOf(
    LanguageOption("eng", "English"),
    LanguageOption("ger", "German"),
    LanguageOption("fre", "French"),
    LanguageOption("spa", "Spanish"),
    LanguageOption("ita", "Italian"),
    LanguageOption("jpn", "Japanese"),
    LanguageOption("kor", "Korean"),
    LanguageOption("chi", "Chinese"),
    LanguageOption("rus", "Russian"),
    LanguageOption("por", "Portuguese"),
    LanguageOption("dut", "Dutch"),
    LanguageOption("ara", "Arabic"),
    LanguageOption("pol", "Polish"),
    LanguageOption("swe", "Swedish"),
    LanguageOption("tur", "Turkish"),
)

// A saved code might be the alternate ISO form this canonical option
// list does not itself carry (deu rather than ger, from some other
// real Jellyfin client): matched by real name, the one thing both
// forms actually agree on, rather than left looking unset.
fun matchLanguageOption(code: String?): LanguageOption? {
    if (code.isNullOrEmpty()) return null
    val lower = code.lowercase()
    return LANGUAGE_OPTIONS.find { it.code == lower || it.name.equals(languageName(code), ignoreCase = true) || it.name.equals(FALLBACK_ENGLISH_NAMES[lower], ignoreCase = true) }
}
