package com.jellio.tv.data.model

val KEY_BY_CODE = mapOf(
    "eng" to "lang_english", "en" to "lang_english",
    "ger" to "lang_german", "deu" to "lang_german", "de" to "lang_german",
    "fre" to "lang_french", "fra" to "lang_french", "fr" to "lang_french",
    "spa" to "lang_spanish", "es" to "lang_spanish",
    "ita" to "lang_italian", "it" to "lang_italian",
    "jpn" to "lang_japanese", "ja" to "lang_japanese",
    "kor" to "lang_korean", "ko" to "lang_korean",
    "chi" to "lang_chinese", "zho" to "lang_chinese", "zh" to "lang_chinese",
    "rus" to "lang_russian", "ru" to "lang_russian",
    "por" to "lang_portuguese", "pt" to "lang_portuguese",
    "dut" to "lang_dutch", "nld" to "lang_dutch", "nl" to "lang_dutch",
    "ara" to "lang_arabic", "ar" to "lang_arabic",
    "pol" to "lang_polish", "pl" to "lang_polish",
    "swe" to "lang_swedish", "sv" to "lang_swedish",
    "tur" to "lang_turkish", "tr" to "lang_turkish",
    "hr" to "lang_hr", "hrv" to "lang_hr",
    "el" to "lang_el", "ell" to "lang_el", "gre" to "lang_el",
    "he" to "lang_he", "heb" to "lang_he",
    "hu" to "lang_hu", "hun" to "lang_hu",
    "ro" to "lang_ro", "ron" to "lang_ro", "rum" to "lang_ro",
    "th" to "lang_th", "tha" to "lang_th",
    "vi" to "lang_vi", "vie" to "lang_vi",
    "id" to "lang_id", "ind" to "lang_id",
    "ms" to "lang_ms", "msa" to "lang_ms", "may" to "lang_ms",
    "uk" to "lang_uk", "ukr" to "lang_uk",
    "bg" to "lang_bg", "bul" to "lang_bg",
    "sr" to "lang_sr", "srp" to "lang_sr",
    "sk" to "lang_sk", "slk" to "lang_sk", "slo" to "lang_sk",
    "sl" to "lang_sl", "slv" to "lang_sl",
    "et" to "lang_et", "est" to "lang_et",
    "lv" to "lang_lv", "lav" to "lang_lv",
    "lt" to "lang_lt", "lit" to "lang_lt",
    "fa" to "lang_fa", "fas" to "lang_fa", "per" to "lang_fa",
    "ta" to "lang_ta", "tam" to "lang_ta",
    "te" to "lang_te", "tel" to "lang_te",
    "ml" to "lang_ml", "mal" to "lang_ml",
)

private val LANGUAGE_KEY_MAP = KEY_BY_CODE

private val FALLBACK_ENGLISH_NAMES = mapOf(
    "eng" to "English", "en" to "English",
    "ger" to "German", "deu" to "German", "de" to "German",
    "fre" to "French", "fra" to "French", "fr" to "French",
    "spa" to "Spanish", "es" to "Spanish",
    "ita" to "Italian", "it" to "Italian",
    "jpn" to "Japanese", "ja" to "Japanese",
    "kor" to "Korean", "ko" to "Korean",
    "chi" to "Chinese", "zho" to "Chinese", "zh" to "Chinese",
    "rus" to "Russian", "ru" to "Russian",
    "por" to "Portuguese", "pt" to "Portuguese",
    "dut" to "Dutch", "nld" to "Dutch", "nl" to "Dutch",
    "ara" to "Arabic", "ar" to "Arabic",
    "pol" to "Polish", "pl" to "Polish",
    "swe" to "Swedish", "sv" to "Swedish",
    "tur" to "Turkish", "tr" to "Turkish",
    "hr" to "Croatian", "hrv" to "Croatian",
    "el" to "Greek", "ell" to "Greek", "gre" to "Greek",
    "he" to "Hebrew", "heb" to "Hebrew",
    "hu" to "Hungarian", "hun" to "Hungarian",
    "ro" to "Romanian", "ron" to "Romanian", "rum" to "Romanian",
    "th" to "Thai", "tha" to "Thai",
    "vi" to "Vietnamese", "vie" to "Vietnamese",
    "id" to "Indonesian", "ind" to "Indonesian",
    "ms" to "Malay", "msa" to "Malay", "may" to "Malay",
    "uk" to "Ukrainian", "ukr" to "Ukrainian",
    "bg" to "Bulgarian", "bul" to "Bulgarian",
    "sr" to "Serbian", "srp" to "Serbian",
    "sk" to "Slovak", "slk" to "Slovak", "slo" to "Slovak",
    "sl" to "Slovenian", "slv" to "Slovenian",
    "et" to "Estonian", "est" to "Estonian",
    "lv" to "Latvian", "lav" to "Latvian",
    "lt" to "Lithuanian", "lit" to "Lithuanian",
    "fa" to "Persian", "fas" to "Persian", "per" to "Persian",
    "ta" to "Tamil", "tam" to "Tamil",
    "te" to "Telugu", "tel" to "Telugu",
    "ml" to "Malayalam", "mal" to "Malayalam",
)

fun languageName(code: String?): String {
    if (code.isNullOrEmpty()) return com.jellio.tv.ui.i18n.Strings.get("lang_unknown")
    val lower = code.lowercase()
    val key = KEY_BY_CODE[lower]
    return if (key != null) com.jellio.tv.ui.i18n.Strings.get(key) else (FALLBACK_ENGLISH_NAMES[lower] ?: code.uppercase())
}

data class LanguageOption(val code: String, val name: String)

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

fun matchLanguageOption(code: String?): LanguageOption? {
    if (code.isNullOrEmpty()) return null
    val lower = code.lowercase()
    return LANGUAGE_OPTIONS.find { it.code == lower || it.name.equals(languageName(code), ignoreCase = true) || it.name.equals(FALLBACK_ENGLISH_NAMES[lower], ignoreCase = true) }
}
