package com.jellio.tv.ui.player

import com.jellio.tv.ui.i18n.Strings
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.jellio.tv.data.network.APP_VERSION
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.ui.PlayerView
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.playback.SkipSegments
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.SUBTITLE_POSITIONS
import com.jellio.tv.data.model.SubtitleStyle
import com.jellio.tv.data.session.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(
    session: Session,
    itemId: String,
    mediaSourceId: String?,
    onBack: () -> Unit,
    onPlayNext: (String) -> Unit,
    modifier: Modifier = Modifier,
    startPositionTicks: Long? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val subtitleStyle by viewModel.subtitleStyle.collectAsState()

    LaunchedEffect(itemId, mediaSourceId, startPositionTicks) {
        viewModel.load(session, itemId, mediaSourceId, startPositionTicks = startPositionTicks)
    }
    val failover by viewModel.failover.collectAsState()
    DisposableEffect(Unit) {
        PlayerPresence.active = true
        onDispose {
            PlayerPresence.active = false
            viewModel.onScreenDisposed()
        }
    }
    val frameRateActivity = LocalContext.current as? android.app.Activity
    LaunchedEffect(uiState.videoFrameRate) {
        val fps = uiState.videoFrameRate
        if (frameRateActivity != null && fps != null && PlayerPrefs.matchFrameRate) {
            FrameRateMatcher.apply(frameRateActivity, fps)
        }
    }
    DisposableEffect(frameRateActivity) {
        onDispose { frameRateActivity?.let { FrameRateMatcher.restore(it) } }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when {
            uiState.isLoading -> uiState.pauseInfo?.let { BufferingOverlay(info = it) }
                ?: com.jellio.tv.ui.common.TitleLoadingArt(emptyList(), null, "", Modifier)
            uiState.error != null -> Box(Modifier.fillMaxSize()) {
                uiState.pauseInfo?.art?.let { com.jellio.tv.ui.common.TitleArtBackdrop(it, Modifier.fillMaxSize()) }
                PlaybackErrorPanel(
                    problem = PlaybackProblem(uiState.error ?: Strings.get("playback_err_cant_start_title"), uiState.errorDetail.orEmpty()),
                    onBack = onBack,
                    onRetry = if (uiState.failoverRetry) {
                        { viewModel.retryFailover(session) }
                    } else {
                        null
                    },
                )
            }
            uiState.streamUrl != null -> PlayerSurface(
                session = session,
                deviceId = viewModel.deviceId,
                streamUrl = uiState.streamUrl!!,
                startPositionTicks = uiState.startPositionTicks,
                resumePercent = uiState.resumePercent,
                title = uiState.title,
                subtitle = uiState.subtitle,
                artworkUrl = uiState.artworkUrl,
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
                toastMessage = uiState.toastMessage,
                toastId = uiState.toastId,
                onDismissToast = { id -> viewModel.clearToast(id) },
                pauseInfo = uiState.pauseInfo,
                upNextInfo = uiState.upNextInfo,
                skipSegments = uiState.skipSegments,
                skipSegmentsResolved = uiState.skipSegmentsResolved,
                sleepTimerEndTimeMs = uiState.sleepTimerEndTimeMs,
                onBack = onBack,
                onReportStart = { viewModel.reportStart(it) },
                onUpNextShown = { viewModel.preloadUpNextSources(session) },
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
                onSetSubtitleLook = { viewModel.setSubtitleLook(it) },
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
                switchingLoaded = uiState.switchingLoaded,
                onSeekReload = { ticks -> viewModel.reloadAt(session, ticks) },
                onSwitchSettled = { viewModel.clearSwitching() },
                onBufferSwitch = { ticks -> viewModel.switchForBuffering(session, ticks) },
                expectedRuntimeMs = uiState.expectedRuntimeMs,
                reencodingVideo = uiState.reencodingVideo,
                onStreamFailed = { reason -> viewModel.streamFailed(session, reason) },
                onPlaceholder = { sign -> viewModel.placeholderDetected(session, sign) },
                onPlaybackStarted = {
                    viewModel.failoverPlaying()
                    if (uiState.switchingTo != null && uiState.switchingLoaded) {
                        viewModel.clearSwitching()
                    }
                },
                remoteControlManager = viewModel.remoteControlManager,
            )
        }
        failover?.let { notice ->
            FailoverOverlay(
                notice = notice,
                onStop = {
                    viewModel.stopFailover()
                    onBack()
                },
            )
        }
    }
}

