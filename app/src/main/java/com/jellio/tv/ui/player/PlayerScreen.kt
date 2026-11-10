package com.jellio.tv.ui.player

import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.data.prefs.PlayerPrefs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.jellio.tv.data.session.Session
import kotlinx.coroutines.flow.first

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
    val context = LocalContext.current
    val httpDataSourceFactory = remember(session.accessToken) {
        streamDataSourceFactory(viewModel.streamClient, viewModel.deviceId, session.accessToken)
    }
    // The player is built while PlaybackInfo loads, after the loading art's
    // first frame, and handed to the surface once the stream URL is known.
    val prebuiltPlayer = remember { PrebuiltPlayer() }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        if (!prebuiltPlayer.wanted) return@LaunchedEffect
        prebuiltPlayer.offer(buildStreamPlayer(context, httpDataSourceFactory))
    }
    DisposableEffect(Unit) {
        onDispose { prebuiltPlayer.release() }
    }
    val frameRateActivity = context as? android.app.Activity
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
                httpDataSourceFactory = httpDataSourceFactory,
                prebuiltPlayer = prebuiltPlayer,
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
                onPlayNext = { nextId ->
                    PlayStartTrace.begin(if (nextId == uiState.upNextInfo?.itemId) "next episode" else "player")
                    onPlayNext(nextId)
                },
                onPlayStartTraced = { viewModel.reportPlayStart(it) },
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
