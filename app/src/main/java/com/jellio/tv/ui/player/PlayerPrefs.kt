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
    private const val DIALOGUE_BOOST = "dialogue_boost"
    private const val AUDIO_PASSTHROUGH = "audio_passthrough"

    var autoSkipIntro by mutableStateOf(false)
        private set

    // "standard", "more" or "max": see JellioLoadControl.
    var prebuffer by mutableStateOf("standard")
        private set

    var dialogueBoost by mutableStateOf(false)
        private set

    var audioPassthrough by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        autoSkipIntro = prefs.getBoolean(AUTO_SKIP, false)
        prebuffer = prefs.getString(PREBUFFER, "standard") ?: "standard"
        dialogueBoost = prefs.getBoolean(DIALOGUE_BOOST, false)
        audioPassthrough = prefs.getBoolean(AUDIO_PASSTHROUGH, false)
    }

    fun setPrebuffer(context: Context, level: String) {
        prebuffer = level
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREBUFFER, level).apply()
    }

    fun setAutoSkipIntro(context: Context, enabled: Boolean) {
        autoSkipIntro = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(AUTO_SKIP, enabled).apply()
    }

    fun setDialogueBoost(context: Context, enabled: Boolean) {
        dialogueBoost = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DIALOGUE_BOOST, enabled).apply()
    }

    fun setAudioPassthrough(context: Context, enabled: Boolean) {
        audioPassthrough = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(AUDIO_PASSTHROUGH, enabled).apply()
    }
}
