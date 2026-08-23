package com.jellio.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val streamUrl: String? = null,
    val mediaSourceId: String? = null,
    val startPositionTicks: Long = 0,
    val title: String = "",
    val subtitle: String = "",
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

    fun load(session: Session, itemId: String, mediaSourceId: String?) {
        val key = itemId + "|" + mediaSourceId
        if (loadedItemId == key) return
        loadedItemId = key
        this.itemId = itemId
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
                _uiState.value = PlayerUiState(
                    isLoading = false,
                    streamUrl = target.streamUrl,
                    mediaSourceId = target.mediaSource.Id,
                    startPositionTicks = target.startPositionTicks,
                    title = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(),
                    subtitle = if (isEpisode) episodeCode + item.Name.orEmpty() else "",
                )
            } catch (err: Exception) {
                _uiState.value = PlayerUiState(isLoading = false, error = err.message ?: "Could not start playback")
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
