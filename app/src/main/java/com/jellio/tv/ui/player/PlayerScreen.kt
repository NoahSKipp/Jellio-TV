package com.jellio.tv.ui.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.Border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.jellio.tv.data.network.APP_VERSION
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
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
import com.jellio.tv.ui.detail.FilterChipRow
import com.jellio.tv.ui.detail.QUALITY_ORDER
import com.jellio.tv.ui.detail.SourceCard
import com.jellio.tv.ui.detail.sourceAudioLanguages
import com.jellio.tv.ui.detail.sourceQuality
import com.jellio.tv.data.model.languageName
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
// Where a relative seek lands. The player often doesn't know a stream's
// length yet (C.TIME_UNSET, a transcode still starting), and clamping to
// that sent every skip back to the start; only a known length caps it.
private fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val target = (positionMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0) target.coerceAtMost(durationMs) else target
}

private fun formatSpeed(speed: Float): String {
    val whole = speed.toInt()
    return if (speed == whole.toFloat()) "${whole}x" else "${speed}x"
}

// Real screens/player.js's own SLEEP_TIMER_OPTIONS: the same five real
// durations its own sleep popover offers.
private val SLEEP_TIMER_OPTIONS = listOf(15, 30, 45, 60, 90)

// screens/player.js's EPISODE_SLEEP_TIMER_OPTIONS: stop after this many
// episodes instead of after some minutes.
private val EPISODE_SLEEP_TIMER_OPTIONS = listOf(1, 2, 3, 5)

// Episodes left before the episode sleep timer stops playback. Lives
// outside the player screen because each next episode opens a fresh one.
private object EpisodeSleepTimer {
    var remaining by androidx.compose.runtime.mutableStateOf<Int?>(null)
}

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
            // Same as the web player: the title's backdrop with its logo
            // gently pulsing while the stream is prepared.
            uiState.isLoading -> uiState.pauseInfo?.let { BufferingOverlay(info = it) }
            uiState.error != null -> Box(Modifier.fillMaxSize()) {
                uiState.pauseInfo?.backdropUrl?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                PlaybackErrorPanel(
                    problem = PlaybackProblem(uiState.error ?: "Couldn't start playback", uiState.errorDetail.orEmpty()),
                    onRetry = { viewModel.retry(session) },
                    onBack = onBack,
                )
            }
            uiState.streamUrl != null -> PlayerSurface(
                session = session,
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
                directPlay = uiState.directPlay,
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
                onShowToast = { message -> viewModel.showToast(message) },
                switchingTo = uiState.switchingTo,
                onSeekReload = { ticks -> viewModel.reloadAt(session, ticks) },
                onSwitchSettled = { viewModel.clearSwitching() },
            )
        }
    }
}

