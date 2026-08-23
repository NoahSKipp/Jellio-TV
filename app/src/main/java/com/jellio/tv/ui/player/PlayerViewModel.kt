package com.jellio.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val streamUrl: String? = null,
    val mediaSourceId: String? = null,
    val startPositionTicks: Long = 0,
    val title: String = "",
    val subtitle: String = "",
    val subtitleTracks: List<SubtitleTrackUiState> = emptyList(),
    val selectedSubtitleIndex: Int? = null,
    val isSwitchingSubtitle: Boolean = false,
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
                val item = repository.getItem(session.userId, itemId)
                val startTicks = item.UserData?.PlaybackPositionTicks ?: 0
                val target = repository.resolvePlayback(session.userId, itemId, mediaSourceId, startTicks)
                val isEpisode = item.Type == "Episode" && item.SeriesName != null
                val episodeCode = if (item.ParentIndexNumber != null && item.IndexNumber != null) {
                    "S${item.ParentIndexNumber} E${item.IndexNumber} · "
                } else {
                    ""
                }

                val subtitleTracks = buildSubtitleTracks(itemId, target.mediaSource)

                _uiState.value = PlayerUiState(
                    isLoading = false,
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    title = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(),
                    subtitle = if (isEpisode) episodeCode + item.Name.orEmpty() else "",
                    subtitleTracks = subtitleTracks,
                )
            } catch (err: Exception) {
                _uiState.value = PlayerUiState(isLoading = false, error = err.message ?: "Could not start playback")
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
