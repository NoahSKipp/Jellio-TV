package com.jellio.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.PlaybackTarget
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.MediaStreamDto
import com.jellio.tv.data.playback.TranscodeReason
import com.jellio.tv.data.playback.VideoTranscodePolicy
import com.jellio.tv.data.playback.videoInfoOf
import com.jellio.tv.data.model.SubtitleStyle
import com.jellio.tv.data.model.TrickplayInfoDto
import com.jellio.tv.data.prefs.SubtitleStylePreferences
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

// Real fields screens/player.js's own subtitle popover reads off each
// real MediaStream (Index/DisplayTitle/Language/IsTextSubtitleStream),
// url only ever populated for a real text based track this player can
// side-load directly (buildSubtitleUrl's own real WebVTT endpoint); an
// image based one (PGS, VobSub) has none, selecting it instead needs a
// real burned-in transcode reload.
data class SubtitleTrackUiState(
    val streamIndex: Int,
    val label: String,
    val isTextBased: Boolean,
    val url: String?,
    val language: String? = null,
    val isExternal: Boolean = false,
    val embeddedTextOrdinal: Int = -1,
)

// Real port of screens/player.js's own buildResumePrompt()/
// buildPauseOverlay() data needs: a real choice offered once, over the
// paused frame already sitting at the saved position, instead of just
// always seeking straight there with no way back to the real start.
data class PauseOverlayInfo(
    val backdropUrl: String?,
    val logoUrl: String?,
    val title: String,
    val rating: String?,
    val year: String?,
    val officialRating: String?,
    val isEpisode: Boolean,
    val episodeCode: String?,
    val episodeTitle: String?,
    val overview: String?,
)

// Real port of screens/player.js's own buildUpNextOverlay() data
// needs. thumbnailUrl always requested as a real Primary image
// (getImageUrl(episode.Id, 'Primary', ...) in that file, unconditional
// even when the tag it carries actually came from ParentThumbImageTag
// rather than the episode's own ImageTags.Primary), not "fixed" to
// match the tag's own real source.
data class UpNextInfo(
    val itemId: String,
    val thumbnailUrl: String?,
    val title: String,
    val overview: String? = null,
    val seriesName: String? = null,
)

// Real port of screens/player.js's own buildEpisodeRow(): thumbnailUrl
// always a real Primary image (ImageTags.Primary or, absent that, the
// season/series own ParentThumbImageTag), same real fallback that
// function's own thumbTag line documents; rating pre-formatted to one
// decimal the same way that row's own CommunityRating.toFixed(1) is.
data class EpisodePanelEntry(
    val itemId: String,
    val title: String,
    val thumbnailUrl: String?,
    val episodeCode: String?,
    val rating: String?,
    val overview: String?,
)

// Real port of screens/player.js's own showScrubPreview(): tileUrl one
// real sheet, col/row that sheet's own cell this exact millisecond
// falls in, frameWidth/frameHeight one real cell's own pixel size,
// sheetWidth/sheetHeight the whole real sheet's own pixel size, same
// real values that function's own backgroundSize/backgroundPosition
// CSS math already needs.
data class TrickplayFrame(
    val tileUrl: String,
    val col: Int,
    val row: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val sheetWidth: Int,
    val sheetHeight: Int,
)

// Real port of screens/player.js's own audioStreamLabel(): a real
// track's own Language (uppercased) or DisplayTitle when there is no
// Language, Codec and ChannelLayout appended after a real "·" when
// either one is actually present, same real fallback chain that
// function's own comment documents.
data class AudioTrackUiState(
    val streamIndex: Int,
    val label: String,
    val language: String? = null,
)

// Real port of screens/player.js's own activeSkipSegment() return
// shape: targetSeconds is the segment's own End, seekToAbsoluteSeconds'
// own real target once the reader presses the button this label is for.
data class SkipSegment(
    val label: String,
    val targetSeconds: Double,
)

