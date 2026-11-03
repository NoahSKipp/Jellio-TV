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

    // Read and written in one edit so concurrent saves don't drop each
    // other; expired picks are dropped here, as nothing else reads them.
    suspend fun remember(itemId: String, mediaSourceId: String) {
        val now = System.currentTimeMillis()
        context.streamPrefsDataStore.edit { prefs ->
            val map = parse(prefs[Keys.REMEMBERED_SOURCES]).filterValues { now - it.timestampMs <= REMEMBER_TTL_MS }.toMutableMap()
            map[itemId] = RememberedSource(mediaSourceId, now)
            prefs[Keys.REMEMBERED_SOURCES] = adapter.toJson(map)
        }
    }

    private suspend fun readMap(): Map<String, RememberedSource> =
        parse(context.streamPrefsDataStore.data.map { it[Keys.REMEMBERED_SOURCES] }.first())

    private fun parse(raw: String?): Map<String, RememberedSource> =
        raw?.let { runCatching { adapter.fromJson(it) }.getOrNull() } ?: emptyMap()
}
