package com.jellio.tv.data.prefs

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class QuickHubButton(val id: String, val labelKey: String, val subKey: String) {
    CENTER("center", "settings_quick_hub_btn_ok", "settings_quick_hub_btn_ok_sub"),
    PLAY_PAUSE("play_pause", "settings_quick_hub_btn_play_pause", "settings_quick_hub_btn_play_pause_sub"),
    MENU("menu", "settings_quick_hub_btn_menu", "settings_quick_hub_btn_menu_sub"),
    BACK("back", "settings_quick_hub_btn_back", "settings_quick_hub_btn_back_sub"),
    NONE("none", "settings_quick_hub_btn_none", "settings_quick_hub_btn_none_sub");

    companion object {
        fun fromId(id: String?, isAmazon: Boolean = false): QuickHubButton {
            val defaultBtn = if (isAmazon) PLAY_PAUSE else MENU
            if (id == null) return defaultBtn
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: defaultBtn
        }
    }
}

enum class QuickHubTrigger(val id: String, val labelKey: String, val subKey: String) {
    LONG_PRESS("long_press", "settings_quick_hub_trigger_long", "settings_quick_hub_trigger_long_sub"),
    SHORT_PRESS("short_press", "settings_quick_hub_trigger_short", "settings_quick_hub_trigger_short_sub");

    companion object {
        fun fromId(id: String?): QuickHubTrigger {
            if (id == null) return LONG_PRESS
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: LONG_PRESS
        }
    }
}

object QuickHubPreferences {
    private const val PREFS = "jellio_quick_hub"
    private const val KEY_BUTTON = "quick_hub_button"
    private const val KEY_TRIGGER = "quick_hub_trigger"

    var button by mutableStateOf(QuickHubButton.MENU)
        private set

    var trigger by mutableStateOf(QuickHubTrigger.LONG_PRESS)
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val isAmazon = android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            android.os.Build.BRAND.equals("Amazon", ignoreCase = true)

        val legacy = sp.getString("quick_hub_shortcut", null)
        val savedButton = sp.getString(KEY_BUTTON, null)
        val savedTrigger = sp.getString(KEY_TRIGGER, null)

        button = when {
            savedButton != null -> QuickHubButton.fromId(savedButton, isAmazon)
            legacy != null -> when {
                legacy.contains("play_pause", ignoreCase = true) -> QuickHubButton.PLAY_PAUSE
                legacy.contains("menu", ignoreCase = true) -> QuickHubButton.MENU
                legacy.contains("center", ignoreCase = true) -> QuickHubButton.CENTER
                legacy.contains("none", ignoreCase = true) -> QuickHubButton.NONE
                else -> if (isAmazon) QuickHubButton.PLAY_PAUSE else QuickHubButton.MENU
            }
            else -> if (isAmazon) QuickHubButton.PLAY_PAUSE else QuickHubButton.MENU
        }

        trigger = QuickHubTrigger.fromId(savedTrigger)
        if (button == QuickHubButton.CENTER || button == QuickHubButton.BACK) {
            trigger = QuickHubTrigger.LONG_PRESS
        }
    }

    fun setButton(context: Context, newButton: QuickHubButton) {
        button = newButton
        if (newButton == QuickHubButton.CENTER || newButton == QuickHubButton.BACK) {
            trigger = QuickHubTrigger.LONG_PRESS
        }
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_BUTTON, newButton.id)
            .putString(KEY_TRIGGER, trigger.id)
            .apply()
    }

    fun setTrigger(context: Context, newTrigger: QuickHubTrigger) {
        if (newTrigger == QuickHubTrigger.SHORT_PRESS && (button == QuickHubButton.CENTER || button == QuickHubButton.BACK)) {
            return
        }
        trigger = newTrigger
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_TRIGGER, newTrigger.id).apply()
    }
}
