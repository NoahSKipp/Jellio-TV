package com.jellio.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEBOUNCE_MS = 400L

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<BaseItemDto> = emptyList(),
    val hasSearched: Boolean = false,
    val error: String? = null,
)

// Mirrors runtime/api.js's own searchItems(): the real /Users/{id}/Items
// endpoint with a searchTerm added, Movie/Series only, same as the web
// build. Debounced client side rather than on every keystroke, same
// real reasoning any live-typing search box needs regardless of platform.
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(session: Session, query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false, hasSearched = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            delay(DEBOUNCE_MS)
            // screens/search.js's own header comment documents the real
            // bug this fixes: a failed request must not "look identical
            // to search doing nothing", real feedback reported live
            // against exactly that. A caught failure now surfaces its
            // own distinct real message rather than folding into the
            // same "No results" state a genuine empty search leaves.
            try {
                val results = repository.searchItems(session.userId, query)
                if (_uiState.value.query == query) {
                    _uiState.value = _uiState.value.copy(results = results, isSearching = false, hasSearched = true, error = null)
                }
            } catch (err: Exception) {
                if (_uiState.value.query == query) {
                    _uiState.value = _uiState.value.copy(
                        results = emptyList(),
                        isSearching = false,
                        hasSearched = true,
                        error = "Could not load search results. Check your connection and try again.",
                    )
                }
            }
        }
    }
}
