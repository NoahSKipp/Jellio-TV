package com.jellio.tv.ui.detail

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

data class SeasonUiState(val season: BaseItemDto, val episodes: List<BaseItemDto> = emptyList())

data class DetailUiState(
    val isLoading: Boolean = true,
    val item: BaseItemDto? = null,
    val error: String? = null,
    val seasons: List<BaseItemDto> = emptyList(),
    val selectedSeasonId: String? = null,
    val selectedSeasonEpisodes: List<BaseItemDto> = emptyList(),
    val cast: List<com.jellio.tv.data.model.PersonDto> = emptyList(),
    val trailers: List<com.jellio.tv.data.model.TrailerDto> = emptyList(),
    val playLabel: String = "Play",
    val isWatchlisted: Boolean = false,
    val isWatched: Boolean = false,
    val likes: Boolean? = null,
    val resolvingPlay: Boolean = false,
)

// Real behavior ported from screens/detail.js: a series has no video
// of its own, its own hero Play button lazily resolves which episode
// (resolveSeriesPlayTarget, JellioRepository's own real port of it)
// before ever opening a stream picker, everything else (Watchlist,
// Mark Watched, thumbs) reads/writes the real item itself, or, for an
// Episode, the series it belongs to (a personal rating is a whole
// show's own opinion, not one episode's, same real feedback that file
// ported from).
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var loadedItemId: String? = null
    private var ratingTargetId: String? = null
    private var seriesPlayEpisode: BaseItemDto? = null

    fun load(session: Session, itemId: String) {
        if (loadedItemId == itemId) return
        loadedItemId = itemId
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)
            try {
                val item = repository.getItemDetails(session.userId, itemId)
                ratingTargetId = item.Id
                _uiState.value = DetailUiState(
                    isLoading = false,
                    item = item,
                    cast = item.People?.filter { it.Type == "Actor" }?.take(20) ?: emptyList(),
                    trailers = item.RemoteTrailers?.filter { !it.Url.isNullOrEmpty() } ?: emptyList(),
                    isWatchlisted = item.UserData?.IsFavorite ?: false,
                    isWatched = item.UserData?.Played ?: false,
                )

                if (item.Type == "Episode" && item.SeriesId != null) {
                    runCatching { repository.getItem(session.userId, item.SeriesId) }.getOrNull()?.let { series ->
                        ratingTargetId = series.Id
                        _uiState.value = _uiState.value.copy(likes = series.UserData?.Likes)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(likes = item.UserData?.Likes)
                }

                if (item.Type == "Series") {
                    loadSeasons(session, item.Id)
                    resolveSeriesPlay(session, item.Id)
                }
            } catch (err: Exception) {
                _uiState.value = DetailUiState(isLoading = false, error = err.message ?: "Could not load this title")
            }
        }
    }

    private suspend fun resolveSeriesPlay(session: Session, seriesId: String) {
        val target = runCatching { repository.resolveSeriesPlayTarget(seriesId, session.userId) }.getOrNull() ?: return
        seriesPlayEpisode = target.episode
        if (target.resume) {
            _uiState.value = _uiState.value.copy(
                playLabel = "Resume S${target.episode.ParentIndexNumber} E${target.episode.IndexNumber}",
            )
        }
    }

    private fun loadSeasons(session: Session, seriesId: String) {
        viewModelScope.launch {
            val seasons = runCatching { repository.getSeasons(seriesId, session.userId) }.getOrDefault(emptyList())
                .sortedBy { if (repository.isSpecialsSeason(it)) 1 else 0 }
            _uiState.value = _uiState.value.copy(seasons = seasons)
            seasons.firstOrNull()?.let { selectSeason(session, seriesId, it.Id) }
        }
    }

    fun selectSeason(session: Session, seriesId: String, seasonId: String) {
        _uiState.value = _uiState.value.copy(selectedSeasonId = seasonId, selectedSeasonEpisodes = emptyList())
        viewModelScope.launch {
            val episodes = runCatching { repository.getEpisodes(seriesId, session.userId, seasonId) }.getOrDefault(emptyList())
            if (_uiState.value.selectedSeasonId == seasonId) {
                _uiState.value = _uiState.value.copy(selectedSeasonEpisodes = episodes)
            }
        }
    }

    // The one real item this screen's own Play button should hand off
    // to a stream picker/player for: itself for a movie or an episode,
    // whichever real episode resolveSeriesPlay above already resolved
    // for a series (a series has no video of its own).
    fun resolvePlayTarget(): BaseItemDto? {
        val item = _uiState.value.item ?: return null
        return if (item.Type == "Series") seriesPlayEpisode else item
    }

    fun toggleWatchlist(session: Session) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            val newValue = runCatching { repository.toggleFavorite(session.userId, item) }.getOrDefault(_uiState.value.isWatchlisted)
            _uiState.value = _uiState.value.copy(isWatchlisted = newValue)
        }
    }

    fun toggleWatched(session: Session) {
        val item = _uiState.value.item ?: return
        val next = !_uiState.value.isWatched
        viewModelScope.launch {
            val updated = runCatching { repository.setPlayed(session.userId, item.Id, next) }.getOrNull()
            _uiState.value = _uiState.value.copy(isWatched = updated?.Played ?: next)
        }
    }

    fun setLike(session: Session, likes: Boolean) {
        val targetId = ratingTargetId ?: return
        val next = if (_uiState.value.likes == likes) null else likes
        viewModelScope.launch {
            val updated = runCatching { repository.setRating(session.userId, targetId, next) }.getOrNull()
            _uiState.value = _uiState.value.copy(likes = updated?.Likes ?: next)
        }
    }

    suspend fun getMediaSources(session: Session, itemId: String): List<MediaSourceDto> =
        runCatching { repository.getMediaSources(session.userId, itemId) }.getOrDefault(emptyList())
}
