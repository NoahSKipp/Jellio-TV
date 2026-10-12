package com.jellio.tv.ui.player

import android.content.Context
import android.content.SharedPreferences
import com.jellio.tv.data.model.MediaSourceDto

// Streams that failed to play (an error, a placeholder clip, no picture),
// remembered per title for 48 hours so the stream picker can flag them and
// automatic retries skip them. Same idea as the web's runtime/streamHealth.js.
object BadStreams {
    private const val LIFETIME_MS = 48L * 60 * 60 * 1000
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("jellio_bad_streams", Context.MODE_PRIVATE)
    }

    // A source's id can change when Gelato refreshes its rows; its name
    // (the addon's release line) doesn't, so both are kept.
    private fun keys(itemId: String, source: MediaSourceDto): List<String> = listOfNotNull(
        source.Id?.let { "$itemId|id:$it" },
        source.Name?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let { "$itemId|name:$it" },
    )

    fun mark(itemId: String, source: MediaSourceDto) {
        val store = prefs ?: return
        val now = System.currentTimeMillis()
        val editor = store.edit()
        store.all.forEach { (key, value) -> if ((value as? Long ?: 0L) < now) editor.remove(key) }
        keys(itemId, source).forEach { editor.putLong(it, now + LIFETIME_MS) }
        editor.apply()
    }

    fun isBad(itemId: String, source: MediaSourceDto): Boolean {
        val store = prefs ?: return false
        val now = System.currentTimeMillis()
        return keys(itemId, source).any { store.getLong(it, 0L) > now }
    }
}

// A clip far shorter than the title it stands in for: AIOStreams' "download
// failed" video and similar placeholders run about two minutes against a
// runtime of an hour or more.
fun looksLikePlaceholder(actualMs: Long, expectedMs: Long): Boolean =
    actualMs > 0 && expectedMs >= 10 * 60 * 1000 && actualMs < expectedMs / 4
