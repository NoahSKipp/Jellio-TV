package com.jellio.tv.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recommendationPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jellio_recommendation_prefs")

@Singleton
class RecommendationPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private val listStringType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(listStringType)

    private fun communityEnabledKey(userId: String) = booleanPreferencesKey("community_row_enabled_$userId")
    private fun excludedUsersKey(userId: String) = stringPreferencesKey("community_excluded_users_$userId")
    private fun selectedGenresKey(userId: String) = stringPreferencesKey("selected_genres_$userId")

    suspend fun isCommunityRowEnabled(userId: String): Boolean =
        context.recommendationPrefsDataStore.data.map { it[communityEnabledKey(userId)] ?: true }.first()

    suspend fun setCommunityRowEnabled(userId: String, enabled: Boolean) {
        context.recommendationPrefsDataStore.edit { it[communityEnabledKey(userId)] = enabled }
    }

    suspend fun getCommunityExcludedUsers(userId: String): Set<String> {
        val raw = context.recommendationPrefsDataStore.data.map { it[excludedUsersKey(userId)] }.first() ?: return emptySet()
        return runCatching { stringListAdapter.fromJson(raw)?.toSet() }.getOrNull() ?: emptySet()
    }

    suspend fun setCommunityExcludedUsers(userId: String, excluded: Set<String>) {
        context.recommendationPrefsDataStore.edit {
            it[excludedUsersKey(userId)] = stringListAdapter.toJson(excluded.toList())
        }
    }

    suspend fun toggleCommunityExcludedUser(userId: String, targetUserId: String): Boolean {
        val current = getCommunityExcludedUsers(userId).toMutableSet()
        val willBeExcluded = if (current.contains(targetUserId)) {
            current.remove(targetUserId)
            false
        } else {
            current.add(targetUserId)
            true
        }
        setCommunityExcludedUsers(userId, current)
        return willBeExcluded
    }

    suspend fun getSelectedGenres(userId: String): List<String> {
        val raw = context.recommendationPrefsDataStore.data.map { it[selectedGenresKey(userId)] }.first() ?: return emptyList()
        return runCatching { stringListAdapter.fromJson(raw) }.getOrNull() ?: emptyList()
    }

    suspend fun setSelectedGenres(userId: String, genres: List<String>) {
        context.recommendationPrefsDataStore.edit {
            it[selectedGenresKey(userId)] = stringListAdapter.toJson(genres)
        }
    }

    suspend fun toggleSelectedGenre(userId: String, genre: String): List<String> {
        val current = getSelectedGenres(userId).toMutableList()
        if (current.contains(genre)) {
            current.remove(genre)
        } else {
            current.add(genre)
        }
        setSelectedGenres(userId, current)
        return current
    }
}