// Real port of screens/player.js's own activeSkipSegment(): Skip Intro
// wins over Skip Credits at the same instant, same real order that
// file's own if/else chain checks them in (a title's own Introduction
// and Credits segments never actually overlap in practice, but this
// keeps the exact same real precedence regardless).
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
    val streamUrl: String? = null,
    val mediaSourceId: String? = null,
    val startPositionTicks: Long = 0,
    val resumePercent: Int? = null,
    val title: String = "",
    val subtitle: String = "",
    val subtitleTracks: List<SubtitleTrackUiState> = emptyList(),
    val selectedSubtitleIndex: Int? = null,
    val isSwitchingSubtitle: Boolean = false,
    val audioTracks: List<AudioTrackUiState> = emptyList(),
    // null means the real MediaSource's own DefaultAudioStreamIndex is
    // active, same real meaning screens/player.js's own
    // currentAudioStreamIndex == null carries before a reader ever
    // actually picks a different one.
    val selectedAudioStreamIndex: Int? = null,
    val defaultAudioStreamIndex: Int? = null,
    // Whether streamUrl is currently a Direct Play (Static=true) source
    // rather than a transcode - switchAudioTrack() below reads this to
    // decide whether a track change can happen as a plain local
    // ExoPlayer track selection (PlayerScreen's own LaunchedEffect on
    // selectedAudioStreamIndex/directPlay) or needs a real fresh
    // PlaybackInfo negotiation the way a transcoded source always did.
    val directPlay: Boolean = false,
    // The server is re-encoding this stream's video (Video transcode
    // policy Avoid or Block): give up on it sooner when no picture comes.
    val reencodingVideo: Boolean = false,
    val pauseInfo: PauseOverlayInfo? = null,
    val upNextInfo: UpNextInfo? = null,
    val skipSegments: IntroSkipperSegmentsDto? = null,
    // Device epoch millis, converted once here from the real server's
    // own EndTimeUtc so PlayerSurface's own local enforcement (see
    // SleepTimerStatusDto's own header comment for why that has to
    // exist) never has to parse a timestamp itself.
    val sleepTimerEndTimeMs: Long? = null,
    // Only ever populated with more than one entry, same real gating
    // screens/player.js's own getMediaSources(itemId).then() applies to
    // sourceButton.disabled: a single-source title has nothing to
    // switch to, so the Sources button stays hidden rather than opening
    // onto a list of one.
    val sourceOptions: List<MediaSourceDto> = emptyList(),
    // Only ever populated for a real Episode with a real SeriesId, same
    // real gating screens/player.js's own getSeasons(item.SeriesId)
    // .then() applies to episodesButton.disabled: a Movie has nothing
    // to browse to here.
    val seriesId: String? = null,
    val seasons: List<BaseItemDto> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<EpisodePanelEntry> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    // True only when this MediaSource actually carries a real
    // generated Trickplay sheet, same real gate screens/player.js's
    // own pickTrickplayInfo() != null already checks before that
    // file's own scrub preview ever gets built at all.
    val hasTrickplay: Boolean = false,
    // Real port of screens/player.js's own showPlayerToast(): a short
    // lived status/error line surfacing failures the four re-
    // negotiating actions below already had no visible feedback for.
    // toastId bumps on every call so PlayerScreen's own auto-dismiss
    // effect restarts even when the exact same message fires twice in
    // a row, same real behaviour that file's own clearTimeout + fresh
    // setTimeout on every call already gets for free.
    val toastMessage: String? = null,
    // Set while another stream is being fetched and started, so the screen
    // can say so until it actually plays.
    val switchingTo: String? = null,
    val toastId: Long = 0L,
)

