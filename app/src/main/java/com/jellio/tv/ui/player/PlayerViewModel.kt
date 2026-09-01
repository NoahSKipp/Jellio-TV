package com.jellio.tv.ui.player

import com.jellio.tv.ui.i18n.Strings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.PlaybackTarget
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.MediaStreamDto
import com.jellio.tv.data.streams.BadKind
import com.jellio.tv.data.streams.FailoverCounts
import com.jellio.tv.data.streams.FailoverLoad
import com.jellio.tv.data.streams.FailoverOutcome
import com.jellio.tv.data.streams.describeFailover
import com.jellio.tv.data.streams.failoverOutcome
import com.jellio.tv.data.streams.isSourceNotReady
import com.jellio.tv.data.streams.outcomeLabel
import com.jellio.tv.data.streams.planFailover
import com.jellio.tv.data.playback.TranscodeReason
import com.jellio.tv.data.playback.VideoTranscodePolicy
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
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class SubtitleTrackUiState(
    val streamIndex: Int,
    val label: String,
    val isTextBased: Boolean,
    val url: String?,
    val language: String? = null,
    val isExternal: Boolean = false,
    val embeddedTextOrdinal: Int = -1,
)

data class PauseOverlayInfo(
    val art: List<com.jellio.tv.ui.common.ArtCandidate>,
    val logoUrl: String?,
    val title: String,
    val rating: Double?,
    val year: String?,
    val officialRating: String?,
    val isEpisode: Boolean,
    val episodeCode: String?,
    val episodeTitle: String?,
    val overview: String?,
) {
    val backdropUrl: String? get() = art.firstOrNull { !it.blurred }?.url
}

data class UpNextInfo(
    val itemId: String,
    val thumbnailUrl: String?,
    val title: String,
    val overview: String? = null,
    val seriesName: String? = null,
)

data class EpisodePanelEntry(
    val itemId: String,
    val title: String,
    val thumbnailUrl: String?,
    val episodeCode: String?,
    val rating: String?,
    val overview: String?,
)

data class AudioTrackUiState(
    val streamIndex: Int,
    val label: String,
    val language: String? = null,
)

data class SkipSegment(
    val label: String,
    val targetSeconds: Double,
)

fun activeSkipSegment(segments: IntroSkipperSegmentsDto?, currentSeconds: Double): SkipSegment? {
    segments ?: return null
    val intro = segments.Introduction
    if (intro != null && intro.End > 0 && currentSeconds >= intro.Start && currentSeconds < intro.End) {
        return SkipSegment("Skip Intro", intro.End)
    }
    val credits = segments.Credits
    if (credits != null && credits.End > 0 && currentSeconds >= credits.Start && currentSeconds < credits.End) {
        return SkipSegment("Skip Credits", credits.End)
    }
    return null
}

// Shown while an automatic retry loads: which attempt this is and the
// stream being tried.
data class FailoverNotice(val attempt: Int, val sourceLabel: String, val slowTranscode: Boolean = false)

