package com.jellio.tv.data.prefs

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class QuickHubShortcut(val id: String, val labelKey: String) {
    LONG_PRESS_PLAY_PAUSE("long_press_play_pause", "settings_quick_hub_shortcut_play_pause"),
    LONG_PRESS_MENU("long_press_menu", "settings_quick_hub_shortcut_menu"),
    LONG_PRESS_CENTER("long_press_center", "settings_quick_hub_shortcut_center"),
    NONE("none", "settings_quick_hub_shortcut_none");

    fun next(): QuickHubShortcut {
        val all = entries
        val nextIndex = (ordinal + 1) % all.size
        return all[nextIndex]
    }

    companion object {
        fun fromId(id: String?, isAmazon: Boolean = false): QuickHubShortcut {
            val defaultShortcut = if (isAmazon) LONG_PRESS_PLAY_PAUSE else LONG_PRESS_MENU
            if (id == null) return defaultShortcut
            return when (id.lowercase()) {
                "long_press_play_pause", "play_pause" -> LONG_PRESS_PLAY_PAUSE
                "long_press_menu", "menu" -> LONG_PRESS_MENU
                "long_press_center", "center" -> LONG_PRESS_CENTER
                "none" -> NONE
                else -> defaultShortcut
            }
        }
    }
}

object QuickHubPreferences {
    private const val PREFS = "jellio_quick_hub"
    private const val KEY_SHORTCUT = "quick_hub_shortcut"

    var shortcut by mutableStateOf(QuickHubShortcut.LONG_PRESS_MENU)
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val isAmazon = android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            android.os.Build.BRAND.equals("Amazon", ignoreCase = true)
        val saved = sp.getString(KEY_SHORTCUT, null)
        shortcut = QuickHubShortcut.fromId(saved, isAmazon)
    }

    fun setShortcut(context: Context, newShortcut: QuickHubShortcut) {
        shortcut = newShortcut
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SHORTCUT, newShortcut.id).apply()
    }
}
