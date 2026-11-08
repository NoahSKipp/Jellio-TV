package com.jellio.tv.data.prefs

import android.view.KeyEvent

object QuickHubKeys {
    const val HOLD_TIMEOUT_MS = 450L

    // Back is needed for normal navigation, so it never fires on a short press.
    fun requiresLongPress(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_BACK

    // OK selects things everywhere, so it can never open the hub.
    fun isOkKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    fun isSystemRestrictedKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_VOICE_ASSIST,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT -> true
        else -> false
    }

    fun isValidCustomKey(keyCode: Int): Boolean =
        keyCode > KeyEvent.KEYCODE_UNKNOWN && !isSystemRestrictedKey(keyCode) && !isOkKey(keyCode)

    fun forcesLongPress(button: QuickHubButton, customKeyCode: Int): Boolean = when (button) {
        QuickHubButton.BACK -> true
        QuickHubButton.CUSTOM -> requiresLongPress(customKeyCode)
        else -> false
    }

    fun isTargetKey(keyCode: Int, button: QuickHubButton, customKeyCode: Int): Boolean = when (button) {
        QuickHubButton.PLAY_PAUSE -> keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK
        QuickHubButton.MENU -> keyCode == KeyEvent.KEYCODE_MENU
        QuickHubButton.BACK -> keyCode == KeyEvent.KEYCODE_BACK
        QuickHubButton.CUSTOM -> keyCode == customKeyCode && isValidCustomKey(customKeyCode)
        QuickHubButton.NONE -> false
    }
}

data class QuickHubKeyConfig(
    val button: QuickHubButton,
    val trigger: QuickHubTrigger,
    val customKeyCode: Int,
    /** False when the hub cannot open here (player, login, other overlays). */
    val available: Boolean,
    val hubOpen: Boolean,
)

/**
 * Decides what happens to each key event for the Quick Hub shortcut. A press is
 * either taken whole (its DOWN, repeats and UP) or left alone whole.
 */
class QuickHubKeyGate {
    enum class Action {
        /** Dispatch the event normally. */
        PASS,

        /** Drop the event. */
        CONSUME,

        /** Drop the event and toggle the hub now. */
        TOGGLE,

        /** Drop the event and start the hold timer. */
        START_HOLD,

        /** Released before the hold fired: dispatch the held DOWN and this UP as a normal press. */
        REPLAY_PRESS,
    }

    var holdingKey: Int? = null
        private set
    private var holdFired = false
    private val swallowed = mutableSetOf<Int>()

    val isHolding: Boolean get() = holdingKey != null

    fun onKey(keyCode: Int, isDown: Boolean, repeatCount: Int, isCanceled: Boolean, config: QuickHubKeyConfig): Action {
        val freshDown = isDown && repeatCount == 0

        if (keyCode in swallowed) {
            if (!freshDown) {
                if (!isDown) swallowed.remove(keyCode)
                return Action.CONSUME
            }
            // The UP of the earlier press never came; treat this as a new press.
            swallowed.remove(keyCode)
        }

        if (holdingKey == keyCode) {
            if (!freshDown) {
                if (isDown) return Action.CONSUME
                val fired = holdFired
                clearHold()
                return if (fired || isCanceled) Action.CONSUME else Action.REPLAY_PRESS
            }
            clearHold()
        }

        if (!freshDown) return Action.PASS
        if (!config.available || config.button == QuickHubButton.NONE) return Action.PASS
        if (config.hubOpen && keyCode == KeyEvent.KEYCODE_BACK) return Action.PASS
        if (!QuickHubKeys.isTargetKey(keyCode, config.button, config.customKeyCode)) return Action.PASS
        if (holdingKey != null) return Action.PASS

        val longPress = config.trigger == QuickHubTrigger.LONG_PRESS || QuickHubKeys.requiresLongPress(keyCode)
        return if (longPress) {
            holdingKey = keyCode
            holdFired = false
            Action.START_HOLD
        } else {
            swallowed.add(keyCode)
            Action.TOGGLE
        }
    }

    /** Returns true when the hub should toggle now. */
    fun onHoldTimeout(keyCode: Int, available: Boolean): Boolean {
        if (holdingKey != keyCode || holdFired || !available) return false
        holdFired = true
        return true
    }

    /** Window focus or the activity went away mid-press: drop the hold and eat what is left of it. */
    fun onFocusLost() {
        holdingKey?.let { swallowed.add(it) }
        clearHold()
    }

    /** Someone else took this DOWN; eat its repeats and UP. */
    fun swallowUntilUp(keyCode: Int) {
        swallowed.add(keyCode)
    }

    /** Someone else took this UP. */
    fun forget(keyCode: Int) {
        swallowed.remove(keyCode)
    }

    private fun clearHold() {
        holdingKey = null
        holdFired = false
    }
}
