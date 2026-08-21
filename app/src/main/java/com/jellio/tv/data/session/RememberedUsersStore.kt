package com.jellio.tv.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

private val Context.rememberedUsersDataStore: DataStore<Preferences> by preferencesDataStore(name = "jellio_remembered_users")

// Real port of runtime/auth.js's own {userId: {accessToken, name,
// primaryImageTag, ts}} shape (rememberUser()/getRememberedUsers()).
@JsonClass(generateAdapter = true)
data class RememberedUserEntry(
    val accessToken: String,
    val name: String,
    val primaryImageTag: String? = null,
    val timestampMs: Long,
)

// Mirrors runtime/auth.js's own rememberedKey(): keyed per real server
// address, the same real reason that file's own header gives (a
// shared device signed into more than one real server keeps a
// separate list for each rather than one list bleeding across
// servers). DataStore instead of localStorage, the same real job
// StreamPreferences/HomeCustomizationStore already give other client
// only state in this codebase.
@Singleton
class RememberedUsersStore @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, RememberedUserEntry::class.java)
    private val adapter = moshi.adapter<Map<String, RememberedUserEntry>>(mapType)

    private fun keyFor(serverAddress: String) = stringPreferencesKey("remembered::$serverAddress")

    suspend fun getRememberedUsers(serverAddress: String): Map<String, RememberedUserEntry> {
        val raw = context.rememberedUsersDataStore.data.map { it[keyFor(serverAddress)] }.first() ?: return emptyMap()
        return runCatching { adapter.fromJson(raw) }.getOrNull() ?: emptyMap()
    }

    suspend fun rememberUser(serverAddress: String, userId: String, entry: RememberedUserEntry) {
        val map = getRememberedUsers(serverAddress).toMutableMap()
        map[userId] = entry
        context.rememberedUsersDataStore.edit { it[keyFor(serverAddress)] = adapter.toJson(map) }
    }

    suspend fun forgetUser(serverAddress: String, userId: String) {
        val map = getRememberedUsers(serverAddress).toMutableMap()
        map.remove(userId)
        context.rememberedUsersDataStore.edit { it[keyFor(serverAddress)] = adapter.toJson(map) }
    }
}
