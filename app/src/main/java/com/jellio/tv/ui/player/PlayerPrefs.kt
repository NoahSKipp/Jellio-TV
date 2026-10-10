package com.jellio.tv.ui.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Player preferences kept on this device, like screens/player.js's
// jellio_player_autoskip.
object PlayerPrefs {
    private const val PREFS = "jellio_player"
    private const val AUTO_SKIP = "autoskip"
    private const val PREBUFFER = "prebuffer"

    var autoSkipIntro by mutableStateOf(false)
        private set

    // "standard", "more" or "max": see JellioLoadControl.
    var prebuffer by mutableStateOf("standard")
        private set

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        autoSkipIntro = prefs.getBoolean(AUTO_SKIP, false)
        prebuffer = prefs.getString(PREBUFFER, "standard") ?: "standard"
    }

    fun setPrebuffer(context: Context, level: String) {
        prebuffer = level
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREBUFFER, level).apply()
    }

    fun setAutoSkipIntro(context: Context, enabled: Boolean) {
        autoSkipIntro = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(AUTO_SKIP, enabled).apply()
    }
}
