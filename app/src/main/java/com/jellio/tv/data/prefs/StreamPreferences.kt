package com.jellio.tv.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.streamPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jellio_stream_prefs")

@JsonClass(generateAdapter = true)
data class RememberedSource(val mediaSourceId: String, val timestampMs: Long)

// Mirrors components/streamPicker.js's own real REMEMBER_ENABLED_KEY/
// REMEMBERED_SOURCES_KEY: on by default (absent reads as on, same real
// reasoning that file's own isRememberStreamEnabled() documents), one
// remembered choice per real item id rather than one single slot, and
// a choice past the same real 4 day TTL is dropped as it is found
// rather than kept around stale (a source Gelato resolved days ago is
// exactly the kind of thing real feedback called "bad").
private val REMEMBER_TTL_MS = 4L * 24 * 60 * 60 * 1000

@Singleton
class StreamPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private object Keys {
        val REMEMBER_ENABLED = booleanPreferencesKey("remember_enabled")
        val REMEMBERED_SOURCES = stringPreferencesKey("remembered_sources")
    }

    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, RememberedSource::class.java)
    private val adapter = moshi.adapter<Map<String, RememberedSource>>(mapType)

    suspend fun isRememberEnabled(): Boolean =
        context.streamPrefsDataStore.data.map { it[Keys.REMEMBER_ENABLED] ?: true }.first()

    suspend fun setRememberEnabled(enabled: Boolean) {
        context.streamPrefsDataStore.edit { it[Keys.REMEMBER_ENABLED] = enabled }
    }

    suspend fun rememberedMediaSourceId(itemId: String): String? {
        val entry = readMap()[itemId] ?: return null
        if (System.currentTimeMillis() - entry.timestampMs > REMEMBER_TTL_MS) return null
        return entry.mediaSourceId
    }

    suspend fun remember(itemId: String, mediaSourceId: String) {
        val map = readMap().toMutableMap()
        map[itemId] = RememberedSource(mediaSourceId, System.currentTimeMillis())
        writeMap(map)
    }

    private suspend fun readMap(): Map<String, RememberedSource> {
        val raw = context.streamPrefsDataStore.data.map { it[Keys.REMEMBERED_SOURCES] }.first() ?: return emptyMap()
        return runCatching { adapter.fromJson(raw) }.getOrNull() ?: emptyMap()
    }

    private suspend fun writeMap(map: Map<String, RememberedSource>) {
        context.streamPrefsDataStore.edit { it[Keys.REMEMBERED_SOURCES] = adapter.toJson(map) }
    }
}
