package com.jellio.tv.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jellio.tv.data.model.SUBTITLE_BACKGROUNDS
import com.jellio.tv.data.model.SUBTITLE_SIZES
import com.jellio.tv.data.model.SubtitleStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.subtitleStyleDataStore: DataStore<Preferences> by preferencesDataStore(name = "jellio_subtitle_style")

// Mirrors screens/player.js's own SUBTITLE_STYLE_KEY: plain client
// only persistence, same real reasoning that file's own
// loadSubtitleStyle() header documents, a display preference nothing
// server side needs to know about. DataStore instead of localStorage,
// the same real job StreamPreferences already gives the other client
// only preference in this codebase.
@Singleton
class SubtitleStylePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val SIZE = stringPreferencesKey("size")
        val BACKGROUND = stringPreferencesKey("background")
    }

    val style: Flow<SubtitleStyle> = context.subtitleStyleDataStore.data.map { prefs ->
        SubtitleStyle(
            size = prefs[Keys.SIZE]?.takeIf { v -> SUBTITLE_SIZES.any { it.value == v } } ?: "medium",
            background = prefs[Keys.BACKGROUND]?.takeIf { v -> SUBTITLE_BACKGROUNDS.any { it.value == v } } ?: "semi",
        )
    }

    suspend fun setSize(size: String) {
        context.subtitleStyleDataStore.edit { it[Keys.SIZE] = size }
    }

    suspend fun setBackground(background: String) {
        context.subtitleStyleDataStore.edit { it[Keys.BACKGROUND] = background }
    }
}