data class PlayerUiState(
    // The title's runtime, to spot a placeholder clip far shorter than it.
    val expectedRuntimeMs: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val errorDetail: String? = null,
    // The list of other streams could not be loaded; OK tries again.
    val failoverRetry: Boolean = false,
    val streamUrl: String? = null,
    val mediaSourceId: String? = null,
    val startPositionTicks: Long = 0,
    val resumePercent: Int? = null,
    val title: String = "",
    val subtitle: String = "",
    // Poster shown by the system media controls (Google Home, the phone's remote).
    val artworkUrl: String? = null,
    // The video's frame rate, for matching the TV's refresh rate.
    val videoFrameRate: Float? = null,
    val subtitleTracks: List<SubtitleTrackUiState> = emptyList(),
    val selectedSubtitleIndex: Int? = null,
    val isSwitchingSubtitle: Boolean = false,
    val audioTracks: List<AudioTrackUiState> = emptyList(),
    val selectedAudioStreamIndex: Int? = null,
    val defaultAudioStreamIndex: Int? = null,
    val directPlay: Boolean = false,
    // The server is re-encoding this stream's video (Video transcode
    // policy Avoid or Block): give up on it sooner when no picture comes.
    val reencodingVideo: Boolean = false,
    val pauseInfo: PauseOverlayInfo? = null,
    val upNextInfo: UpNextInfo? = null,
    val skipSegments: IntroSkipperSegmentsDto? = null,
    val sleepTimerEndTimeMs: Long? = null,
    val sourceOptions: List<MediaSourceDto> = emptyList(),
    val seriesId: String? = null,
    val seasons: List<BaseItemDto> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<EpisodePanelEntry> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val toastMessage: String? = null,
    // Set while another stream is being fetched and started, so the screen
    // can say so until it actually plays.
    val switchingTo: String? = null,
    val switchingLoaded: Boolean = false,
    val toastId: Long = 0L,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val subtitleStylePreferences: SubtitleStylePreferences,
    private val syncPlay: com.jellio.tv.data.syncplay.SyncPlayManager,
    private val watchNextSyncer: com.jellio.tv.data.watchnext.WatchNextSyncer,
) : ViewModel() {
    val deviceId: String get() = repository.deviceId

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

    fun stopFailover() {
        _failover.value = null
        failingOver = true
    }

    private fun sourceLabel(source: com.jellio.tv.data.model.MediaSourceDto): String =
        source.Name?.lineSequence()?.firstOrNull()?.trim()?.ifEmpty { null } ?: com.jellio.tv.ui.i18n.Strings.get("player_another_stream")

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
            viewModelScope.launch {
                val target = runCatching {
                    repository.resolvePlayback(
                        userId = session.userId,
                        itemId = id,
                        mediaSourceId = sourceId,
                        startTimeTicks = state.startPositionTicks,
                        audioStreamIndex = state.selectedAudioStreamIndex,
                        forceTranscode = true,
                    )
                }.getOrNull()
                if (target != null) {
                    val subtitleTracks = buildSubtitleTracks(id, target.mediaSource, target.startPositionTicks)
                    _uiState.value = state.copy(
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
        viewModelScope.launch {
            var loaded: FailoverLoad<com.jellio.tv.data.model.MediaSourceDto>? = null
            if (remuxFailed && sourceId != null) {
                loaded = repository.getFailoverSources(session.userId, id, fresh = notReady)
                val playable = loaded.sources.orEmpty().filter {
                    it.Id != null && it.Id != sourceId && it.Id !in triedSourceIds && !BadStreams.isBad(id, it) && repository.canDirectPlay(it)
                }
                if (playable.isEmpty() && repository.canDirectPlay(source, ignoreAudio = true) && directRetriedSourceIds.add(sourceId)) {
                    android.util.Log.w("JellioPlayer", "server remux failed before the first frame, playing source $sourceId as is")
                    _failover.value = FailoverNotice(attempt = triedSourceIds.size + 1, sourceLabel = sourceLabel(source), slowTranscode = false)
                    val target = runCatching {
                        repository.resolvePlayback(
                            userId = session.userId,
                            itemId = id,
                            mediaSourceId = sourceId,
                            startTimeTicks = state.startPositionTicks,
                            forceDirectPlay = true,
                        )
                    }.getOrNull()
                    if (target != null) {
                        _uiState.value = state.copy(
                            streamUrl = target.streamUrl,
                            directPlay = true,
                            subtitleTracks = buildSubtitleTracks(id, target.mediaSource, 0L),
                        )
                        failingOver = false
                        return@launch
                    }
                }
            }
            if (!notReady) BadStreams.mark(id, source)

            source.Id?.let { if (it !in triedSourceIds) triedSourceIds.add(it) }
            chooseNextStream(session, PendingFailover(id, sourceId, reason, state, remuxFailed, notReady), loaded)
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
        viewModelScope.launch { chooseNextStream(session, pending, null) }
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
        // Avoid and Block try streams whose video can be copied first;
        // Block never falls back to one the server would re-encode.
        val policy = VideoTranscodePolicy
        val plan = planFailover(
            loaded.sources,
            triedSourceIds,
            id = { it.Id },
            isBad = { if (BadStreams.isBad(id, it)) BadKind.HARD else null },
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
                    com.jellio.tv.ui.i18n.Strings.get("tv_failover_list_title")
                } else {
                    "None of the streams worked"
                },
                errorDetail = when {
                    listFailed?.kind == "timeout" -> com.jellio.tv.ui.i18n.Strings.get("tv_failover_list_timeout")
                    listFailed != null -> com.jellio.tv.ui.i18n.Strings.get("tv_failover_list_error")
                    triedSourceIds.size > 1 ->
                        "${triedSourceIds.size} streams were tried for this title and none of them played. Try again later, or pick one from Change stream on the title's page."
                    else -> "This stream couldn't be played and there's no other one to try. Try again later."
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

    fun setSubtitleSize(size: String) {
        viewModelScope.launch { subtitleStylePreferences.setSize(size) }
    }

    fun setSubtitleLook(style: SubtitleStyle) {
        viewModelScope.launch { subtitleStylePreferences.setLook(style.color, style.edge, style.position) }
    }

    fun setSubtitleBackground(background: String) {
        viewModelScope.launch { subtitleStylePreferences.setBackground(background) }
    }

    private var loadedItemId: String? = null
    private var itemId: String? = null
    private var mediaSourceIdParam: String? = null
    private var playbackSession: Session? = null
    private var toastSeq = 0L

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

    fun load(session: Session, itemId: String, mediaSourceId: String?, fromFailover: Boolean = false) {
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
            lastReportedDurationSeconds = 0.0
        }
        this.itemId = itemId
        this.mediaSourceIdParam = mediaSourceId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PlayerUiState(isLoading = true)
            PlayStartTrace.mark(itemId, "player open", if (fromFailover) "failover" else "source=${mediaSourceId ?: "default"}")
            try {
                val prewarmed = if (mediaSourceId == null && !fromFailover && prewarmedPlayback?.itemId == itemId) {
                    prewarmedPlayback.also { prewarmedPlayback = null }
                } else {
                    null
                }
                // Warms the cached user and transcode policy while the item loads.
                launch { runCatching { repository.getUser(session.userId) } }
                launch { runCatching { repository.loadVideoTranscodePolicy() } }
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
                val earlyTicks = preview?.UserData?.PlaybackPositionTicks
                var targetFrom = "fresh"
                val earlyTarget = if (earlyTicks != null) {
                    async {
                        repository.takePrefetchedPlayback(itemId, mediaSourceId, earlyTicks)?.also { targetFrom = "prefetched" }
                            ?: runCatching { repository.resolvePlayback(session.userId, itemId, mediaSourceId, earlyTicks) }.getOrNull()?.also { targetFrom = "early" }
                    }
                } else {
                    null
                }
                val earlyAudio = if (prewarmed == null) async { runCatching { repository.getAudioLanguagePreference(session.userId) }.getOrNull() } else null
                val item = prewarmed?.item ?: repository.getItemDetails(session.userId, itemId)
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
                // not at this account's own resume point.
                val groupTarget = syncPlay.target.value?.takeIf { com.jellio.tv.data.syncplay.SyncPlayManager.sameId(it.itemId, itemId) }
                val startTicks = groupTarget?.startPositionTicks ?: item.UserData?.PlaybackPositionTicks ?: 0
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
                    "S${item.ParentIndexNumber} E${item.IndexNumber} · "
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
                    val others = runCatching { repository.getMediaSources(session.userId, itemId, reason = "transcode check") }.getOrDefault(emptyList())
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
                            error = com.jellio.tv.ui.i18n.Strings.get("player_stream_unplayable_title"),
                            errorDetail = com.jellio.tv.ui.i18n.Strings.format(
                                "player_stream_unplayable_transcode_off",
                                classification.reasons.joinToString(", ") { TranscodeReason.describe(it) },
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
                val subtitleTracks = buildSubtitleTracks(itemId, target.mediaSource, if (target.directPlay) 0L else target.startPositionTicks)
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

                if (item.Type == "Episode") {
                    viewModelScope.launch {
                        val next = runCatching { repository.getNextEpisode(session.userId, item) }.getOrNull()
                        if (next != null) {
                            _uiState.value = _uiState.value.copy(upNextInfo = buildUpNextInfo(session, next))
                        }
                    }
                }

                viewModelScope.launch {
                    // Credits starting in the first half are bad data; they
                    // would show Skip Credits and Up Next right at the start.
                    val segments = repository.getIntroSkipperSegments(itemId)?.let { found ->
                        val credits = found.Credits
                        if (credits != null && expectedRuntimeMs > 0 && credits.Start * 1000 < expectedRuntimeMs / 2) found.copy(Credits = null) else found
                    }
                    if (segments != null && (segments.Introduction != null || segments.Credits != null)) {
                        _uiState.value = _uiState.value.copy(skipSegments = segments)
                    }
                }

                viewModelScope.launch {
                    val status = repository.getSleepTimerStatus()
                    if (status?.Active == true) {
                        val endMs = parseSleepTimerEndTimeMs(status.EndTimeUtc)
                        if (endMs != null) {
                            _uiState.value = _uiState.value.copy(sleepTimerEndTimeMs = endMs)
                        }
                    }
                }

                viewModelScope.launch {
                    val sources = runCatching { repository.getMediaSources(session.userId, itemId, reason = "player") }.getOrNull()
                    if (sources != null && sources.size > 1) {
                        _uiState.value = _uiState.value.copy(sourceOptions = sources)
                    }
                }

                val seriesId = item.SeriesId
                if (isEpisode && seriesId != null) {
                    _uiState.value = _uiState.value.copy(seriesId = seriesId)
                    viewModelScope.launch {
                        val seasons = runCatching { repository.getSeasons(seriesId, session.userId) }.getOrDefault(emptyList())
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

    private fun buildUpNextInfo(session: Session, episode: BaseItemDto): UpNextInfo {
        val thumbTag = episode.ImageTags?.get("Primary") ?: episode.ParentThumbImageTag
        val thumbnailUrl = thumbTag?.let { repository.imageUrl(session.serverAddress, episode.Id, it, "Primary", 400, session.accessToken) }
        val title = if (episode.IndexNumber != null && episode.ParentIndexNumber != null) {
            "S${episode.ParentIndexNumber} E${episode.IndexNumber} · ${episode.Name.orEmpty()}"
        } else {
            episode.Name.orEmpty()
        }
        return UpNextInfo(
            itemId = episode.Id,
            thumbnailUrl = thumbnailUrl,
            title = title,
            overview = episode.Overview?.takeIf { it.isNotBlank() },
            seriesName = episode.SeriesName,
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
            episodeCode = if (isEpisode && hasEpisodeCode) "S${item.ParentIndexNumber} E${item.IndexNumber}" else null,
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
                val subtitleTracks = buildSubtitleTracks(id, target.mediaSource, if (target.directPlay) 0L else target.startPositionTicks)
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
                        com.jellio.tv.ui.i18n.Strings.get("player_start_over_unavailable")
                    } else {
                        "Could not start over: ${err.message ?: err}"
                    },
                )
            }
        }
    }

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
                val label = stream.DisplayTitle ?: stream.Language ?: com.jellio.tv.ui.i18n.Strings.get("player_subtitle_fallback")
                val url = if (isText) {
                    runCatching { repository.buildSubtitleUrl(itemId, resolvedMediaSourceId, stream, offsetTicks) }.getOrNull()
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
        if (VideoTranscodePolicy.avoiding) " (slow: burns into video)" else " (image)"

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
        val language = stream.Language?.uppercase() ?: stream.DisplayTitle ?: com.jellio.tv.ui.i18n.Strings.get("lang_unknown")
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

    private fun sameLanguage(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        if (a.equals(b, ignoreCase = true)) return true
        fun short(code: String): String = runCatching { java.util.Locale.forLanguageTag(code).language }.getOrDefault(code).lowercase()
        val left = short(a)
        val right = short(b)
        return left.isNotEmpty() && left == right ||
            runCatching { java.util.Locale(a).isO3Language.equals(java.util.Locale(b).isO3Language, ignoreCase = true) }.getOrDefault(false)
    }

    fun selectBurnedInSubtitle(session: Session, streamIndex: Int, currentPositionTicks: Long) {
        val id = itemId ?: return
        val requestedLabel = _uiState.value.subtitleTracks.firstOrNull { it.streamIndex == streamIndex }
            ?.label?.removeSuffix(imageSubtitleSuffix()) ?: com.jellio.tv.ui.i18n.Strings.get("player_subtitle_fallback")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitchingSubtitle = true)
            try {
                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    mediaSourceIdParam,
                    currentPositionTicks,
                    burnInSubtitleStreamIndex = streamIndex,
                    audioStreamIndex = _uiState.value.selectedAudioStreamIndex,
                )
                val subtitleTracks = buildSubtitleTracks(id, target.mediaSource, if (target.directPlay) 0L else target.startPositionTicks)
                _uiState.value = _uiState.value.copy(
                    isSwitchingSubtitle = false,
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = null,
                    selectedSubtitleIndex = streamIndex,
                    subtitleTracks = subtitleTracks,
                    directPlay = target.directPlay,
                )
                rememberSubtitleChoice(streamIndex)
                showToast(com.jellio.tv.ui.i18n.Strings.format("tv_requested_burned_in", requestedLabel))
            } catch (err: Exception) {
                _uiState.value = _uiState.value.copy(isSwitchingSubtitle = false)
                showToast(
                    if (err is IllegalStateException) {
                        com.jellio.tv.ui.i18n.Strings.get("player_subtitle_unavailable")
                    } else {
                        com.jellio.tv.ui.i18n.Strings.format("player_subtitle_switch_failed", err.message ?: "")
                    },
                )
            }
        }
    }

    fun switchAudioTrack(session: Session, streamIndex: Int, currentPositionTicks: Long) {
        val id = itemId ?: return
        val label = _uiState.value.audioTracks.firstOrNull { it.streamIndex == streamIndex }?.label ?: "audio"
        if (_uiState.value.directPlay && currentSource?.let { repository.canDirectPlay(it, streamIndex) } != false) {
            _uiState.value = _uiState.value.copy(selectedAudioStreamIndex = streamIndex)
            showToast(com.jellio.tv.ui.i18n.Strings.format("tv_audio_label", label))
            return
        }
        showToast(com.jellio.tv.ui.i18n.Strings.format("tv_switching_to", label))
        viewModelScope.launch {
            try {
                val currentSubIndex = _uiState.value.selectedSubtitleIndex
                val isBurnedIn = currentSubIndex != null &&
                    _uiState.value.subtitleTracks.firstOrNull { it.streamIndex == currentSubIndex }?.isTextBased == false
                val burnInSub = if (isBurnedIn) currentSubIndex else null

                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    mediaSourceIdParam,
                    currentPositionTicks,
                    burnInSubtitleStreamIndex = burnInSub,
                    audioStreamIndex = streamIndex,
                )
                val subtitleTracks = buildSubtitleTracks(id, target.mediaSource, if (target.directPlay) 0L else target.startPositionTicks)
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
                showToast(com.jellio.tv.ui.i18n.Strings.format("tv_requested_reloading", label))
            } catch (err: Exception) {
                showToast(
                    if (err is IllegalStateException) {
                        com.jellio.tv.ui.i18n.Strings.get("player_audio_unavailable")
                    } else {
                        com.jellio.tv.ui.i18n.Strings.format("player_audio_switch_failed", err.message ?: "")
                    },
                )
            }
        }
    }

    fun reloadAt(session: Session, positionTicks: Long) {
        val id = itemId ?: return
        viewModelScope.launch {
            try {
                val currentSubIndex = _uiState.value.selectedSubtitleIndex
                val isBurnedIn = currentSubIndex != null &&
                    _uiState.value.subtitleTracks.firstOrNull { it.streamIndex == currentSubIndex }?.isTextBased == false
                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    mediaSourceIdParam,
                    positionTicks,
                    burnInSubtitleStreamIndex = if (isBurnedIn) currentSubIndex else null,
                    audioStreamIndex = _uiState.value.selectedAudioStreamIndex,
                )
                _uiState.value = _uiState.value.copy(
                    streamUrl = target.streamUrl,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = null,
                    directPlay = target.directPlay,
                    subtitleTracks = buildSubtitleTracks(id, target.mediaSource, if (target.directPlay) 0L else target.startPositionTicks),
                )
            } catch (err: Exception) {
                showToast(com.jellio.tv.ui.i18n.Strings.get("tv_seek_failed"))
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
            isBad = { if (BadStreams.isBad(id, it)) BadKind.HARD else null },
            byLanguage = { orderByLanguage(it, language) },
            needsReencode = if (policy.avoiding) { s -> policy.needsReencode(s) } else null,
            blockReencode = policy.blocking,
        )
        val outcome = failoverOutcome(loaded, plan) { it.Id }
        logFailoverDecision(PendingFailover(id, current, "buffering switch", _uiState.value, false), plan.counts, loaded, outcome)
        val next = plan.ordered.firstOrNull() ?: return false
        currentSource?.let { BadStreams.mark(id, it) }
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
                val subtitleTracks = buildSubtitleTracks(id, target.mediaSource, if (target.directPlay) 0L else target.startPositionTicks)
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
                        com.jellio.tv.ui.i18n.Strings.get("player_stream_unavailable")
                    } else {
                        com.jellio.tv.ui.i18n.Strings.get("player_stream_switch_failed")
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
        _uiState.value = _uiState.value.copy(selectedSeasonId = seasonId, isLoadingEpisodes = true)
        viewModelScope.launch {
            val episodes = runCatching { repository.getEpisodes(seriesId, session.userId, seasonId) }
                .getOrDefault(emptyList())
                .map { buildEpisodePanelEntry(session, it) }
            if (request != episodeRequest) return@launch
            _uiState.value = _uiState.value.copy(episodes = episodes, isLoadingEpisodes = false)
        }
    }

    private fun buildEpisodePanelEntry(session: Session, episode: BaseItemDto): EpisodePanelEntry {
        val thumbTag = episode.ImageTags?.get("Primary") ?: episode.ParentThumbImageTag
        val thumbnailUrl = thumbTag?.let { repository.imageUrl(session.serverAddress, episode.Id, it, "Primary", 400, session.accessToken) }
        val hasCode = episode.ParentIndexNumber != null && episode.IndexNumber != null
        return EpisodePanelEntry(
            itemId = episode.Id,
            title = episode.Name.orEmpty(),
            thumbnailUrl = thumbnailUrl,
            episodeCode = if (hasCode) com.jellio.tv.ui.i18n.seasonEpisodeTag(episode.ParentIndexNumber, episode.IndexNumber) else null,
            rating = episode.CommunityRating?.let { "%.1f".format(it) },
            overview = episode.Overview,
        )
    }

    fun reportStart(positionTicks: Long) {
        val id = itemId ?: return
        currentSource?.let { BadStreams.unmark(id, it) }
        val sourceId = uiState.value.mediaSourceId
        viewModelScope.launch { repository.reportPlaybackStart(id, sourceId, positionTicks) }
    }

    fun reportProgress(positionTicks: Long, isPaused: Boolean) {
        val id = itemId ?: return
        // Warmed from 70% on, late enough to still be fresh when Up Next
        // comes; the repository skips repeats for a few minutes.
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
            runCatching {
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
        viewModelScope.launch { runCatching { repository.getMediaSources(session.userId, nextId, reason = "up next") } }
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
        viewModelScope.launch { runCatching { repository.creditRealWatch(id) } }
        viewModelScope.launch { runCatching { repository.setPlayed(userId, id, true) } }
    }

    private var lastReportedDurationSeconds = 0.0

    fun reportRealDurationIfUseful(candidateSeconds: Double) {
        if (candidateSeconds <= 0 || !candidateSeconds.isFinite()) return
        if (candidateSeconds < lastReportedDurationSeconds + 5) return
        lastReportedDurationSeconds = candidateSeconds
        val id = itemId ?: return
        viewModelScope.launch {
            runCatching { repository.reportRealDuration(id, (candidateSeconds * 10_000_000L).toLong()) }
        }
    }

    fun startSleepTimer(minutes: Int) {
        val endMs = System.currentTimeMillis() + minutes * 60_000L
        _uiState.value = _uiState.value.copy(sleepTimerEndTimeMs = endMs)
        viewModelScope.launch { runCatching { repository.startSleepTimer(minutes) } }
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

// "ger", "deu", "de" and "German" all mean the same language: compared as
// ISO 639-2 codes so the server's and the player's spellings match.
internal fun normalizeLanguage(code: String?): String? {
    val raw = code?.trim()?.lowercase()?.ifBlank { null } ?: return null
    val aliases = mapOf("ger" to "deu", "fre" to "fra", "dut" to "nld", "chi" to "zho", "cze" to "ces", "gre" to "ell", "per" to "fas", "rum" to "ron", "slo" to "slk", "wel" to "cym", "arm" to "hye", "baq" to "eus", "geo" to "kat", "ice" to "isl", "mac" to "mkd", "may" to "msa", "bur" to "mya", "alb" to "sqi", "tib" to "bod")
    aliases[raw]?.let { return it }
    return runCatching { java.util.Locale.forLanguageTag(raw.substringBefore('-')).isO3Language }.getOrNull()?.ifBlank { null } ?: raw
}
