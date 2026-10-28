package com.jellio.tv.ui.live

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.network.APP_VERSION
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.ScreenSpinner
import com.jellio.tv.ui.common.primaryButtonColors
import com.jellio.tv.ui.i18n.Strings
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay

private const val INFO_VISIBLE_MS = 5_000L

@OptIn(UnstableApi::class)
@Composable
fun LivePlayerScreen(
    session: Session,
    channelId: String,
    imageUrl: (String, String?) -> String,
    onBack: () -> Unit,
    viewModel: LivePlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current
    val focus = remember { FocusRequester() }

    LaunchedEffect(channelId) { viewModel.start(session, channelId) }

    val serverHeaders = remember(session.accessToken) {
        mapOf(
            "X-Emby-Token" to session.accessToken,
            "Authorization" to buildEmbyAuthorizationHeader(viewModel.deviceId, APP_VERSION, session.accessToken),
        )
    }
    val dataSource = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Jellio-TV/$APP_VERSION")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            // Tuners can go quiet for several seconds; let the read wait it out.
            .setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(serverHeaders)
    }

    val player = remember {
        // Start on a small buffer, keep a deeper one to ride out stalls.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build()
            .apply {
                setWakeMode(C.WAKE_MODE_NETWORK)
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        var renderedFirst = false
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (!renderedFirst) {
                    renderedFirst = true
                    viewModel.onFirstFrame()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> viewModel.onBuffering(true)
                    Player.STATE_READY -> viewModel.onBuffering(false)
                    Player.STATE_ENDED -> viewModel.onPlayerError("ended")
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlayerError(error.errorCodeName)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                renderedFirst = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            viewModel.stop()
        }
    }

    LaunchedEffect(uiState.streamUrl) {
        val url = uiState.streamUrl
        if (url == null) {
            player.stop()
            player.clearMediaItems()
        } else {
            // Headers are chosen per stream: the token only ever goes to the server.
            val headers = uiState.requestHeaders
            dataSource.setDefaultRequestProperties(headers ?: serverHeaders)
            dataSource.setUserAgent(headers?.get("User-Agent") ?: "Jellio-TV/$APP_VERSION")
            val mime = if (headers == null) MimeTypes.VIDEO_MP2T else uiState.mimeType
            player.setMediaItem(MediaItem.Builder().setUri(url).apply { if (mime != null) setMimeType(mime) }.build())
            player.prepare()
            player.play()
        }
    }

    DisposableEffect(lifecycleOwner) {
        var backgrounded = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgrounded = true
                    viewModel.stop()
                }
                Lifecycle.Event.ON_START -> if (backgrounded) {
                    backgrounded = false
                    viewModel.resume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        hostView.keepScreenOn = true
        onDispose { hostView.keepScreenOn = false }
    }

    var guideOpen by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf<List<LiveProgramme>?>(null) }
    var infoVisible by remember { mutableStateOf(true) }
    LaunchedEffect(uiState.infoVersion) {
        infoVisible = true
        delay(INFO_VISIBLE_MS)
        infoVisible = false
    }

    BackHandler(enabled = !guideOpen) {
        viewModel.stop()
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (guideOpen || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> { viewModel.zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> { viewModel.zap(1); true }
                    Key.DirectionCenter, Key.Enter, Key.Info, Key.DirectionLeft, Key.DirectionRight -> {
                        if (uiState.status is LiveStatus.Failed) viewModel.retry() else viewModel.showInfo()
                        true
                    }
                    Key.Guide, Key.Menu -> {
                        val channel = uiState.channel
                        if (channel != null && !channel.userStream) {
                            guide = null
                            guideOpen = true
                        }
                        true
                    }
                    Key.MediaPlayPause, Key.MediaPause, Key.MediaStop -> {
                        // Live has no pause: stopping frees the tuner.
                        viewModel.stop()
                        onBack()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = false; setKeepContentOnPlayerReset(false) } },
            modifier = Modifier.fillMaxSize(),
        )

        val status = uiState.status
        if (status == LiveStatus.Starting || status == LiveStatus.Reconnecting) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                ScreenSpinner()
                Text(
                    text = Strings.get(if (status == LiveStatus.Reconnecting) "live_reconnecting" else "live_tuning"),
                    color = JellioTextSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (status is LiveStatus.Failed) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = status.message, color = JellioText, style = MaterialTheme.typography.titleMedium)
                Surface(
                    onClick = viewModel::retry,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = primaryButtonColors(),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(text = Strings.get("live_retry"), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
        }

        val channel = uiState.channel
        if (channel != null && (infoVisible || status != LiveStatus.Playing)) {
            InfoBar(channel, uiState.now, uiState.next, imageUrl, Modifier.align(Alignment.BottomCenter))
        }

        if (guideOpen && channel != null) {
            LaunchedEffect(channel.id) {
                val now = System.currentTimeMillis()
                guide = runCatching { viewModel.loadGuide(channel.id, now) }.getOrDefault(emptyList())
            }
            GuideSheet(
                channel = channel,
                programmes = guide,
                imageUrl = imageUrl,
                onPlay = null,
                onClose = { guideOpen = false },
            )
        }
    }

    LaunchedEffect(guideOpen) { if (!guideOpen) runCatching { focus.requestFocus() } }
}

@Composable
private fun InfoBar(
    channel: LiveChannel,
    now: LiveProgramme?,
    next: LiveProgramme?,
    imageUrl: (String, String?) -> String,
    modifier: Modifier = Modifier,
) {
    val nowMs = System.currentTimeMillis()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(start = 48.dp, end = 48.dp, top = 64.dp, bottom = 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ChannelLogo(channel, imageUrl)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = Strings.get("live_badge"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE53935)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text(text = channel.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (channel.userStream && !channel.group.isNullOrBlank()) {
                    Text(text = channel.group, color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
                if (now != null) {
                    Text(
                        text = formatClock(now.start) + " – " + formatClock(now.end) + "  " + now.name.ifBlank { Strings.get("live_nothing_listed") },
                        color = JellioText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Box(modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.6f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                        Box(modifier = Modifier.fillMaxWidth(now.progress(nowMs).coerceIn(0f, 1f)).height(3.dp).background(Color(0xFFFF9800)))
                    }
                }
                if (next != null) {
                    Text(
                        text = Strings.get("live_next") + "  " + formatClock(next.start) + "  " + next.name,
                        color = JellioTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(text = Strings.get("live_player_hint"), color = JellioTextSecondary.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
