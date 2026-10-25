package com.jellio.tv.ui.player

import android.content.Context

// Current audio and subtitle offsets in milliseconds, read on the playback
// thread as samples are read.
class SyncDelays(audio: Int, subtitle: Int) {
    @Volatile var audioMs: Int = audio
    @Volatile var subtitleMs: Int = subtitle
}

// Subtitle offset per title, like the web player's per-item subtitle sync.
object SubtitleSync {
    private const val PREFS = "jellio_subtitle_offset"

    fun load(context: Context, itemId: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(itemId, 0)

    fun save(context: Context, itemId: String, offsetMs: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (offsetMs == 0) prefs.remove(itemId) else prefs.putInt(itemId, offsetMs)
        prefs.apply()
    }
}
