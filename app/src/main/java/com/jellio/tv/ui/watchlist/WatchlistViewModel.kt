package com.jellio.tv.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchlistUiState(
    val isLoading: Boolean = true,
    val movies: List<BaseItemDto> = emptyList(),
    val series: List<BaseItemDto> = emptyList(),
    val canDeleteItems: Boolean = false,
)

// Real Jellyfin favorites underneath this app's own Watchlist label,
// same real endpoint runtime/api.js's own getWatchlistItems() calls.
// Split into Movies/Series client side: the real endpoint returns
// both types in one real list, no server side grouping to lean on.
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    private var loadedForUserId: String? = null

    fun load(session: Session) {
        if (loadedForUserId == session.userId) return
        loadedForUserId = session.userId
        viewModelScope.launch {
            _uiState.value = WatchlistUiState(isLoading = true)
            val items = runCatching { repository.getWatchlistItems(session.userId) }.getOrDefault(emptyList())
            val canDelete = repository.canDeleteItems(session.userId)
            _uiState.value = WatchlistUiState(
                isLoading = false,
                movies = items.filter { it.Type == "Movie" },
                series = items.filter { it.Type == "Series" },
                canDeleteItems = canDelete,
            )
        }
    }

    fun refresh(session: Session) {
        loadedForUserId = null
        load(session)
    }

    // Real port of components/cardOptionsMenu.js's own Watchlist/Mark
    // Watched/Remove from Library options: real screens/home.js's own
    // renderWatchlist() reuses the exact same generic buildCard(), no
    // special "drop off this screen the instant it is un-favorited"
    // behaviour, same real choice kept here rather than invented.
    fun toggleWatchlist(session: Session, item: BaseItemDto) {
        viewModelScope.launch {
            val newValue = runCatching { repository.toggleFavorite(session.userId, item) }.getOrNull() ?: return@launch
            updateItem(item.Id) { it.copy(UserData = (it.UserData ?: UserItemDataDto()).copy(IsFavorite = newValue)) }
        }
    }

    fun toggleWatched(session: Session, item: BaseItemDto) {
        val next = !(item.UserData?.Played ?: false)
        viewModelScope.launch {
            val updated = runCatching { repository.setPlayed(session.userId, item.Id, next) }.getOrNull() ?: return@launch
            updateItem(item.Id) { it.copy(UserData = updated) }
        }
    }

    fun deleteItem(item: BaseItemDto) {
        viewModelScope.launch {
            runCatching { repository.deleteItem(item.Id) }.onSuccess { removeItem(item.Id) }
        }
    }

    private fun updateItem(itemId: String, transform: (BaseItemDto) -> BaseItemDto) {
        val state = _uiState.value
        _uiState.value = state.copy(
            movies = state.movies.map { if (it.Id == itemId) transform(it) else it },
            series = state.series.map { if (it.Id == itemId) transform(it) else it },
        )
    }

    private fun removeItem(itemId: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            movies = state.movies.filterNot { it.Id == itemId },
            series = state.series.filterNot { it.Id == itemId },
        )
    }
}
