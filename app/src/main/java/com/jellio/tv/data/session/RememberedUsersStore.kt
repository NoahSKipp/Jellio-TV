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

@JsonClass(generateAdapter = true)
data class RememberedUserEntry(
    val accessToken: String,
    val name: String,
    val primaryImageTag: String? = null,
    val timestampMs: Long,
)

@Singleton
class RememberedUsersStore @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, RememberedUserEntry::class.java)
    private val adapter = moshi.adapter<Map<String, RememberedUserEntry>>(mapType)

    private fun keyFor(serverAddress: String) = stringPreferencesKey("remembered::$serverAddress")

    private fun parse(raw: String?): Map<String, RememberedUserEntry> =
        raw?.let { runCatching { adapter.fromJson(it) }.getOrNull() } ?: emptyMap()

    suspend fun getRememberedUsers(serverAddress: String): Map<String, RememberedUserEntry> =
        parse(context.rememberedUsersDataStore.data.map { it[keyFor(serverAddress)] }.first())

    suspend fun rememberUser(serverAddress: String, userId: String, entry: RememberedUserEntry) =
        update(serverAddress) { it[userId] = entry }

    suspend fun forgetUser(serverAddress: String, userId: String) =
        update(serverAddress) { it.remove(userId) }

    // Read and written in one edit, so two sign-ins at once both stick.
    private suspend fun update(serverAddress: String, change: (MutableMap<String, RememberedUserEntry>) -> Unit) {
        context.rememberedUsersDataStore.edit { prefs ->
            val map = parse(prefs[keyFor(serverAddress)]).toMutableMap()
            change(map)
            prefs[keyFor(serverAddress)] = adapter.toJson(map)
        }
    }
}
