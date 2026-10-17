package com.jellio.tv.ui.player

import android.content.Context
import android.content.SharedPreferences
import com.jellio.tv.data.model.MediaSourceDto

// Streams that failed to play (an error, a placeholder clip, no picture),
// remembered per title for 48 hours so the stream picker can flag them and
// automatic retries skip them. Same idea as the web's runtime/streamHealth.js.
object BadStreams {
    private const val LIFETIME_MS = 48L * 60 * 60 * 1000
    private const val SUBTITLE_CRASH_MIGRATION_KEY = "_v3_desc_poison_cleared"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val store = context.applicationContext.getSharedPreferences("jellio_bad_streams", Context.MODE_PRIVATE)
        prefs = store
        if (!store.getBoolean(SUBTITLE_CRASH_MIGRATION_KEY, false)) {
            store.edit().clear().putBoolean(SUBTITLE_CRASH_MIGRATION_KEY, true).apply()
            return
        }
        purgeLegacyAndExpired(store, System.currentTimeMillis())
    }

    // Key strictly by unique source.Id so one failing stream never poisons other
    // streams on the same title.
    internal fun keys(itemId: String, source: MediaSourceDto): List<String> = listOfNotNull(
        source.Id?.takeIf { it.isNotEmpty() }?.let { "$itemId|id:$it" },
    )



    private fun purgeLegacyAndExpired(store: SharedPreferences, now: Long) {
        val all = store.all
        var dirty = false
        val editor = store.edit()
        all.forEach { (key, value) ->
            if (key == SUBTITLE_CRASH_MIGRATION_KEY) return@forEach
            if (key.contains("|name:") || key.contains("|desc:") || (value as? Long ?: 0L) < now) {
                editor.remove(key)
                dirty = true
            }
        }
        if (dirty) editor.apply()
    }

    fun mark(itemId: String, source: MediaSourceDto) {
        val store = prefs ?: return
        val now = System.currentTimeMillis()
        val editor = store.edit()
        store.all.forEach { (key, value) ->
            if (key == SUBTITLE_CRASH_MIGRATION_KEY) return@forEach
            if (key.contains("|name:") || key.contains("|desc:") || (value as? Long ?: 0L) < now) editor.remove(key)
        }
        keys(itemId, source).forEach { editor.putLong(it, now + LIFETIME_MS) }
        editor.apply()
    }

    fun unmark(itemId: String, source: MediaSourceDto) {
        val store = prefs ?: return
        val editor = store.edit()
        keys(itemId, source).forEach { editor.remove(it) }
        editor.apply()
    }

    fun isBad(itemId: String, source: MediaSourceDto): Boolean {
        val store = prefs ?: return false
        val now = System.currentTimeMillis()
        purgeLegacyAndExpired(store, now)
        return keys(itemId, source).any { store.getLong(it, 0L) > now }
    }
}

// A clip far shorter than the title it stands in for: AIOStreams' "download
// failed" video and similar placeholders run under three minutes against a
// runtime of ten minutes or more. Capping actualMs at 180s ensures real
// streams are never misclassified as placeholders when catalog metadata has
// an inflated expected runtime.
private const val MAX_PLACEHOLDER_MS = 3L * 60 * 1000

fun looksLikePlaceholder(actualMs: Long, expectedMs: Long): Boolean =
    actualMs in 1..MAX_PLACEHOLDER_MS && expectedMs >= 10 * 60 * 1000 && actualMs < expectedMs / 4

