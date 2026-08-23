package com.jellio.tv.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
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
            _uiState.value = WatchlistUiState(
                isLoading = false,
                movies = items.filter { it.Type == "Movie" },
                series = items.filter { it.Type == "Series" },
            )
        }
    }

    fun refresh(session: Session) {
        loadedForUserId = null
        load(session)
    }
}
