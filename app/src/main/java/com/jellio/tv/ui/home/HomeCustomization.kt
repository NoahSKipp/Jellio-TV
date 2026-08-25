package com.jellio.tv.ui.home

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.homeCustomizationDataStore: DataStore<Preferences> by preferencesDataStore(name = "jellio_home_customization")

// Real components/homeCustomizer.js's own defaultCustomization()
// shape, the same real { order, hidden } pair that file's own
// STORAGE_KEY JSON blob holds.
@JsonClass(generateAdapter = true)
data class HomeCustomizationDto(
    val order: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
)

// Ported in spirit from Harbor's own real home row customization
// (components/homeCustomizer.js's own header names the real upstream
// source and settles the real scope: reorder, hide and reset a row's
// own position, no per-row rename, no Top 10 numerals, nothing else
// Harbor's own fuller feature carries that this runtime has no
// equivalent concept to attach to). Pure functions here, no storage
// side effects, mirroring that file's own effectiveOrder()/
// moveRowKey()/toggleRowHiddenKey() exactly; HomeViewModel owns
// actually persisting the result through HomeCustomizationStore below.
object HomeCustomization {
    // Real port of effectiveOrder(): whatever this session's own rows
    // actually are right now, filtered through whatever order this
    // reader saved last time (any stored key no longer live today
    // silently dropped), any live key never seen before appended after
    // it in its own natural order rather than lost.
    fun effectiveOrder(liveKeys: List<String>, customization: HomeCustomizationDto): List<String> {
        val liveSet = liveKeys.toSet()
        val ordered = customization.order.filter { liveSet.contains(it) }.toMutableList()
        val seen = ordered.toHashSet()
        liveKeys.forEach { key -> if (seen.add(key)) ordered.add(key) }
        return ordered
    }

    fun moveKey(customization: HomeCustomizationDto, liveKeys: List<String>, key: String, delta: Int): HomeCustomizationDto {
        val order = effectiveOrder(liveKeys, customization).toMutableList()
        val idx = order.indexOf(key)
        if (idx < 0) return customization
        val target = idx + delta
        if (target < 0 || target >= order.size) return customization
        val tmp = order[idx]
        order[idx] = order[target]
        order[target] = tmp
        return customization.copy(order = order)
    }

    fun toggleHidden(customization: HomeCustomizationDto, key: String): HomeCustomizationDto {
        val hidden = if (customization.hidden.contains(key)) {
            customization.hidden - key
        } else {
            customization.hidden + key
        }
        return customization.copy(hidden = hidden)
    }
}

// Real localStorage STORAGE_KEY equivalent: DataStore Preferences
// instead of localStorage for the same real reason SessionManager's
// own header already documents (no browser storage to reuse here),
// same real single-JSON-blob shape kept rather than splitting order/
// hidden into two separate keys.
@Singleton
class HomeCustomizationStore @Inject constructor(
    @ApplicationContext context: Context,
    private val moshi: Moshi,
) {
    private val dataStore = context.homeCustomizationDataStore
    private val storageKey = stringPreferencesKey("customization")
    private val adapter = moshi.adapter(HomeCustomizationDto::class.java)

    suspend fun load(): HomeCustomizationDto {
        val raw = dataStore.data.first()[storageKey] ?: return HomeCustomizationDto()
        return runCatching { adapter.fromJson(raw) }.getOrNull() ?: HomeCustomizationDto()
    }

    suspend fun save(customization: HomeCustomizationDto) {
        dataStore.edit { it[storageKey] = adapter.toJson(customization) }
    }

    suspend fun reset() {
        dataStore.edit { it[storageKey] = adapter.toJson(HomeCustomizationDto()) }
    }
}
