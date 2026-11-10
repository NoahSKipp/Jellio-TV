package com.jellio.tv.ui.player

import android.content.Context
import android.content.SharedPreferences
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.streams.BadKind

object BadStreams {
    internal const val LIFETIME_MS = 48L * 60 * 60 * 1000
    // A slow start or a buffering switch is as likely a slow backend as a
    // dead release, so it is remembered briefly and only by source id.
    internal const val SOFT_LIFETIME_MS = 2L * 60 * 60 * 1000
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

    // The source id, plus the full release name when the stream names one,
    // so the same release behind another addon is skipped too.
    internal fun keys(itemId: String, source: MediaSourceDto): List<String> {
        val name = source.Name?.trim().orEmpty()
        return listOfNotNull(
            source.Id?.takeIf { it.isNotEmpty() }?.let { "$itemId|id:$it" },
            name.takeIf { it.contains('\n') }?.let { "$itemId|release:$it" },
        )
    }

    internal fun markKeys(itemId: String, source: MediaSourceDto, soft: Boolean): List<String> =
        keys(itemId, source).filter { !soft || it.startsWith("$itemId|id:") }

    // Failures that say more about a slow backend than about the stream.
    fun isSoftFailure(reason: String): Boolean =
        reason.startsWith("no picture after") || reason == PlaceholderSign.STALLED_EARLY.reason

    internal fun kindOf(untilMs: List<Long>, now: Long): BadKind? {
        var kind: BadKind? = null
        for (until in untilMs) {
            if (until <= now) continue
            if (until - now > SOFT_LIFETIME_MS) return BadKind.HARD
            kind = BadKind.SOFT
        }
        return kind
    }

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

    fun mark(itemId: String, source: MediaSourceDto, soft: Boolean = false) {
        val store = prefs ?: return
        val now = System.currentTimeMillis()
        val editor = store.edit()
        store.all.forEach { (key, value) ->
            if (key == SUBTITLE_CRASH_MIGRATION_KEY) return@forEach
            if (key.contains("|name:") || key.contains("|desc:") || (value as? Long ?: 0L) < now) editor.remove(key)
        }
        val until = now + if (soft) SOFT_LIFETIME_MS else LIFETIME_MS
        markKeys(itemId, source, soft).forEach { editor.putLong(it, maxOf(store.getLong(it, 0L), until)) }
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

    fun kind(itemId: String, source: MediaSourceDto): BadKind? {
        val store = prefs ?: return null
        val now = System.currentTimeMillis()
        purgeLegacyAndExpired(store, now)
        return kindOf(keys(itemId, source).map { store.getLong(it, 0L) }, now)
    }
}

private const val MAX_PLACEHOLDER_MS = 3L * 60 * 1000

fun looksLikePlaceholder(actualMs: Long, expectedMs: Long): Boolean =
    actualMs in 1..MAX_PLACEHOLDER_MS && expectedMs >= 10 * 60 * 1000 && actualMs * 4 < expectedMs

