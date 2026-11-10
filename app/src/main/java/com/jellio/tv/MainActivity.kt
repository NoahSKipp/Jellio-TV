package com.jellio.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Surface
import com.jellio.tv.data.prefs.QuickHubKeyConfig
import com.jellio.tv.data.prefs.QuickHubKeyGate
import com.jellio.tv.data.prefs.QuickHubKeys
import com.jellio.tv.data.prefs.QuickHubPreferences
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioTvTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val deepLinkItemId = mutableStateOf<String?>(null)
    private var isPlayerActive: Boolean = false
    var isQuickHubOpen: Boolean = false
    var isRecordingShortcut: Boolean = false
    var onShortcutKeyRecorded: ((Int, KeyEvent) -> Boolean)? = null
    var onQuickHubShortcut: (() -> Unit)? = null
    var isQuickHubSuppressed: Boolean = false

    private val quickHubGate = QuickHubKeyGate()
    private var quickHubHoldJob: kotlinx.coroutines.Job? = null
    private var quickHubHeldDown: KeyEvent? = null

    private fun quickHubAvailable(): Boolean = !isPlayerActive && !isQuickHubSuppressed && onQuickHubShortcut != null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN
        if (!isDown && event.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event)

        if (isRecordingShortcut) {
            if (onShortcutKeyRecorded?.invoke(keyCode, event) == true) {
                // Recording ends on the DOWN; its repeats and UP must not reach the dialog's buttons.
                if (isDown) quickHubGate.swallowUntilUp(keyCode) else quickHubGate.forget(keyCode)
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        StartupPrefs.awaitBlocking()
        val action = quickHubGate.onKey(
            keyCode = keyCode,
            isDown = isDown,
            repeatCount = event.repeatCount,
            isCanceled = event.isCanceled,
            config = QuickHubKeyConfig(
                button = QuickHubPreferences.button,
                trigger = QuickHubPreferences.trigger,
                customKeyCode = QuickHubPreferences.customKeyCode,
                available = quickHubAvailable(),
                hubOpen = isQuickHubOpen,
            ),
        )
        val heldDown = quickHubHeldDown
        if (!quickHubGate.isHolding) cancelQuickHubHold()

        return when (action) {
            QuickHubKeyGate.Action.PASS -> super.dispatchKeyEvent(event)
            QuickHubKeyGate.Action.CONSUME -> true
            QuickHubKeyGate.Action.TOGGLE -> {
                onQuickHubShortcut?.invoke()
                true
            }
            QuickHubKeyGate.Action.START_HOLD -> {
                quickHubHoldJob?.cancel()
                // Input events are recycled after dispatch, so keep a copy for a possible replay.
                quickHubHeldDown = KeyEvent(event)
                quickHubHoldJob = lifecycleScope.launch {
                    delay(QuickHubKeys.HOLD_TIMEOUT_MS)
                    if (quickHubGate.onHoldTimeout(keyCode, quickHubAvailable())) onQuickHubShortcut?.invoke()
                }
                true
            }
            QuickHubKeyGate.Action.REPLAY_PRESS -> {
                replayPress(heldDown ?: KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN), event)
                true
            }
        }
    }

    private fun replayPress(down: KeyEvent, up: KeyEvent) {
        if (up.keyCode == KeyEvent.KEYCODE_BACK) {
            // Compose key handlers (dismissOnBack, settings content) see Back first, as they would without the hold.
            val handled = window.superDispatchKeyEvent(down) or window.superDispatchKeyEvent(up)
            if (!handled) onBackPressedDispatcher.onBackPressed()
        } else {
            super.dispatchKeyEvent(down)
            super.dispatchKeyEvent(up)
        }
    }

    private fun cancelQuickHubHold() {
        quickHubHoldJob?.cancel()
        quickHubHoldJob = null
        quickHubHeldDown = null
    }

    private fun resetQuickHubGesture() {
        cancelQuickHubHold()
        quickHubGate.onFocusLost()
    }

    override fun onPause() {
        super.onPause()
        resetQuickHubGesture()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) resetQuickHubGesture()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                try {
                    enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
                } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The first frame reads motion, overscan and language; the rest load meanwhile.
        com.jellio.tv.ui.perf.MotionPolicy.init(this)
        com.jellio.tv.data.prefs.DisplayPreferences.init(this)
        com.jellio.tv.data.playback.DeviceDecoderCaps.attach(this)
        StartupPrefs.start(this)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            JellioTvTheme {
                val prefetchViewModel: com.jellio.tv.ui.home.GelatoPrefetchViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                val prefetchLambda = androidx.compose.runtime.remember(prefetchViewModel) {
                    { item: com.jellio.tv.data.model.BaseItemDto -> prefetchViewModel.prefetch(item) }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.jellio.tv.ui.home.LocalGelatoPrefetch provides prefetchLambda,
                    androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.jellio.tv.ui.common.TvBringIntoViewSpec,
                ) {
                    val overscanPercent = com.jellio.tv.data.prefs.DisplayPreferences.overscanPercent
                    val isCalibrating = com.jellio.tv.data.prefs.DisplayPreferences.isCalibrating
                    val config = androidx.compose.ui.platform.LocalConfiguration.current
                    val insetX = if (isCalibrating) 0.dp else (config.screenWidthDp * (overscanPercent / 100f) / 2f).dp
                    val insetY = if (isCalibrating) 0.dp else (config.screenHeightDp * (overscanPercent / 100f) / 2f).dp
                    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                        // One dark ground behind every screen and the sidebar alike.
                        Surface(
                            colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = JellioBg),
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black)
                                .padding(horizontal = insetX, vertical = insetY),
                        ) {
                            JellioTvRoot(
                                deepLinkItemId = deepLinkItemId.value,
                                onDeepLinkConsumed = { deepLinkItemId.value = null },
                                onPlayerActiveChanged = { isPlayerActive = it },
                            )
                        }

                        if (isCalibrating) {
                            com.jellio.tv.ui.settings.OverscanCalibrationModal(
                                onDismiss = { com.jellio.tv.data.prefs.DisplayPreferences.isCalibrating = false },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.host != "jellio.tv") return
        // Launcher entries open the title's page. /play links from older
        // syncs, still on the home screen, do the same instead of playing.
        when (uri.path) {
            "/play", "/item" -> uri.getQueryParameter("id")?.let { deepLinkItemId.value = "detail:$it" }
        }
    }
}
