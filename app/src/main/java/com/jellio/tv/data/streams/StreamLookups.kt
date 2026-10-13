package com.jellio.tv.data.streams

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// Every stream list is a Gelato resolution on the server, which fans out
// to the indexers. Results are reused across screens: a list with streams
// for 8 minutes, an empty one for 2, so a title is not looked up again in
// a short window.
class SourceCache<T>(
    private val ttlMs: Long = 8 * 60_000L,
    private val emptyTtlMs: Long = 2 * 60_000L,
) {
    private class Entry<T>(val at: Long, val value: List<T>)

    private val entries = ConcurrentHashMap<String, Entry<T>>()

    fun get(key: String, now: Long): List<T>? {
        val entry = entries[key] ?: return null
        val ttl = if (entry.value.isEmpty()) emptyTtlMs else ttlMs
        if (now - entry.at > ttl) {
            entries.remove(key, entry)
            return null
        }
        return entry.value
    }

    fun put(key: String, value: List<T>, now: Long) {
        entries[key] = Entry(now, value)
    }

    fun clear() = entries.clear()
}

// Counts lookups and prefetches per screen and logs the totals when the
// screen changes. adb logcat -s JellioStreams
object StreamLookupLog {
    private const val TAG = "JellioStreams"
    private val lookups = AtomicInteger()
    private val cached = AtomicInteger()
    private val prefetches = AtomicInteger()

    @Volatile
    private var screen = "start"

    fun lookup(itemId: String, reason: String, count: Int, ms: Long) {
        lookups.incrementAndGet()
        Log.i(TAG, "resolve $itemId for $reason: $count streams in $ms ms")
    }

    fun cacheHit() {
        cached.incrementAndGet()
    }

    fun prefetch(itemId: String) {
        prefetches.incrementAndGet()
        Log.i(TAG, "prefetch $itemId")
    }

    fun screen(name: String) {
        if (name == screen) return
        Log.i(TAG, "$screen: ${lookups.getAndSet(0)} resolutions, ${prefetches.getAndSet(0)} prefetches, ${cached.getAndSet(0)} served from cache")
        screen = name
    }
}
