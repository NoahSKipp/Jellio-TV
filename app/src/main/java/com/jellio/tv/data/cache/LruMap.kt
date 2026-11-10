package com.jellio.tv.data.cache

import java.util.Collections

// Drops the least recently read or written entry once it holds more than maxSize.
// Single calls are synchronized; iterate inside synchronized(map) if ever needed.
internal fun <K, V> lruMap(maxSize: Int): MutableMap<K, V> {
    require(maxSize > 0) { "maxSize must be positive" }
    return Collections.synchronizedMap(
        object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
        },
    )
}
