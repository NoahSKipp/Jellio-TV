package com.jellio.tv.ui.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
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
                resumePercent = uiState.resumePercent,
                title = uiState.title,
                subtitle = uiState.subtitle,
                subtitleTracks = uiState.subtitleTracks,
                selectedSubtitleIndex = uiState.selectedSubtitleIndex,
                pauseInfo = uiState.pauseInfo,
                onBack = onBack,
                onReportStart = { viewModel.reportStart(it) },
                onReportProgress = { positionTicks, paused -> viewModel.reportProgress(positionTicks, paused) },
                onReportStopped = { viewModel.reportStopped(it) },
                onSelectSubtitle = { track, positionTicks ->
                    when {
                        track == null -> viewModel.selectSubtitle(null)
                        track.isTextBased -> viewModel.selectSubtitle(track.streamIndex)
                        else -> viewModel.selectBurnedInSubtitle(session, track.streamIndex, positionTicks)
                    }
                },
                onRestart = { viewModel.restart(session) },
            )
        }
    }
}

@Composable
private fun PlayerSurface(
    streamUrl: String,
    startPositionTicks: Long,
    resumePercent: Int?,
    title: String,
    subtitle: String,
    subtitleTracks: List<SubtitleTrackUiState>,
    selectedSubtitleIndex: Int?,
    pauseInfo: PauseOverlayInfo?,
    onBack: () -> Unit,
    onReportStart: (Long) -> Unit,
    onReportProgress: (Long, Boolean) -> Unit,
    onReportStopped: (Long) -> Unit,
    onSelectSubtitle: (SubtitleTrackUiState?, Long) -> Unit,
    onRestart: () -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    // Every real text based subtitle track declared as a real
    // MediaItem.SubtitleConfiguration from this very first prepare()
    // call, not added later: Media3 requires a full setMediaSource
    // reload to attach one mid-playback, which wipes the buffer, so
    // toggling between them afterward is real track *selection*
    // (below), never a second real prepare().
    val player = remember(streamUrl) {
        val subtitleConfigs = subtitleTracks.filter { it.isTextBased && it.url != null }.map { track ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setId(track.streamIndex.toString())
                .setLabel(track.label)
                .build()
        }
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(mediaItem)
            // Real port of screens/player.js's own hasResumePosition
            // gate: autoplay stays off until the reader actually picks
            // Resume or Start Over on the prompt below, the paused frame
            // at the saved position showing through behind that choice
            // instead of playback already running underneath it. A fresh
            // item with no saved position keeps starting immediately,
            // same as before this existed.
            playWhenReady = startPositionTicks <= 0
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var playWhenReadyState by remember(streamUrl) { mutableStateOf(startPositionTicks <= 0) }
    var isEnded by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showResumePrompt by remember(streamUrl) { mutableStateOf(startPositionTicks > 0) }
    var hasReportedStart by remember { mutableStateOf(false) }
    var seekedToResume by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playWhenReadyState = playWhenReady
            }
            override fun onPlaybackStateChanged(state: Int) {
                isEnded = state == Player.STATE_ENDED
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            onReportStopped(player.currentPosition * TICKS_PER_MS)
            player.release()
        }
    }

    // Off, or a specific real text track: a plain real track selection
    // override, no reload, since every text track was already declared
    // on the MediaItem above before prepare() ever ran.
    LaunchedEffect(player, selectedSubtitleIndex) {
        val params = player.trackSelectionParameters.buildUpon()
        params.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (selectedSubtitleIndex == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            val targetId = selectedSubtitleIndex.toString()
            val group = player.currentTracks.groups.firstOrNull { group ->
                group.type == C.TRACK_TYPE_TEXT && (0 until group.length).any { i -> group.getTrackFormat(i).id == targetId }
            }
            if (group != null) {
                val trackIndex = (0 until group.length).first { i -> group.getTrackFormat(i).id == targetId }
                params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            }
        }
        player.trackSelectionParameters = params.build()
    }

    LaunchedEffect(player, startPositionTicks) {
        while (isActive) {
            if (player.duration > 0 && player.playbackState != Player.STATE_IDLE) {
                durationMs = player.duration
                if (!seekedToResume && startPositionTicks > 0) {
                    seekedToResume = true
                    player.seekTo(startPositionTicks / TICKS_PER_MS)
                }
                // Gated on real playback actually running (matches
                // screens/player.js's own timeupdate-driven trigger,
                // never metadata alone), not just duration being known:
                // a resume prompt still waiting on the reader's own
                // choice has a real duration already but has not
                // started playing yet.
                if (!hasReportedStart && isPlaying) {
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

    LaunchedEffect(controlsVisible, isPlaying, showSubtitleMenu) {
        if (controlsVisible && isPlaying && !showSubtitleMenu) {
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
                if (showSubtitleMenu) {
                    if (event.key == Key.Back) {
                        showSubtitleMenu = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                // Real port of screens/player.js's own hasResumePosition
                // gate: seeking/play-pause stay inert behind this real
                // choice, the same way showSubtitleMenu above already
                // blocks them behind its own popover; Resume/Start Over
                // themselves are plain focusable Surfaces below, reached
                // through the TV focus system rather than a key here.
                if (showResumePrompt) {
                    if (event.key == Key.Back) {
                        onBack()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
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

        // Real port of screens/player.js's own pauseOverlay visibility
        // rule: hasReportedStart && !video.ended, !showResumePrompt on
        // top of that here since this player also gates real playback
        // start behind that prompt (see the playWhenReady comment
        // above), a state the web side's own always-autoplaying video
        // element never had to account for.
        val showPauseOverlay = pauseInfo != null && hasReportedStart && !showResumePrompt && !playWhenReadyState && !isEnded
        if (showPauseOverlay) {
            PauseOverlay(info = pauseInfo!!)
        }

        if (controlsVisible) {
            PlayerControls(
                title = title,
                subtitle = subtitle,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                hasSubtitleTracks = subtitleTracks.isNotEmpty(),
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onOpenSubtitleMenu = { showSubtitleMenu = true },
            )
        }

        if (showSubtitleMenu) {
            SubtitleMenu(
                tracks = subtitleTracks,
                selectedIndex = selectedSubtitleIndex,
                onSelect = { track ->
                    showSubtitleMenu = false
                    onSelectSubtitle(track, player.currentPosition * TICKS_PER_MS)
                },
                onDismiss = { showSubtitleMenu = false },
            )
        }

        if (showResumePrompt) {
            ResumePrompt(
                percent = resumePercent,
                onResume = {
                    showResumePrompt = false
                    player.play()
                },
                onRestart = {
                    showResumePrompt = false
                    onRestart()
                },
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
    hasSubtitleTracks: Boolean,
    onPlayPause: () -> Unit,
    onOpenSubtitleMenu: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 48.dp)) {
            Text(text = title, color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, color = JellioTextSecondary)
            }
        }

        if (hasSubtitleTracks) {
            Surface(
                onClick = onOpenSubtitleMenu,
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.4f)),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 48.dp).size(56.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.ClosedCaption, contentDescription = "Subtitles", tint = JellioText)
                }
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
                Brush.verticalGradient(listOf(Color.Transparent, JellioBg)),
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

// Real port of screens/player.js's own buildPauseOverlay(): an eyebrow
// naming what is playing, the series (or movie) own name and rating,
// the exact episode this pause landed on and its own overview, not the
// item alone (an Episode's own Overview is the episode's, its own Name
// never was the series name). pauseInfo.backdropUrl already carries
// seriesAwareArtworkUrl()'s own real fallback chain, computed once in
// PlayerViewModel rather than here.
@Composable
private fun PauseOverlay(info: PauseOverlayInfo, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        if (info.backdropUrl != null) {
            AsyncImage(
                model = info.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.9f))),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp, end = 96.dp).widthIn(max = 640.dp),
        ) {
            Text(text = "You're watching", color = JellioTextSecondary, style = androidx.tv.material3.MaterialTheme.typography.labelSmall)
            Text(
                text = info.title,
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                info.rating?.let { Text(text = it, color = JellioTextSecondary) }
                info.year?.let { Text(text = it, color = JellioTextSecondary) }
                info.officialRating?.let { Text(text = it, color = JellioTextSecondary) }
            }
            if (info.isEpisode) {
                info.episodeCode?.let {
                    Text(text = it, color = JellioTextSecondary, modifier = Modifier.padding(top = 12.dp))
                }
                info.episodeTitle?.takeIf { it.isNotEmpty() }?.let {
                    Text(text = it, color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
                }
            }
            info.overview?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    color = JellioTextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// Real port of screens/player.js's own buildResumePrompt(), ported
// from Harbor's own player/resume-prompt.tsx idea in turn (that file's
// own comment): a real choice instead of always just seeking straight
// to the saved position, shown once over the paused frame already
// sitting there (see PlayerSurface's own playWhenReady comment above),
// Start Over a real choice this player did not offer before rather
// than something to dig for elsewhere.
@Composable
private fun ResumePrompt(percent: Int?, onResume: () -> Unit, onRestart: () -> Unit, modifier: Modifier = Modifier) {
    val resumeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { resumeFocusRequester.requestFocus() }
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.background(JellioBgElevated, RoundedCornerShape(16.dp)).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Resume playback?", color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            if (percent != null) {
                Text(text = "$percent% watched", color = JellioTextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            Row(modifier = Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = onResume,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioText),
                    modifier = Modifier.focusRequester(resumeFocusRequester),
                ) {
                    Text(text = "Resume", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
                Surface(
                    onClick = onRestart,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = JellioText),
                ) {
                    Text(text = "Start Over", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
            }
        }
    }
}

// Mirrors screens/player.js's own real subtitle popover: Off first,
// then every real track this source carries, an image based one
// (PGS, VobSub) labelled the same real "(image)" suffix that file's
// own renderSubtitleTrackList() uses, selecting one of those a real
// burned-in transcode rather than a plain track switch.
@Composable
private fun SubtitleMenu(
    tracks: List<SubtitleTrackUiState>,
    selectedIndex: Int?,
    onSelect: (SubtitleTrackUiState?) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(vertical = 48.dp),
        ) {
            Text(
                text = "Subtitles",
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LazyColumn {
                item { SubtitleMenuRow(label = "Off", isSelected = selectedIndex == null, onClick = { onSelect(null) }) }
                items(tracks) { track ->
                    SubtitleMenuRow(label = track.label, isSelected = selectedIndex == track.streamIndex, onClick = { onSelect(track) })
                }
            }
        }
    }
}

@Composable
private fun SubtitleMenuRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = JellioText,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
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
