package com.jellio.tv.ui.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.SUBTITLE_BACKGROUNDS
import com.jellio.tv.data.model.SUBTITLE_SIZES
import com.jellio.tv.data.model.SubtitleStyle
import com.jellio.tv.data.model.subtitleBackgroundOption
import com.jellio.tv.data.model.subtitleSizeOption
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.detail.SourceCard
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
// Real port of screens/player.js's own showPlayerToast() 4000ms
// setTimeout.
private const val TOAST_DURATION_MS = 4_000L
private const val TICKS_PER_MS = 10_000L
// Real screens/player.js's own UPNEXT_FALLBACK_TRIGGER_SECONDS/
// UPNEXT_COUNTDOWN_SECONDS: shouldShowUpNextNow()'s own fixed
// seconds-left fallback, used only when skipSegments carries no real
// Credits segment for this title (see shouldShowUpNextNow() below).
private const val UPNEXT_FALLBACK_TRIGGER_SECONDS = 120
private const val UPNEXT_COUNTDOWN_SECONDS = 15

// Real screens/player.js's own PLAYBACK_SPEEDS: the exact same six
// real options its own speed popover offers, not a guessed range.
private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

// Real port of that file's own `speed + 'x'` label: JS number-to-
// string already drops a whole speed's own trailing .0 for free
// (1 + 'x' === '1x'), Kotlin's own Float.toString() does not (1f
// stringifies as "1.0"), so a whole speed is special cased here to
// match that exact same real label instead.
private fun formatSpeed(speed: Float): String {
    val whole = speed.toInt()
    return if (speed == whole.toFloat()) "${whole}x" else "${speed}x"
}

// Real screens/player.js's own SLEEP_TIMER_OPTIONS: the same five real
// durations its own sleep popover offers.
private val SLEEP_TIMER_OPTIONS = listOf(15, 30, 45, 60, 90)

