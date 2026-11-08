package com.jellio.tv.data.prefs

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class QuickHubButton(val id: String, val labelKey: String, val subKey: String) {
    PLAY_PAUSE("play_pause", "settings_quick_hub_btn_play_pause", "settings_quick_hub_btn_play_pause_sub"),
    MENU("menu", "settings_quick_hub_btn_menu", "settings_quick_hub_btn_menu_sub"),
    BACK("back", "settings_quick_hub_btn_back", "settings_quick_hub_btn_back_sub"),
    CUSTOM("custom", "settings_quick_hub_btn_custom", "settings_quick_hub_btn_custom_sub"),
    NONE("none", "settings_quick_hub_btn_none", "settings_quick_hub_btn_none_sub");

    companion object {
        fun default(isAmazon: Boolean): QuickHubButton = if (isAmazon) PLAY_PAUSE else BACK

        // Unknown ids, including the retired "center" (OK) option, fall back to the default.
        fun fromId(id: String?, isAmazon: Boolean = false): QuickHubButton {
            if (id == null) return default(isAmazon)
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: default(isAmazon)
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
    private const val KEY_CUSTOM_KEY_CODE = "quick_hub_custom_key_code"
    private const val KEY_CUSTOM_KEY_LABEL = "quick_hub_custom_key_label"

    var button by mutableStateOf(QuickHubButton.MENU)
        private set

    var trigger by mutableStateOf(QuickHubTrigger.LONG_PRESS)
        private set

    var customKeyCode by mutableStateOf(0)
        private set

    var customKeyLabel by mutableStateOf("")
        private set

    fun getKeyDisplayName(keyCode: Int): String = when (keyCode) {
        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
        android.view.KeyEvent.KEYCODE_ENTER,
        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> "OK / Center"
        android.view.KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Play / Pause"
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> "Play"
        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> "Pause"
        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "Fast Forward"
        android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> "Rewind"
        android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> "Next Track"
        android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Previous Track"
        android.view.KeyEvent.KEYCODE_MENU -> "Menu"
        android.view.KeyEvent.KEYCODE_BACK -> "Back"
        android.view.KeyEvent.KEYCODE_GUIDE -> "Guide"
        android.view.KeyEvent.KEYCODE_INFO -> "Info"
        android.view.KeyEvent.KEYCODE_SEARCH -> "Search"
        android.view.KeyEvent.KEYCODE_CAPTIONS -> "Subtitles / Captions"
        android.view.KeyEvent.KEYCODE_CHANNEL_UP -> "Channel Up"
        android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> "Channel Down"
        android.view.KeyEvent.KEYCODE_PROG_RED -> "Red Button"
        android.view.KeyEvent.KEYCODE_PROG_GREEN -> "Green Button"
        android.view.KeyEvent.KEYCODE_PROG_YELLOW -> "Yellow Button"
        android.view.KeyEvent.KEYCODE_PROG_BLUE -> "Blue Button"
        android.view.KeyEvent.KEYCODE_BOOKMARK -> "Bookmark"
        android.view.KeyEvent.KEYCODE_SETTINGS -> "Settings"
        android.view.KeyEvent.KEYCODE_TV -> "TV"
        android.view.KeyEvent.KEYCODE_TV_INPUT -> "Input"
        android.view.KeyEvent.KEYCODE_DVR -> "DVR"
        else -> android.view.KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ').lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun isSystemRestrictedKey(keyCode: Int): Boolean = QuickHubKeys.isSystemRestrictedKey(keyCode)

    fun init(context: Context) {
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val isAmazon = android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            android.os.Build.BRAND.equals("Amazon", ignoreCase = true)

        val legacy = sp.getString("quick_hub_shortcut", null)
        val savedButton = sp.getString(KEY_BUTTON, null)
        val savedTrigger = sp.getString(KEY_TRIGGER, null)
        customKeyCode = sp.getInt(KEY_CUSTOM_KEY_CODE, 0)
        customKeyLabel = sp.getString(KEY_CUSTOM_KEY_LABEL, "") ?: ""

        val defaultButton = QuickHubButton.default(isAmazon)
        button = when {
            savedButton != null -> QuickHubButton.fromId(savedButton, isAmazon)
            legacy != null -> when {
                legacy.contains("play_pause", ignoreCase = true) -> QuickHubButton.PLAY_PAUSE
                legacy.contains("menu", ignoreCase = true) -> QuickHubButton.MENU
                legacy.contains("back", ignoreCase = true) -> QuickHubButton.BACK
                legacy.contains("none", ignoreCase = true) -> QuickHubButton.NONE
                else -> defaultButton
            }
            else -> defaultButton
        }

        // Custom keys that are no longer allowed (OK, or no usable code) are forgotten.
        if (customKeyCode != 0 && !QuickHubKeys.isValidCustomKey(customKeyCode)) {
            customKeyCode = 0
            customKeyLabel = ""
            sp.edit().remove(KEY_CUSTOM_KEY_CODE).remove(KEY_CUSTOM_KEY_LABEL).apply()
        }
        if (button == QuickHubButton.CUSTOM && !QuickHubKeys.isValidCustomKey(customKeyCode)) {
            button = defaultButton
        }
        if (savedButton != null && savedButton != button.id) {
            sp.edit().putString(KEY_BUTTON, button.id).apply()
        }

        trigger = QuickHubTrigger.fromId(savedTrigger)
        if (QuickHubKeys.forcesLongPress(button, customKeyCode)) {
            trigger = QuickHubTrigger.LONG_PRESS
        }
    }

    fun setButton(context: Context, newButton: QuickHubButton) {
        if (newButton == QuickHubButton.CUSTOM && !QuickHubKeys.isValidCustomKey(customKeyCode)) return
        button = newButton
        if (QuickHubKeys.forcesLongPress(newButton, customKeyCode)) {
            trigger = QuickHubTrigger.LONG_PRESS
        }
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_BUTTON, newButton.id)
            .putString(KEY_TRIGGER, trigger.id)
            .apply()
    }

    fun setCustomButton(context: Context, keyCode: Int, label: String, triggerType: QuickHubTrigger) {
        if (!QuickHubKeys.isValidCustomKey(keyCode)) return
        button = QuickHubButton.CUSTOM
        customKeyCode = keyCode
        customKeyLabel = label
        trigger = if (QuickHubKeys.requiresLongPress(keyCode)) QuickHubTrigger.LONG_PRESS else triggerType
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_BUTTON, QuickHubButton.CUSTOM.id)
            .putString(KEY_TRIGGER, trigger.id)
            .putInt(KEY_CUSTOM_KEY_CODE, keyCode)
            .putString(KEY_CUSTOM_KEY_LABEL, label)
            .apply()
    }

    fun setTrigger(context: Context, newTrigger: QuickHubTrigger) {
        if (newTrigger == QuickHubTrigger.SHORT_PRESS && QuickHubKeys.forcesLongPress(button, customKeyCode)) return
        trigger = newTrigger
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_TRIGGER, newTrigger.id).apply()
    }
}
