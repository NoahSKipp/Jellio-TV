package com.jellio.tv.ui.player

import com.jellio.tv.data.catching
import com.jellio.tv.data.i18n.Strings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.PlaybackTarget
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.MediaStreamDto
import com.jellio.tv.data.streams.FailoverCounts
import com.jellio.tv.data.streams.FailoverLoad
import com.jellio.tv.data.streams.FailoverOutcome
import com.jellio.tv.data.streams.describeFailover
import com.jellio.tv.data.streams.failoverOutcome
import com.jellio.tv.data.streams.isSourceNotReady
import com.jellio.tv.data.streams.outcomeLabel
import com.jellio.tv.data.streams.planFailover
import com.jellio.tv.data.playback.SkipSegments
import com.jellio.tv.data.playback.mergeSkipSegments
import com.jellio.tv.data.playback.skipSegmentsComplete
import com.jellio.tv.data.playback.VideoTranscodePolicy
import com.jellio.tv.data.playback.outlookReason
import com.jellio.tv.ui.common.LoadFailure
import com.jellio.tv.ui.common.displayText
import com.jellio.tv.ui.common.loadFailureOf
import com.jellio.tv.data.playback.videoInfoOf
import com.jellio.tv.data.model.SubtitleStyle
import com.jellio.tv.data.prefs.SubtitleStylePreferences
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val subtitleStylePreferences: SubtitleStylePreferences,
    private val syncPlay: com.jellio.tv.data.syncplay.SyncPlayManager,
    private val watchNextSyncer: com.jellio.tv.data.watchnext.WatchNextSyncer,
    val remoteControlManager: com.jellio.tv.data.remote.RemoteControlManager,
) : ViewModel() {
    val deviceId: String get() = repository.deviceId
    val streamClient: okhttp3.OkHttpClient get() = repository.streamClient

    private val _failover = MutableStateFlow<FailoverNotice?>(null)
    val failover: StateFlow<FailoverNotice?> = _failover.asStateFlow()
    private val triedSourceIds = mutableListOf<String>()
    private val transcodeRetriedSourceIds = mutableSetOf<String>()
    private val directRetriedSourceIds = mutableSetOf<String>()
    private var failoverLanguage: String? = null
    private var failingOver = false
    private var currentSource: com.jellio.tv.data.model.MediaSourceDto? = null
    // Streams passed over for needing a video re-encode, so a wrong guess
    // from a stream's name can't bounce between two of them.
    private val transcodeSkipped = mutableSetOf<String>()
    private var transcodeSkippedFor: String? = null

    fun failoverPlaying() {
        _failover.value = null
    }

    // The retry in progress, so Stop or leaving the player ends it.
    private var failoverJob: kotlinx.coroutines.Job? = null

    fun stopFailover() {
        failoverJob?.cancel()
        _failover.value = null
        failingOver = true
    }

    private fun sourceLabel(source: com.jellio.tv.data.model.MediaSourceDto): String =
        source.Name?.lineSequence()?.firstOrNull()?.trim()?.ifEmpty { null } ?: Strings.get("player_another_stream")

    private fun streamLanguage(): String? =
        preferredStreamLanguage(audioLanguagePreference, com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage.code)

    // Each source is failed over for a placeholder clip at most once.
    private val placeholderSources = mutableSetOf<String>()

    fun placeholderDetected(session: Session, sign: PlaceholderSign) {
        val key = currentSource?.Id ?: _uiState.value.mediaSourceId ?: return
        if (!placeholderSources.add(key)) return
        android.util.Log.w("JellioPlayer", "${sign.reason} on source $key, trying the next one")
        streamFailed(session, sign.reason)
    }

    // notReady: the server could not open the stream's stored link and
    // fetches the title's streams again, so the stream is not marked bad and
    // the next one comes from a fresh list.
    fun streamFailed(session: Session, reason: String, notReady: Boolean = false) {
        if (failingOver || syncPlay.group.value != null) return
        val id = itemId ?: return
        val source = currentSource ?: return
        val sourceId = source.Id
        val state = _uiState.value
        if (
            !notReady &&
            reason == "error before the first frame" &&
            state.directPlay &&
            !VideoTranscodePolicy.blocking &&
            sourceId != null &&
            transcodeRetriedSourceIds.add(sourceId)
        ) {
            failingOver = true
            android.util.Log.w("JellioPlayer", "direct play failed before first frame, retrying source $sourceId with server transcode")
            _failover.value = FailoverNotice(
                attempt = triedSourceIds.size + 1,
                sourceLabel = sourceLabel(source),
                slowTranscode = false,
            )
            failoverJob = viewModelScope.launch {
                val target = catching {
                    repository.resolvePlayback(
                        userId = session.userId,
                        itemId = id,
                        mediaSourceId = sourceId,
                        startTimeTicks = state.startPositionTicks,
                        audioStreamIndex = state.selectedAudioStreamIndex,
                        forceTranscode = true,
                    )
                }.getOrNull()
                ensureActive()
                if (target != null) {
                    val subtitleTracks = buildSubtitleTracks(id, target.mediaSource, target.startPositionTicks)
                    _uiState.value = _uiState.value.copy(
                        streamUrl = target.streamUrl,
                        directPlay = false,
                        subtitleTracks = subtitleTracks,
                    )
                    failingOver = false
                    return@launch
                }
                failingOver = false
                streamFailed(session, reason)
            }
            return
        }
        failingOver = true
        android.util.Log.w("JellioPlayer", "stream failed ($reason), trying the next one")
        val remuxFailed = !state.directPlay && reason == "error before the first frame"
        failoverJob = viewModelScope.launch {
            var loaded: FailoverLoad<com.jellio.tv.data.model.MediaSourceDto>? = null
            if (remuxFailed && sourceId != null) {
                loaded = repository.getFailoverSources(session.userId, id, fresh = notReady)
                val playable = loaded.sources.orEmpty().filter {
                    it.Id != null && it.Id != sourceId && it.Id !in triedSourceIds && !BadStreams.isBad(id, it) && repository.canDirectPlay(it)
                }
                if (playable.isEmpty() && repository.canDirectPlay(source, ignoreAudio = true) && directRetriedSourceIds.add(sourceId)) {
                    android.util.Log.w("JellioPlayer", "server remux failed before the first frame, playing source $sourceId as is")
                    _failover.value = FailoverNotice(attempt = triedSourceIds.size + 1, sourceLabel = sourceLabel(source), slowTranscode = false)
                    val target = catching {
                        repository.resolvePlayback(
                            userId = session.userId,
                            itemId = id,
                            mediaSourceId = sourceId,
                            startTimeTicks = state.startPositionTicks,
                            forceDirectPlay = true,
                        )
                    }.getOrNull()
                    ensureActive()
                    if (target != null) {
                        _uiState.value = _uiState.value.copy(
                            streamUrl = target.streamUrl,
                            directPlay = true,
                            subtitleTracks = buildSubtitleTracks(id, target.mediaSource, 0L),
                        )
                        failingOver = false
                        return@launch
                    }
                }
            }
            if (!notReady) BadStreams.mark(id, source, soft = BadStreams.isSoftFailure(reason))

            source.Id?.let { if (it !in triedSourceIds) triedSourceIds.add(it) }
            chooseNextStream(session, PendingFailover(id, sourceId, reason, _uiState.value, remuxFailed, notReady), loaded)
        }
    }

    private data class PendingFailover(
        val itemId: String,
        val failedSourceId: String?,
        val reason: String,
        val state: PlayerUiState,
        val remuxFailed: Boolean,
        val freshList: Boolean = false,
    )

    private var pendingFailover: PendingFailover? = null

    fun retryFailover(session: Session) {
        val pending = pendingFailover ?: return
        if (failingOver) return
        failingOver = true
        _uiState.value = pending.state.copy(isLoading = true, error = null, errorDetail = null, failoverRetry = false)
        failoverJob = viewModelScope.launch { chooseNextStream(session, pending, null) }
    }

    private suspend fun chooseNextStream(
        session: Session,
        pending: PendingFailover,
        preloaded: FailoverLoad<com.jellio.tv.data.model.MediaSourceDto>?,
    ) {
        val id = pending.itemId
        val state = pending.state
        val language = failoverLanguage ?: streamLanguage()
        failoverLanguage = language
        val loaded = preloaded ?: repository.getFailoverSources(session.userId, id, fresh = pending.freshList)
        currentCoroutineContext().ensureActive()
        // Avoid and Block try streams whose video can be copied first;
        // Block never falls back to one the server would re-encode.
        val policy = VideoTranscodePolicy
        val plan = planFailover(
            loaded.sources,
            triedSourceIds,
            id = { it.Id },
            isBad = { BadStreams.kind(id, it) },
            byLanguage = { orderByLanguage(it, language) },
            needsReencode = if (policy.avoiding) { s -> policy.needsReencode(s) } else null,
            blockReencode = policy.blocking,
            preferred = if (pending.remuxFailed) { s -> repository.canDirectPlay(s) } else null,
        )
        val outcome = failoverOutcome(loaded, plan) { it.Id }
        logFailoverDecision(pending, plan.counts, loaded, outcome)
        val next = (outcome as? FailoverOutcome.Navigate)?.let { nav -> plan.ordered.first { it.Id == nav.sourceId } }
        if (next == null) {
            pendingFailover = pending
            _failover.value = null
            val listFailed = outcome as? FailoverOutcome.ListFailed
            _uiState.value = PlayerUiState(
                isLoading = false,
                error = if (listFailed != null) {
                    Strings.get("tv_failover_list_title")
                } else {
                    Strings.get("player_failover_none_worked")
                },
                errorDetail = when {
                    listFailed?.kind == "timeout" -> Strings.get("tv_failover_list_timeout")
                    listFailed != null -> Strings.get("tv_failover_list_error")
                    triedSourceIds.size > 1 -> Strings.format("player_failover_none_worked_many", triedSourceIds.size)
                    else -> Strings.get("player_failover_no_other")
                },
                failoverRetry = listFailed != null,
                title = state.title,
                pauseInfo = state.pauseInfo,
            )
            failingOver = false
            return
        }
        pendingFailover = null
        _failover.value = FailoverNotice(
            attempt = triedSourceIds.size + 1,
            sourceLabel = sourceLabel(next),
            slowTranscode = state.reencodingVideo && pending.reason.startsWith("no picture"),
        )
        failingOver = false
        currentSource = next
        load(session, id, next.Id, fromFailover = true)
    }

    private fun logFailoverDecision(
        pending: PendingFailover,
        counts: FailoverCounts,
        loaded: FailoverLoad<*>,
        outcome: FailoverOutcome,
    ) {
        android.util.Log.w("JellioPlayer", describeFailover(pending.reason, counts, loaded, outcome))
        viewModelScope.launch {
            repository.logFailover(
                com.jellio.tv.data.model.FailoverLogBody(
                    ItemId = pending.itemId,
                    FailedSourceId = pending.failedSourceId,
                    Reason = pending.reason,
                    Total = counts.total,
                    Tried = counts.tried,
                    BadHard = counts.badHard,
                    BadSoft = counts.badSoft,
                    AfterLanguage = counts.afterLanguage,
                    AfterTranscode = counts.afterTranscode,
                    ListOrigin = loaded.origin.name.lowercase(),
                    ListError = loaded.error?.kind,
                    ListMs = loaded.error?.ms,
                    Outcome = outcomeLabel(outcome),
                    NextSourceId = (outcome as? FailoverOutcome.Navigate)?.sourceId,
                ),
            )
        }
    }
    // -------------------------------------------------------------------------

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _subtitleStyle = MutableStateFlow(SubtitleStyle())
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    init {
        viewModelScope.launch {
            subtitleStylePreferences.style.collect { _subtitleStyle.value = it }
        }
    }

    private fun syncSubtitleStyleToServer(style: SubtitleStyle) {
        viewModelScope.launch {
            catching {
                repository.saveSubtitleStyle(
                    com.jellio.tv.data.model.SubtitleStyleDto(
                        size = style.size,
                        background = style.background,
                        color = style.color,
                        edge = style.edge,
                        position = style.position,
                    ),
                )
            }
        }
    }

    fun setSubtitleSize(size: String) {
        viewModelScope.launch {
            subtitleStylePreferences.setSize(size)
            syncSubtitleStyleToServer(_subtitleStyle.value.copy(size = size))
        }
    }

    fun setSubtitleLook(style: SubtitleStyle) {
        viewModelScope.launch {
            subtitleStylePreferences.setLook(style.color, style.edge, style.position)
            syncSubtitleStyleToServer(style)
        }
    }

    fun setSubtitleBackground(background: String) {
        viewModelScope.launch {
            subtitleStylePreferences.setBackground(background)
            syncSubtitleStyleToServer(_subtitleStyle.value.copy(background = background))
        }
    }

    private var loadedItemId: String? = null
    private var itemId: String? = null
    private var currentItem: BaseItemDto? = null
    private var mediaSourceIdParam: String? = null
    private var playbackSession: Session? = null
    private var hasCheckedPreviousEpisodes = false
    private var toastSeq = 0L

    // The exception goes to the log; the viewer gets a translated reason.
    private fun actionFailedText(err: Throwable, key: String): String {
        android.util.Log.w("JellioPlayer", "$key", err)
        return Strings.get(if (loadFailureOf(err) == LoadFailure.Unreachable) "error_server_unreachable" else key)
    }

    fun showToast(message: String) {
        toastSeq++
        _uiState.value = _uiState.value.copy(toastMessage = message, toastId = toastSeq)
    }

    fun clearSwitching() {
        val label = _uiState.value.switchingTo ?: return
        _uiState.value = _uiState.value.copy(switchingTo = null, switchingLoaded = false)
        showToast(Strings.format("player_switch_done", label))
    }

    fun clearToast(id: Long) {
        if (_uiState.value.toastId == id) {
            _uiState.value = _uiState.value.copy(toastMessage = null)
        }
    }

    data class PrewarmedPlayback(
        val itemId: String,
        val item: BaseItemDto,
        val target: PlaybackTarget,
        val audioPreference: String?,
    )

    private var prewarmedPlayback: PrewarmedPlayback? = null
    private var loadJob: kotlinx.coroutines.Job? = null

    fun onScreenDisposed() {
        failoverJob?.cancel()
        failoverJob = null
        loadJob?.cancel()
        loadJob = null
        loadedItemId = null
        failingOver = false
        _failover.value = null
    }

    fun retry(session: Session) {
        val id = itemId ?: return
        loadedItemId = null
        load(session, id, mediaSourceIdParam)
    }

    fun load(
        session: Session,
        itemId: String,
        mediaSourceId: String?,
        fromFailover: Boolean = false,
        startPositionTicks: Long? = null,
    ) {
        val key = itemId + "|" + mediaSourceId
        if (!fromFailover && loadedItemId == key) return
        loadedItemId = key
        if (!fromFailover) {
            placeholderSources.clear()
            triedSourceIds.clear()
            transcodeRetriedSourceIds.clear()
            directRetriedSourceIds.clear()
            failoverLanguage = null
            pendingFailover = null
            if (mediaSourceId != null) {
                BadStreams.unmark(itemId, com.jellio.tv.data.model.MediaSourceDto(Id = mediaSourceId))
            }

            failingOver = false
            _failover.value = null
            hasCreditedRealWatch = false
            hasCheckedPreviousEpisodes = false
            currentItem = null
            lastReportedDurationSeconds = 0.0
        }
        this.itemId = itemId
        this.mediaSourceIdParam = mediaSourceId
        this.playbackSession = session
        // Drops an episode list still on its way for the previous title.
        episodeRequest++
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PlayerUiState(isLoading = true)
            PlayStartTrace.mark(itemId, "player open", if (fromFailover) "failover" else "source=${mediaSourceId ?: "default"}")
            warmAtOpen = repository.recentWarmSources(itemId)
            repository.claimFirstBytesWarm(itemId)
            try {
                val prewarmed = if (mediaSourceId == null && !fromFailover && prewarmedPlayback?.itemId == itemId) {
                    prewarmedPlayback.also { prewarmedPlayback = null }
                } else {
                    null
                }
                // Warms the cached user and transcode policy while the item loads.
                launch { catching { repository.getUser(session.userId) } }
                launch { catching { repository.loadVideoTranscodePolicy() } }
                // The loading art goes up at once from what the row or title
                // page already knew, not after the server answers.
                if (prewarmed == null) {
                    repository.previewItem(itemId)?.let { preview ->
                        val previewIsEpisode = preview.Type == "Episode" && preview.SeriesName != null
                        _uiState.value = PlayerUiState(
                            isLoading = true,
                            title = if (previewIsEpisode) preview.SeriesName.orEmpty() else preview.Name.orEmpty(),
                            pauseInfo = buildPauseOverlayInfo(session, preview, previewIsEpisode),
                        )
                    }
                }
                val preview = if (prewarmed == null) repository.previewItem(itemId) else null
                val earlyTicks = startPositionTicks ?: preview?.UserData?.PlaybackPositionTicks
                var targetFrom = "fresh"
                val earlyTarget = if (earlyTicks != null) {
                    async {
                        repository.takePrefetchedPlayback(itemId, mediaSourceId, earlyTicks)?.also { targetFrom = "prefetched" }
                            ?: catching { repository.resolvePlayback(session.userId, itemId, mediaSourceId, earlyTicks) }.getOrNull()?.also { targetFrom = "early" }
                    }
                } else {
                    null
                }
                val earlyAudio = if (prewarmed == null) async { catching { repository.getAudioLanguagePreference(session.userId) }.getOrNull() } else null
                val item = prewarmed?.item ?: repository.getItemDetails(session.userId, itemId)
                currentItem = item
                PlayStartTrace.mark(itemId, "details", if (prewarmed != null) "prewarmed" else "")
                val isEpisode = item.Type == "Episode" && item.SeriesName != null
                // The title's own backdrop and logo go up while the stream
                // is still being prepared, not a blank screen.
                _uiState.value = PlayerUiState(
                    isLoading = true,
                    title = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(),
                    pauseInfo = buildPauseOverlayInfo(session, item, isEpisode),
                )
                // Following the group into its title starts where the group is,
                // not at this account's own resume point. Remote cast commands can also specify start position.
                val groupTarget = syncPlay.target.value?.takeIf { com.jellio.tv.data.syncplay.SyncPlayManager.sameId(it.itemId, itemId) }
                val startTicks = startPositionTicks ?: groupTarget?.startPositionTicks ?: item.UserData?.PlaybackPositionTicks ?: 0
                val audioPreference = prewarmed?.audioPreference ?: earlyAudio?.await() ?: repository.getAudioLanguagePreference(session.userId)
                audioLanguagePreference = audioPreference
                var target = prewarmed?.target?.also { targetFrom = "prewarmed" }
                    ?: earlyTarget?.await()?.takeIf { earlyTicks == startTicks }
                    ?: (if (fromFailover) null else repository.takePrefetchedPlayback(itemId, mediaSourceId, startTicks))?.also { targetFrom = "prefetched" }
                    ?: repository.resolvePlayback(session.userId, itemId, mediaSourceId, startTicks).also { targetFrom = "fresh" }
                PlayStartTrace.mark(itemId, "playback info", "from=$targetFrom direct=${target.directPlay}")
                // The reader's audio language wins over the file's default
                // when the file has it; a transcode has to be asked for it.
                val preferredAudio = preferredAudioIndex(target.mediaSource, audioPreference)
                if (prewarmed == null && preferredAudio != null && !target.directPlay && preferredAudio != target.mediaSource.DefaultAudioStreamIndex) {
                    target = repository.resolvePlayback(session.userId, itemId, mediaSourceId, startTicks, audioStreamIndex = preferredAudio)
                    PlayStartTrace.mark(itemId, "audio renegotiated", "index=$preferredAudio")
                }
                val episodeCode = if (item.ParentIndexNumber != null && item.IndexNumber != null) {
                    com.jellio.tv.data.i18n.seasonEpisodeTag(item.ParentIndexNumber, item.IndexNumber) + " · "
                } else {
                    ""
                }

                repository.loadVideoTranscodePolicy()
                val classification = VideoTranscodePolicy.classify(target.mediaSource)
                val loggedSource = target.mediaSource
                val loggedDirect = target.directPlay
                viewModelScope.launch { repository.logPlaybackDecision(itemId, loggedSource, loggedDirect) }
                // Avoid and Block move to a stream whose video can be
                // copied; Block won't start a video re-encode at all.
                if (VideoTranscodePolicy.avoiding && classification.needsVideoTranscode && !fromFailover && syncPlay.group.value == null) {
                    if (transcodeSkippedFor != itemId) {
                        transcodeSkipped.clear()
                        transcodeSkippedFor = itemId
                    }
                    target.mediaSource.Id?.let { transcodeSkipped.add(it) }
                    val others = catching { repository.getMediaSources(session.userId, itemId, reason = "transcode check") }.getOrDefault(emptyList())
                        .filter { it.Id != null && it.Id !in transcodeSkipped && !BadStreams.isBad(itemId, it) && !VideoTranscodePolicy.needsReencode(it) }
                    // One known to copy (probed, or by its name) before one nothing is known about.
                    val copyable = others.firstOrNull { videoInfoOf(it) != null } ?: others.firstOrNull()
                    if (copyable != null && (mediaSourceId == null || VideoTranscodePolicy.blocking)) {
                        loadedItemId = null
                        load(session, itemId, copyable.Id)
                        return@launch
                    }
                    if (VideoTranscodePolicy.blocking) {
                        _uiState.value = PlayerUiState(
                            isLoading = false,
                            error = Strings.get("player_stream_unplayable_title"),
                            errorDetail = Strings.format(
                                "player_stream_unplayable_transcode_off",
                                classification.reasons.joinToString(", ") { outlookReason(it).displayText() },
                            ),
                            title = _uiState.value.title,
                            pauseInfo = _uiState.value.pauseInfo,
                        )
                        return@launch
                    }
                }

                currentSource = target.mediaSource
                PlayStartTrace.mark(itemId, "stream url ready")
                val expectedRuntimeMs = (item.RunTimeTicks ?: 0L) / 10_000
                val subtitleTracks = subtitleTracksFor(itemId, target)
                subtitleSeriesId = if (isEpisode) item.SeriesId else null
                val initialSubtitle = initialSubtitleIndex(
                    subtitleTracks,
                    subtitleSeriesId,
                    repository.getSubtitleLanguagePreference(session.userId),
                )
                val audioTracks = buildAudioTracks(target.mediaSource)
                val resumePercent = item.UserData?.PlayedPercentage?.takeIf { startTicks > 0 && !fromFailover }?.roundToInt()

                playbackSession = session

                _uiState.value = PlayerUiState(
                    expectedRuntimeMs = expectedRuntimeMs,
                    isLoading = false,
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = resumePercent,
                    title = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(),
                    subtitle = if (isEpisode) episodeCode + item.Name.orEmpty() else "",
                    artworkUrl = artworkUrl(session, item, isEpisode),
                    videoFrameRate = target.mediaSource.MediaStreams?.firstOrNull { it.Type == "Video" }
                        ?.let { it.RealFrameRate ?: it.AverageFrameRate },
                    subtitleTracks = subtitleTracks,
                    selectedSubtitleIndex = initialSubtitle,
                    pauseInfo = buildPauseOverlayInfo(session, item, isEpisode),
                    audioTracks = audioTracks,
                    defaultAudioStreamIndex = target.mediaSource.DefaultAudioStreamIndex,
                    selectedAudioStreamIndex = preferredAudio,
                    directPlay = target.directPlay,
                    reencodingVideo = VideoTranscodePolicy.avoiding && classification.needsVideoTranscode,
                )

                // Children of this load: a newer load or leaving cancels them.
                if (item.Type == "Episode") {
                    launch {
                        val next = catching { repository.getNextEpisode(session.userId, item) }.getOrNull()
                        if (next != null) {
                            _uiState.value = _uiState.value.copy(upNextInfo = buildUpNextInfo(session, next))
                            // The stream list is cheap: warm it as soon as the next episode is known.
                            repository.prefetchStreams(next.Id)
                        }
                    }
                }

                launch { loadSkipSegments(session, itemId, item.RunTimeTicks) }

                launch {
                    val status = repository.getSleepTimerStatus()
                    if (status?.Active == true) {
                        val endMs = parseSleepTimerEndTimeMs(status.EndTimeUtc)
                        if (endMs != null) {
                            _uiState.value = _uiState.value.copy(sleepTimerEndTimeMs = endMs)
                        }
                    }
                }

                launch {
                    val sources = catching { repository.getMediaSources(session.userId, itemId, reason = "player") }.getOrNull()
                    if (sources != null && sources.size > 1) {
                        _uiState.value = _uiState.value.copy(sourceOptions = sources)
                    }
                }

                val seriesId = item.SeriesId
                if (isEpisode && seriesId != null) {
                    _uiState.value = _uiState.value.copy(seriesId = seriesId)
                    launch {
                        val seasons = catching { repository.getSeasons(seriesId, session.userId) }.getOrDefault(emptyList())
                        if (seasons.isNotEmpty()) {
                            val ordered = seasons.sortedBy { if (isSpecialsSeason(it)) 1 else 0 }
                            _uiState.value = _uiState.value.copy(seasons = ordered)
                            val defaultSeason = ordered.firstOrNull { it.Id == item.SeasonId } ?: ordered.firstOrNull()
                            if (defaultSeason != null) {
                                loadSeasonEpisodes(session, seriesId, defaultSeason.Id)
                            }
                        }
                    }
                }
            } catch (err: kotlinx.coroutines.CancellationException) {
                throw err
            } catch (err: Exception) {
                // A retry that the server can't even start moves on too, and
                // so does a chosen stream whose stored link expired.
                val notReady = isSourceNotReady(err)
                if ((fromFailover || (notReady && mediaSourceId != null)) && syncPlay.group.value == null) {
                    if (currentSource == null) {
                        currentSource = com.jellio.tv.data.model.MediaSourceDto(Id = mediaSourceId ?: itemId)
                    }
                    val why = if (notReady) "source not ready" else err.message
                    streamFailed(session, "could not start: $why", notReady)
                    return@launch
                }
                val problem = friendlyPlaybackError(err)

                _uiState.value = PlayerUiState(
                    isLoading = false,
                    error = problem.title,
                    errorDetail = problem.detail,
                    title = _uiState.value.title,
                    pauseInfo = _uiState.value.pauseInfo,
                )
            }
        }
    }

    // screens/player.js order: community data, then Media Segments (or the
    // Intro Skipper), then chapter names, then the plugin's own analysis.
    // Each step only fills what is still missing. Credits in the first half
    // of the runtime are dropped as bad data by the merge.
    private suspend fun loadSkipSegments(session: Session, itemId: String, runTimeTicks: Long?) {
        val runtimeSeconds = (runTimeTicks ?: 0L) / 10_000_000.0
        var merged: SkipSegments? = null
        // False once the title changed or nothing is missing any more.
        suspend fun step(fetch: suspend () -> SkipSegments?): Boolean {
            merged = mergeSkipSegments(merged, fetch(), runtimeSeconds)
            if (this.itemId != itemId) return false
            publishSkipSegments(merged, resolved = false)
            return !skipSegmentsComplete(merged, runtimeSeconds)
        }
        if (step { repository.getCommunitySkipSegments(itemId) } &&
            step { repository.getIntroSkipperSegments(itemId) } &&
            step { repository.getChapterSkipSegments(session.userId, itemId, runTimeTicks) }
        ) {
            step { repository.getJellioIntroCredits(itemId) }
        }
        if (this.itemId != itemId) return
        publishSkipSegments(merged, resolved = true)
    }

    private fun publishSkipSegments(segments: SkipSegments?, resolved: Boolean) {
        val usable = segments?.takeIf { it.intro != null || it.credits != null }
        _uiState.value = _uiState.value.copy(skipSegments = usable, skipSegmentsResolved = resolved)
    }

    // The episode's own still, else the show's thumb from its own item.
    private fun episodeThumbnailUrl(session: Session, episode: BaseItemDto): String? {
        episode.ImageTags?.get("Primary")?.let {
            return repository.imageUrl(session.serverAddress, episode.Id, it, "Primary", 400, session.accessToken)
        }
        val parentTag = episode.ParentThumbImageTag ?: return null
        val parentId = episode.ParentThumbItemId ?: return null
        return repository.imageUrl(session.serverAddress, parentId, parentTag, "Thumb", 400, session.accessToken)
    }

    private fun buildUpNextInfo(session: Session, episode: BaseItemDto): UpNextInfo {
        val thumbnailUrl = episodeThumbnailUrl(session, episode)
        val title = if (episode.IndexNumber != null && episode.ParentIndexNumber != null) {
            com.jellio.tv.data.i18n.seasonEpisodeTag(episode.ParentIndexNumber, episode.IndexNumber) + " · " + episode.Name.orEmpty()
        } else {
            episode.Name.orEmpty()
        }
        return UpNextInfo(
            itemId = episode.Id,
            thumbnailUrl = thumbnailUrl,
            title = title,
            overview = episode.Overview?.takeIf { it.isNotBlank() },
            seriesName = episode.SeriesName,
            episodeName = episode.Name.orEmpty(),
            episodeCode = com.jellio.tv.data.i18n.seasonEpisodeTag(episode.ParentIndexNumber, episode.IndexNumber).takeIf { it.isNotEmpty() },
        )
    }

    private fun artworkUrl(session: Session, item: BaseItemDto, isEpisode: Boolean): String? {
        val (id, tag) = if (isEpisode && item.SeriesId != null) {
            item.SeriesId to item.SeriesPrimaryImageTag
        } else {
            item.Id to (item.ImageTags?.get("Primary") ?: item.PrimaryImageTag)
        }
        if (tag == null) return null
        return session.serverAddress.trimEnd('/') + "/Items/" + id + "/Images/Primary?maxWidth=600&tag=" + tag + "&api_key=" + session.accessToken
    }

    private fun buildPauseOverlayInfo(session: Session, item: BaseItemDto, isEpisode: Boolean): PauseOverlayInfo {
        val seriesId = item.SeriesId
        val artId = if (isEpisode && seriesId != null) seriesId else item.Id
        val art = com.jellio.tv.ui.common.titleArtCandidates(
            item,
            { id, tag, type, width -> repository.imageUrl(session.serverAddress, id, tag, type, width, session.accessToken) },
            seriesFirst = isEpisode,
            backdropWidth = 1600,
        )
        val logoTag = if (isEpisode) item.ParentLogoImageTag else item.ImageTags?.get("Logo")
        val logoArtId = if (isEpisode && item.ParentLogoItemId != null) item.ParentLogoItemId else artId
        val logoUrl = logoTag?.let {
            repository.imageUrl(session.serverAddress, logoArtId, it, "Logo", 800, session.accessToken)
        }
        val hasEpisodeCode = item.ParentIndexNumber != null && item.IndexNumber != null
        return PauseOverlayInfo(
            art = art,
            logoUrl = logoUrl,
            title = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(),
            rating = item.CommunityRating,
            year = item.ProductionYear?.toString(),
            officialRating = item.OfficialRating,
            isEpisode = isEpisode,
            episodeCode = if (isEpisode && hasEpisodeCode) com.jellio.tv.data.i18n.seasonEpisodeTag(item.ParentIndexNumber, item.IndexNumber) else null,
            episodeTitle = if (isEpisode) item.Name else null,
            overview = item.Overview,
        )
    }

    fun restart(session: Session) {
        val id = itemId ?: return
        viewModelScope.launch {
            try {
                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    mediaSourceIdParam,
                    0,
                    audioStreamIndex = _uiState.value.selectedAudioStreamIndex,
                )
                val subtitleTracks = subtitleTracksFor(id, target)
                _uiState.value = _uiState.value.copy(
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = null,
                    // Start over keeps the subtitles that were on.
                    selectedSubtitleIndex = _uiState.value.selectedSubtitleIndex?.takeIf { index ->
                        subtitleTracks.any { it.streamIndex == index && it.isTextBased }
                    },
                    subtitleTracks = subtitleTracks,
                    directPlay = target.directPlay,
                )
            } catch (err: Exception) {
                showToast(
                    if (err is IllegalStateException) {
                        Strings.get("player_start_over_unavailable")
                    } else {
                        actionFailedText(err, "player_start_over_error")
                    },
                )
            }
        }
    }

    // A transcode's subtitles are timed from where its encode started.
    private suspend fun subtitleTracksFor(itemId: String, target: PlaybackTarget): List<SubtitleTrackUiState> =
        buildSubtitleTracks(itemId, target.mediaSource, if (target.directPlay) 0L else target.startPositionTicks)

    private suspend fun buildSubtitleTracks(itemId: String, mediaSource: MediaSourceDto, offsetTicks: Long = 0): List<SubtitleTrackUiState> {
        val resolvedMediaSourceId = mediaSource.Id ?: itemId
        var embeddedTextIndex = 0
        return mediaSource.MediaStreams
            ?.filter { it.Type == "Subtitle" }
            ?.mapNotNull { stream ->
                val isExternal = stream.DeliveryMethod == "External"
                val isText = stream.IsTextSubtitleStream == true
                val currentEmbeddedTextOrdinal = if (!isExternal && isText) embeddedTextIndex++ else -1
                // Block: an image subtitle would make the server re-encode the video.
                if (!isText && VideoTranscodePolicy.blocking) return@mapNotNull null
                val index = stream.Index ?: return@mapNotNull null
                val label = stream.DisplayTitle ?: stream.Language ?: Strings.get("player_subtitle_fallback")
                val url = if (isText) {
                    catching { repository.buildSubtitleUrl(itemId, resolvedMediaSourceId, stream, offsetTicks) }.getOrNull()
                } else {
                    null
                }
                SubtitleTrackUiState(
                    streamIndex = index,
                    label = if (isText) label else label + imageSubtitleSuffix(),
                    isTextBased = isText,
                    url = url,
                    language = stream.Language,
                    isExternal = isExternal,
                    embeddedTextOrdinal = currentEmbeddedTextOrdinal,
                )
            }
            ?: emptyList()
    }

    private fun imageSubtitleSuffix(): String =
        " (" + Strings.get(if (VideoTranscodePolicy.avoiding) "player_subtitle_image_slow" else "player_subtitle_image") + ")"

    private var audioLanguagePreference: String? = null

    // The first audio stream in the reader's preferred language, if any.
    private fun preferredAudioIndex(mediaSource: MediaSourceDto, preference: String?): Int? {
        val wanted = normalizeLanguage(preference) ?: return null
        return repository.getAudioStreams(mediaSource).firstOrNull { normalizeLanguage(it.Language) == wanted }?.Index
    }

    private fun buildAudioTracks(mediaSource: MediaSourceDto): List<AudioTrackUiState> =
        repository.getAudioStreams(mediaSource).mapNotNull { stream ->
            val index = stream.Index ?: return@mapNotNull null
            AudioTrackUiState(index, audioStreamLabel(stream), stream.Language)
        }

    private fun audioStreamLabel(stream: MediaStreamDto): String {
        val language = stream.Language?.uppercase() ?: stream.DisplayTitle ?: Strings.get("lang_unknown")
        val parts = listOfNotNull(
            stream.Codec?.uppercase()?.takeIf { it.isNotEmpty() },
            stream.ChannelLayout?.takeIf { it.isNotEmpty() },
        )
        return if (parts.isNotEmpty()) "$language · ${parts.joinToString(" ")}" else language
    }

    fun selectSubtitle(index: Int?) {
        _uiState.value = _uiState.value.copy(selectedSubtitleIndex = index)
        rememberSubtitleChoice(index)
    }

    // The show this episode belongs to, for remembering its subtitle pick.
    private var subtitleSeriesId: String? = null

    private fun rememberSubtitleChoice(index: Int?) {
        val seriesId = subtitleSeriesId ?: return
        val choice = if (index == null) {
            "off"
        } else {
            val track = _uiState.value.subtitleTracks.firstOrNull { it.streamIndex == index } ?: return
            track.language?.takeIf { it.isNotBlank() } ?: ("label:" + track.label)
        }
        subtitleStylePreferences.setSubtitleChoice(seriesId, choice)
    }

    private fun initialSubtitleIndex(tracks: List<SubtitleTrackUiState>, seriesId: String?, defaultLanguage: String?): Int? {
        val remembered = seriesId?.let { subtitleStylePreferences.subtitleChoice(it) }
        if (remembered == "off") return null
        val text = tracks.filter { it.isTextBased }
        if (remembered != null) {
            val match = if (remembered.startsWith("label:")) {
                text.firstOrNull { it.label == remembered.removePrefix("label:") }
            } else {
                text.firstOrNull { sameLanguage(it.language, remembered) }
            }
            if (match != null) return match.streamIndex
        }
        val language = defaultLanguage ?: return null
        return text.firstOrNull { sameLanguage(it.language, language) }?.streamIndex
    }

    fun selectBurnedInSubtitle(session: Session, streamIndex: Int, currentPositionTicks: Long) {
        val id = itemId ?: return
        val requestedLabel = _uiState.value.subtitleTracks.firstOrNull { it.streamIndex == streamIndex }
            ?.label?.removeSuffix(imageSubtitleSuffix()) ?: Strings.get("player_subtitle_fallback")
        viewModelScope.launch {
            try {
                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    mediaSourceIdParam,
                    currentPositionTicks,
                    burnInSubtitleStreamIndex = streamIndex,
                    audioStreamIndex = _uiState.value.selectedAudioStreamIndex,
                )
                val subtitleTracks = subtitleTracksFor(id, target)
                _uiState.value = _uiState.value.copy(
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = null,
                    selectedSubtitleIndex = streamIndex,
                    subtitleTracks = subtitleTracks,
                    directPlay = target.directPlay,
                )
                rememberSubtitleChoice(streamIndex)
                showToast(Strings.format("tv_requested_burned_in", requestedLabel))
            } catch (err: Exception) {
                showToast(
                    if (err is IllegalStateException) {
                        Strings.get("player_subtitle_unavailable")
                    } else {
                        actionFailedText(err, "player_subtitle_switch_error")
                    },
                )
            }
        }
    }

    fun switchAudioTrack(session: Session, streamIndex: Int, currentPositionTicks: Long) {
        val id = itemId ?: return
        val label = _uiState.value.audioTracks.firstOrNull { it.streamIndex == streamIndex }?.label ?: Strings.get("tv_audio")
        if (_uiState.value.directPlay && currentSource?.let { repository.canDirectPlay(it, streamIndex) } != false) {
            _uiState.value = _uiState.value.copy(selectedAudioStreamIndex = streamIndex)
            showToast(Strings.format("tv_audio_label", label))
            return
        }
        showToast(Strings.format("tv_switching_to", label))
        viewModelScope.launch {
            try {
                val currentSubIndex = _uiState.value.selectedSubtitleIndex
                val burnInSub = burnedInSubtitleIndex()

                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    mediaSourceIdParam,
                    currentPositionTicks,
                    burnInSubtitleStreamIndex = burnInSub,
                    audioStreamIndex = streamIndex,
                )
                val subtitleTracks = subtitleTracksFor(id, target)
                val audioTracks = buildAudioTracks(target.mediaSource)
                _uiState.value = _uiState.value.copy(
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = null,
                    selectedSubtitleIndex = currentSubIndex,
                    subtitleTracks = subtitleTracks,
                    selectedAudioStreamIndex = streamIndex,
                    audioTracks = audioTracks,
                    defaultAudioStreamIndex = target.mediaSource.DefaultAudioStreamIndex,
                    directPlay = target.directPlay,
                )
                showToast(Strings.format("tv_requested_reloading", label))
            } catch (err: Exception) {
                showToast(
                    if (err is IllegalStateException) {
                        Strings.get("player_audio_unavailable")
                    } else {
                        actionFailedText(err, "player_audio_switch_error")
                    },
                )
            }
        }
    }

    // The picked subtitle when it is an image one the server burns in.
    private fun burnedInSubtitleIndex(): Int? {
        val index = _uiState.value.selectedSubtitleIndex ?: return null
        return index.takeIf { _uiState.value.subtitleTracks.firstOrNull { it.streamIndex == index }?.isTextBased == false }
    }

    fun reloadAt(session: Session, positionTicks: Long) {
        val id = itemId ?: return
        viewModelScope.launch {
            try {
                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    mediaSourceIdParam,
                    positionTicks,
                    burnInSubtitleStreamIndex = burnedInSubtitleIndex(),
                    audioStreamIndex = _uiState.value.selectedAudioStreamIndex,
                )
                _uiState.value = _uiState.value.copy(
                    streamUrl = target.streamUrl,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = null,
                    directPlay = target.directPlay,
                    subtitleTracks = subtitleTracksFor(id, target),
                )
            } catch (err: Exception) {
                showToast(Strings.get("tv_seek_failed"))
            }
        }
    }

    // Moves a stream that keeps buffering to the next one in the preferred
    // language, at the same spot. False when there is none left to try.
    suspend fun switchForBuffering(session: Session, currentPositionTicks: Long): Boolean {
        val id = itemId ?: return false
        val current = _uiState.value.mediaSourceId
        current?.let { if (it !in triedSourceIds) triedSourceIds.add(it) }
        val loaded = repository.getFailoverSources(session.userId, id)
        val language = failoverLanguage ?: streamLanguage()
        val policy = VideoTranscodePolicy
        val plan = planFailover(
            loaded.sources?.filter { it.Id != current },
            triedSourceIds,
            id = { it.Id },
            isBad = { BadStreams.kind(id, it) },
            byLanguage = { orderByLanguage(it, language) },
            needsReencode = if (policy.avoiding) { s -> policy.needsReencode(s) } else null,
            blockReencode = policy.blocking,
        )
        val outcome = failoverOutcome(loaded, plan) { it.Id }
        logFailoverDecision(PendingFailover(id, current, "buffering switch", _uiState.value, false), plan.counts, loaded, outcome)
        val next = plan.ordered.firstOrNull() ?: return false
        currentSource?.let { BadStreams.mark(id, it, soft = true) }
        switchSource(session, next, currentPositionTicks)
        return true
    }

    fun switchSource(session: Session, source: MediaSourceDto, currentPositionTicks: Long) {
        val id = itemId ?: return
        if (source.Id == _uiState.value.mediaSourceId) return
        _uiState.value = _uiState.value.copy(switchingTo = sourceLabel(source), switchingLoaded = false)
        viewModelScope.launch {
            try {
                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    source.Id,
                    currentPositionTicks,
                )
                mediaSourceIdParam = target.mediaSource.Id
                currentSource = target.mediaSource
                val subtitleTracks = subtitleTracksFor(id, target)
                val audioTracks = buildAudioTracks(target.mediaSource)
                _uiState.value = _uiState.value.copy(
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = null,
                    selectedSubtitleIndex = null,
                    subtitleTracks = subtitleTracks,
                    selectedAudioStreamIndex = null,
                    audioTracks = audioTracks,
                    defaultAudioStreamIndex = target.mediaSource.DefaultAudioStreamIndex,
                    directPlay = target.directPlay,
                    reencodingVideo = VideoTranscodePolicy.avoiding && VideoTranscodePolicy.needsReencode(target.mediaSource),
                    switchingLoaded = true,
                )
            } catch (err: Exception) {
                _uiState.value = _uiState.value.copy(switchingTo = null, switchingLoaded = false)
                showToast(
                    if (err is IllegalStateException) {
                        Strings.get("player_stream_unavailable")
                    } else {
                        Strings.get("player_stream_switch_failed")
                    },
                )
            }
        }
    }

    private fun isSpecialsSeason(season: BaseItemDto): Boolean {
        if (season.IndexNumber == 0) return true
        return season.Name?.contains("special", ignoreCase = true) == true
    }

    private var episodeRequest = 0

    fun loadSeasonEpisodes(session: Session, seriesId: String, seasonId: String) {
        val request = ++episodeRequest
        _uiState.value = _uiState.value.copy(selectedSeasonId = seasonId)
        viewModelScope.launch {
            val episodes = catching { repository.getEpisodes(seriesId, session.userId, seasonId) }
                .getOrDefault(emptyList())
                .map { buildEpisodePanelEntry(session, it) }
            if (request != episodeRequest) return@launch
            _uiState.value = _uiState.value.copy(episodes = episodes)
        }
    }

    private fun buildEpisodePanelEntry(session: Session, episode: BaseItemDto): EpisodePanelEntry {
        val thumbnailUrl = episodeThumbnailUrl(session, episode)
        val hasCode = episode.ParentIndexNumber != null && episode.IndexNumber != null
        return EpisodePanelEntry(
            itemId = episode.Id,
            title = episode.Name.orEmpty(),
            thumbnailUrl = thumbnailUrl,
            episodeCode = if (hasCode) com.jellio.tv.data.i18n.seasonEpisodeTag(episode.ParentIndexNumber, episode.IndexNumber) else null,
            rating = episode.CommunityRating?.let { "%.1f".format(it) },
            overview = episode.Overview,
        )
    }

    private var warmAtOpen: Set<String>? = null

    fun reportPlayStart(run: PlayStartRun) {
        val state = _uiState.value
        val sourceId = state.mediaSourceId
        val warm = warmAtOpen?.let { seen -> sourceId?.let { com.jellio.tv.data.streams.normalizeSourceId(it) in seen } }
        val record = playStartRecord(run, sourceId, state.directPlay, warm, state.startPositionTicks) ?: return
        repository.reportPlayStart(record)
    }

    fun reportStart(positionTicks: Long) {
        val id = itemId ?: return
        currentSource?.let { BadStreams.unmark(id, it) }
        val sourceId = uiState.value.mediaSourceId
        viewModelScope.launch { repository.reportPlaybackStart(id, sourceId, positionTicks) }
    }

    fun reportProgress(positionTicks: Long, isPaused: Boolean) {
        val id = itemId ?: return
        if (!hasCheckedPreviousEpisodes && positionTicks >= 50_000_000L) {
            hasCheckedPreviousEpisodes = true
            val session = playbackSession
            val item = currentItem
            if (session != null && item != null && item.Type == "Episode") {
                viewModelScope.launch(Dispatchers.IO) {
                    repository.autoCompletePreviousEpisodesIfSkipped(session.userId, item)
                }
            }
        }
        // Warmed again from 70% on, so a long episode's list is still fresh
        // when Up Next comes; the repository skips repeats for a few minutes.
        val expected = _uiState.value.expectedRuntimeMs
        val nextId = _uiState.value.upNextInfo?.itemId
        if (nextId != null && expected > 0 && positionTicks / 10_000 >= expected * 7 / 10) {
            repository.prefetchStreams(nextId)
        }
        viewModelScope.launch { repository.reportPlaybackProgress(id, uiState.value.mediaSourceId, positionTicks, isPaused) }
    }

    fun prewarmNextEpisode(session: Session) {
        val nextId = _uiState.value.upNextInfo?.itemId ?: return
        if (prewarmedPlayback?.itemId == nextId) return
        viewModelScope.launch(Dispatchers.IO) {
            catching {
                val nextItem = repository.getItemDetails(session.userId, nextId)
                val audioPref = repository.getAudioLanguagePreference(session.userId)
                val startTicks = nextItem.UserData?.PlaybackPositionTicks ?: 0L
                var target = repository.resolvePlayback(session.userId, nextId, null, startTicks)
                val preferredAudio = preferredAudioIndex(target.mediaSource, audioPref)
                if (preferredAudio != null && !target.directPlay && preferredAudio != target.mediaSource.DefaultAudioStreamIndex) {
                    target = repository.resolvePlayback(session.userId, nextId, null, startTicks, audioStreamIndex = preferredAudio)
                }
                prewarmedPlayback = PrewarmedPlayback(
                    itemId = nextId,
                    item = nextItem,
                    target = target,
                    audioPreference = audioPref,
                )
                repository.prewarmStreamUrl(target.streamUrl)
            }
        }
    }

    // The next episode's stream list and playback targets, pre-warmed once when
    // its Up Next card appears so autoplay starts instantaneously without delay.
    fun preloadUpNextSources(session: Session) {
        val nextId = _uiState.value.upNextInfo?.itemId ?: return
        viewModelScope.launch { catching { repository.getMediaSources(session.userId, nextId, reason = "up next") } }
        prewarmNextEpisode(session)
    }

    fun reportStopped(positionTicks: Long) {
        val id = itemId ?: return
        val reportedPositionTicks = if (hasCreditedRealWatch) 0 else positionTicks
        // Not viewModelScope: the player is usually closing, and the Google TV
        // row should catch up even when the viewer leaves for the launcher.
        val mediaSourceId = uiState.value.mediaSourceId
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repository.reportPlaybackStopped(id, mediaSourceId, reportedPositionTicks)
            watchNextSyncer.refresh()
        }
    }

    private var hasCreditedRealWatch = false

    fun markRealWatchComplete() {
        if (hasCreditedRealWatch) return
        hasCreditedRealWatch = true
        val id = itemId ?: return
        val userId = playbackSession?.userId ?: return
        viewModelScope.launch { catching { repository.creditRealWatch(id) } }
        viewModelScope.launch { catching { repository.setPlayed(userId, id, true) } }
    }

    private var lastReportedDurationSeconds = 0.0

    fun reportRealDurationIfUseful(candidateSeconds: Double) {
        if (candidateSeconds <= 0 || !candidateSeconds.isFinite()) return
        if (candidateSeconds < lastReportedDurationSeconds + 5) return
        lastReportedDurationSeconds = candidateSeconds
        val id = itemId ?: return
        viewModelScope.launch {
            catching { repository.reportRealDuration(id, (candidateSeconds * 10_000_000L).toLong()) }
        }
    }

    fun startSleepTimer(minutes: Int) {
        val endMs = System.currentTimeMillis() + minutes * 60_000L
        _uiState.value = _uiState.value.copy(sleepTimerEndTimeMs = endMs)
        viewModelScope.launch { catching { repository.startSleepTimer(minutes) } }
    }

    fun cancelSleepTimer() {
        _uiState.value = _uiState.value.copy(sleepTimerEndTimeMs = null)
        viewModelScope.launch { repository.cancelSleepTimer() }
    }
}

private fun parseSleepTimerEndTimeMs(endTimeUtc: String?): Long? {
    endTimeUtc ?: return null
    return runCatching { java.time.Instant.parse(endTimeUtc).toEpochMilli() }.getOrNull()
}