// The real mechanism runtime/api.js's own getPlaybackInfo()/
// buildStreamUrl() already document: negotiate once here, hand the
// player a plain stream URL it can just open, then report real
// progress through the same three Sessions/Playing endpoints every
// real Jellyfin client uses, not a second tracking scheme this app
// invented.
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val subtitleStylePreferences: SubtitleStylePreferences,
    private val syncPlay: com.jellio.tv.data.syncplay.SyncPlayManager,
    private val watchNextSyncer: com.jellio.tv.data.watchnext.WatchNextSyncer,
) : ViewModel() {

    // ---- Stream failover -------------------------------------------------
    // A stream that errors before its first frame, shows no picture, or
    // turns out to be a placeholder clip is remembered as bad and the next
    // one is loaded by itself: same language as this attempt first, else
    // the reader's audio language. Not in a Group Watch.
    private val _failover = MutableStateFlow<FailoverNotice?>(null)
    val failover: StateFlow<FailoverNotice?> = _failover.asStateFlow()
    private val triedSourceIds = mutableListOf<String>()
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
        source.Name?.lineSequence()?.firstOrNull()?.trim()?.ifEmpty { null } ?: "another stream"

    private fun hasAudioLanguage(source: com.jellio.tv.data.model.MediaSourceDto, language: String): Boolean =
        source.MediaStreams.orEmpty().any { it.Type == "Audio" && sameLanguage(it.Language, language) }

    fun streamFailed(session: Session, reason: String) {
        if (failingOver || syncPlay.group.value != null) return
        val id = itemId ?: return
        val source = currentSource ?: return
        val state = _uiState.value
        failingOver = true
        android.util.Log.w("JellioPlayer", "stream failed ($reason), trying the next one")
        viewModelScope.launch {
            BadStreams.mark(id, source)

            source.Id?.let { if (it !in triedSourceIds) triedSourceIds.add(it) }
            val language = failoverLanguage
                ?: state.audioTracks.firstOrNull { it.streamIndex == (state.selectedAudioStreamIndex ?: state.defaultAudioStreamIndex) }?.language
                ?: source.MediaStreams.orEmpty().firstOrNull { it.Type == "Audio" }?.Language
                ?: audioLanguagePreference
            failoverLanguage = language
            val sources = runCatching { repository.getMediaSources(session.userId, id, reason = "failover") }.getOrDefault(emptyList())
            val candidates = sources.filter { it.Id != null && it.Id !in triedSourceIds && !BadStreams.isBad(id, it) }
            fun pick(list: List<com.jellio.tv.data.model.MediaSourceDto>) = if (language != null) {
                list.firstOrNull { hasAudioLanguage(it, language) } ?: list.firstOrNull()
            } else {
                list.firstOrNull()
            }
            // Avoid and Block try streams whose video can be copied first;
            // Block never falls back to one the server would re-encode.
            val policy = VideoTranscodePolicy
            val next = if (policy.avoiding) {
                val (reencode, copyable) = candidates.partition { policy.needsReencode(it) }
                pick(copyable) ?: if (policy.blocking) null else pick(reencode)
            } else {
                pick(candidates)
            }
            if (next == null) {
                _failover.value = null
                _uiState.value = PlayerUiState(
                    isLoading = false,
                    error = "None of the streams worked",
                    errorDetail = if (triedSourceIds.size > 1) {
                        "${triedSourceIds.size} streams were tried for this title and none of them played. Try again later, or pick one from Change stream on the title's page."
                    } else {
                        "This stream couldn't be played and there's no other one to try. Try again later."
                    },
                    title = state.title,
                    pauseInfo = state.pauseInfo,
                )
                failingOver = false
                return@launch
            }
            _failover.value = FailoverNotice(
                attempt = triedSourceIds.size + 1,
                sourceLabel = sourceLabel(next),
                slowTranscode = state.reencodingVideo && reason.startsWith("no picture"),
            )
            failingOver = false
            currentSource = next
            load(session, id, next.Id, fromFailover = true)
        }
    }
    // -------------------------------------------------------------------------

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Kept out of PlayerUiState on purpose: load() below fully replaces
    // that state object on every real title change (a fresh
    // PlayerUiState(...), not a .copy()), but this is a real persisted
    // reader preference (SubtitleStylePreferences' own header comment),
    // not per-title playback state, so it has to survive that reset.
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

    fun setSubtitleBackground(background: String) {
        viewModelScope.launch { subtitleStylePreferences.setBackground(background) }
    }

    private var loadedItemId: String? = null
    private var itemId: String? = null
    private var mediaSourceIdParam: String? = null
    // Picked once here, same real reasoning switchSource() above never
    // rebuilds its own scrub preview handlers either (screens/player.js
    // has no equivalent recompute step for a source that switches out
    // from under an already built one), not kept in sync with a later
    // source switch.
    private var trickplaySession: Session? = null
    private var trickplayInfo: TrickplayInfoDto? = null
    private var trickplayMediaSourceId: String? = null
    private var trickplayRequestedForSourceId: String? = null
    private var toastSeq = 0L

    fun showToast(message: String) {
        toastSeq++
        _uiState.value = _uiState.value.copy(toastMessage = message, toastId = toastSeq)
    }

    // Called by PlayerScreen's own auto-dismiss effect once its delay
    // elapses; the id guard means a toast that already got replaced by
    // a newer one in the meantime does not get incorrectly cleared out
    // from under it.
    fun clearSwitching() {
        if (_uiState.value.switchingTo != null) _uiState.value = _uiState.value.copy(switchingTo = null)
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
            triedSourceIds.clear()
            failoverLanguage = null

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
            try {
                val prewarmed = if (mediaSourceId == null && !fromFailover && prewarmedPlayback?.itemId == itemId) {
                    prewarmedPlayback.also { prewarmedPlayback = null }
                } else {
                    null
                }
                val item = prewarmed?.item ?: repository.getItemDetails(session.userId, itemId)
                val isEpisode = item.Type == "Episode" && item.SeriesName != null
                // The title's own backdrop and logo go up while the stream
                // is still being prepared, not a blank screen.
                _uiState.value = PlayerUiState(
                    isLoading = true,
                    title = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(),
                    pauseInfo = buildPauseOverlayInfo(session, item, isEpisode),
                )
                val startTicks = item.UserData?.PlaybackPositionTicks ?: 0
                val audioPreference = prewarmed?.audioPreference ?: repository.getAudioLanguagePreference(session.userId)
                audioLanguagePreference = audioPreference
                var target = prewarmed?.target ?: repository.resolvePlayback(session.userId, itemId, mediaSourceId, startTicks)
                // The reader's audio language wins over the file's default
                // when the file has it; a transcode has to be asked for it.
                val preferredAudio = preferredAudioIndex(target.mediaSource, audioPreference)
                if (prewarmed == null && preferredAudio != null && !target.directPlay && preferredAudio != target.mediaSource.DefaultAudioStreamIndex) {
                    target = repository.resolvePlayback(session.userId, itemId, mediaSourceId, startTicks, audioStreamIndex = preferredAudio)
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
                            error = "This stream can't be played here",
                            errorDetail = "It needs the server to re-encode the video (" +
                                classification.reasons.joinToString(", ") { TranscodeReason.describe(it) } +
                                "), which is turned off on this server, and no other stream for it can be played as is.",
                            title = _uiState.value.title,
                            pauseInfo = _uiState.value.pauseInfo,
                        )
                        return@launch
                    }
                }

                currentSource = target.mediaSource
                val expectedRuntimeMs = (item.RunTimeTicks ?: 0L) / 10_000
                // Do not abort before playback starts based on cached
                // target.mediaSource.RunTimeTicks: let ExoPlayer open the
                // stream first and rely on actual playback duration once
                // STATE_READY is reached.
                val subtitleTracks = buildSubtitleTracks(itemId, target.mediaSource, if (target.directPlay) 0L else target.startPositionTicks)
                subtitleSeriesId = if (isEpisode) item.SeriesId else null
                val initialSubtitle = initialSubtitleIndex(
                    subtitleTracks,
                    subtitleSeriesId,
                    repository.getSubtitleLanguagePreference(session.userId),
                )
                val audioTracks = buildAudioTracks(target.mediaSource)
                val resumePercent = item.UserData?.PlayedPercentage?.takeIf { startTicks > 0 && !fromFailover }?.roundToInt()

                trickplaySession = session
                trickplayInfo = null
                trickplayMediaSourceId = target.mediaSource.Id
                trickplayRequestedForSourceId = null

                _uiState.value = PlayerUiState(
                    expectedRuntimeMs = expectedRuntimeMs,
                    isLoading = false,
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    hasTrickplay = false,
                    resumePercent = resumePercent,
                    title = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(),
                    subtitle = if (isEpisode) episodeCode + item.Name.orEmpty() else "",
                    subtitleTracks = subtitleTracks,
                    selectedSubtitleIndex = initialSubtitle,
                    pauseInfo = buildPauseOverlayInfo(session, item, isEpisode),
                    audioTracks = audioTracks,
                    defaultAudioStreamIndex = target.mediaSource.DefaultAudioStreamIndex,
                    selectedAudioStreamIndex = preferredAudio,
                    directPlay = target.directPlay,
                    reencodingVideo = VideoTranscodePolicy.avoiding && classification.needsVideoTranscode,
                )

                // Real port of screens/player.js's own real fire-and-
                // forget getNextEpisode(item).then(...): resolved
                // alongside real playback rather than blocking it, the
                // overlay itself only actually shown once
                // shouldShowUpNextNow's own real timing condition is
                // met, far later than this.
                if (item.Type == "Episode") {
                    viewModelScope.launch {
                        val next = runCatching { repository.getNextEpisode(session.userId, item) }.getOrNull()
                        if (next != null) {
                            _uiState.value = _uiState.value.copy(upNextInfo = buildUpNextInfo(session, next))
                            // Real Nuvio-competitive gap, not a
                            // hypothetical one: the up next card itself
                            // doesn't show until shouldShowUpNextNow's
                            // own real timing condition near the very
                            // end of this episode, but the real id it
                            // needs is already known right here, at the
                            // very start of it. Firing this now hands it
                            // this whole episode's own runtime as lead
                            // time - far more than a poster's own
                            // hover/focus debounce ever gets - so a real
                            // binge watcher never sees a loading gap
                            // between episodes at all, matching
                            // AIOStreams' own precacheNextEpisode setting
                            // doing the equivalent one layer further
                            // upstream.
                            repository.prefetchStreams(next.Id)
                        }
                    }
                }

                // Real port of screens/player.js's own real fire-and-
                // forget getIntroSkipperSegments(itemId).then(...): not
                // gated on item.Type, that file's own real endpoint
                // works for a Movie too despite its own "/Episode/"
                // route name. Only assigned when at least one real
                // segment key actually came back, same real truthy
                // check that file's own .then() callback runs.
                viewModelScope.launch {
                    val segments = repository.getIntroSkipperSegments(itemId)
                    if (segments != null && (segments.Introduction != null || segments.Credits != null)) {
                        _uiState.value = _uiState.value.copy(skipSegments = segments)
                    }
                }

                // Real port of screens/player.js's own real fire-and-
                // forget getSleepTimerStatus().then(...): a real timer
                // started before this exact title was opened (still
                // running server side against this same user/device
                // pair) picks back up here rather than reading as
                // cancelled just because a fresh PlayerViewModel state
                // object was built for this real title.
                viewModelScope.launch {
                    val status = repository.getSleepTimerStatus()
                    if (status?.Active == true) {
                        val endMs = parseSleepTimerEndTimeMs(status.EndTimeUtc)
                        if (endMs != null) {
                            _uiState.value = _uiState.value.copy(sleepTimerEndTimeMs = endMs)
                        }
                    }
                }

                // Real port of screens/player.js's own real fire-and-
                // forget getMediaSources(itemId).then(...): only wired
                // up once there is a genuine choice behind it, same real
                // gating that file's own sources.length > 1 check
                // applies before ever enabling sourceButton.
                viewModelScope.launch {
                    val sources = runCatching { repository.getMediaSources(session.userId, itemId, reason = "player") }.getOrNull()
                    if (sources != null && sources.size > 1) {
                        _uiState.value = _uiState.value.copy(sourceOptions = sources)
                    }
                }

                // Real port of screens/player.js's own real fire-and-
                // forget getSeasons(item.SeriesId).then(...): only wired
                // up for a real Episode with a real SeriesId, same real
                // gating that file's own isEpisodeItem && item.SeriesId
                // check applies before ever enabling episodesButton.
                // Specials sorted last, same real isSpecialsSeason()
                // convention screens/detail.js's own season tabs already
                // settled on, then that file's own default season picked
                // (this Episode's own SeasonId, or the first real one)
                // loaded immediately, same real behaviour that function's
                // own trailing if (!episodeList.children.length) block
                // guarantees.
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
                if (syncPlay.group.value == null) {
                    if (currentSource == null) {
                        currentSource = com.jellio.tv.data.model.MediaSourceDto(Id = mediaSourceId ?: itemId)
                    }
                    streamFailed(session, "could not start: ${err.message}")
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

    // Real port of screens/player.js's own seriesAwareArtworkUrl(): an
    // Episode's own real BackdropImageTags/ImageTags.Primary are the
    // episode's own thumbnail, not the show's own real artwork every
    // other real pause screen shows here, so SeriesId/
    // ParentBackdropImageTags/SeriesPrimaryImageTag are what this reads
    // instead for one; a movie has no series to prefer over its own.
    private fun buildPauseOverlayInfo(session: Session, item: BaseItemDto, isEpisode: Boolean): PauseOverlayInfo {
        val seriesId = item.SeriesId
        val artId = if (isEpisode && seriesId != null) seriesId else item.Id
        val backdropTag = if (isEpisode) item.ParentBackdropImageTags?.firstOrNull() else item.BackdropImageTags?.firstOrNull()
        val primaryTag = if (isEpisode) item.SeriesPrimaryImageTag else item.ImageTags?.get("Primary")
        val tag = backdropTag ?: primaryTag
        val backdropUrl = tag?.let {
            repository.imageUrl(session.serverAddress, artId, it, if (backdropTag != null) "Backdrop" else "Primary", 1600, session.accessToken)
        }
        val logoTag = if (isEpisode) item.ParentLogoImageTag else item.ImageTags?.get("Logo")
        val logoArtId = if (isEpisode && item.ParentLogoItemId != null) item.ParentLogoItemId else artId
        val logoUrl = logoTag?.let {
            repository.imageUrl(session.serverAddress, logoArtId, it, "Logo", 800, session.accessToken)
        }
        val hasEpisodeCode = item.ParentIndexNumber != null && item.IndexNumber != null
        return PauseOverlayInfo(
            backdropUrl = backdropUrl,
            logoUrl = logoUrl,
            title = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(),
            rating = item.CommunityRating?.let { "%.1f ★".format(it) },
            year = item.ProductionYear?.toString(),
            officialRating = item.OfficialRating,
            isEpisode = isEpisode,
            episodeCode = if (isEpisode && hasEpisodeCode) "S${item.ParentIndexNumber}E${item.IndexNumber}" else null,
            episodeTitle = if (isEpisode) item.Name else null,
            overview = item.Overview,
        )
    }

    // Real port of screens/player.js's own Start Over button: video.
    // currentTime = 0 alone used to just resume anyway there, real
    // feedback traced to a forced transcode only ever encoding forward
    // from its own saved start position, nothing earlier ever existing
    // in that stream at all. A fresh real PlaybackInfo negotiation at
    // ticks 0 instead asks the server for a real stream that actually
    // starts there, same real reason selectBurnedInSubtitle above needs
    // one too.
    fun restart(session: Session) {
        val id = itemId ?: return
        viewModelScope.launch {
            try {
                // Real screens/player.js's own Start Over handler still
                // passes its own currentAudioStreamIndex into this same
                // real negotiation: a reader's own chosen audio track
                // survives a real restart, same real persistence that
                // file's own comment already documents for a seek.
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
                // Real port of screens/player.js's own Start Over catch
                // block: that file's own real negotiated == null check
                // ('Could not start over, that stream is no longer
                // available.') has no separate branch here since
                // resolvePlayback() already throws for exactly that
                // case, IllegalStateException specifically for it; any
                // other exception type is the real network/server
                // failure that function's own generic message covers.
                // The existing stream just keeps playing either way,
                // same real fallback that file's own early return leaves
                // in place too.
                showToast(
                    if (err is IllegalStateException) {
                        "Could not start over, that stream is no longer available."
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
                val label = stream.DisplayTitle ?: stream.Language ?: "Subtitle"
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
        val language = stream.Language?.uppercase() ?: stream.DisplayTitle ?: "Unknown"
        val parts = listOfNotNull(
            stream.Codec?.uppercase()?.takeIf { it.isNotEmpty() },
            stream.ChannelLayout?.takeIf { it.isNotEmpty() },
        )
        return if (parts.isNotEmpty()) "$language · ${parts.joinToString(" ")}" else language
    }

    // Off, or a real text based track: no reload needed, ExoPlayer
    // already has every text track declared as a real
    // MediaItem.SubtitleConfiguration from the very first prepare(),
    // this only changes which one the player currently selects.
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

    // Which text track to start with: what was picked for this show last
    // time, else the reader's default subtitle language, else none. Image
    // tracks need a burned-in transcode, so they're never picked here.
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

    // An image based subtitle (screens/player.js's own real
    // selectBurnedInSubtitle()) has no WebVTT form to side-load,
    // nothing this player can render as a real text track: the only
    // way to show one at all is a fresh PlaybackInfo negotiation
    // forcing a real transcode with that stream burned directly into
    // the video, resuming from wherever real playback currently sits.
    fun selectBurnedInSubtitle(session: Session, streamIndex: Int, currentPositionTicks: Long) {
        val id = itemId ?: return
        val requestedLabel = _uiState.value.subtitleTracks.firstOrNull { it.streamIndex == streamIndex }
            ?.label?.removeSuffix(imageSubtitleSuffix()) ?: "subtitle"
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
                showToast("Requested $requestedLabel (burned in), reloading…")
            } catch (err: Exception) {
                _uiState.value = _uiState.value.copy(isSwitchingSubtitle = false)
                showToast(
                    if (err is IllegalStateException) {
                        "That subtitle track is no longer available."
                    } else {
                        "Subtitle switch failed: ${err.message ?: err}"
                    },
                )
            }
        }
    }

    // Port of screens/player.js's switchAudioTrack(): triggers a fresh
    // PlaybackInfo negotiation with Jellyfin for the requested AudioStreamIndex,
    // reloading the stream at currentPositionTicks with the server transcoding/remuxing
    // or direct-playing the selected audio track.
    fun switchAudioTrack(session: Session, streamIndex: Int, currentPositionTicks: Long) {
        val id = itemId ?: return
        val label = _uiState.value.audioTracks.firstOrNull { it.streamIndex == streamIndex }?.label ?: "audio"
        // Direct play carries every audio track already: the player swaps
        // to it in place (PlayerScreen's selectAudioTrack effect), no
        // reload, no restart, unless the picked track needs transcoding.
        if (_uiState.value.directPlay && currentSource?.let { repository.canDirectPlay(it, streamIndex) } != false) {
            _uiState.value = _uiState.value.copy(selectedAudioStreamIndex = streamIndex)
            showToast("Audio: $label")
            return
        }
        showToast("Switching to $label…")
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
                showToast("Requested $label, reloading…")
            } catch (err: Exception) {
                showToast(
                    if (err is IllegalStateException) {
                        "That audio track is no longer available."
                    } else {
                        "Audio switch failed: ${err.message ?: err}"
                    },
                )
            }
        }
    }

    // Port of screens/player.js's switchSource(source): a fresh PlaybackInfo
    // negotiation against the picked source's Id at currentPositionTicks.
    // A transcoded stream can only start where the server begins encoding,
    // so seeking in one restarts it from the new position, as
    // screens/player.js does; same source, audio and burned-in subtitles.
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
                showToast("Couldn't jump there. Try again.")
            }
        }
    }

    fun switchSource(session: Session, source: MediaSourceDto, currentPositionTicks: Long) {
        val id = itemId ?: return
        if (source.Id == _uiState.value.mediaSourceId) return
        _uiState.value = _uiState.value.copy(switchingTo = source.Name?.lineSequence()?.firstOrNull()?.trim()?.ifBlank { null } ?: "another stream")
        viewModelScope.launch {
            try {
                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    source.Id,
                    currentPositionTicks,
                )
                mediaSourceIdParam = target.mediaSource.Id
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
                )
            } catch (err: Exception) {
                _uiState.value = _uiState.value.copy(switchingTo = null)
                // Real port of that file's own switchSource() catch
                // chain: no success toast there either, only these two
                // real failure messages, the second one a fixed generic
                // string rather than err.message (that function's own
                // real text, not this file's own invention).
                showToast(
                    if (err is IllegalStateException) {
                        "That stream is no longer available."
                    } else {
                        "Could not switch streams. Check your connection and try again."
                    },
                )
            }
        }
    }

    // Real port of screens/detail.js's own isSpecialsSeason(): real
    // Jellyfin IndexNumber 0, or a name that just reads as Specials,
    // the exact same real convention this app's own DetailScreen season
    // tabs already use for that screen's own tab bar, duplicated here
    // for this second, in-player one rather than shared, same real
    // duplication screens/player.js's own copy of that function keeps
    // against screens/detail.js's own original.
    private fun isSpecialsSeason(season: BaseItemDto): Boolean {
        if (season.IndexNumber == 0) return true
        return season.Name?.contains("special", ignoreCase = true) == true
    }

    // Real port of screens/player.js's own loadSeasonEpisodes(): swaps
    // in a new season's own episode list, called both for the real
    // default season load()'s own real fire-and-forget above already
    // triggers and for every later real season tab tap PlayerScreen
    // sends here.
    // Only the latest season asked for fills the panel; an earlier answer
    // arriving late is dropped.
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
            episodeCode = if (hasCode) "S${episode.ParentIndexNumber}E${episode.IndexNumber}" else null,
            rating = episode.CommunityRating?.let { "%.1f".format(it) },
            overview = episode.Overview,
        )
    }

    // Real port of screens/player.js's own showScrubPreview(): the same
    // real thumbnailIndex/tileIndex/col/row math that function's own
    // CSS backgroundSize/backgroundPosition needs, called here on
    // demand (a plain synchronous computation, no real network call of
    // its own beyond the tile image URL PlayerScreen's own AsyncImage
    // then fetches) rather than on a real mousemove listener this
    // Android TV remote has no equivalent input for; PlayerScreen calls
    // this after every D-pad seek instead, the closest real TV native
    // reading of "show a preview of where this seek is about to land."
    fun trickplayFrame(positionMs: Long): TrickplayFrame? {
        val info = trickplayInfo ?: return null
        val session = trickplaySession ?: return null
        val id = itemId ?: return null
        val hoveredMs = positionMs.toDouble()
        val thumbsPerTile = maxOf(1, info.TileWidth * info.TileHeight)
        val thumbnailIndex = maxOf(0, (hoveredMs / info.Interval).toInt())
        val tileIndex = thumbnailIndex / thumbsPerTile
        val indexInTile = thumbnailIndex % thumbsPerTile
        val col = indexInTile % info.TileWidth
        val row = indexInTile / info.TileWidth
        val tileUrl = repository.trickplayTileUrl(
            session.serverAddress,
            session.accessToken,
            id,
            trickplayMediaSourceId,
            info.Width,
            tileIndex,
        )
        return TrickplayFrame(
            tileUrl = tileUrl,
            col = col,
            row = row,
            frameWidth = info.Width,
            frameHeight = info.Height,
            sheetWidth = info.TileWidth * info.Width,
            sheetHeight = info.TileHeight * info.Height,
        )
    }

    fun reportStart(positionTicks: Long) {
        val id = itemId ?: return
        currentSource?.let { BadStreams.unmark(id, it) }
        val sourceId = uiState.value.mediaSourceId
        val session = trickplaySession
        if (session != null && trickplayRequestedForSourceId != sourceId) {
            trickplayRequestedForSourceId = sourceId
            loadTrickplayDuringPlayback(session, id, sourceId)
        }
        viewModelScope.launch { repository.reportPlaybackStart(id, sourceId, positionTicks) }
    }

    fun reportProgress(positionTicks: Long, isPaused: Boolean) {
        val id = itemId ?: return
        viewModelScope.launch { repository.reportPlaybackProgress(id, uiState.value.mediaSourceId, positionTicks, isPaused) }
    }

    // Real bug, matches the exact same one screens/player.js's own
    // cleanup() had to work around (real Jellio fix, confirmed against
    // that file's own header before porting this here too): reporting
    // the real current position here unconditionally used to undo
    // markRealWatchComplete() below the moment this screen tore down.
    // Native UserDataManager.UpdatePlayState still divides by the
    // library's own inflated RunTimeTicks on every real stop report, so
    // a real position sitting under its own 90% MaxResumePct there
    // resurrects a real resume position (PlaybackPositionTicks set back
    // to that real value) while never touching Played (stays true,
    // setPlayed() below already set it) - watched correctly marked, but
    // still stuck in Continue Watching with no time left. Reporting 0
    // once markRealWatchComplete() already ran keeps native Jellyfin's
    // own math in its own "ignore progress during the beginning" branch
    // instead, which only ever touches positionTicks, never Played.
    // Pre-warms the next episode stream, playback info, and network buffer
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

    private fun loadTrickplayDuringPlayback(session: Session, itemId: String, mediaSourceId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = runCatching { repository.getTrickplayInfo(session.userId, itemId, mediaSourceId) }.getOrNull()
            if (info != null && this@PlayerViewModel.itemId == itemId) {
                trickplaySession = session
                trickplayInfo = info
                trickplayMediaSourceId = mediaSourceId
                _uiState.value = _uiState.value.copy(hasTrickplay = true)
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

    // Real port of screens/player.js's own markRealWatchComplete():
    // item.RunTimeTicks is the library's own metadata runtime, not
    // whatever Gelato actually resolved and streamed, so a genuine full
    // watch can land under AchievementService's own 90% metadata based
    // gate and under native Jellyfin's own MaxResumePct gate alike.
    // creditRealWatch feeds Jellio's own achievement/feed credit;
    // setPlayed mirrors that same real completion into native
    // Jellyfin's own Played flag too (Continue Watching, Up Next, the
    // native watched checkmark all read that, not this plugin's own
    // data), same real reason player.js's own markRealWatchComplete()
    // calls both.
    private var hasCreditedRealWatch = false

    fun markRealWatchComplete() {
        if (hasCreditedRealWatch) return
        hasCreditedRealWatch = true
        val id = itemId ?: return
        val userId = trickplaySession?.userId ?: return
        viewModelScope.launch { runCatching { repository.creditRealWatch(id) } }
        viewModelScope.launch { runCatching { repository.setPlayed(userId, id, true) } }
    }

    // Real port of screens/player.js's own reportRealDurationIfUseful():
    // monotonically increasing on purpose, not just deduped. Up Next's
    // own real position (skipSegments' own Credits.Start, or the
    // fallback trigger) is only ever a lower bound on this title's own
    // real length (it always lands before the real end), so a later,
    // smaller candidate is never actually better information and must
    // not overwrite an already reported larger one; ExoPlayer's own
    // real duration and the real position 'ended' fires at are both the
    // true real total whenever they do fire, always >= any lower bound
    // reported before them, so this same rule lets either of those
    // through regardless of order.
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

    // Real port of screens/player.js's own startSleepTimer(minutes)
    // handler: the real server call still fires (for real cross-client
    // status parity, see SleepTimerStatusDto's own header comment), the
    // real end time computed locally from `minutes` directly rather
    // than round-tripping through the server's own response, so
    // PlayerSurface's own local countdown can start immediately without
    // waiting on that request.
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

// Real .NET DateTime "O"-style UTC timestamp (fractional seconds up to
// 100ns ticks) EndTimeUtc always serializes as: Instant.parse() already
// accepts that exact real ISO-8601 shape, no custom parsing needed. A
// failure here (an unexpected real format) leaves the real timer
// un-restored rather than crashing the whole screen over a best-effort
// status refresh.
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