@Composable
private fun PlayerSurface(
    session: Session,
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
    directPlay: Boolean,
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
    onShowToast: (String) -> Unit,
    switchingTo: String?,
    onSwitchSettled: () -> Unit,
    onSeekReload: (Long) -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var resumePromptDismissed by remember { mutableStateOf(false) }
    val showResumePrompt = resumePercent != null && !resumePromptDismissed

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
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).build())
            .build()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Jellio-TV/$APP_VERSION")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(
                buildMap {
                    put("X-Emby-Token", session.accessToken)
                    put("Authorization", buildEmbyAuthorizationHeader("AndroidTV", APP_VERSION, session.accessToken))
                }
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .build().apply {
            setMediaItem(mediaItem)
            // A transcode already starts at startPositionTicks (the URL's
            // StartTimeTicks); only a direct-play file needs the seek.
            if (startPositionTicks > 0 && directPlay) {
                seekTo(startPositionTicks / TICKS_PER_MS)
            }
            playWhenReady = !showResumePrompt
            prepare()
        }
    }

    DisposableEffect(player) {
        val mediaSession = MediaSession.Builder(context, player).build()
        onDispose { mediaSession.release() }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var playWhenReadyState by remember(streamUrl) { mutableStateOf(!showResumePrompt) }
    var isEnded by remember { mutableStateOf(false) }
    var stopAtEnd by remember { mutableStateOf(false) }
    var autoSkippedTo by remember { mutableStateOf<Double?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var exoError by remember { mutableStateOf<PlaybackProblem?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    // Where in the title the stream's own time zero is: 0 for direct play,
    // the start position for a transcode that began encoding there.
    val streamOffsetMs = if (directPlay) 0L else startPositionTicks / TICKS_PER_MS
    fun realPositionMs(): Long = streamOffsetMs + player.currentPosition

    // A seek to a point in the title. Direct play just seeks; a transcode
    // restarts from there, after a short pause so a run of skips becomes
    // one reload.
    var pendingReloadMs by remember(streamUrl) { mutableStateOf<Long?>(null) }
    fun seekToReal(targetMs: Long) {
        if (directPlay) {
            player.seekTo(targetMs.coerceAtLeast(0L))
        } else {
            pendingReloadMs = targetMs.coerceAtLeast(0L)
            positionMs = pendingReloadMs!!
        }
    }
    LaunchedEffect(pendingReloadMs) {
        val target = pendingReloadMs ?: return@LaunchedEffect
        delay(700)
        onSeekReload(target * TICKS_PER_MS)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    // Bumped on every key press, so the controls stay up while the remote
    // is in use.
    var interaction by remember { mutableStateOf(0) }
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
    var hasReportedStart by remember(player) { mutableStateOf(false) }
    var seekedToResume by remember(player) { mutableStateOf(false) }
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
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                exoError = friendlyPlaybackError(error)
                isBuffering = false
            }
            override fun onPlaybackStateChanged(state: Int) {
                isEnded = state == Player.STATE_ENDED
                isBuffering = state == Player.STATE_BUFFERING || (state == Player.STATE_IDLE && exoError == null)
                // Real port of screens/player.js's own 'ended' listener:
                // needs no known duration at all, the strongest of the
                // three real signals since ExoPlayer only ever reaches
                // STATE_ENDED once this real stream has genuinely run
                // out of data to play.
                if (isEnded) {
                    onReportRealDuration(realPositionMs() / 1000.0)
                    onMarkRealWatchComplete()
                }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (directPlay) {
                    selectAudioTrack(player, audioTracks, selectedAudioStreamIndex, defaultAudioStreamIndex)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            onReportStopped((streamOffsetMs + player.currentPosition) * TICKS_PER_MS)
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

    LaunchedEffect(player, selectedAudioStreamIndex, directPlay) {
        if (!directPlay) return@LaunchedEffect
        selectAudioTrack(player, audioTracks, selectedAudioStreamIndex, defaultAudioStreamIndex)
    }

    LaunchedEffect(player, startPositionTicks) {
        while (isActive) {
            if (player.duration > 0 && player.playbackState != Player.STATE_IDLE) {
                durationMs = streamOffsetMs + player.duration
                // Real port of screens/player.js's own
                // reconcileDuration(): ExoPlayer's own real duration,
                // the strongest of the three real signals
                // reportRealDurationIfUseful() takes since it is the
                // real total, not a lower bound off wherever playback
                // happens to be right now.
                if (directPlay) onReportRealDuration(durationMs / 1000.0)
                if (!seekedToResume && startPositionTicks > 0 && directPlay) {
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
                    onReportStart(realPositionMs() * TICKS_PER_MS)
                }
            }
            if (pendingReloadMs == null) positionMs = realPositionMs()
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
                    // The episode sleep timer counts this boundary; on the
                    // last one there's no Up Next, playback stops at the end.
                    EpisodeSleepTimer.remaining?.let { left ->
                        if (left <= 1) {
                            EpisodeSleepTimer.remaining = null
                            upNextDismissed = true
                            stopAtEnd = true
                        } else {
                            EpisodeSleepTimer.remaining = left - 1
                        }
                    }
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
    LaunchedEffect(upNextShown, upNextDismissed) {
        // Dismissed means stay on this episode, so no countdown either.
        if (!upNextShown || upNextDismissed) return@LaunchedEffect
        upNextCountdown = UPNEXT_COUNTDOWN_SECONDS
        while (upNextCountdown > 0) {
            delay(1000)
            upNextCountdown -= 1
        }
        upNextInfo?.let { onPlayNext(it.itemId) }
    }

    // The episode sleep timer ran out: leave the player once this one ends
    // (a film, with no Up Next, counts as its one episode).
    LaunchedEffect(isEnded) {
        if (!isEnded) return@LaunchedEffect
        if (!stopAtEnd && upNextInfo == null && EpisodeSleepTimer.remaining != null) {
            EpisodeSleepTimer.remaining = null
            stopAtEnd = true
        }
        if (stopAtEnd) onBack()
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(PROGRESS_REPORT_INTERVAL_MS)
            if (hasReportedStart) {
                onReportProgress(realPositionMs() * TICKS_PER_MS, !player.isPlaying)
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, interaction, showSubtitleMenu, showSpeedMenu, showSleepMenu, showAudioMenu, showSourcePanel, showEpisodesPanel) {
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
            // The focused button just left; keep the remote on the player.
            focusRequester.requestFocus()
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
            .focusable()
            .onPreviewKeyEvent {
                interaction++
                false
            }
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
                // With the controls up the D-pad moves between them (the seek
                // bar scrubs on its own); hidden, Left/Right seek straight
                // away and any other key brings them up.
                when (event.key) {
                    Key.Back -> {
                        if (controlsVisible) controlsVisible = false else onBack()
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight, Key.MediaRewind, Key.MediaFastForward -> {
                        val isMedia = event.key == Key.MediaRewind || event.key == Key.MediaFastForward
                        if (controlsVisible && !isMedia) return@onKeyEvent false
                        controlsVisible = true
                        val forward = event.key == Key.DirectionRight || event.key == Key.MediaFastForward
                        val newPos = seekTarget(positionMs, if (forward) SEEK_STEP_MS else -SEEK_STEP_MS, durationMs)
                        seekToReal(newPos)
                        // Real port of screens/player.js's own
                        // showScrubPreview(): a preview of the seek's own
                        // landing spot, right after each seek.
                        if (hasTrickplay) {
                            scrubPositionMs = newPos
                            scrubFrame = onComputeTrickplayFrame(newPos)
                        }
                        true
                    }
                    Key.MediaPlayPause -> {
                        if (player.isPlaying) player.pause() else player.play()
                        controlsVisible = true
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (controlsVisible) return@onKeyEvent false
                        if (player.isPlaying) player.pause() else player.play()
                        controlsVisible = true
                        scrubFrame = null
                        scrubPositionMs = null
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (controlsVisible) return@onKeyEvent false
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

        val showPauseOverlay = pauseInfo != null && hasReportedStart && !playWhenReadyState && !showResumePrompt && !isEnded
        if (showPauseOverlay) {
            PauseOverlay(info = pauseInfo!!)
        }

        val problem = exoError
        if (problem != null) {
            PlaybackErrorPanel(
                problem = problem,
                onRetry = {
                    exoError = null
                    isBuffering = true
                    player.prepare()
                    player.play()
                },
                onBack = onBack,
            )
        } else if (isBuffering && pauseInfo != null) {
            BufferingOverlay(info = pauseInfo!!)
        } else if (isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PulsingTitle(text = title.ifBlank { "Loading" })
            }
        }

        // Switching streams: say so until the new one is actually playing.
        if (switchingTo != null) {
            LaunchedEffect(switchingTo, isBuffering, isPlaying) {
                if (!isBuffering && isPlaying) {
                    delay(300)
                    onSwitchSettled()
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(JellioBgElevated.copy(alpha = 0.92f))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Switching to $switchingTo…",
                    color = JellioText,
                    style = androidx.tv.material3.MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
            }
        }

        val anyMenuOpen = showSubtitleMenu || showSpeedMenu || showSleepMenu || showAudioMenu || showSourcePanel || showEpisodesPanel
        if (controlsVisible) {
            PlayerControls(
                title = title,
                subtitle = subtitle,
                isPlaying = isPlaying,
                enabled = !anyMenuOpen,
                positionMs = positionMs,
                durationMs = durationMs,
                hasSubtitleTracks = subtitleTracks.isNotEmpty(),
                hasAudioTracks = audioTracks.size > 1,
                hasSourceOptions = sourceOptions.size > 1,
                hasEpisodes = seasons.isNotEmpty(),
                scrubFrame = scrubFrame,
                scrubPositionMs = scrubPositionMs,
                speedLabel = formatSpeed(playbackSpeed),
                sleepTimerActive = sleepTimerEndTimeMs != null || EpisodeSleepTimer.remaining != null,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onSkip = { deltaMs ->
                    seekToReal(seekTarget(positionMs, deltaMs, durationMs))
                },
                onScrub = { target ->
                    scrubPositionMs = target
                    scrubFrame = if (hasTrickplay) onComputeTrickplayFrame(target) else null
                },
                onScrubEnd = { target ->
                    if (target != null) seekToReal(target)
                    scrubFrame = null
                    scrubPositionMs = null
                },
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
                    onSelectAudioTrack(streamIndex, realPositionMs() * TICKS_PER_MS)
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
                    onSelectSource(source, realPositionMs() * TICKS_PER_MS)
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
                activeEpisodes = EpisodeSleepTimer.remaining,
                minutesActive = sleepTimerEndTimeMs != null,
                onSelect = { minutes ->
                    showSleepMenu = false
                    EpisodeSleepTimer.remaining = null
                    onStartSleepTimer(minutes)
                },
                onSelectEpisodes = { count ->
                    showSleepMenu = false
                    if (sleepTimerEndTimeMs != null) onCancelSleepTimer()
                    EpisodeSleepTimer.remaining = count
                    onShowToast(if (count == 1) "Stopping after this episode" else "Stopping after $count episodes")
                },
                onCancel = {
                    showSleepMenu = false
                    EpisodeSleepTimer.remaining = null
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
        // Auto-skip intros (Settings > Playback): once per intro, the same
        // as screens/player.js does when it's switched on.
        LaunchedEffect(activeSkip?.targetSeconds, PlayerPrefs.autoSkipIntro) {
            val segment = activeSkip ?: return@LaunchedEffect
            if (PlayerPrefs.autoSkipIntro && segment.label == "Skip Intro" && autoSkippedTo != segment.targetSeconds) {
                autoSkippedTo = segment.targetSeconds
                seekToReal((segment.targetSeconds * 1000).toLong())
                onShowToast("Skipped intro")
            }
        }
        if (activeSkip != null && !(upNextInfo != null && upNextShown && !upNextDismissed)) {
            SkipSegmentButton(
                label = activeSkip.label,
                onClick = { seekToReal((activeSkip.targetSeconds * 1000).toLong()) },
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
                    onSelectSubtitle(track, realPositionMs() * TICKS_PER_MS)
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
                    resumePromptDismissed = true
                    player.play()
                },
                onRestart = {
                    resumePromptDismissed = true
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
    enabled: Boolean = true,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long?) -> Unit,
    onOpenSubtitleMenu: () -> Unit,
    onOpenSpeedMenu: () -> Unit,
    onOpenSleepMenu: () -> Unit,
    onOpenAudioMenu: () -> Unit,
    onOpenSourceMenu: () -> Unit,
    onOpenEpisodesMenu: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().focusProperties { canFocus = enabled }) {
        Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 48.dp)) {
            Text(text = title, color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, color = JellioTextSecondary)
            }
        }

        val playPauseFocusRequester = remember { FocusRequester() }
        LaunchedEffect(enabled) {
            if (enabled) {
                playPauseFocusRequester.requestFocus()
            }
        }

        // Real port of screens/player.js's own transport row: back 10
        // seconds, play/pause, forward 10 seconds.
        Row(
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            TransportButton(icon = Icons.Filled.Replay10, label = "Back 10 seconds", size = 68.dp, onClick = { onSkip(-SEEK_STEP_MS) })
            TransportButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (isPlaying) "Pause" else "Play",
                size = 96.dp,
                onClick = onPlayPause,
                modifier = Modifier.focusRequester(playPauseFocusRequester),
            )
            TransportButton(icon = Icons.Filled.Forward10, label = "Forward 10 seconds", size = 68.dp, onClick = { onSkip(SEEK_STEP_MS) })
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
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onScrub = onScrub,
                onScrubEnd = onScrubEnd,
                onPlayPause = onPlayPause,
            )

            // Real port of css/app.css's own .jellio-player-pill /
            // screens/player.js's own buildPillButton(): real feedback
            // live found this screen's own previous shape (a lone "1x"
            // pill plus a separate row of bare icon circles, pinned to
            // this screen's own top-right corner) didn't match the web
            // build's own real player at all - one real pill-shaped bar
            // instead, icon-over-label per button exactly like that
            // file's own real speed/subtitle/audio/source/episodes/
            // sleep buttons, docked under this real seek row the same
            // way that file's own CSS margin already places it.
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(JellioBgElevated.copy(alpha = 0.82f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(999.dp)),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(6.dp),
                    ) {
                        PlayerPillButton(icon = Icons.Filled.Speed, label = speedLabel, onClick = onOpenSpeedMenu)
                        if (hasSubtitleTracks) {
                            PlayerPillButton(icon = Icons.Filled.ClosedCaption, label = "Subtitles", onClick = onOpenSubtitleMenu)
                        }
                        // Real port of the pill's own audioButton: disabled
                        // (rebuildAudioMenu()'s own early return) whenever this
                        // source carries one real audio track or fewer, same real
                        // condition hasAudioTracks already checks before this
                        // button even renders.
                        if (hasAudioTracks) {
                            PlayerPillButton(icon = Icons.Filled.GraphicEq, label = "Audio", onClick = onOpenAudioMenu)
                        }
                        // Real port of the pill's own sourceButton: disabled
                        // (sourceButton.disabled) until getMediaSources(itemId)
                        // actually resolves more than one real option, same real
                        // condition hasSourceOptions already checks before this
                        // button even renders.
                        if (hasSourceOptions) {
                            PlayerPillButton(icon = Icons.Filled.SwapHoriz, label = "Sources", onClick = onOpenSourceMenu)
                        }
                        // Real port of the pill's own episodesButton: disabled
                        // (episodesButton.disabled) until getSeasons(item.SeriesId)
                        // actually resolves a real season, same real condition
                        // hasEpisodes already checks before this button even
                        // renders. A Movie never gets here at all, same real gate
                        // this file's own isEpisodeItem && item.SeriesId check
                        // applies before that fetch ever fires.
                        if (hasEpisodes) {
                            PlayerPillButton(icon = Icons.Filled.VideoLibrary, label = "Episodes", onClick = onOpenEpisodesMenu)
                        }
                        // Real port of the pill's own sleepButton: no live
                        // countdown label the way that file's own button never
                        // gets one either, just its own real
                        // jellio-player-pill-btn-active class toggled on and
                        // off, ported here as the same real active tint.
                        PlayerPillButton(
                            icon = Icons.Filled.Bedtime,
                            label = "Sleep",
                            active = sleepTimerActive,
                            onClick = onOpenSleepMenu,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.35f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = CircleShape,
            )
        ),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = label, tint = JellioText, modifier = Modifier.size(size / 2))
        }
    }
}

// The seek bar as its own stop for the remote: Left/Right move a target
// (further the longer they're held), the picture under it previews the
// spot, and letting go jumps there. Up/Down leave it as usual.
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long?) -> Unit,
    onPlayPause: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Long?>(null) }
    val shown = target ?: positionMs
    val progress = if (durationMs > 0) (shown.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused && target != null) {
                    target = null
                    onScrubEnd(null)
                }
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (durationMs <= 0) return@onPreviewKeyEvent true
                        if (event.type == KeyEventType.KeyDown) {
                            val held = event.nativeKeyEvent.repeatCount
                            val step = when {
                                held > 40 -> 60_000L
                                held > 15 -> 30_000L
                                else -> SEEK_STEP_MS
                            }
                            val next = ((target ?: positionMs) + if (event.key == Key.DirectionRight) step else -step).coerceIn(0L, durationMs)
                            target = next
                            onScrub(next)
                        } else if (event.type == KeyEventType.KeyUp) {
                            val landing = target
                            target = null
                            onScrubEnd(landing)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (event.type == KeyEventType.KeyUp) {
                            val landing = target
                            if (landing != null) {
                                target = null
                                onScrubEnd(landing)
                            } else {
                                onPlayPause()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val barHeight = if (focused) 8.dp else 4.dp
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth().height(20.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(barHeight).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(barHeight)
                        .background(if (focused) JellioSecondary else JellioText, RoundedCornerShape(4.dp)),
                )
            }
            if (focused) {
                Box(modifier = Modifier.fillMaxWidth(progress), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(text = formatMs(shown), color = if (target != null) JellioText else JellioTextSecondary)
            Text(text = " / " + formatMs(durationMs), color = JellioTextSecondary)
            if (focused && target == null) {
                Text(
                    text = "   ◀ ▶ to scrub",
                    color = JellioTextSecondary,
                    style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
private fun PlayerPillButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (active) JellioSecondary else JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = if (active) JellioSecondary else JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(999.dp),
            )
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label)
            Text(text = label, style = androidx.tv.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
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
private fun BufferingOverlay(info: PauseOverlayInfo, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        if (info.backdropUrl != null) {
            AsyncImage(
                model = info.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // A very light gradient just to make controls readable if they are open
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                    startY = 0f,
                    endY = 1080f // Approximate 1080p height
                )
            ))
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (info.logoUrl != null) {
                val infiniteTransition = rememberInfiniteTransition(label = "bouncing_logo")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "logo_scale"
                )
                AsyncImage(
                    model = info.logoUrl,
                    contentDescription = info.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(0.4f).fillMaxHeight(0.4f).scale(scale)
                )
            } else {
                PulsingTitle(text = info.title.ifBlank { "Loading" })
            }
        }
    }
}

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
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = JellioSecondary,
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.35f),
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.5.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
                    ),
                ) {
                    Text(text = "Play now ($secondsRemaining)", modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.28f),
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
                    ),
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
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.32f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, Color.White),
                shape = RoundedCornerShape(999.dp),
            )
        ),
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
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = JellioSecondary,
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.35f),
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.5.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
                    ),
                    modifier = Modifier.focusRequester(resumeFocusRequester),
                ) {
                    Text(text = "Resume", modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
                Surface(
                    onClick = onRestart,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.28f),
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
                    ),
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

// Speed drawer matching SubtitleMenu and AudioMenu right drawers.
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
private fun SpeedMenu(selectedSpeed: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val focusRequesters = remember { PLAYBACK_SPEEDS.map { FocusRequester() } }
    val activeIndex = PLAYBACK_SPEEDS.indexOfFirst { it == selectedSpeed }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching {
            focusRequesters.getOrNull(activeIndex)?.requestFocus() ?: focusRequesters.firstOrNull()?.requestFocus()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(vertical = 48.dp),
        ) {
            Text(
                text = "Playback Speed",
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LazyColumn {
                itemsIndexed(PLAYBACK_SPEEDS) { index, speed ->
                    val req = focusRequesters.getOrNull(index) ?: remember { FocusRequester() }
                    SubtitleMenuRow(
                        label = formatSpeed(speed),
                        isSelected = speed == selectedSpeed,
                        onClick = {
                            onSelect(speed)
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(req),
                    )
                }
            }
        }
    }
}

// Sleep timer drawer matching SubtitleMenu and AudioMenu right drawers.
@Composable
private fun SleepMenuHeading(text: String) {
    Text(
        text = text,
        color = JellioTextSecondary,
        style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
private fun SleepMenu(
    activeEpisodes: Int?,
    minutesActive: Boolean,
    onSelect: (Int) -> Unit,
    onSelectEpisodes: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { firstItemFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(vertical = 48.dp),
        ) {
            Text(
                text = "Sleep Timer",
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LazyColumn {
                item {
                    SubtitleMenuRow(
                        label = "Cancel timer",
                        isSelected = false,
                        onClick = {
                            onCancel()
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(firstItemFocusRequester),
                    )
                }
                item { SleepMenuHeading(if (minutesActive) "Stop after (running)" else "Stop after") }
                items(SLEEP_TIMER_OPTIONS) { minutes ->
                    SubtitleMenuRow(
                        label = "$minutes min",
                        isSelected = false,
                        onClick = {
                            onSelect(minutes)
                            onDismiss()
                        },
                    )
                }
                item { SleepMenuHeading("Or stop after") }
                items(EPISODE_SLEEP_TIMER_OPTIONS) { count ->
                    SubtitleMenuRow(
                        label = if (count == 1) "This episode" else "$count episodes",
                        isSelected = activeEpisodes == count,
                        onClick = {
                            onSelectEpisodes(count)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

private fun selectAudioTrack(
    player: Player,
    audioTracks: List<AudioTrackUiState>,
    targetIndex: Int?,
    defaultIndex: Int?,
) {
    val targetStreamIndex = targetIndex ?: defaultIndex ?: return
    val targetTrack = audioTracks.firstOrNull { it.streamIndex == targetStreamIndex } ?: return
    val ordinal = audioTracks.indexOf(targetTrack)
    if (ordinal < 0) return

    val tracks = player.currentTracks
    val allAudioCandidates = mutableListOf<Pair<androidx.media3.common.Tracks.Group, Int>>()
    for (group in tracks.groups) {
        if (group.type == C.TRACK_TYPE_AUDIO) {
            for (i in 0 until group.length) {
                allAudioCandidates.add(Pair(group, i))
            }
        }
    }
    if (allAudioCandidates.isEmpty()) return

    val match = allAudioCandidates.firstOrNull { (group, i) ->
        val format = group.getTrackFormat(i)
        val lang = format.language
        lang != null && targetTrack.language != null && lang.equals(targetTrack.language, ignoreCase = true)
    } ?: allAudioCandidates.getOrNull(ordinal)

    if (match != null && match.first.isTrackSupported(match.second)) {
        val params = player.trackSelectionParameters.buildUpon()
        params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        params.setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, match.second))
        player.trackSelectionParameters = params.build()
    }
}

// Audio menu right drawer matching SubtitleMenu: lists all audio tracks with
// auto-focus on the active track and high-contrast TV selection.
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
private fun AudioMenu(
    tracks: List<AudioTrackUiState>,
    selectedStreamIndex: Int?,
    defaultStreamIndex: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val focusRequesters = remember(tracks) { tracks.map { FocusRequester() } }
    val activeIndex = tracks.indexOfFirst {
        if (selectedStreamIndex == null) it.streamIndex == defaultStreamIndex else it.streamIndex == selectedStreamIndex
    }.coerceAtLeast(0)

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching {
            focusRequesters.getOrNull(activeIndex)?.requestFocus() ?: focusRequesters.firstOrNull()?.requestFocus()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
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
                text = "Audio",
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LazyColumn {
                itemsIndexed(tracks) { index, track ->
                    val isActive = if (selectedStreamIndex == null) {
                        track.streamIndex == defaultStreamIndex
                    } else {
                        track.streamIndex == selectedStreamIndex
                    }
                    val req = focusRequesters.getOrNull(index) ?: remember { FocusRequester() }
                    SubtitleMenuRow(
                        label = track.label,
                        isSelected = isActive,
                        onClick = {
                            if (!isActive) onSelect(track.streamIndex)
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(req),
                    )
                }
            }
        }
    }
}

// Sources side panel right drawer.
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
private fun SourcePanel(
    sources: List<MediaSourceDto>,
    currentMediaSourceId: String?,
    onSelect: (MediaSourceDto) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { firstFocusRequester.requestFocus() }
    }
    // Same language and quality chips as the stream picker before playback,
    // combinable the same way.
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf<String?>(null) }
    val languages = remember(sources) {
        val counts = linkedMapOf<String, Int>()
        sources.forEach { source -> sourceAudioLanguages(source).forEach { code -> counts[code] = (counts[code] ?: 0) + 1 } }
        counts.keys.sortedWith(compareByDescending<String> { counts[it] ?: 0 }.thenBy { languageName(it) })
    }
    val qualities = remember(sources) {
        val present = sources.mapNotNull { sourceQuality(it) }.toSet()
        QUALITY_ORDER.filter { it in present }
    }
    val languageFocus = remember(languages) { (listOf<String?>(null) + languages).associateWith { FocusRequester() } }
    val qualityFocus = remember(qualities) { (listOf<String?>(null) + qualities).associateWith { FocusRequester() } }
    val showLanguages = languages.size > 1
    val showQualities = qualities.size > 1
    val filtered = sources.filter { source ->
        (selectedLanguage == null || sourceAudioLanguages(source).contains(selectedLanguage)) &&
            (selectedQuality == null || sourceQuality(source) == selectedQuality)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 48.dp),
        ) {
            Text(text = "Sources", color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (showLanguages) {
                    item(key = "languages") {
                        FilterChipRow(
                            chips = listOf<Pair<String?, String>>(null to "All") + languages.map { it to languageName(it) },
                            selected = selectedLanguage,
                            onSelect = { selectedLanguage = it },
                            chipFocusRequesters = languageFocus,
                            topPadding = 12.dp,
                        )
                    }
                }
                if (showQualities) {
                    item(key = "qualities") {
                        FilterChipRow(
                            chips = listOf<Pair<String?, String>>(null to "All") + qualities.map { it to it },
                            selected = selectedQuality,
                            onSelect = { selectedQuality = it },
                            chipFocusRequesters = qualityFocus,
                            upTarget = if (showLanguages) languageFocus[selectedLanguage] else null,
                            topPadding = if (showLanguages) 0.dp else 12.dp,
                        )
                    }
                }
                item(key = "count") {
                    Text(
                        text = if (filtered.isEmpty()) "No streams match these filters" else "${filtered.size} stream${if (filtered.size == 1) "" else "s"}",
                        color = JellioTextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                itemsIndexed(filtered, key = { _, it -> it.Id ?: it.hashCode() }) { index, source ->
                    SourceCard(
                        source = source,
                        onClick = {
                            if (source.Id != currentMediaSourceId) onSelect(source)
                            onDismiss()
                        },
                        isActive = source.Id == currentMediaSourceId,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                    )
                }
            }
        }
    }
}

// Episodes side panel right drawer: season tabs plus episode list.
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
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
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { firstFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 48.dp),
        ) {
            Text(text = "Episodes", color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                items(seasons, key = { it.Id }) { season ->
                    val isActive = season.Id == selectedSeasonId
                    Surface(
                        onClick = { onSelectSeason(season.Id) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isActive) JellioSecondary else Color.White.copy(alpha = 0.08f),
                            contentColor = if (isActive) JellioBg else JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.28f),
                            focusedContentColor = JellioText,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(999.dp),
                            )
                        ),
                    ) {
                        Text(text = season.Name.orEmpty(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                itemsIndexed(episodes, key = { _, it -> it.itemId }) { index, episode ->
                    EpisodeRow(
                        episode = episode,
                        isActive = episode.itemId == currentItemId,
                        onClick = { onSelectEpisode(episode.itemId) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodePanelEntry,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) JellioSecondary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp),
            )
        ),
        modifier = modifier.fillMaxWidth(),
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
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
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { firstFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
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
                item {
                    SubtitleMenuRow(
                        label = "Off",
                        isSelected = selectedIndex == null,
                        onClick = { onSelect(null) },
                        modifier = Modifier.focusRequester(firstFocusRequester),
                    )
                }
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
                        focusedContainerColor = Color.White.copy(alpha = 0.28f),
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
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
private fun SubtitleMenuRow(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(8.dp),
            )
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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

// Stands in for a title with no logo image: its name, pulsing slowly
// the way the logo does.
@Composable
private fun PulsingTitle(text: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_title")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "title_alpha",
    )
    Text(
        text = text,
        color = JellioText.copy(alpha = alpha),
        style = androidx.tv.material3.MaterialTheme.typography.displaySmall,
        modifier = modifier.padding(horizontal = 48.dp),
    )
}
