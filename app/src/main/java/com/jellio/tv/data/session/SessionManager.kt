package com.jellio.tv.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jellio_session")

data class Session(
    val serverAddress: String,
    val accessToken: String,
    val userId: String,
    val userName: String,
)

// Mirrors frontend/runtime/auth.js's own real shape: a token store
// independent of anything else, real Jellyfin login, no native
// ApiClient equivalent here to defer to. DataStore instead of
// localStorage, same real job.
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SERVER_ADDRESS = stringPreferencesKey("server_address")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    val sessionFlow: Flow<Session?> = context.dataStore.data.map { prefs ->
        val serverAddress = prefs[Keys.SERVER_ADDRESS]
        val accessToken = prefs[Keys.ACCESS_TOKEN]
        val userId = prefs[Keys.USER_ID]
        val userName = prefs[Keys.USER_NAME]
        if (serverAddress != null && accessToken != null && userId != null && userName != null) {
            Session(serverAddress, accessToken, userId, userName)
        } else {
            null
        }
    }.distinctUntilChanged()

    // Read by NetworkModule's own interceptors, which run on OkHttp's
    // dispatcher threads rather than a coroutine scope: DataStore
    // caches the parsed Preferences in memory after its first real
    // disk read, so this suspend call is fast on every call after
    // that, not a fresh disk hit each time.
    suspend fun serverAddress(): String? = context.dataStore.data.map { it[Keys.SERVER_ADDRESS] }.first()

    suspend fun accessToken(): String? = context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }.first()

    suspend fun deviceId(): String {
        val existing = context.dataStore.data.map { it[Keys.DEVICE_ID] }.first()
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun saveServerAddress(serverAddress: String) {
        context.dataStore.edit { it[Keys.SERVER_ADDRESS] = serverAddress }
    }

    suspend fun saveSession(serverAddress: String, accessToken: String, userId: String, userName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_ADDRESS] = serverAddress
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USER_NAME] = userName
        }
    }

    // Drops the token/identity, keeps the server address: the reader
    // is signing out of an account, not un-configuring which server
    // this box talks to, same real distinction auth.js's own sign out
    // draws.
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_NAME)
        }
    }
}
