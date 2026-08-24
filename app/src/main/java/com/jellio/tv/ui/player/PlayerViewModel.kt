package com.jellio.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

// Real port of screens/player.js's own buildResumePrompt()/
// buildPauseOverlay() data needs: a real choice offered once, over the
// paused frame already sitting at the saved position, instead of just
// always seeking straight there with no way back to the real start.
data class PauseOverlayInfo(
    val backdropUrl: String?,
    val title: String,
    val rating: String?,
    val year: String?,
    val officialRating: String?,
    val isEpisode: Boolean,
    val episodeCode: String?,
    val episodeTitle: String?,
    val overview: String?,
)

data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val streamUrl: String? = null,
    val mediaSourceId: String? = null,
    val startPositionTicks: Long = 0,
    val resumePercent: Int? = null,
    val title: String = "",
    val subtitle: String = "",
    val subtitleTracks: List<SubtitleTrackUiState> = emptyList(),
    val selectedSubtitleIndex: Int? = null,
    val isSwitchingSubtitle: Boolean = false,
    val pauseInfo: PauseOverlayInfo? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadedItemId: String? = null
    private var itemId: String? = null
    private var mediaSourceIdParam: String? = null

    fun load(session: Session, itemId: String, mediaSourceId: String?) {
        val key = itemId + "|" + mediaSourceId
        if (loadedItemId == key) return
        loadedItemId = key
        this.itemId = itemId
        this.mediaSourceIdParam = mediaSourceId
        viewModelScope.launch {
            _uiState.value = PlayerUiState(isLoading = true)
            try {
                val item = repository.getItemDetails(session.userId, itemId)
                val startTicks = item.UserData?.PlaybackPositionTicks ?: 0
                val target = repository.resolvePlayback(session.userId, itemId, mediaSourceId, startTicks)
                val isEpisode = item.Type == "Episode" && item.SeriesName != null
                val episodeCode = if (item.ParentIndexNumber != null && item.IndexNumber != null) {
                    "S${item.ParentIndexNumber} E${item.IndexNumber} · "
                } else {
                    ""
                }

                val subtitleTracks = buildSubtitleTracks(itemId, target.mediaSource)
                val resumePercent = item.UserData?.PlayedPercentage?.takeIf { startTicks > 0 }?.roundToInt()

                _uiState.value = PlayerUiState(
                    isLoading = false,
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = resumePercent,
                    title = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(),
                    subtitle = if (isEpisode) episodeCode + item.Name.orEmpty() else "",
                    subtitleTracks = subtitleTracks,
                    pauseInfo = buildPauseOverlayInfo(session, item, isEpisode),
                )
            } catch (err: Exception) {
                _uiState.value = PlayerUiState(isLoading = false, error = err.message ?: "Could not start playback")
            }
        }
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
            repository.imageUrl(session.serverAddress, artId, it, if (backdropTag != null) "Backdrop" else "Primary", 1600)
        }
        val hasEpisodeCode = item.ParentIndexNumber != null && item.IndexNumber != null
        return PauseOverlayInfo(
            backdropUrl = backdropUrl,
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
                val target = repository.resolvePlayback(session.userId, id, mediaSourceIdParam, 0)
                val subtitleTracks = buildSubtitleTracks(id, target.mediaSource)
                _uiState.value = _uiState.value.copy(
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    resumePercent = null,
                    selectedSubtitleIndex = null,
                    subtitleTracks = subtitleTracks,
                )
            } catch (err: Exception) {
                // Real feedback the web side already surfaces via a toast
                // (showPlayerToast('Could not start over: ...')): this
                // screen has none of that chrome ported yet, so the
                // existing stream just keeps playing rather than losing
                // playback entirely over a failed re-negotiation.
            }
        }
    }

    private suspend fun buildSubtitleTracks(itemId: String, mediaSource: MediaSourceDto): List<SubtitleTrackUiState> {
        val resolvedMediaSourceId = mediaSource.Id ?: itemId
        return mediaSource.MediaStreams
            ?.filter { it.Type == "Subtitle" }
            ?.mapNotNull { stream ->
                val index = stream.Index ?: return@mapNotNull null
                val isText = stream.IsTextSubtitleStream == true
                val label = stream.DisplayTitle ?: stream.Language ?: "Subtitle"
                val url = if (isText) {
                    runCatching { repository.buildSubtitleUrl(itemId, resolvedMediaSourceId, stream) }.getOrNull()
                } else {
                    null
                }
                SubtitleTrackUiState(index, if (isText) label else "$label (image)", isText, url)
            }
            ?: emptyList()
    }

    // Off, or a real text based track: no reload needed, ExoPlayer
    // already has every text track declared as a real
    // MediaItem.SubtitleConfiguration from the very first prepare(),
    // this only changes which one the player currently selects.
    fun selectSubtitle(index: Int?) {
        _uiState.value = _uiState.value.copy(selectedSubtitleIndex = index)
    }

    // An image based subtitle (screens/player.js's own real
    // selectBurnedInSubtitle()) has no WebVTT form to side-load,
    // nothing this player can render as a real text track: the only
    // way to show one at all is a fresh PlaybackInfo negotiation
    // forcing a real transcode with that stream burned directly into
    // the video, resuming from wherever real playback currently sits.
    fun selectBurnedInSubtitle(session: Session, streamIndex: Int, currentPositionTicks: Long) {
        val id = itemId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitchingSubtitle = true)
            try {
                val target = repository.resolvePlayback(
                    session.userId,
                    id,
                    mediaSourceIdParam,
                    currentPositionTicks,
                    burnInSubtitleStreamIndex = streamIndex,
                )
                // A different source's own real subtitle track list has
                // no guarantee of matching the one this screen already
                // built (screens/player.js's own real reasoning for
                // rebuilding its subtitle menu after every reload).
                val subtitleTracks = buildSubtitleTracks(id, target.mediaSource)
                _uiState.value = _uiState.value.copy(
                    isSwitchingSubtitle = false,
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    selectedSubtitleIndex = streamIndex,
                    subtitleTracks = subtitleTracks,
                )
            } catch (err: Exception) {
                _uiState.value = _uiState.value.copy(isSwitchingSubtitle = false)
            }
        }
    }

    fun reportStart(positionTicks: Long) {
        val id = itemId ?: return
        viewModelScope.launch { repository.reportPlaybackStart(id, uiState.value.mediaSourceId, positionTicks) }
    }

    fun reportProgress(positionTicks: Long, isPaused: Boolean) {
        val id = itemId ?: return
        viewModelScope.launch { repository.reportPlaybackProgress(id, uiState.value.mediaSourceId, positionTicks, isPaused) }
    }

    fun reportStopped(positionTicks: Long) {
        val id = itemId ?: return
        viewModelScope.launch { repository.reportPlaybackStopped(id, uiState.value.mediaSourceId, positionTicks) }
    }
}
