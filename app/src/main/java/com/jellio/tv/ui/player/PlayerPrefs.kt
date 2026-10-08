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

    var autoSkipIntro by mutableStateOf(false)
        private set

    fun init(context: Context) {
        autoSkipIntro = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUTO_SKIP, false)
    }

    fun setAutoSkipIntro(context: Context, enabled: Boolean) {
        autoSkipIntro = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(AUTO_SKIP, enabled).apply()
    }
}
