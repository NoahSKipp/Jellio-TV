package com.jellio.tv.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var removeFromWatchlistAfterWatching: Boolean
        get() = prefs.getBoolean(KEY_REMOVE_FROM_WATCHLIST_AFTER_WATCHING, true)
        set(value) {
            prefs.edit().putBoolean(KEY_REMOVE_FROM_WATCHLIST_AFTER_WATCHING, value).apply()
        }

    fun isRemoveFromWatchlistAfterWatchingEnabled(): Boolean = removeFromWatchlistAfterWatching

    companion object {
        private const val PREFS_NAME = "jellio_watchlist"
        const val KEY_REMOVE_FROM_WATCHLIST_AFTER_WATCHING = "remove_from_watchlist_after_watching"
    }
}
