package com.jellio.tv.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    private val syncPrefs = context.getSharedPreferences("jellio_session_sync", Context.MODE_PRIVATE)

    @Volatile
    private var cachedServerAddress: String? = syncPrefs.getString("server_address", null)

    @Volatile
    private var cachedAccessToken: String? = syncPrefs.getString("access_token", null)

    @Volatile
    private var cachedDeviceId: String = syncPrefs.getString("device_id", null) ?: run {
        val generated = UUID.randomUUID().toString()
        syncPrefs.edit().putString("device_id", generated).apply()
        generated
    }

    init {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            context.dataStore.data.collect { prefs ->
                val server = prefs[Keys.SERVER_ADDRESS] ?: cachedServerAddress
                val token = prefs[Keys.ACCESS_TOKEN] ?: cachedAccessToken
                val device = prefs[Keys.DEVICE_ID] ?: cachedDeviceId
                cachedServerAddress = server
                cachedAccessToken = token
                cachedDeviceId = device
                syncPrefs.edit()
                    .putString("server_address", server)
                    .putString("access_token", token)
                    .putString("device_id", device)
                    .apply()
            }
        }
    }

    fun getCachedServerAddress(): String? = cachedServerAddress ?: syncPrefs.getString("server_address", null)
    fun getCachedAccessToken(): String? = cachedAccessToken ?: syncPrefs.getString("access_token", null)
    fun getCachedDeviceId(): String = cachedDeviceId

    val sessionFlow: Flow<Session?> = context.dataStore.data.map { prefs ->
        val serverAddress = prefs[Keys.SERVER_ADDRESS] ?: cachedServerAddress
        val accessToken = prefs[Keys.ACCESS_TOKEN] ?: cachedAccessToken
        val userId = prefs[Keys.USER_ID] ?: syncPrefs.getString("user_id", null)
        val userName = prefs[Keys.USER_NAME] ?: syncPrefs.getString("user_name", null)
        if (serverAddress != null && accessToken != null && userId != null && userName != null) {
            Session(serverAddress, accessToken, userId, userName)
        } else {
            null
        }
    }.distinctUntilChanged()

    suspend fun serverAddress(): String? {
        cachedServerAddress?.let { return it }
        val value = syncPrefs.getString("server_address", null)
            ?: context.dataStore.data.map { it[Keys.SERVER_ADDRESS] }.first()
        cachedServerAddress = value
        return value
    }

    suspend fun accessToken(): String? {
        cachedAccessToken?.let { return it }
        val value = syncPrefs.getString("access_token", null)
            ?: context.dataStore.data.map { it[Keys.ACCESS_TOKEN] }.first()
        cachedAccessToken = value
        return value
    }

    suspend fun deviceId(): String {
        return cachedDeviceId
    }

    suspend fun saveServerAddress(serverAddress: String) {
        val normalized = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        cachedServerAddress = normalized
        syncPrefs.edit().putString("server_address", normalized).apply()
        context.dataStore.edit { it[Keys.SERVER_ADDRESS] = normalized }
    }

    suspend fun saveSession(serverAddress: String, accessToken: String, userId: String, userName: String) {
        val normalized = com.jellio.tv.di.normalizeServerAddress(serverAddress)
        cachedServerAddress = normalized
        cachedAccessToken = accessToken
        syncPrefs.edit()
            .putString("server_address", normalized)
            .putString("access_token", accessToken)
            .putString("user_id", userId)
            .putString("user_name", userName)
            .putString("device_id", cachedDeviceId)
            .apply()
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_ADDRESS] = normalized
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.USER_ID] = userId
            prefs[Keys.USER_NAME] = userName
            prefs[Keys.DEVICE_ID] = cachedDeviceId
        }
    }

    suspend fun clearSession() {
        cachedAccessToken = null
        syncPrefs.edit()
            .remove("access_token")
            .remove("user_id")
            .remove("user_name")
            .apply()
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_NAME)
        }
    }
}