@Composable
internal fun PlayerSurface(
    session: Session,
    deviceId: String,
    streamUrl: String,
    startPositionTicks: Long,
    resumePercent: Int?,
    title: String,
    subtitle: String,
    artworkUrl: String?,
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
    toastMessage: String?,
    toastId: Long,
    onDismissToast: (Long) -> Unit,
    pauseInfo: PauseOverlayInfo?,
    upNextInfo: UpNextInfo?,
    skipSegments: SkipSegments?,
    skipSegmentsResolved: Boolean = false,
    sleepTimerEndTimeMs: Long?,
    onBack: () -> Unit,
    onReportStart: (Long) -> Unit,
    onUpNextShown: () -> Unit = {},
    onReportProgress: (Long, Boolean) -> Unit,
    onReportStopped: (Long) -> Unit,
    onMarkRealWatchComplete: () -> Unit,
    onReportRealDuration: (Double) -> Unit,
    onSelectSubtitle: (SubtitleTrackUiState?, Long) -> Unit,
    subtitleStyle: SubtitleStyle,
    onSetSubtitleSize: (String) -> Unit,
    onSetSubtitleBackground: (String) -> Unit,
    onSetSubtitleLook: (SubtitleStyle) -> Unit,
    onSelectAudioTrack: (Int, Long) -> Unit,
    onSelectSource: (MediaSourceDto, Long) -> Unit,
    onSelectSeason: (String) -> Unit,
    onRestart: () -> Unit,
    onPlayNext: (String) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onShowToast: (String) -> Unit,
    switchingTo: String?,
    switchingLoaded: Boolean,
    onSwitchSettled: () -> Unit,
    onBufferSwitch: suspend (Long) -> Boolean,
    onSeekReload: (Long) -> Unit,
    expectedRuntimeMs: Long = 0,
    reencodingVideo: Boolean = false,
    onStreamFailed: (String) -> Unit = {},
    onPlaceholder: (PlaceholderSign) -> Unit = {},
    onPlaybackStarted: () -> Unit = {},
    remoteControlManager: com.jellio.tv.data.remote.RemoteControlManager? = null,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val groupWatch: com.jellio.tv.ui.groupwatch.GroupWatchViewModel = hiltViewModel()
    val syncPlay = groupWatch.syncPlay
    val syncGroup by syncPlay.group.collectAsState()
    val syncTarget by syncPlay.target.collectAsState()
    // Set while the group is playing this very title: play, pause and
    // seek then go through the server so everyone moves together.
    val syncPlaylistItemId = syncTarget?.takeIf { com.jellio.tv.data.syncplay.SyncPlayManager.sameId(it.itemId, currentItemId) }?.playlistItemId
    val syncScope = rememberCoroutineScope()
    // Set while this TV reloads to catch up with the group: the reloaded
    // player waits for the group's Unpause instead of starting itself.
    var syncReloadPending by remember { mutableStateOf(false) }
    // A seek the server is waiting on: Ready goes out once playable, even
    // when the seek landed inside the buffer and never buffered.
    var syncReadyNeeded by remember { mutableStateOf(false) }

    var resumePromptDismissed by remember { mutableStateOf(false) }
    // No resume question in a group: the group decides where playback is.
    val inGroupAtOpen = remember { syncPlay.group.value != null }
    val showResumePrompt = resumePercent != null && !resumePromptDismissed && !inGroupAtOpen

    val activeSideloadedTrack = subtitleTracks.firstOrNull {
        it.streamIndex == selectedSubtitleIndex && it.isTextBased && it.url != null && (!directPlay || it.isExternal)
    }

    val httpDataSourceFactory = remember(session.accessToken) {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Jellio-TV/$APP_VERSION")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(
                buildMap {
                    put("X-Emby-Token", session.accessToken)
                    put("Authorization", buildEmbyAuthorizationHeader(deviceId, APP_VERSION, session.accessToken))
                }
            )
    }

    // Audio and subtitle sync, read by SyncDelayMediaSource as samples are read.
    val syncDelays = remember(currentItemId) { SyncDelays(PlayerPrefs.audioDelayMs, SubtitleSync.load(context, currentItemId)) }
    var subtitleOffsetMs by remember(currentItemId) { mutableStateOf(syncDelays.subtitleMs) }
    var audioDelayMs by remember { mutableStateOf(syncDelays.audioMs) }
    var subtitleResyncSeq by remember { mutableStateOf(0) }
    fun setSubtitleOffset(ms: Int) {
        subtitleOffsetMs = ms
        syncDelays.subtitleMs = ms
        SubtitleSync.save(context, currentItemId, ms)
        subtitleResyncSeq++
    }

    @OptIn(UnstableApi::class)
    fun buildBaseMediaSource(sideloadedTrack: SubtitleTrackUiState?): androidx.media3.exoplayer.source.MediaSource {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDisplayTitle(title)
                    .setArtist(subtitle.ifEmpty { null })
                    .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
            )
            .build()
        val videoSource = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)
            .createMediaSource(mediaItem)
        val subUrl = sideloadedTrack?.url ?: return videoSource
        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setId(SIDELOADED_SUBTITLE_PREFIX + sideloadedTrack.streamIndex)
            .setLabel(sideloadedTrack.label)
            .setLanguage(sideloadedTrack.language)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val subtitleSource = androidx.media3.exoplayer.source.SingleSampleMediaSource.Factory(httpDataSourceFactory)
            .setTreatLoadErrorsAsEndOfStream(true)
            .createMediaSource(subtitleConfig, C.TIME_UNSET)
        return androidx.media3.exoplayer.source.MergingMediaSource(videoSource, subtitleSource)
    }

    // The sync layer only goes in once an offset is set, so normal playback
    // reads the stream directly; setting one reloads the source with it.
    @OptIn(UnstableApi::class)
    fun buildPlayerMediaSource(sideloadedTrack: SubtitleTrackUiState?): androidx.media3.exoplayer.source.MediaSource {
        val base = buildBaseMediaSource(sideloadedTrack)
        syncDelays.wrapped = syncDelays.audioMs != 0 || syncDelays.subtitleMs != 0
        if (!syncDelays.wrapped) return base
        return SyncDelayMediaSource(
            base,
            audioDelayUs = { syncDelays.audioMs * 1000L },
            subtitleDelayUs = { syncDelays.subtitleMs * 1000L },
        )
    }

    val player = remember(streamUrl) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val audioSink = DefaultAudioSink.Builder(context)
            .setAudioCapabilities(
                if (PlayerPrefs.audioPassthrough) {
                    AudioCapabilities.getCapabilities(context)
                } else {
                    AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
                }
            )
            .build()
        val renderersFactory = object : DefaultRenderersFactory(context) {
            @OptIn(UnstableApi::class)
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                return audioSink
            }
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(buildLoadControl(PlayerPrefs.prebuffer))
            .setAudioAttributes(audioAttributes, true)
            .build().apply {
            // Direct play opens at the resume point instead of buffering
            // from 0:00 and seeking once the duration is known.
            val resumeMs = if (directPlay && startPositionTicks > 0) startPositionTicks / TICKS_PER_MS else 0L
            setMediaSource(buildPlayerMediaSource(activeSideloadedTrack), resumeMs)
            // Keeps the device and its Wi-Fi awake while playing.
            setWakeMode(C.WAKE_MODE_NETWORK)
            // In a group the server starts playback; only a TV following a
            // group already playing this title starts on its own.
            val followingPlayingGroup = syncPlay.target.value?.let {
                com.jellio.tv.data.syncplay.SyncPlayManager.sameId(it.itemId, currentItemId) && it.isPlaying
            } == true
            val groupHolds = syncPlay.group.value != null && (!followingPlayingGroup || syncReloadPending)
            playWhenReady = !showResumePrompt && !groupHolds
            prepare()
        }
    }

    DisposableEffect(player, PlayerPrefs.dialogueBoost) {
        val sessionId = player.audioSessionId
        val loudnessEnhancer = if (sessionId != C.AUDIO_SESSION_ID_UNSET && PlayerPrefs.dialogueBoost) {
            runCatching {
                android.media.audiofx.LoudnessEnhancer(sessionId).apply {
                    setTargetGain(600)
                    enabled = true
                }
            }.getOrNull()
        } else {
            null
        }
        onDispose { runCatching { loudnessEnhancer?.release() } }
    }

    DisposableEffect(player) {
        val mediaSession = MediaSession.Builder(context, player).build()
        onDispose { mediaSession.release() }
    }

    // Turned down while a sound sent from the dashboard plays.
    LaunchedEffect(player) {
        androidx.compose.runtime.snapshotFlow { com.jellio.tv.ui.announce.PlaybackDuck.level }
            .collect { player.volume = it }
    }

    var isPlaying by remember(player) { mutableStateOf(false) }
    var playWhenReadyState by remember(streamUrl) { mutableStateOf(!showResumePrompt) }
    var isEnded by remember(streamUrl) { mutableStateOf(false) }
    var endedAsPlaceholder by remember(streamUrl) { mutableStateOf(false) }
    val placeholderWatch = remember(streamUrl) { PlaceholderWatch() }
    var stopAtEnd by remember { mutableStateOf(false) }
    var subtitleMissingReported by remember { mutableStateOf(false) }
    val selectedSubtitleLanguage = subtitleTracks.firstOrNull { it.streamIndex == selectedSubtitleIndex }?.language
    // The player's listener lives as long as the player; these keep it
    // reading the current choices, not the ones from when it was made.
    val currentSubtitleTracks by androidx.compose.runtime.rememberUpdatedState(subtitleTracks)
    val currentSubtitleIndex by androidx.compose.runtime.rememberUpdatedState(selectedSubtitleIndex)
    val currentSubtitleLanguage by androidx.compose.runtime.rememberUpdatedState(selectedSubtitleLanguage)
    val currentAudioIndex by androidx.compose.runtime.rememberUpdatedState(selectedAudioStreamIndex)
    val currentAudioTracks by androidx.compose.runtime.rememberUpdatedState(audioTracks)
    val currentSwitchSettled by androidx.compose.runtime.rememberUpdatedState(onSwitchSettled)
    val currentSwitchingTo by androidx.compose.runtime.rememberUpdatedState(switchingTo)
    val currentSwitchingLoaded by androidx.compose.runtime.rememberUpdatedState(switchingLoaded)
    // Both arrive after the player is built; the position loop must see them.
    val currentUpNextInfo by androidx.compose.runtime.rememberUpdatedState(upNextInfo)
    val currentSkipSegments by androidx.compose.runtime.rememberUpdatedState(skipSegments)
    var seekFlash by remember { mutableStateOf<SeekFlash?>(null) }
    var holdSeekTargetMs by remember(streamUrl) { mutableStateOf<Long?>(null) }
    var holdSeekDeltaMs by remember(streamUrl) { mutableLongStateOf(0L) }
    var holdSeekKey by remember(streamUrl) { mutableStateOf<Key?>(null) }
    var holdSeekStartPos by remember(streamUrl) { mutableLongStateOf(0L) }
    var holdSeekStartTime by remember(streamUrl) { mutableLongStateOf(0L) }
    var holdSeekLastTickTime by remember(streamUrl) { mutableLongStateOf(0L) }
    var wasPlayingBeforeHold by remember(streamUrl) { mutableStateOf(false) }
    var autoSkippedTo by remember { mutableStateOf<Double?>(null) }
    var isBuffering by remember(player) {
        mutableStateOf(player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_IDLE)
    }
    val bufferWatch = remember { BufferWatch() }
    var bufferPromptVisible by remember { mutableStateOf(false) }

    val hostView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(isPlaying, isBuffering) {
        hostView.keepScreenOn = isPlaying || isBuffering
        onDispose { hostView.keepScreenOn = false }
    }
    var exoError by remember { mutableStateOf<PlaybackProblem?>(null) }
    var positionMs by remember { mutableLongStateOf(startPositionTicks / TICKS_PER_MS) }
    var durationMs by remember { mutableLongStateOf(0L) }
    // The title's known runtime until the stream reports its own.
    val shownDurationMs = if (durationMs > 0) durationMs else expectedRuntimeMs

    // Where in the title the stream's own time zero is: 0 for direct play,
    // the start position for a transcode that began encoding there.
    val streamOffsetMs = if (directPlay) 0L else startPositionTicks / TICKS_PER_MS
    fun realPositionMs(): Long = streamOffsetMs + player.currentPosition

    var pendingReloadMs by remember(streamUrl) { mutableStateOf<Long?>(null) }
    fun seekToReal(targetMs: Long) {
        bufferWatch.lastSeekAt = System.currentTimeMillis()
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

    fun togglePlay() {
        if (syncPlaylistItemId != null && groupWatch.party.shouldAsk()) {
            groupWatch.party.ask(if (player.isPlaying) "pause" else "play")
        } else if (syncPlaylistItemId != null) {
            val wasPlaying = player.isPlaying
            syncScope.launch { if (wasPlaying) syncPlay.requestPause() else syncPlay.requestUnpause() }
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun userSeek(targetMs: Long) {
        if (syncPlaylistItemId != null && groupWatch.party.shouldAsk()) {
            groupWatch.party.ask("seek", targetMs.coerceAtLeast(0L) * TICKS_PER_MS)
        } else if (syncPlaylistItemId != null) {
            positionMs = targetMs.coerceAtLeast(0L)
            syncScope.launch { syncPlay.requestSeek(targetMs.coerceAtLeast(0L) * TICKS_PER_MS) }
        } else {
            seekToReal(targetMs)
        }
    }

    // A held seek key let go: jump there and play on if the hold paused it.
    fun releaseHoldSeek(target: Long) {
        userSeek(target)
        holdSeekTargetMs = null
        holdSeekDeltaMs = 0L
        holdSeekKey = null
        if (wasPlayingBeforeHold) {
            if (syncPlaylistItemId == null && directPlay) player.play()
            wasPlayingBeforeHold = false
        }
    }

    // Group commands, run at the moment the server set (its clock,
    // converted to this one). A newer command replaces one still waiting.
    LaunchedEffect(player, syncPlaylistItemId) {
        val playlistItemId = syncPlaylistItemId ?: return@LaunchedEffect
        syncPlay.commands.collectLatest { command ->
            if (command.playlistItemId != null && command.playlistItemId != playlistItemId) return@collectLatest
            // A replayed command from long before this player opened.
            if (System.currentTimeMillis() - command.receivedAtMs > 60_000L) return@collectLatest
            val wait = syncPlay.remoteToLocalMs(command.whenRemoteMs) - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            val reportedMs = (command.positionTicks ?: 0L) / TICKS_PER_MS
            when (command.command) {
                "Unpause" -> {
                    val targetMs = syncPlay.estimateTicksNow(command.positionTicks ?: 0L, command.whenRemoteMs) / TICKS_PER_MS
                    resumePromptDismissed = true
                    syncReloadPending = false
                    if (kotlin.math.abs(realPositionMs() - targetMs) <= SYNC_DRIFT_MS) {
                        player.play()
                    } else if (directPlay) {
                        player.seekTo(targetMs.coerceAtLeast(0L))
                        player.play()
                    } else {
                        player.pause()
                        syncPlay.notifyBuffering(realPositionMs() * TICKS_PER_MS, false, playlistItemId)
                        syncReloadPending = true
                        syncReadyNeeded = true
                        seekToReal(targetMs)
                    }
                }
                "Pause" -> {
                    player.pause()
                    if (command.positionTicks != null && kotlin.math.abs(realPositionMs() - reportedMs) > SYNC_DRIFT_MS) seekToReal(reportedMs)
                }
                "Seek" -> {
                    // The server holds the group until every member reports
                    // Ready at the new position.
                    player.pause()
                    if (!directPlay) syncReloadPending = true
                    syncReadyNeeded = true
                    seekToReal(reportedMs)
                }
                "Stop" -> player.pause()
            }
        }
    }

    LaunchedEffect(player, remoteControlManager) {
        val manager = remoteControlManager ?: return@LaunchedEffect
        manager.playstateCommands.collectLatest { command ->
            when (command.command.lowercase()) {
                "pause" -> player.pause()
                "unpause" -> player.play()
                "playpause" -> if (player.isPlaying) player.pause() else player.play()
                "stop" -> onBack()
                "seek" -> {
                    if (command.seekPositionTicks != null) {
                        val targetMs = command.seekPositionTicks / TICKS_PER_MS
                        seekToReal(targetMs)
                    }
                }
                "nexttrack" -> currentUpNextInfo?.let { onPlayNext(it.itemId) }
            }
        }
    }

    LaunchedEffect(player, remoteControlManager) {
        val manager = remoteControlManager ?: return@LaunchedEffect
        manager.generalCommands.collectLatest { command ->
            when (command.name.lowercase()) {
                "volumeup" -> {
                    player.volume = (player.volume + 0.1f).coerceAtMost(1f)
                }
                "volumedown" -> {
                    player.volume = (player.volume - 0.1f).coerceAtLeast(0f)
                }
                "mute" -> {
                    player.volume = 0f
                }
                "unmute" -> {
                    player.volume = 1f
                }
                "togglemute" -> {
                    player.volume = if (player.volume > 0f) 0f else 1f
                }
                "setvolume" -> {
                    command.arguments["Volume"]?.toFloatOrNull()?.let { v ->
                        player.volume = (v / 100f).coerceIn(0f, 1f)
                    }
                }
            }
        }
    }

    // Cleared when the player closes, so a party's countdown and ready
    // check don't carry over to other screens.
    DisposableEffect(syncPlaylistItemId) {
        groupWatch.party.setPlaylistItem(syncPlaylistItemId)
        onDispose { groupWatch.party.setPlaylistItem(null) }
    }

    // Joining a group well into its title: follow it, or start from the
    // beginning alone. Picks the group after a few seconds by itself.
    var catchUpOpen by remember(currentItemId) {
        mutableStateOf(
            inGroupAtOpen && startPositionTicks > 120_000L * TICKS_PER_MS &&
                syncPlay.target.value?.let { com.jellio.tv.data.syncplay.SyncPlayManager.sameId(it.itemId, currentItemId) } == true,
        )
    }
    LaunchedEffect(catchUpOpen) {
        if (catchUpOpen) {
            delay(10_000)
            catchUpOpen = false
        }
    }

    // Tells the group when this TV is loading and when it's ready, so
    // the server holds everyone until the slowest member has caught up.
    var publishedQueue by remember(currentItemId) { mutableStateOf(false) }
    var unpauseAfterPublish by remember(currentItemId) { mutableStateOf(false) }
    LaunchedEffect(isBuffering, syncPlaylistItemId, syncReadyNeeded, pendingReloadMs, player) {
        val playlistItemId = syncPlaylistItemId ?: return@LaunchedEffect
        // A reload is on its way; the reloaded player reports for itself,
        // not this one at the old position.
        if (pendingReloadMs != null) return@LaunchedEffect
        val ticks = realPositionMs() * TICKS_PER_MS
        if (isBuffering) {
            syncPlay.notifyBuffering(ticks, player.playWhenReady, playlistItemId)
        } else if (player.playbackState == Player.STATE_READY) {
            syncReadyNeeded = false
            syncPlay.notifyReady(ticks, player.playWhenReady, playlistItemId)
            groupWatch.party.markReady(playlistItemId)
            if (unpauseAfterPublish) {
                unpauseAfterPublish = false
                syncPlay.requestUnpause()
            }
        }
    }

    LaunchedEffect(syncGroup?.groupId, currentItemId) {
        if (syncGroup == null) return@LaunchedEffect
        if (publishedQueue || syncPlaylistItemId != null) return@LaunchedEffect
        val target = syncPlay.target.value
        if (target != null && com.jellio.tv.data.syncplay.SyncPlayManager.sameId(target.itemId, currentItemId)) return@LaunchedEffect
        // In a host-only party a guest asks the host instead of changing the queue.
        if (groupWatch.party.shouldAsk()) return@LaunchedEffect
        publishedQueue = true
        if (syncPlay.publishQueue(currentItemId, startPositionTicks).isSuccess) {
            // A party waits for everyone, counts down, then starts the group.
            if (!groupWatch.party.published(currentItemId)) unpauseAfterPublish = true
            groupWatch.announceStarted(currentItemId, title.ifBlank { Strings.get("tv_something") })
        } else {
            publishedQueue = false
        }
    }
    // Bumped on every key press, so the controls stay up while the remote
    // is in use.
    var interaction by remember { mutableStateOf(0) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSourcePanel by remember { mutableStateOf(false) }
    var showEpisodesPanel by remember { mutableStateOf(false) }
    var showReactMenu by remember { mutableStateOf(false) }
    var hasReportedStart by remember(player) { mutableStateOf(false) }
    var hasEverReportedStart by remember { mutableStateOf(false) }
    val currentHasReportedStart by androidx.compose.runtime.rememberUpdatedState(hasEverReportedStart)
    val currentPositionTicks by androidx.compose.runtime.rememberUpdatedState(positionMs * TICKS_PER_MS)
    val currentOnReportStopped by androidx.compose.runtime.rememberUpdatedState(onReportStopped)
    DisposableEffect(Unit) {
        onDispose {
            if (currentHasReportedStart) {
                currentOnReportStopped(currentPositionTicks)
            }
        }
    }
    var seekedToResume by remember(player) { mutableStateOf(directPlay && startPositionTicks > 0) }
    var loadedSideloadedSubtitleIndex by remember(player) { mutableStateOf(activeSideloadedTrack?.streamIndex) }
    // Per episode, not per stream: a seek that reloads a transcode must
    // not bring a shown or dismissed card or prompt back.
    var upNextShown by remember(currentItemId) { mutableStateOf(false) }
    var upNextDismissed by remember(currentItemId) { mutableStateOf(false) }
    var upNextCountdown by remember(currentItemId) { mutableStateOf(UPNEXT_COUNTDOWN_SECONDS) }
    var dismissedSkipKind by remember(currentItemId) { mutableStateOf<SkipKind?>(null) }
    var nearEndUpNextDismissed by remember(currentItemId) { mutableStateOf(false) }
    var nearEndUpNextAnnounced by remember(currentItemId) { mutableStateOf(false) }

    LaunchedEffect(player, playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    val bufferScope = rememberCoroutineScope()
    LaunchedEffect(streamUrl) {
        bufferWatch.reset()
        bufferPromptVisible = false
    }
    fun switchForBuffering() {
        bufferPromptVisible = false
        bufferWatch.offered()
        val ticks = realPositionMs() * TICKS_PER_MS
        bufferScope.launch {
            if (!onBufferSwitch(ticks)) {
                bufferWatch.snooze(System.currentTimeMillis(), 10 * 60_000L)
                onShowToast(Strings.get("player_buffer_no_other"))
            }
        }
    }
    LaunchedEffect(isBuffering, streamUrl) {
        if (!isBuffering || PlayerPrefs.bufferSwitch == "off") return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (!bufferWatch.counts(now, playWhenReadyState, syncPlaylistItemId != null, switchingTo != null)) return@LaunchedEffect
        bufferWatch.stallStarted(now)
        try {
            while (true) {
                if (!bufferPromptVisible && bufferWatch.shouldOffer(System.currentTimeMillis())) {
                    bufferWatch.offered()
                    if (PlayerPrefs.bufferSwitch == "auto") switchForBuffering() else bufferPromptVisible = true
                    break
                }
                delay(1000)
            }
        } finally {
            bufferWatch.stallEnded(System.currentTimeMillis())
        }
    }

    LaunchedEffect(sleepTimerEndTimeMs) {
        val endTimeMs = sleepTimerEndTimeMs ?: return@LaunchedEffect
        val remaining = endTimeMs - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
        onBack()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (bufferWatch.firstFrameAt == 0L) bufferWatch.firstFrameAt = System.currentTimeMillis()
                if (placeholderWatch.firstFrameAt == 0L) placeholderWatch.firstFrameAt = System.currentTimeMillis()
                PlayStartTrace.end("first frame")
                if (currentSwitchingTo != null && currentSwitchingLoaded) {
                    currentSwitchSettled()
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing && currentSwitchingTo != null && currentSwitchingLoaded) {
                    currentSwitchSettled()
                }
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) bufferWatch.lastSeekAt = System.currentTimeMillis()
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playWhenReadyState = playWhenReady
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isBuffering = false
                // Never got going: set it aside and try the next stream.
                if (!hasReportedStart && syncGroup == null) {
                    onStreamFailed("error before the first frame")
                    return
                }
                exoError = friendlyPlaybackError(error)
            }
            override fun onPlaybackStateChanged(state: Int) {
                isEnded = state == Player.STATE_ENDED
                isBuffering = state == Player.STATE_BUFFERING || (state == Player.STATE_IDLE && exoError == null)
                if (isEnded && !looksLikePlaceholder(realPositionMs(), expectedRuntimeMs) && !stoppedTooEarly(realPositionMs(), expectedRuntimeMs)) {
                    onReportRealDuration(realPositionMs() / 1000.0)
                    onMarkRealWatchComplete()
                }
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                if (tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO || it.type == C.TRACK_TYPE_AUDIO }) {
                    placeholderWatch.hasAudio = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }
                }
                val found = applySubtitleSelection(player, currentSubtitleTracks, currentSubtitleIndex, currentSubtitleLanguage)
                // Picked, the stream has its tracks, and none of them is it:
                // say so instead of quietly showing nothing.
                if (currentSubtitleIndex != null && !found && !subtitleMissingReported &&
                    tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO || it.type == C.TRACK_TYPE_AUDIO }
                ) {
                    subtitleMissingReported = true
                    onShowToast(Strings.get("tv_subtitle_not_in_stream"))
                }
                if (directPlay) {
                    selectAudioTrack(player, currentAudioTracks, currentAudioIndex, defaultAudioStreamIndex)
                }
            }
        }
        player.addListener(listener)
        // A side-loaded subtitle file that fails to download used to fail
        // silently; this shows what the server answered.
        val loadErrors = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onLoadError(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                error: java.io.IOException,
                wasCanceled: Boolean,
            ) {
                if (mediaLoadData.trackType != C.TRACK_TYPE_TEXT || wasCanceled) return
                val status = (error as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                onShowToast(if (status != null) Strings.format("player_subtitles_load_error_status", status) else Strings.get("player_subtitles_load_error"))
            }
        }
        player.addAnalyticsListener(loadErrors)
        onDispose {
            player.removeListener(listener)
            if (player.currentPosition > 0) {
                positionMs = streamOffsetMs + player.currentPosition
            }
            player.release()
        }
    }

    LaunchedEffect(player, activeSideloadedTrack?.streamIndex) {
        if (activeSideloadedTrack?.streamIndex != loadedSideloadedSubtitleIndex) {
            loadedSideloadedSubtitleIndex = activeSideloadedTrack?.streamIndex
            val pos = player.currentPosition.coerceAtLeast(0L)
            player.setMediaSource(buildPlayerMediaSource(activeSideloadedTrack), pos)
            player.prepare()
        }
    }

    LaunchedEffect(player, subtitleTracks, selectedSubtitleIndex) {
        subtitleMissingReported = false
        applySubtitleSelection(player, subtitleTracks, selectedSubtitleIndex, selectedSubtitleLanguage)
    }

    LaunchedEffect(player, selectedAudioStreamIndex, directPlay) {
        if (!directPlay) return@LaunchedEffect
        selectAudioTrack(player, audioTracks, selectedAudioStreamIndex, defaultAudioStreamIndex)
    }

    fun checkPlaceholder() {
        if (placeholderWatch.reported || syncGroup != null) return
        val now = System.currentTimeMillis()
        val stalled = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
        if (!stalled) {
            placeholderWatch.stallStartedAt = 0L
        } else if (placeholderWatch.stallStartedAt == 0L) {
            placeholderWatch.stallStartedAt = now
            // Waiting on a seek is not the stream failing.
            placeholderWatch.stallAfterSeek = now - bufferWatch.lastSeekAt < 2_000
        }
        placeholderWatch.sampleFrames(
            player.videoDecoderCounters?.also { it.ensureUpdated() }?.renderedOutputBufferCount,
            player.isPlaying,
            now,
        )
        val sign = detectPlaceholder(
            PlaceholderSignals(
                expectedRuntimeMs = expectedRuntimeMs,
                durationMs = if (directPlay && player.duration > 0) streamOffsetMs + player.duration else 0L,
                firstFrameRendered = placeholderWatch.firstFrameAt > 0,
                formatFrameRate = player.videoFormat?.frameRate?.takeIf { it > 0f },
                measuredFrames = placeholderWatch.measuredFrames,
                measuredSeconds = placeholderWatch.measuredSeconds,
                hasAudioTrack = placeholderWatch.hasAudio,
                ended = player.playbackState == Player.STATE_ENDED,
                stalledMs = if (placeholderWatch.stallStartedAt > 0 && !placeholderWatch.stallAfterSeek) now - placeholderWatch.stallStartedAt else 0L,
                positionMs = realPositionMs(),
            ),
        ) ?: return
        placeholderWatch.reported = true
        endedAsPlaceholder = sign == PlaceholderSign.ENDED_EARLY || player.playbackState == Player.STATE_ENDED
        onPlaceholder(sign)
    }

    LaunchedEffect(player, startPositionTicks) {
        while (isActive) {
            checkPlaceholder()
            if (player.duration > 0 && player.playbackState != Player.STATE_IDLE) {
                durationMs = streamOffsetMs + player.duration
                val placeholder = looksLikePlaceholder(durationMs, expectedRuntimeMs)
                if (directPlay && !placeholder) onReportRealDuration(durationMs / 1000.0)
                if (!seekedToResume && startPositionTicks > 0 && directPlay) {
                    seekedToResume = true
                    player.seekTo(startPositionTicks / TICKS_PER_MS)
                }
                if (!hasReportedStart && isPlaying && (realPositionMs() > 0 || player.currentPosition > 0)) {
                    hasReportedStart = true
                    hasEverReportedStart = true
                    onReportStart(realPositionMs() * TICKS_PER_MS)
                    onPlaybackStarted()
                }
            }
            if (pendingReloadMs == null) positionMs = realPositionMs()
            if (durationMs > 0 && positionMs.toDouble() / durationMs.toDouble() >= 0.9 && !looksLikePlaceholder(durationMs, expectedRuntimeMs)) {
                onMarkRealWatchComplete()
            }
            if (currentUpNextInfo != null && !upNextShown && !upNextDismissed && durationMs > 0) {
                if (shouldShowUpNextNow(currentSkipSegments, positionMs / 1000.0, durationMs / 1000.0)) {
                    upNextShown = true
                    onUpNextShown()
                    // The episode sleep timer counts this boundary; on the
                    // last one there's no Up Next, playback stops at the end.
                    if (EpisodeSleepTimer.countEpisode()) {
                        upNextDismissed = true
                        stopAtEnd = true
                    }
                    onReportRealDuration(positionMs / 1000.0)
                    onMarkRealWatchComplete()
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(upNextShown, upNextDismissed) {
        // Dismissed means stay on this episode, so no countdown either.
        if (!upNextShown || upNextDismissed) return@LaunchedEffect
        upNextCountdown = UPNEXT_COUNTDOWN_SECONDS
        while (upNextCountdown > 0) {
            delay(1000)
            upNextCountdown -= 1
        }
        // A host-only party's guests follow the host to the next episode.
        if (!groupWatch.party.shouldAsk()) upNextInfo?.let { onPlayNext(it.itemId) }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(500)
            if (player.isPlaying && player.currentPosition > 0) {
                onPlaybackStarted()
                break
            }
        }
    }

    // No picture 25 s after playback was asked for (12 s for a video
    // re-encode the server was told to avoid): try the next stream.
    LaunchedEffect(player, playWhenReadyState) {
        if (!playWhenReadyState || hasReportedStart) return@LaunchedEffect
        val timeoutMs = if (reencodingVideo) REENCODE_START_TIMEOUT_MS else START_TIMEOUT_MS
        delay(timeoutMs)
        if (!hasReportedStart && player.playbackState != Player.STATE_READY && syncGroup == null) {
            onStreamFailed("no picture after ${timeoutMs / 1000}s")
        }
    }

    LaunchedEffect(isEnded) {
        if (!isEnded) return@LaunchedEffect
        checkPlaceholder()
        if (placeholderWatch.reported) {
            endedAsPlaceholder = true
            return@LaunchedEffect
        }
        if (!stopAtEnd && upNextInfo == null && EpisodeSleepTimer.remaining != null) {
            EpisodeSleepTimer.remaining = null
            stopAtEnd = true
        }
        // No credits data, so the card never counted this episode toward
        // an episode sleep timer: count it here.
        if (!stopAtEnd && upNextInfo != null && !upNextShown) {
            if (EpisodeSleepTimer.countEpisode()) stopAtEnd = true
        }
        if (stopAtEnd) onBack()
    }

    // The end screen: the next episode with Play Next, Replay and Back,
    // or Replay and Back for a movie or a series finale.
    val showEndScreen = isEnded && !stopAtEnd && !endedAsPlaceholder
    var endCountdown by remember(streamUrl) { mutableStateOf<Int?>(null) }
    LaunchedEffect(showEndScreen) {
        if (!showEndScreen) {
            endCountdown = null
            return@LaunchedEffect
        }
        val next = upNextInfo ?: return@LaunchedEffect
        // The card's countdown already runs on into the end screen, and a
        // dismissed card or near-end prompt means stay on this episode.
        if (upNextShown || upNextDismissed || nearEndUpNextDismissed) return@LaunchedEffect
        onUpNextShown()
        endCountdown = UPNEXT_COUNTDOWN_SECONDS
        while ((endCountdown ?: 0) > 0) {
            delay(1000)
            endCountdown = (endCountdown ?: 1) - 1
        }
        if (!groupWatch.party.shouldAsk()) onPlayNext(next.itemId)
    }
    val upNextCardVisible = upNextInfo != null && upNextShown && !upNextDismissed && !showEndScreen
    // Both sit over the player's own controls and take the remote, so the
    // controls go away while they're up.
    LaunchedEffect(upNextCardVisible, showEndScreen) {
        if (upNextCardVisible || showEndScreen) controlsVisible = false
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(PROGRESS_REPORT_INTERVAL_MS)
            if (hasReportedStart) {
                onReportProgress(realPositionMs() * TICKS_PER_MS, !player.isPlaying)
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, interaction, showSubtitleMenu, showSpeedMenu, showSleepMenu, showAudioMenu, showSourcePanel, showEpisodesPanel, showReactMenu) {
        if (controlsVisible && isPlaying && !showSubtitleMenu && !showSpeedMenu && !showSleepMenu && !showAudioMenu && !showSourcePanel && !showEpisodesPanel && !showReactMenu) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    val pausedByViewer = pauseInfo != null && hasReportedStart && !playWhenReadyState && !showResumePrompt && !isEnded
    val menuOpen = showSubtitleMenu || showSpeedMenu || showSleepMenu || showAudioMenu || showSourcePanel || showEpisodesPanel || showReactMenu
    fun closeOpenMenu() {
        when {
            showReactMenu -> showReactMenu = false
            showSubtitleMenu -> showSubtitleMenu = false
            showSpeedMenu -> showSpeedMenu = false
            showSleepMenu -> showSleepMenu = false
            showAudioMenu -> showAudioMenu = false
            showSourcePanel -> showSourcePanel = false
            showEpisodesPanel -> showEpisodesPanel = false
        }
    }
    var pauseOverlayVisible by remember { mutableStateOf(false) }
    // The key that took the pause screen away, so its key up only does that.
    var pauseOverlayDismissKey by remember { mutableStateOf<Key?>(null) }
    LaunchedEffect(pausedByViewer, menuOpen, interaction) {
        pauseOverlayVisible = false
        if (!pausedByViewer || menuOpen) return@LaunchedEffect
        delay(PAUSE_OVERLAY_DELAY_MS)
        pauseOverlayVisible = true
        controlsVisible = false
    }

    val activeSkipState by remember(skipSegments) { derivedStateOf { activeSkipSegment(skipSegments, positionMs / 1000.0) } }
    val activeSkip = activeSkipState
    // screens/player.js: a dismissed prompt stays away until a different
    // kind of segment comes up.
    LaunchedEffect(activeSkip?.kind) {
        val kind = activeSkip?.kind
        if (kind != null && kind != dismissedSkipKind) dismissedSkipKind = null
    }
    val nearEndReachedState by remember(skipSegments, skipSegmentsResolved) {
        derivedStateOf { shouldShowNearEndUpNext(skipSegments, skipSegmentsResolved, positionMs / 1000.0, durationMs / 1000.0) }
    }
    // Anything that owns the screen or the remote keeps both prompts away.
    val promptsBlocked = showEndScreen || upNextCardVisible || menuOpen || bufferPromptVisible ||
        showResumePrompt || catchUpOpen || exoError != null || pauseOverlayVisible
    val skipPromptVisible = activeSkip != null && activeSkip.kind != dismissedSkipKind && !promptsBlocked &&
        !(upNextInfo != null && upNextShown && !upNextDismissed)
    // The last episode of an episode sleep timer ends playback, so no offer.
    val nearEndUpNextVisible = upNextInfo != null && nearEndReachedState && !nearEndUpNextDismissed &&
        !skipPromptVisible && !promptsBlocked && EpisodeSleepTimer.remaining != 1
    val promptVisible = skipPromptVisible || nearEndUpNextVisible
    // With the controls away a shown prompt has focus, so the remote's
    // arrows move between its buttons and Back dismisses it.
    val promptOwnsRemote = promptVisible && !controlsVisible
    fun dismissPrompt() {
        if (skipPromptVisible) dismissedSkipKind = activeSkip?.kind else if (nearEndUpNextVisible) nearEndUpNextDismissed = true
    }
    LaunchedEffect(nearEndUpNextVisible) {
        if (nearEndUpNextVisible && !nearEndUpNextAnnounced) {
            nearEndUpNextAnnounced = true
            onUpNextShown()
        }
    }
    LaunchedEffect(promptVisible) {
        if (!promptVisible && !promptsBlocked && !controlsVisible) runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            // The focused button just left; keep the remote on the player.
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
            .onPreviewKeyEvent { event ->
                if (pauseOverlayVisible && event.type == KeyEventType.KeyDown) pauseOverlayDismissKey = event.key
                interaction++
                false
            }
            .onKeyEvent { event ->
                val isSeekKey = event.key == Key.DirectionLeft || event.key == Key.DirectionRight ||
                    event.key == Key.MediaRewind || event.key == Key.MediaFastForward
                val isMedia = event.key == Key.MediaRewind || event.key == Key.MediaFastForward
                val forward = event.key == Key.DirectionRight || event.key == Key.MediaFastForward

                if (event.type == KeyEventType.KeyDown) {
                    val noOverlayOpen = !showEndScreen && !upNextCardVisible && !menuOpen &&
                        !bufferPromptVisible && !showResumePrompt && !catchUpOpen && exoError == null &&
                        (!promptOwnsRemote || isMedia)
                    if (isSeekKey && (!controlsVisible || isMedia) && noOverlayOpen) {
                        val repeatCount = event.nativeKeyEvent.repeatCount
                        val now = android.os.SystemClock.uptimeMillis()
                        if (repeatCount == 0 && holdSeekKey != event.key) {
                            holdSeekKey = event.key
                            holdSeekStartTime = now
                            holdSeekLastTickTime = now
                            holdSeekStartPos = positionMs
                            holdSeekTargetMs = null
                            holdSeekDeltaMs = 0L
                        } else if (repeatCount > 0 || (now - holdSeekStartTime > 250L)) {
                            // Hold to fast forward / rewind
                            if (holdSeekTargetMs == null && player.isPlaying && syncPlaylistItemId == null && directPlay) {
                                wasPlayingBeforeHold = true
                                player.pause()
                            }
                            // Throttle seek repeat events to a steady ~180ms cadence to prevent runaway skipping
                            if (now - holdSeekLastTickTime >= 180L || holdSeekTargetMs == null) {
                                holdSeekLastTickTime = now
                                val elapsed = now - holdSeekStartTime
                                val step = when {
                                    elapsed > 5000L -> 30_000L
                                    elapsed > 2000L -> 15_000L
                                    else -> SEEK_STEP_MS
                                }
                                val base = holdSeekTargetMs ?: holdSeekStartPos
                                val next = seekTarget(base, if (forward) step else -step, shownDurationMs)
                                holdSeekTargetMs = next
                                holdSeekDeltaMs += if (forward) step else -step
                                seekFlash = SeekFlash(next, holdSeekDeltaMs, System.nanoTime())
                            }
                        }
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }

                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                if (!isSeekKey) holdSeekTargetMs?.let { releaseHoldSeek(it) }
                if (pauseOverlayDismissKey == event.key) {
                    pauseOverlayDismissKey = null
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay -> togglePlay()
                        Key.Back -> Unit
                        else -> controlsVisible = true
                    }
                    return@onKeyEvent true
                }
                // The Up Next card and the end screen own the remote while
                // shown: arrows move between their buttons instead of seeking.
                if (showEndScreen || upNextCardVisible) {
                    if (event.key == Key.Back) {
                        if (showEndScreen) onBack() else upNextDismissed = true
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (promptOwnsRemote) {
                    when (event.key) {
                        Key.Back -> {
                            dismissPrompt()
                            return@onKeyEvent true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            controlsVisible = true
                            return@onKeyEvent true
                        }
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter -> return@onKeyEvent false
                        else -> Unit
                    }
                }
                if (menuOpen) {
                    if (event.key == Key.Back) closeOpenMenu()
                    return@onKeyEvent event.key == Key.Back
                }
                if (bufferPromptVisible) {
                    if (event.key == Key.Back) {
                        bufferPromptVisible = false
                        bufferWatch.snooze(System.currentTimeMillis(), 10 * 60_000L)
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (showResumePrompt || catchUpOpen) {
                    if (event.key == Key.Back) {
                        onBack()
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                when (event.key) {
                    Key.Back -> {
                        if (controlsVisible) controlsVisible = false else onBack()
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight, Key.MediaRewind, Key.MediaFastForward -> {
                        holdSeekTargetMs?.let { target ->
                            seekFlash = SeekFlash(target, holdSeekDeltaMs, System.nanoTime())
                            releaseHoldSeek(target)
                            return@onKeyEvent true
                        }
                        if (controlsVisible && !isMedia) return@onKeyEvent false
                        // Controls hidden: quick single tap jump, with a small badge saying
                        // where to, rather than bringing the whole overlay up.
                        holdSeekKey = null
                        val base = seekFlash?.target ?: positionMs
                        val newPos = seekTarget(base, if (forward) SEEK_STEP_MS else -SEEK_STEP_MS, shownDurationMs)
                        userSeek(newPos)
                        val total = (seekFlash?.totalMs ?: 0L) + if (forward) SEEK_STEP_MS else -SEEK_STEP_MS
                        seekFlash = SeekFlash(newPos, total, System.nanoTime())
                        true
                    }
                    Key.MediaPlayPause -> {
                        togglePlay()
                        controlsVisible = true
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (controlsVisible) return@onKeyEvent false
                        togglePlay()
                        controlsVisible = true
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
            update = { view ->
                if (view.player !== player) view.player = player
                view.subtitleView?.let { subtitleView ->
                    subtitleView.setStyle(subtitleCaptionStyle(subtitleStyle))
                    subtitleView.setFractionalTextSize(subtitleFractionalTextSize(subtitleStyle))
                    subtitleView.setBottomPaddingFraction(SUBTITLE_POSITIONS.firstOrNull { it.first == subtitleStyle.position }?.second ?: 0.08f)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (pauseOverlayVisible && pausedByViewer) {
            PauseOverlay(info = pauseInfo!!)
        }

        val problem = exoError
        if (problem != null) {
            LaunchedEffect(problem) { controlsVisible = false }
            PlaybackErrorPanel(problem = problem, onBack = onBack)
        } else if (isBuffering && pauseInfo != null) {
            BufferingOverlay(info = pauseInfo!!)
        } else if (isBuffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PulsingTitle(text = title.ifBlank { Strings.get("tv_loading") })
            }
        }

        // The quick-jump badge: shown for a moment after the last press,
        // adding up repeated presses (+30s after three).
        seekFlash?.let { flash ->
            SeekFlashBadge(
                flash = flash,
                holding = holdSeekTargetMs != null,
                durationMs = shownDurationMs,
                onExpired = { seekFlash = null },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // Switching streams: shown until the new stream has buffered and plays.
        if (switchingTo != null) {
            LaunchedEffect(switchingTo, switchingLoaded, player) {
                if (!switchingLoaded) return@LaunchedEffect
                val startMs = System.currentTimeMillis()
                while (isActive) {
                    val playing = isPlaying || player.isPlaying
                    val ready = !isBuffering && player.playbackState == Player.STATE_READY
                    if (playing && ready) break
                    if (System.currentTimeMillis() - startMs > 15_000L) break
                    delay(100)
                }
                delay(150)
                currentSwitchSettled()
            }
            SwitchingCard(
                label = switchingTo,
                step = Strings.get(if (switchingLoaded) "player_switch_loading" else "player_switch_connecting"),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (controlsVisible) {
            PlayerControls(
                title = title,
                subtitle = subtitle,
                isPlaying = isPlaying,
                enabled = !menuOpen,
                positionMs = { positionMs },
                durationMs = shownDurationMs,
                hasSubtitleTracks = subtitleTracks.isNotEmpty(),
                hasAudioTracks = true,
                hasSourceOptions = sourceOptions.size > 1,
                hasEpisodes = seasons.isNotEmpty(),
                speedLabel = formatSpeed(playbackSpeed),
                sleepTimerActive = sleepTimerEndTimeMs != null || EpisodeSleepTimer.remaining != null,
                onPlayPause = { togglePlay() },
                onSkip = { deltaMs ->
                    userSeek(seekTarget(positionMs, deltaMs, shownDurationMs))
                },
                onScrubEnd = { target ->
                    if (target != null) userSeek(target)
                },
                onOpenSubtitleMenu = { showSubtitleMenu = true },
                onOpenSpeedMenu = { showSpeedMenu = true },
                onOpenSleepMenu = { showSleepMenu = true },
                inGroup = syncGroup != null,
                onOpenReactMenu = { showReactMenu = true },
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

        // A subtitle offset change re-reads the subtitle track from where
        // playback is, once the buttons have been left alone for a moment.
        LaunchedEffect(subtitleResyncSeq) {
            if (subtitleResyncSeq == 0) return@LaunchedEffect
            kotlinx.coroutines.delay(600)
            val pos = player.currentPosition.coerceAtLeast(0L)
            if (syncDelays.wrapped) {
                player.seekTo(pos)
            } else {
                player.setMediaSource(buildPlayerMediaSource(activeSideloadedTrack), pos)
                player.prepare()
            }
        }

        if (showAudioMenu) {
            AudioMenu(
                tracks = audioTracks,
                selectedStreamIndex = selectedAudioStreamIndex,
                defaultStreamIndex = defaultAudioStreamIndex,
                onSelect = { streamIndex ->
                    showAudioMenu = false
                    onSelectAudioTrack(streamIndex, realPositionMs() * TICKS_PER_MS)
                },
                onToggleDialogueBoost = {
                    val next = !PlayerPrefs.dialogueBoost
                    PlayerPrefs.setDialogueBoost(context, next)
                    onShowToast(Strings.get(if (next) "player_dialogue_boost_enabled" else "player_dialogue_boost_disabled"))
                },
                audioDelayMs = audioDelayMs,
                onAudioDelay = { ms ->
                    audioDelayMs = ms
                    syncDelays.audioMs = ms
                    PlayerPrefs.setAudioDelay(context, ms)
                    // The first offset adds the sync layer, which needs a reload.
                    if (!syncDelays.wrapped && ms != 0) subtitleResyncSeq++
                },
                onDismiss = { showAudioMenu = false },
            )
        }

        if (showSourcePanel && sourceOptions.size > 1) {
            SourcePanel(
                sources = sourceOptions,
                currentMediaSourceId = currentMediaSourceId,
                onSelect = { source ->
                    showSourcePanel = false
                    onSelectSource(source, realPositionMs() * TICKS_PER_MS)
                },
                onBlocked = { onShowToast(Strings.get("tv_transcode_blocked")) },
                onDismiss = { showSourcePanel = false },
            )
        }

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
                    onShowToast(if (count == 1) Strings.get("player_this_episode") else Strings.format("player_stop_episodes", count))
                },
                onCancel = {
                    showSleepMenu = false
                    EpisodeSleepTimer.remaining = null
                    onCancelSleepTimer()
                },
                onDismiss = { showSleepMenu = false },
            )
        }

        // Auto-skip intros (Settings > Playback): once per intro, the same
        // as screens/player.js does when it's switched on.
        LaunchedEffect(activeSkip?.targetSeconds, PlayerPrefs.autoSkipIntro) {
            val segment = activeSkip ?: return@LaunchedEffect
            if (PlayerPrefs.autoSkipIntro && segment.kind == SkipKind.INTRO && autoSkippedTo != segment.targetSeconds) {
                autoSkippedTo = segment.targetSeconds
                dismissedSkipKind = SkipKind.INTRO
                userSeek((segment.targetSeconds * 1000).toLong())
                onShowToast(Strings.get("tv_skipped_intro"))
            }
        }
        if (skipPromptVisible && activeSkip != null) {
            PlayerPrompt(
                eyebrow = activeSkip.eyebrow,
                title = activeSkip.label,
                actionLabel = Strings.get("player_skip"),
                actionIcon = Icons.Filled.SkipNext,
                onAction = { userSeek((activeSkip.targetSeconds * 1000).toLong()) },
                onDismiss = { dismissPrompt() },
                takeFocus = !controlsVisible,
                focusKey = activeSkip.kind,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 148.dp),
            )
        }
        if (nearEndUpNextVisible && upNextInfo != null) {
            PlayerPrompt(
                eyebrow = Strings.get("player_up_next"),
                title = upNextInfo.episodeName.ifBlank { upNextInfo.title },
                detail = listOfNotNull(upNextInfo.episodeCode, upNextInfo.seriesName).joinToString("  ·  "),
                thumbnailUrl = upNextInfo.thumbnailUrl,
                actionLabel = Strings.get("player_play_next_episode"),
                actionIcon = Icons.Filled.PlayArrow,
                onAction = {
                    // This episode counts toward an episode sleep timer as
                    // if it had played to the end.
                    EpisodeSleepTimer.countEpisode()
                    onPlayNext(upNextInfo.itemId)
                },
                onDismiss = { dismissPrompt() },
                takeFocus = !controlsVisible,
                focusKey = upNextInfo.itemId,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 148.dp),
            )
        }

        if (upNextCardVisible && upNextInfo != null) {
            UpNextOverlay(
                info = upNextInfo,
                secondsRemaining = upNextCountdown,
                onPlayNow = { onPlayNext(upNextInfo.itemId) },
                onDismiss = { upNextDismissed = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 40.dp),
            )
        }

        if (showEndScreen) {
            EndScreen(
                finishedTitle = title,
                finishedSubtitle = subtitle,
                next = upNextInfo,
                countdown = if (upNextShown && !upNextDismissed) upNextCountdown else endCountdown,
                onPlayNext = { upNextInfo?.let { onPlayNext(it.itemId) } },
                onReplay = onRestart,
                onBack = onBack,
                art = pauseInfo?.art.orEmpty(),
                info = pauseInfo,
            )
        }

        if (showReactMenu) {
            val replyTo = remember { groupWatch.party.replyTarget() }
            ReactMenu(
                replyTo = replyTo,
                onReact = { emoji ->
                    showReactMenu = false
                    groupWatch.react(emoji)
                },
                onDismiss = { showReactMenu = false },
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
                onSetSubtitleLook = onSetSubtitleLook,
                subtitleOffsetMs = subtitleOffsetMs,
                onSubtitleOffset = { ms -> setSubtitleOffset(ms) },
                canSyncSubtitles = selectedSubtitleIndex != null && subtitleTracks.firstOrNull { it.streamIndex == selectedSubtitleIndex }?.isTextBased == true,
                onDismiss = { showSubtitleMenu = false },
            )
        }

        if (catchUpOpen) {
            CatchUpPrompt(
                groupAtTicks = startPositionTicks,
                onFollow = { catchUpOpen = false },
                onSolo = {
                    catchUpOpen = false
                    syncScope.launch {
                        syncPlay.leave()
                        onRestart()
                    }
                },
            )
        }

        if (bufferPromptVisible) {
            BufferSwitchPrompt(
                onSwitch = { switchForBuffering() },
                onKeep = {
                    bufferPromptVisible = false
                    bufferWatch.snooze(System.currentTimeMillis(), 10 * 60_000L)
                },
                onAlways = {
                    PlayerPrefs.setBufferSwitch(context, "auto")
                    switchForBuffering()
                },
            )
        }

        if (showResumePrompt) {
            ResumePrompt(
                percent = resumePercent,
                resumeTicks = startPositionTicks,
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

        if (toastMessage != null) {
            PlayerToast(message = toastMessage, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp))
        }
    }
}
