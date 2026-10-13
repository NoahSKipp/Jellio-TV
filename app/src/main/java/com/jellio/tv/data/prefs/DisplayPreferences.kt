package com.jellio.tv.data.prefs

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

// Controls the display safe area (overscan inset margin) for TVs where
// the HDMI picture cuts off edges (common with 16:9 overscan on consumer panels).
// Stored as an integer percentage (0% to 8%).
object DisplayPreferences {
    private const val PREFS = "jellio_display"
    private const val KEY_OVERSCAN = "overscan_percent"

    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 8

    var overscanPercent by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        overscanPercent = sp.getInt(KEY_OVERSCAN, 0).coerceIn(MIN_PERCENT, MAX_PERCENT)
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
}
