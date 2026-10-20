package com.jellio.tv.data.prefs

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val label: String, val nativeName: String, val localeTag: String) {
    ENGLISH("en", "English", "English", "en-US"),
    GERMAN("de", "German", "Deutsch", "de-DE"),
    SPANISH("es", "Spanish", "Español", "es-ES"),
    POLISH("pl", "Polish", "Polski", "pl-PL");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}

// Controls the display safe area (overscan inset margin) for TVs where
// the HDMI picture cuts off edges (common with 16:9 overscan on consumer panels),
// as well as the active UI display language.
object DisplayPreferences {
    private const val PREFS = "jellio_display"
    private const val KEY_OVERSCAN = "overscan_percent"
    private const val KEY_DISPLAY_LANGUAGE = "display_language"

    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 8

    var overscanPercent by mutableIntStateOf(0)
        private set

    var displayLanguage by mutableStateOf(AppLanguage.ENGLISH)
        private set

    var isCalibrating by mutableStateOf(false)

    fun init(context: Context) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        overscanPercent = sp.getInt(KEY_OVERSCAN, 0).coerceIn(MIN_PERCENT, MAX_PERCENT)
        val langCode = sp.getString(KEY_DISPLAY_LANGUAGE, "en")
        displayLanguage = AppLanguage.fromCode(langCode)
    }

    fun setOverscan(context: Context, percent: Int) {
        val clamped = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        overscanPercent = clamped
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OVERSCAN, clamped)
            .apply()
    }

    fun setDisplayLanguage(context: Context, language: AppLanguage) {
        displayLanguage = language
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_LANGUAGE, language.code)
            .apply()
    }
}
