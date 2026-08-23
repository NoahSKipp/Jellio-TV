package com.jellio.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SEEK_STEP_MS = 10_000L
private const val PROGRESS_REPORT_INTERVAL_MS = 10_000L
private const val CONTROLS_HIDE_DELAY_MS = 4_000L
private const val TICKS_PER_MS = 10_000L

// No native jellyfin-web playbackManager to lean on here (screens/
// player.js's own header explains why the web build needed none
// either): a bare Media3 ExoPlayer opened straight against the real
// negotiated stream URL, this screen's own custom Compose chrome over
// it rather than PlayerView's own default controller, matching this
// app's own real design instead of stock Android TV playback UI.
@Composable
fun PlayerScreen(
    session: Session,
    itemId: String,
    mediaSourceId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(itemId, mediaSourceId) { viewModel.load(session, itemId, mediaSourceId) }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = uiState.error ?: "Could not start playback", color = JellioText)
                }
            }
            uiState.streamUrl != null -> PlayerSurface(
                streamUrl = uiState.streamUrl!!,
                startPositionTicks = uiState.startPositionTicks,
                title = uiState.title,
                subtitle = uiState.subtitle,
                onBack = onBack,
                onReportStart = { viewModel.reportStart(it) },
                onReportProgress = { positionTicks, paused -> viewModel.reportProgress(positionTicks, paused) },
                onReportStopped = { viewModel.reportStopped(it) },
            )
        }
    }
}

@Composable
private fun PlayerSurface(
    streamUrl: String,
    startPositionTicks: Long,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onReportStart: (Long) -> Unit,
    onReportProgress: (Long, Boolean) -> Unit,
    onReportStopped: (Long) -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val player = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            playWhenReady = true
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var hasReportedStart by remember { mutableStateOf(false) }
    var seekedToResume by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            onReportStopped(player.currentPosition * TICKS_PER_MS)
            player.release()
        }
    }

    LaunchedEffect(player, startPositionTicks) {
        while (isActive) {
            if (player.duration > 0 && player.playbackState != Player.STATE_IDLE) {
                durationMs = player.duration
                if (!seekedToResume && startPositionTicks > 0) {
                    seekedToResume = true
                    player.seekTo(startPositionTicks / TICKS_PER_MS)
                }
                if (!hasReportedStart) {
                    hasReportedStart = true
                    onReportStart(player.currentPosition * TICKS_PER_MS)
                }
            }
            positionMs = player.currentPosition
            delay(500)
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(PROGRESS_REPORT_INTERVAL_MS)
            if (hasReportedStart) {
                onReportProgress(player.currentPosition * TICKS_PER_MS, !player.isPlaying)
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.Back -> {
                        onBack()
                        true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        controlsVisible = true
                        player.seekTo((player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        controlsVisible = true
                        player.seekTo((player.currentPosition + SEEK_STEP_MS).coerceAtMost(player.duration.coerceAtLeast(0)))
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (controlsVisible) {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        controlsVisible = true
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        controlsVisible = true
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            PlayerControls(
                title = title,
                subtitle = subtitle,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                onBack = onBack,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
            )
        }
    }
}

@Composable
private fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 48.dp)) {
            Text(text = title, color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, color = JellioTextSecondary)
            }
        }

        Box(
            modifier = Modifier.align(Alignment.Center).size(96.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = JellioText,
                modifier = Modifier.size(48.dp),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, JellioBg)),
            ).padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))) {
                Box(
                    modifier = Modifier.fillMaxWidth(progress).height(4.dp).background(JellioText, RoundedCornerShape(2.dp)),
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(text = formatMs(positionMs), color = JellioTextSecondary)
                Text(text = " / " + formatMs(durationMs), color = JellioTextSecondary)
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