// Real port of screens/player.js's own shouldShowUpNextNow(), ported
// from NuvioWeb's own shouldShowNextEpisodeCard() in turn: a real
// Credits segment starts the outro, so showing the card there reads as
// timed to the episode rather than to an arbitrary count of seconds
// left; the fixed-seconds rule is only the fallback for a title Intro
// Skipper has no Credits segment for at all.
private fun shouldShowUpNextNow(segments: IntroSkipperSegmentsDto?, currentSeconds: Double, durationSeconds: Double): Boolean {
    if (durationSeconds <= 0) return false
    val credits = segments?.Credits
    if (credits != null && credits.End > 0 && credits.Start >= 0) {
        return currentSeconds >= credits.Start
    }
    return durationSeconds - currentSeconds <= UPNEXT_FALLBACK_TRIGGER_SECONDS
}

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
    onPlayNext: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()

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
                audioTracks = uiState.audioTracks,
                selectedAudioStreamIndex = uiState.selectedAudioStreamIndex,
                defaultAudioStreamIndex = uiState.defaultAudioStreamIndex,
                sourceOptions = uiState.sourceOptions,
                currentMediaSourceId = uiState.mediaSourceId,
                currentItemId = itemId,
                seasons = uiState.seasons,
                selectedSeasonId = uiState.selectedSeasonId,
                episodes = uiState.episodes,
                hasTrickplay = uiState.hasTrickplay,
                onComputeTrickplayFrame = { positionMs -> viewModel.trickplayFrame(positionMs) },
                toastMessage = uiState.toastMessage,
                toastId = uiState.toastId,
                onDismissToast = { id -> viewModel.clearToast(id) },
                pauseInfo = uiState.pauseInfo,
                upNextInfo = uiState.upNextInfo,
                skipSegments = uiState.skipSegments,
                sleepTimerEndTimeMs = uiState.sleepTimerEndTimeMs,
                onBack = onBack,
                onReportStart = { viewModel.reportStart(it) },
                onReportProgress = { positionTicks, paused -> viewModel.reportProgress(positionTicks, paused) },
                onReportStopped = { viewModel.reportStopped(it) },
                onMarkRealWatchComplete = { viewModel.markRealWatchComplete() },
                onReportRealDuration = { seconds -> viewModel.reportRealDurationIfUseful(seconds) },
                onSelectSubtitle = { track, positionTicks ->
                    when {
                        track == null -> viewModel.selectSubtitle(null)
                        track.isTextBased -> viewModel.selectSubtitle(track.streamIndex)
                        else -> viewModel.selectBurnedInSubtitle(session, track.streamIndex, positionTicks)
                    }
                },
                subtitleStyle = subtitleStyle,
                onSetSubtitleSize = { viewModel.setSubtitleSize(it) },
                onSetSubtitleBackground = { viewModel.setSubtitleBackground(it) },
                onSelectAudioTrack = { streamIndex, positionTicks -> viewModel.switchAudioTrack(session, streamIndex, positionTicks) },
                onSelectSource = { source, positionTicks -> viewModel.switchSource(session, source, positionTicks) },
                onSelectSeason = { seasonId ->
                    val seriesId = uiState.seriesId
                    if (seriesId != null) viewModel.loadSeasonEpisodes(session, seriesId, seasonId)
                },
                onRestart = { viewModel.restart(session) },
                onPlayNext = onPlayNext,
                onStartSleepTimer = { minutes -> viewModel.startSleepTimer(minutes) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
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
    audioTracks: List<AudioTrackUiState>,
    selectedAudioStreamIndex: Int?,
    defaultAudioStreamIndex: Int?,
    sourceOptions: List<MediaSourceDto>,
    currentMediaSourceId: String?,
    currentItemId: String,
    seasons: List<BaseItemDto>,
    selectedSeasonId: String?,
    episodes: List<EpisodePanelEntry>,
    hasTrickplay: Boolean,
    onComputeTrickplayFrame: (Long) -> TrickplayFrame?,
    toastMessage: String?,
    toastId: Long,
    onDismissToast: (Long) -> Unit,
    pauseInfo: PauseOverlayInfo?,
    upNextInfo: UpNextInfo?,
    skipSegments: IntroSkipperSegmentsDto?,
    sleepTimerEndTimeMs: Long?,
    onBack: () -> Unit,
    onReportStart: (Long) -> Unit,
    onReportProgress: (Long, Boolean) -> Unit,
    onReportStopped: (Long) -> Unit,
    onMarkRealWatchComplete: () -> Unit,
    onReportRealDuration: (Double) -> Unit,
    onSelectSubtitle: (SubtitleTrackUiState?, Long) -> Unit,
    subtitleStyle: SubtitleStyle,
    onSetSubtitleSize: (String) -> Unit,
    onSetSubtitleBackground: (String) -> Unit,
    onSelectAudioTrack: (Int, Long) -> Unit,
    onSelectSource: (MediaSourceDto, Long) -> Unit,
    onSelectSeason: (String) -> Unit,
    onRestart: () -> Unit,
    onPlayNext: (String) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
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
    // Not keyed on streamUrl, unlike showResumePrompt/upNextShown
    // above: a subtitle switch or Start Over rebuilds the real player
    // (see the player remember(streamUrl) block above), but the reader's
    // own chosen speed should carry over into it, same real persistence
    // an HTML5 video element's own playbackRate already gets for free
    // across a real src reassignment on the web side.
    var playbackSpeed by remember { mutableStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSourcePanel by remember { mutableStateOf(false) }
    var showEpisodesPanel by remember { mutableStateOf(false) }
    var scrubFrame by remember { mutableStateOf<TrickplayFrame?>(null) }
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }
    var showResumePrompt by remember(streamUrl) { mutableStateOf(startPositionTicks > 0) }
    var hasReportedStart by remember { mutableStateOf(false) }
    var seekedToResume by remember { mutableStateOf(false) }
    var upNextShown by remember(streamUrl) { mutableStateOf(false) }
    var upNextDismissed by remember(streamUrl) { mutableStateOf(false) }
    var upNextCountdown by remember(streamUrl) { mutableStateOf(UPNEXT_COUNTDOWN_SECONDS) }

    // Real port of that file's own persistence: applied to every real
    // player instance this screen creates, not only the first, so a
    // subtitle switch or Start Over rebuilding it (the player remember(
    // streamUrl) block above) keeps the reader's own chosen speed
    // instead of quietly resetting to 1x.
    LaunchedEffect(player, playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    // Real local enforcement of the sleep timer (see
    // SleepTimerStatusDto's own header comment for why this player
    // cannot just trust the real server side
    // SendPlaystateCommand(Stop) the way a real WebSocket-connected
    // Jellyfin client could): waits out the exact same real remaining
    // time PlayerViewModel already computed, then leaves the player the
    // same real way that command's own target ends playback.
    LaunchedEffect(sleepTimerEndTimeMs) {
        val endTimeMs = sleepTimerEndTimeMs ?: return@LaunchedEffect
        val remaining = endTimeMs - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
        onBack()
    }

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
                // Real port of screens/player.js's own 'ended' listener:
                // needs no known duration at all, the strongest of the
                // three real signals since ExoPlayer only ever reaches
                // STATE_ENDED once this real stream has genuinely run
                // out of data to play.
                if (isEnded) {
                    onReportRealDuration(player.currentPosition / 1000.0)
                    onMarkRealWatchComplete()
                }
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
                // Real port of screens/player.js's own
                // reconcileDuration(): ExoPlayer's own real duration,
                // the strongest of the three real signals
                // reportRealDurationIfUseful() takes since it is the
                // real total, not a lower bound off wherever playback
                // happens to be right now.
                onReportRealDuration(durationMs / 1000.0)
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
            // Real port of screens/player.js's own timeupdate-driven
            // REAL_WATCH_COMPLETION_THRESHOLD check: rides durationMs
            // (only ever set once ExoPlayer's own real duration is
            // actually known, see above), same real reason
            // AchievementService's own item.RunTimeTicks based gate
            // alone never reliably catches a genuine full watch of a
            // title whose library metadata runtime is inflated.
            if (durationMs > 0 && positionMs.toDouble() / durationMs.toDouble() >= 0.9) {
                onMarkRealWatchComplete()
            }
            // Real port of screens/player.js's own timeupdate-driven
            // shouldShowUpNextNow() check, fired at most once per real
            // title, same real showUpNext() guard that file's own
            // upNextShown/upNextDismissed pair already enforces.
            if (upNextInfo != null && !upNextShown && !upNextDismissed && durationMs > 0) {
                if (shouldShowUpNextNow(skipSegments, positionMs / 1000.0, durationMs / 1000.0)) {
                    upNextShown = true
                    // Real port of screens/player.js's own showUpNext():
                    // playNextEpisode()/the countdown below can navigate
                    // straight to the next episode's own screen before
                    // 'ended' ever gets a chance to fire on this one, so
                    // this real signal (skipSegments' own Credits.Start,
                    // or the fallback trigger) has to credit and report
                    // right here too, not only from 'ended'.
                    onReportRealDuration(positionMs / 1000.0)
                    onMarkRealWatchComplete()
                }
            }
            delay(500)
        }
    }

    // Real port of screens/player.js's own showUpNext()/
    // updateUpNextCountdown(): a real 15 second countdown, playing the
    // next real episode itself once it reaches zero, cancelled by this
    // LaunchedEffect's own key changing (upNextShown flips back to
    // false only via a fresh streamUrl, matching that file's own
    // window.clearInterval calls on playNextEpisode/hideUpNext).
    LaunchedEffect(upNextShown) {
        if (!upNextShown) return@LaunchedEffect
        upNextCountdown = UPNEXT_COUNTDOWN_SECONDS
        while (upNextCountdown > 0) {
            delay(1000)
            upNextCountdown -= 1
        }
        upNextInfo?.let { onPlayNext(it.itemId) }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(PROGRESS_REPORT_INTERVAL_MS)
            if (hasReportedStart) {
                onReportProgress(player.currentPosition * TICKS_PER_MS, !player.isPlaying)
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, showSubtitleMenu, showSpeedMenu, showSleepMenu, showAudioMenu, showSourcePanel, showEpisodesPanel) {
        if (controlsVisible && isPlaying && !showSubtitleMenu && !showSpeedMenu && !showSleepMenu && !showAudioMenu && !showSourcePanel && !showEpisodesPanel) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    // Real port of screens/player.js's own mouseleave handler clearing
    // scrubPreview: controls hiding is this remote's own closest real
    // equivalent to a mouse actually leaving the seek bar.
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            scrubFrame = null
            scrubPositionMs = null
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Real port of screens/player.js's own showPlayerToast(): that
    // file's own clearTimeout + fresh setTimeout(4000) on every call,
    // toastId as the key so a repeat of the exact same message still
    // restarts this delay instead of being a no-op recomposition.
    LaunchedEffect(toastId) {
        if (toastMessage != null) {
            delay(TOAST_DURATION_MS)
            onDismissToast(toastId)
        }
    }

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
                if (showSpeedMenu) {
                    if (event.key == Key.Back) {
                        showSpeedMenu = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (showSleepMenu) {
                    if (event.key == Key.Back) {
                        showSleepMenu = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (showAudioMenu) {
                    if (event.key == Key.Back) {
                        showAudioMenu = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (showSourcePanel) {
                    if (event.key == Key.Back) {
                        showSourcePanel = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (showEpisodesPanel) {
                    if (event.key == Key.Back) {
                        showEpisodesPanel = false
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
                        val newPos = (player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0)
                        player.seekTo(newPos)
                        // Real port of screens/player.js's own
                        // showScrubPreview(): that file's own mousemove
                        // listener has no equivalent input on a D-pad
                        // remote, so a preview of the seek's own real
                        // landing spot is shown here instead, right
                        // after each seek this key already commits.
                        if (hasTrickplay) {
                            scrubPositionMs = newPos
                            scrubFrame = onComputeTrickplayFrame(newPos)
                        }
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        controlsVisible = true
                        val newPos = (player.currentPosition + SEEK_STEP_MS).coerceAtMost(player.duration.coerceAtLeast(0))
                        player.seekTo(newPos)
                        if (hasTrickplay) {
                            scrubPositionMs = newPos
                            scrubFrame = onComputeTrickplayFrame(newPos)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.MediaPlayPause -> {
                        if (controlsVisible) {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        controlsVisible = true
                        scrubFrame = null
                        scrubPositionMs = null
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
            // Real port of screens/player.js's own applySubtitleStyle():
            // that function drives two ::cue custom properties a
            // browser's own WebVTT renderer reads directly; Media3's own
            // SubtitleView is this app's real equivalent renderer, its
            // own setStyle()/setFractionalTextSize() the real settable
            // surface for the same two style axes (subtitleCaptionStyle()/
            // subtitleFractionalTextSize() below). update rather than
            // factory alone so a style change already in flight applies
            // without recreating the PlayerView mid playback.
            update = { view ->
                view.subtitleView?.let { subtitleView ->
                    subtitleView.setStyle(subtitleCaptionStyle(subtitleStyle))
                    subtitleView.setFractionalTextSize(subtitleFractionalTextSize(subtitleStyle))
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
                hasAudioTracks = audioTracks.size > 1,
                hasSourceOptions = sourceOptions.size > 1,
                hasEpisodes = seasons.isNotEmpty(),
                scrubFrame = scrubFrame,
                scrubPositionMs = scrubPositionMs,
                speedLabel = formatSpeed(playbackSpeed),
                sleepTimerActive = sleepTimerEndTimeMs != null,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onOpenSubtitleMenu = { showSubtitleMenu = true },
                onOpenSpeedMenu = { showSpeedMenu = true },
                onOpenSleepMenu = { showSleepMenu = true },
                onOpenAudioMenu = { showAudioMenu = true },
                onOpenSourceMenu = {
                    showEpisodesPanel = false
                    showSourcePanel = true
                },
                onOpenEpisodesMenu = {
                    showSourcePanel = false
                    showEpisodesPanel = true
                },
            )
        }

        // Real port of that file's own rebuildAudioMenu() early return:
        // audioButton.disabled = true whenever this source carries one
        // real audio track or fewer, nothing worth a real menu for.
        if (showAudioMenu && audioTracks.size > 1) {
            AudioMenu(
                tracks = audioTracks,
                selectedStreamIndex = selectedAudioStreamIndex,
                defaultStreamIndex = defaultAudioStreamIndex,
                onSelect = { streamIndex ->
                    showAudioMenu = false
                    onSelectAudioTrack(streamIndex, player.currentPosition * TICKS_PER_MS)
                },
                onDismiss = { showAudioMenu = false },
            )
        }

        // Real port of screens/player.js's own sourceButton gating:
        // sourceButton.disabled stays true until getMediaSources(itemId)
        // actually resolves more than one real option, same real
        // condition sourceOptions.size > 1 already checks before this
        // button even renders in PlayerControls above.
        if (showSourcePanel && sourceOptions.size > 1) {
            SourcePanel(
                sources = sourceOptions,
                currentMediaSourceId = currentMediaSourceId,
                onSelect = { source ->
                    showSourcePanel = false
                    onSelectSource(source, player.currentPosition * TICKS_PER_MS)
                },
                onDismiss = { showSourcePanel = false },
            )
        }

        // Real port of screens/player.js's own episodesButton gating:
        // episodesButton.disabled stays true until getSeasons(item.
        // SeriesId) actually resolves a real season, same real
        // condition seasons.isNotEmpty() already checks before this
        // button even renders in PlayerControls above.
        if (showEpisodesPanel && seasons.isNotEmpty()) {
            EpisodesPanel(
                seasons = seasons,
                selectedSeasonId = selectedSeasonId,
                episodes = episodes,
                currentItemId = currentItemId,
                onSelectSeason = onSelectSeason,
                onSelectEpisode = { episodeId ->
                    showEpisodesPanel = false
                    if (episodeId != currentItemId) onPlayNext(episodeId)
                },
                onDismiss = { showEpisodesPanel = false },
            )
        }

        if (showSpeedMenu) {
            SpeedMenu(
                selectedSpeed = playbackSpeed,
                onSelect = { speed ->
                    showSpeedMenu = false
                    playbackSpeed = speed
                },
                onDismiss = { showSpeedMenu = false },
            )
        }

        if (showSleepMenu) {
            SleepMenu(
                onSelect = { minutes ->
                    showSleepMenu = false
                    onStartSleepTimer(minutes)
                },
                onCancel = {
                    showSleepMenu = false
                    onCancelSleepTimer()
                },
                onDismiss = { showSleepMenu = false },
            )
        }

        // Real port of screens/player.js's own timeupdate-driven
        // activeSkipSegment() check: a plain derived value off the same
        // positionMs this screen already polls every 500ms, no separate
        // effect needed. Suppressed while the Up Next card already
        // occupies this same real bottom-right corner (real CSS puts
        // both there too, but that file never actually has to render
        // both onscreen at once the way this app's own fixed 120 second
        // Up Next fallback now can against a real Credits segment).
        val activeSkip = activeSkipSegment(skipSegments, positionMs / 1000.0)
        if (activeSkip != null && !(upNextInfo != null && upNextShown && !upNextDismissed)) {
            SkipSegmentButton(
                label = activeSkip.label,
                onClick = { player.seekTo((activeSkip.targetSeconds * 1000).toLong()) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 148.dp),
            )
        }

        if (upNextInfo != null && upNextShown && !upNextDismissed) {
            UpNextOverlay(
                info = upNextInfo,
                secondsRemaining = upNextCountdown,
                onPlayNow = { onPlayNext(upNextInfo.itemId) },
                onDismiss = { upNextDismissed = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 148.dp),
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
                subtitleStyle = subtitleStyle,
                onSetSubtitleSize = onSetSubtitleSize,
                onSetSubtitleBackground = onSetSubtitleBackground,
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

        // Real port of screens/player.js's own .jellio-player-toast:
        // rendered above everything else, independent of
        // controlsVisible, the same real way that file's own toast
        // element is a direct child of root rather than something the
        // pill's own show/hide logic ever touches.
        if (toastMessage != null) {
            PlayerToast(message = toastMessage, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp))
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
    hasAudioTracks: Boolean,
    hasSourceOptions: Boolean,
    hasEpisodes: Boolean,
    scrubFrame: TrickplayFrame?,
    scrubPositionMs: Long?,
    speedLabel: String,
    sleepTimerActive: Boolean,
    onPlayPause: () -> Unit,
    onOpenSubtitleMenu: () -> Unit,
    onOpenSpeedMenu: () -> Unit,
    onOpenSleepMenu: () -> Unit,
    onOpenAudioMenu: () -> Unit,
    onOpenSourceMenu: () -> Unit,
    onOpenEpisodesMenu: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 48.dp)) {
            Text(text = title, color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, color = JellioTextSecondary)
            }
        }

        // Real port of the floating pill's own Speed and Subtitles
        // buttons: the pill itself lives at the bottom on the web side
        // (css/app.css's own .jellio-player-pill), but this screen
        // already established its own top-right corner for the
        // subtitle button before Speed existed, so Speed joins it there
        // instead of standing up a second, differently placed real
        // button just for itself.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 48.dp),
        ) {
            Surface(
                onClick = onOpenSpeedMenu,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = JellioText),
                modifier = Modifier.height(56.dp),
            ) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(text = speedLabel)
                }
            }
            if (hasSubtitleTracks) {
                Surface(
                    onClick = onOpenSubtitleMenu,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.4f)),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.ClosedCaption, contentDescription = "Subtitles", tint = JellioText)
                    }
                }
            }
            // Real port of the pill's own audioButton: disabled
            // (rebuildAudioMenu()'s own early return) whenever this
            // source carries one real audio track or fewer, same real
            // condition hasAudioTracks already checks before this
            // button even renders.
            if (hasAudioTracks) {
                Surface(
                    onClick = onOpenAudioMenu,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.4f)),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.GraphicEq, contentDescription = "Audio", tint = JellioText)
                    }
                }
            }
            // Real port of the pill's own sourceButton: disabled
            // (sourceButton.disabled) until getMediaSources(itemId)
            // actually resolves more than one real option, same real
            // condition hasSourceOptions already checks before this
            // button even renders.
            if (hasSourceOptions) {
                Surface(
                    onClick = onOpenSourceMenu,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.4f)),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.SwapHoriz, contentDescription = "Sources", tint = JellioText)
                    }
                }
            }
            // Real port of the pill's own episodesButton: disabled
            // (episodesButton.disabled) until getSeasons(item.SeriesId)
            // actually resolves a real season, same real condition
            // hasEpisodes already checks before this button even
            // renders. A Movie never gets here at all, same real gate
            // this file's own isEpisodeItem && item.SeriesId check
            // applies before that fetch ever fires.
            if (hasEpisodes) {
                Surface(
                    onClick = onOpenEpisodesMenu,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.4f)),
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.VideoLibrary, contentDescription = "Episodes", tint = JellioText)
                    }
                }
            }
            // Real port of the pill's own sleepButton: no live countdown
            // label the way that file's own button never gets one
            // either, just its own real jellio-player-pill-btn-active
            // class toggled on and off, ported here as a real tinted
            // background instead.
            Surface(
                onClick = onOpenSleepMenu,
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (sleepTimerActive) JellioSecondary else Color.Black.copy(alpha = 0.4f),
                    contentColor = JellioText,
                ),
                modifier = Modifier.size(56.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.Bedtime, contentDescription = "Sleep timer", tint = JellioText)
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
            // Real port of screens/player.js's own scrubPreview: this
            // Android TV remote has no real mousemove to hover the bar
            // with, so the preview tracks the seek's own real landing
            // spot instead (scrubPositionMs, set right after every
            // D-pad seek above), positioned the same real way that
            // file's own ratio * rect.width math does, just against
            // this fraction-of-width trick instead of a pixel rect.
            if (scrubFrame != null && scrubPositionMs != null && durationMs > 0) {
                val scrubProgress = (scrubPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                Box(modifier = Modifier.fillMaxWidth(scrubProgress), contentAlignment = Alignment.CenterEnd) {
                    TrickplayPreview(
                        frame = scrubFrame,
                        timeLabel = formatMs(scrubPositionMs),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
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

// Real port of screens/player.js's own scrub preview image: one real
// cell of the tile sheet cropped out via an oversized AsyncImage offset
// against a clipped frame-sized Box, the same real crop
// backgroundSize/backgroundPosition does in CSS, raw Trickplay pixel
// counts read directly as dp the same real way that file's own CSS
// reads them as px, no per-device density lookup either side bothers
// with for a scrub thumbnail this rough.
private const val TRICKPLAY_DISPLAY_WIDTH_DP = 180f

@Composable
private fun TrickplayPreview(frame: TrickplayFrame, timeLabel: String, modifier: Modifier = Modifier) {
    if (frame.frameWidth <= 0 || frame.frameHeight <= 0) return
    val scale = TRICKPLAY_DISPLAY_WIDTH_DP / frame.frameWidth
    val frameHeightDp = frame.frameHeight * scale
    val sheetWidthDp = frame.sheetWidth * scale
    val sheetHeightDp = frame.sheetHeight * scale
    val offsetXDp = -(frame.col * frame.frameWidth * scale)
    val offsetYDp = -(frame.row * frame.frameHeight * scale)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(TRICKPLAY_DISPLAY_WIDTH_DP.dp, frameHeightDp.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black),
        ) {
            AsyncImage(
                model = frame.tileUrl,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .size(sheetWidthDp.dp, sheetHeightDp.dp)
                    .offset(offsetXDp.dp, offsetYDp.dp),
            )
        }
        Text(
            text = timeLabel,
            color = JellioText,
            modifier = Modifier.padding(top = 4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
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

// Real port of screens/player.js's own buildUpNextOverlay(): a real
// thumbnail and episode label over a real Play now/Dismiss pair, the
// countdown baked directly into the Play now label the same way that
// file's own updateUpNextCountdown() rewrites its button's own
// textContent every second instead of a separate counter element.
@Composable
private fun UpNextOverlay(
    info: UpNextInfo,
    secondsRemaining: Int,
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .widthIn(max = 420.dp)
            .background(JellioBgElevated, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (info.thumbnailUrl != null) {
            AsyncImage(
                model = info.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = "Next Episode", color = JellioTextSecondary, style = androidx.tv.material3.MaterialTheme.typography.labelSmall)
            Text(
                text = info.title,
                color = JellioText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onPlayNow,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioText),
                ) {
                    Text(text = "Play now ($secondsRemaining)", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = JellioText),
                ) {
                    Text(text = "Dismiss", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
        }
    }
}

// Real port of screens/player.js's own skip button: label switches
// between Skip Intro/Skip Credits off activeSkipSegment's own real
// label, one real Surface either way rather than two separate buttons.
@Composable
private fun SkipSegmentButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = JellioText),
        modifier = modifier,
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
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

// Real port of screens/player.js's own .jellio-player-toast: bottom
// center, elevated background, rounded, same real CSS treatment that
// selector already carries.
@Composable
private fun PlayerToast(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(JellioBgElevated, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = message, color = JellioText)
    }
}

// Real port of screens/player.js's own speed popover: a real small
// anchored popover (that file's own .jellio-player-popover, distinct
// from the subtitle drawer's own full-height panel shape), the exact
// same six real PLAYBACK_SPEEDS options, the active one highlighted.
@Composable
private fun SpeedMenu(selectedSpeed: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 104.dp, end = 48.dp)
                .width(160.dp)
                .background(JellioBgElevated, RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp),
        ) {
            PLAYBACK_SPEEDS.forEach { speed ->
                Surface(
                    onClick = { onSelect(speed) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (speed == selectedSpeed) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                        contentColor = JellioText,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = formatSpeed(speed),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

// Real port of screens/player.js's own sleep timer popover: "Cancel
// timer" first (shown unconditionally there too, a real cancel call
// with nothing active just 404s quietly), then the exact same five
// real SLEEP_TIMER_OPTIONS durations.
@Composable
private fun SleepMenu(onSelect: (Int) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 104.dp, end = 48.dp)
                .width(180.dp)
                .background(JellioBgElevated, RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp),
        ) {
            Surface(
                onClick = onCancel,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = JellioText),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(text = "Cancel timer", modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
            }
            SLEEP_TIMER_OPTIONS.forEach { minutes ->
                Surface(
                    onClick = { onSelect(minutes) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = JellioText),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(text = "$minutes min", modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp))
                }
            }
        }
    }
}

// Real port of screens/player.js's own rebuildAudioMenu(): every real
// audio track this source carries, the active one highlighted off
// selectedStreamIndex when set, otherwise off defaultStreamIndex, same
// real fallback that function's own isActive check documents (a
// reader who has never picked a track yet has the MediaSource's own
// real default active, not nothing).
@Composable
private fun AudioMenu(
    tracks: List<AudioTrackUiState>,
    selectedStreamIndex: Int?,
    defaultStreamIndex: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 104.dp, end = 48.dp)
                .widthIn(min = 220.dp, max = 320.dp)
                .background(JellioBgElevated, RoundedCornerShape(12.dp))
                .padding(vertical = 8.dp),
        ) {
            tracks.forEach { track ->
                val isActive = if (selectedStreamIndex == null) {
                    track.streamIndex == defaultStreamIndex
                } else {
                    track.streamIndex == selectedStreamIndex
                }
                Surface(
                    // Real port of that file's own isActive early
                    // return: picking the track already playing just
                    // closes the popover there too, no real
                    // re-negotiation worth firing over a no-op switch.
                    onClick = { if (!isActive) onSelect(track.streamIndex) else onDismiss() },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isActive) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                        contentColor = JellioText,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = track.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

// Real port of screens/player.js's own Sources side panel
// (rebuildSourceMenu()): the exact same real SourceCard this app's own
// pre-playback StreamPickerOverlow already renders (that file's own
// comment documents reusing components/streamPicker.js's own
// buildSourceCard() here rather than a second, plainer list), leaner
// than that full overlay itself, no resume button or language filter,
// same real leaner shape that file's own mid-player sourcePanel has
// against the fuller pre-playback picker.
@Composable
private fun SourcePanel(
    sources: List<MediaSourceDto>,
    currentMediaSourceId: String?,
    onSelect: (MediaSourceDto) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 104.dp, end = 48.dp)
                .widthIn(min = 320.dp, max = 420.dp)
                .heightIn(max = 520.dp)
                .background(JellioBgElevated, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(text = "Sources", color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                items(sources, key = { it.Id ?: it.hashCode() }) { source ->
                    SourceCard(
                        source = source,
                        onClick = {
                            if (source.Id != currentMediaSourceId) onSelect(source) else onDismiss()
                        },
                        isActive = source.Id == currentMediaSourceId,
                    )
                }
            }
        }
    }
}

// Real port of screens/player.js's own Episodes side panel: season
// tabs (buildEpisodeRow's own Specials-last isSpecialsSeason() order,
// applied once in PlayerViewModel rather than here) plus that season's
// own episode list, only ever shown for a real Episode with a real
// SeriesId behind it (a Movie's own episodesButton never leaves its
// disabled state, see hasEpisodes above).
@Composable
private fun EpisodesPanel(
    seasons: List<BaseItemDto>,
    selectedSeasonId: String?,
    episodes: List<EpisodePanelEntry>,
    currentItemId: String,
    onSelectSeason: (String) -> Unit,
    onSelectEpisode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 104.dp, end = 48.dp)
                .widthIn(min = 380.dp, max = 480.dp)
                .heightIn(max = 560.dp)
                .background(JellioBgElevated, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(text = "Episodes", color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                items(seasons, key = { it.Id }) { season ->
                    val isActive = season.Id == selectedSeasonId
                    Surface(
                        onClick = { onSelectSeason(season.Id) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isActive) JellioSecondary else Color.White.copy(alpha = 0.08f),
                            contentColor = if (isActive) JellioBg else JellioText,
                        ),
                    ) {
                        Text(text = season.Name.orEmpty(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                items(episodes, key = { it.itemId }) { episode ->
                    EpisodeRow(episode = episode, isActive = episode.itemId == currentItemId, onClick = { onSelectEpisode(episode.itemId) })
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodePanelEntry, isActive: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) JellioSecondary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.width(120.dp).height(68.dp).background(Color.Black, RoundedCornerShape(8.dp))) {
                if (episode.thumbnailUrl != null) {
                    AsyncImage(
                        model = episode.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    )
                }
                if (episode.rating != null) {
                    Text(
                        text = episode.rating,
                        color = JellioText,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                    )
                }
                if (episode.episodeCode != null) {
                    Text(
                        text = episode.episodeCode,
                        color = JellioText,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = episode.title, color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (episode.overview != null) {
                    Text(
                        text = episode.overview,
                        color = JellioTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
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
    subtitleStyle: SubtitleStyle,
    onSetSubtitleSize: (String) -> Unit,
    onSetSubtitleBackground: (String) -> Unit,
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
                // Real port of screens/player.js's own styleSection,
                // appended below that same real popover's own track
                // list rather than a separate menu: that file's own
                // buildStyleGroup() twice over, Size then Background.
                item {
                    SubtitleStyleGroup(
                        label = "Size",
                        options = SUBTITLE_SIZES.map { it.value to it.label },
                        selected = subtitleStyle.size,
                        onSelect = onSetSubtitleSize,
                    )
                }
                item {
                    SubtitleStyleGroup(
                        label = "Background",
                        options = SUBTITLE_BACKGROUNDS.map { it.value to it.label },
                        selected = subtitleStyle.background,
                        onSelect = onSetSubtitleBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleStyleGroup(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = label,
            color = JellioTextSecondary,
            style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, optionLabel) ->
                Surface(
                    onClick = { onSelect(value) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (value == selected) Color.White.copy(alpha = 0.18f) else JellioBg,
                        contentColor = JellioText,
                    ),
                ) {
                    Text(
                        text = optionLabel,
                        style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// Real port of screens/player.js's own applySubtitleStyle(): that
// function sets a real --jellio-subtitle-bg custom property, this
// app's own CaptionStyleCompat.backgroundColor equivalent read by
// Media3's own SubtitleView. Foreground stays plain white and edge
// type stays NONE either way, the same two axes screens/player.js's
// own real style leaves alone too (only size and background are real
// reader controlled there).
private fun subtitleCaptionStyle(style: SubtitleStyle): CaptionStyleCompat {
    val backgroundColor = when (subtitleBackgroundOption(style.background).value) {
        "none" -> Color.Transparent.toArgb()
        "solid" -> android.graphics.Color.argb(230, 0, 0, 0)
        else -> android.graphics.Color.argb(128, 0, 0, 0)
    }
    return CaptionStyleCompat(
        Color.White.toArgb(),
        backgroundColor,
        Color.Transparent.toArgb(),
        CaptionStyleCompat.EDGE_TYPE_NONE,
        Color.Transparent.toArgb(),
        null,
    )
}

// Real port of screens/player.js's own --jellio-subtitle-size: that
// property is a real rem value a browser's own font-size cascade
// reads directly. SubtitleView has no rem equivalent, only a fraction
// of the video view's own height (DEFAULT_TEXT_SIZE_FRACTION when
// never set), so this scales that same real default by each real size
// option's own rem relative to medium's own real 1.3rem baseline
// rather than trying to reproduce an absolute rem value that has no
// real meaning here.
private fun subtitleFractionalTextSize(style: SubtitleStyle): Float {
    val option = subtitleSizeOption(style.size)
    val medium = SUBTITLE_SIZES.first { it.value == "medium" }
    return SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (option.rem / medium.rem)
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
