package com.jellio.tv.data.prefs

import android.view.KeyEvent
import com.jellio.tv.data.prefs.QuickHubKeyGate.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickHubKeyGateTest {

    private val gate = QuickHubKeyGate()

    private fun config(
        button: QuickHubButton = QuickHubButton.BACK,
        trigger: QuickHubTrigger = QuickHubTrigger.LONG_PRESS,
        customKeyCode: Int = 0,
        available: Boolean = true,
        hubOpen: Boolean = false,
    ) = QuickHubKeyConfig(button, trigger, customKeyCode, available, hubOpen)

    private fun down(code: Int, repeat: Int = 0, cfg: QuickHubKeyConfig = config()) =
        gate.onKey(code, isDown = true, repeatCount = repeat, isCanceled = false, config = cfg)

    private fun up(code: Int, canceled: Boolean = false, cfg: QuickHubKeyConfig = config()) =
        gate.onKey(code, isDown = false, repeatCount = 0, isCanceled = canceled, config = cfg)

    @Test
    fun shortBackPressIsReplayedWhole() {
        assertEquals(Action.START_HOLD, down(KeyEvent.KEYCODE_BACK))
        assertEquals(Action.REPLAY_PRESS, up(KeyEvent.KEYCODE_BACK))
        assertFalse(gate.isHolding)
    }

    @Test
    fun longBackPressOpensHubOnceAndEatsRepeatsAndUp() {
        assertEquals(Action.START_HOLD, down(KeyEvent.KEYCODE_BACK))
        assertEquals(Action.CONSUME, down(KeyEvent.KEYCODE_BACK, repeat = 1))
        assertTrue(gate.onHoldTimeout(KeyEvent.KEYCODE_BACK, available = true))
        assertFalse(gate.onHoldTimeout(KeyEvent.KEYCODE_BACK, available = true))
        val open = config(hubOpen = true)
        assertEquals(Action.CONSUME, down(KeyEvent.KEYCODE_BACK, repeat = 2, cfg = open))
        assertEquals(Action.CONSUME, down(KeyEvent.KEYCODE_BACK, repeat = 30, cfg = open))
        assertEquals(Action.CONSUME, up(KeyEvent.KEYCODE_BACK, cfg = open))
        assertFalse(gate.isHolding)
    }

    @Test
    fun backPassesThroughWhileHubIsOpen() {
        val open = config(hubOpen = true)
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_BACK, cfg = open))
        assertEquals(Action.PASS, up(KeyEvent.KEYCODE_BACK, cfg = open))
    }

    @Test
    fun canceledUpBeforeTimeoutIsDroppedNotReplayed() {
        down(KeyEvent.KEYCODE_MENU, cfg = config(QuickHubButton.MENU))
        assertEquals(Action.CONSUME, up(KeyEvent.KEYCODE_MENU, canceled = true, cfg = config(QuickHubButton.MENU)))
    }

    @Test
    fun lostUpDoesNotEatTheNextPress() {
        down(KeyEvent.KEYCODE_BACK)
        assertTrue(gate.onHoldTimeout(KeyEvent.KEYCODE_BACK, available = true))
        // UP never arrives. The next press starts a fresh hold and a short release goes back.
        assertEquals(Action.START_HOLD, down(KeyEvent.KEYCODE_BACK))
        assertEquals(Action.REPLAY_PRESS, up(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun focusLossDropsHoldAndEatsTheRestOfThePress() {
        down(KeyEvent.KEYCODE_BACK)
        gate.onFocusLost()
        assertFalse(gate.isHolding)
        assertFalse(gate.onHoldTimeout(KeyEvent.KEYCODE_BACK, available = true))
        assertEquals(Action.CONSUME, down(KeyEvent.KEYCODE_BACK, repeat = 3))
        assertEquals(Action.CONSUME, up(KeyEvent.KEYCODE_BACK, canceled = true))
        assertEquals(Action.START_HOLD, down(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun holdThatOutlivesAvailabilityStillCompletes() {
        val menu = config(QuickHubButton.MENU)
        assertEquals(Action.START_HOLD, down(KeyEvent.KEYCODE_MENU, cfg = menu))
        // The player opened mid-hold: the hold no longer fires but its UP stays paired.
        assertFalse(gate.onHoldTimeout(KeyEvent.KEYCODE_MENU, available = false))
        val gone = config(QuickHubButton.MENU, available = false)
        assertEquals(Action.CONSUME, down(KeyEvent.KEYCODE_MENU, repeat = 1, cfg = gone))
        assertEquals(Action.REPLAY_PRESS, up(KeyEvent.KEYCODE_MENU, cfg = gone))
    }

    @Test
    fun unavailableHubLeavesKeysAlone() {
        val gone = config(QuickHubButton.PLAY_PAUSE, available = false)
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, cfg = gone))
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, repeat = 1, cfg = gone))
        assertEquals(Action.PASS, up(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, cfg = gone))
    }

    @Test
    fun upWhoseDownWasNotTakenPasses() {
        val menu = config(QuickHubButton.MENU)
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_MENU, repeat = 4, cfg = menu))
        assertEquals(Action.PASS, up(KeyEvent.KEYCODE_MENU, cfg = menu))
    }

    @Test
    fun shortPressTogglesOnceAndEatsRepeatsAndUp() {
        val menu = config(QuickHubButton.MENU, QuickHubTrigger.SHORT_PRESS)
        assertEquals(Action.TOGGLE, down(KeyEvent.KEYCODE_MENU, cfg = menu))
        val open = menu.copy(hubOpen = true)
        assertEquals(Action.CONSUME, down(KeyEvent.KEYCODE_MENU, repeat = 1, cfg = open))
        assertEquals(Action.CONSUME, up(KeyEvent.KEYCODE_MENU, cfg = open))
        assertEquals(Action.TOGGLE, down(KeyEvent.KEYCODE_MENU, cfg = open))
    }

    @Test
    fun shortPressIsIgnoredForBack() {
        val back = config(QuickHubButton.BACK, QuickHubTrigger.SHORT_PRESS)
        assertEquals(Action.START_HOLD, down(KeyEvent.KEYCODE_BACK, cfg = back))
        assertEquals(Action.REPLAY_PRESS, up(KeyEvent.KEYCODE_BACK, cfg = back))
        val custom = config(QuickHubButton.CUSTOM, QuickHubTrigger.SHORT_PRESS, KeyEvent.KEYCODE_BACK)
        assertEquals(Action.START_HOLD, down(KeyEvent.KEYCODE_BACK, cfg = custom))
    }

    @Test
    fun okKeysNeverOpenTheHub() {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            for (trigger in QuickHubTrigger.entries) {
                val custom = config(QuickHubButton.CUSTOM, trigger, code)
                assertEquals(Action.PASS, down(code, cfg = custom))
                assertEquals(Action.PASS, up(code, cfg = custom))
            }
            for (button in QuickHubButton.entries) {
                assertFalse(QuickHubKeys.isTargetKey(code, button, code))
            }
        }
    }

    @Test
    fun otherKeysPassWhileHolding() {
        val menu = config(QuickHubButton.MENU)
        down(KeyEvent.KEYCODE_MENU, cfg = menu)
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_DPAD_DOWN, cfg = menu))
        assertEquals(Action.PASS, up(KeyEvent.KEYCODE_DPAD_DOWN, cfg = menu))
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_DPAD_CENTER, cfg = menu))
        assertEquals(Action.PASS, up(KeyEvent.KEYCODE_DPAD_CENTER, cfg = menu))
        assertTrue(gate.isHolding)
        assertEquals(Action.REPLAY_PRESS, up(KeyEvent.KEYCODE_MENU, cfg = menu))
    }

    @Test
    fun customKeyMustBeValid() {
        val unknown = config(QuickHubButton.CUSTOM, customKeyCode = KeyEvent.KEYCODE_UNKNOWN)
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_UNKNOWN, cfg = unknown))
        val red = config(QuickHubButton.CUSTOM, QuickHubTrigger.SHORT_PRESS, KeyEvent.KEYCODE_PROG_RED)
        assertEquals(Action.TOGGLE, down(KeyEvent.KEYCODE_PROG_RED, cfg = red))
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_PROG_GREEN, cfg = red))
    }

    @Test
    fun disabledShortcutPassesEverything() {
        val none = config(QuickHubButton.NONE)
        assertEquals(Action.PASS, down(KeyEvent.KEYCODE_BACK, cfg = none))
        assertEquals(Action.PASS, up(KeyEvent.KEYCODE_BACK, cfg = none))
    }

    @Test
    fun keyTakenElsewhereIsSwallowedUntilUp() {
        gate.swallowUntilUp(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(Action.CONSUME, down(KeyEvent.KEYCODE_DPAD_CENTER, repeat = 1))
        assertEquals(Action.CONSUME, up(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(Action.PASS, up(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun forgetDropsSwallow() {
        gate.swallowUntilUp(KeyEvent.KEYCODE_MENU)
        gate.forget(KeyEvent.KEYCODE_MENU)
        assertEquals(Action.PASS, up(KeyEvent.KEYCODE_MENU))
    }

    @Test
    fun longPressRules() {
        assertTrue(QuickHubKeys.forcesLongPress(QuickHubButton.BACK, 0))
        assertTrue(QuickHubKeys.forcesLongPress(QuickHubButton.CUSTOM, KeyEvent.KEYCODE_BACK))
        assertFalse(QuickHubKeys.forcesLongPress(QuickHubButton.CUSTOM, KeyEvent.KEYCODE_PROG_RED))
        // A stale custom code must not lock other buttons to long press.
        assertFalse(QuickHubKeys.forcesLongPress(QuickHubButton.MENU, KeyEvent.KEYCODE_BACK))
        assertFalse(QuickHubKeys.isValidCustomKey(KeyEvent.KEYCODE_UNKNOWN))
        assertFalse(QuickHubKeys.isValidCustomKey(KeyEvent.KEYCODE_HOME))
        assertFalse(QuickHubKeys.isValidCustomKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(QuickHubKeys.isValidCustomKey(KeyEvent.KEYCODE_ENTER))
        assertFalse(QuickHubKeys.isValidCustomKey(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertTrue(QuickHubKeys.isValidCustomKey(KeyEvent.KEYCODE_INFO))
    }

    @Test
    fun retiredOkButtonFallsBackToTheDefault() {
        assertEquals(QuickHubButton.BACK, QuickHubButton.fromId("center"))
        assertEquals(QuickHubButton.PLAY_PAUSE, QuickHubButton.fromId("center", isAmazon = true))
        assertEquals(QuickHubButton.MENU, QuickHubButton.fromId("menu"))
    }
}
