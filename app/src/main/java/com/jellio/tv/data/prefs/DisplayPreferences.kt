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
    FRENCH("fr", "French", "Français", "fr-FR"),
    ITALIAN("it", "Italian", "Italiano", "it-IT"),
    POLISH("pl", "Polish", "Polski", "pl-PL"),
    PORTUGUESE("pt", "Portuguese", "Português", "pt-BR");

    val locale: java.util.Locale
        get() = java.util.Locale.forLanguageTag(localeTag)

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}

// HH:MM clock on Home. Default 24-hour.
enum class ClockStyle(val code: String) {
    HOURS_24("24h"),
    HOURS_12("12h"),
    OFF("off");

    companion object {
        fun fromCode(code: String?): ClockStyle = entries.firstOrNull { it.code == code } ?: HOURS_24
    }
}

object DisplayPreferences {
    private const val PREFS = "jellio_display"
    private const val KEY_OVERSCAN = "overscan_percent"
    private const val KEY_DISPLAY_LANGUAGE = "display_language"
    private const val KEY_SHOW_NOW_PLAYING_PILL = "show_now_playing_pill"
    private const val KEY_CLOCK_STYLE = "clock_style"

    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 8

    var overscanPercent by mutableIntStateOf(0)
        private set

    var displayLanguage by mutableStateOf(AppLanguage.ENGLISH)
        private set

    var showNowPlayingPill by mutableStateOf(true)
        private set

    var clockStyle by mutableStateOf(ClockStyle.HOURS_24)
        private set

    var isCalibrating by mutableStateOf(false)

    fun init(context: Context) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        overscanPercent = sp.getInt(KEY_OVERSCAN, 0).coerceIn(MIN_PERCENT, MAX_PERCENT)
        val langCode = sp.getString(KEY_DISPLAY_LANGUAGE, "en")
        displayLanguage = AppLanguage.fromCode(langCode)
        showNowPlayingPill = sp.getBoolean(KEY_SHOW_NOW_PLAYING_PILL, true)
        clockStyle = ClockStyle.fromCode(sp.getString(KEY_CLOCK_STYLE, null))
    }

    fun setClockStyle(context: Context, style: ClockStyle) {
        clockStyle = style
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLOCK_STYLE, style.code)
            .apply()
    }

    fun setShowNowPlayingPill(context: Context, show: Boolean) {
        showNowPlayingPill = show
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_NOW_PLAYING_PILL, show)
            .apply()
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

    internal fun setDisplayLanguageForTest(language: AppLanguage) {
        displayLanguage = language
    }
}
