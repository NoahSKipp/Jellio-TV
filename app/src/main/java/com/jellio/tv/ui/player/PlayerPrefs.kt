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
    private const val MATCH_FRAME_RATE = "match_frame_rate"
    private const val AUDIO_DELAY = "audio_delay_ms"

    var autoSkipIntro by mutableStateOf(false)
        private set

    // "standard", "more" or "max": see JellioLoadControl.
    var prebuffer by mutableStateOf("standard")
        private set

    var dialogueBoost by mutableStateOf(false)
        private set

    var audioPassthrough by mutableStateOf(false)
        private set

    // Switch the TV's refresh rate to suit the video (24 Hz for most films).
    var matchFrameRate by mutableStateOf(true)
        private set

    // Sound later (+) or earlier (-) than the picture, for this TV's audio setup.
    var audioDelayMs by mutableStateOf(0)
        private set

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        autoSkipIntro = prefs.getBoolean(AUTO_SKIP, false)
        prebuffer = prefs.getString(PREBUFFER, "standard") ?: "standard"
        dialogueBoost = prefs.getBoolean(DIALOGUE_BOOST, false)
        audioPassthrough = prefs.getBoolean(AUDIO_PASSTHROUGH, false)
        matchFrameRate = prefs.getBoolean(MATCH_FRAME_RATE, true)
        audioDelayMs = prefs.getInt(AUDIO_DELAY, 0)
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

    fun setMatchFrameRate(context: Context, enabled: Boolean) {
        matchFrameRate = enabled
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(MATCH_FRAME_RATE, enabled).apply()
    }

    fun setAudioDelay(context: Context, ms: Int) {
        audioDelayMs = ms
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(AUDIO_DELAY, ms).apply()
    }
}
