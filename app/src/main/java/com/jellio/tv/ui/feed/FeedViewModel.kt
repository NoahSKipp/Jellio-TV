package com.jellio.tv.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.FeedEntryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedUiState(
    val isLoading: Boolean = true,
    val entries: List<FeedEntryDto> = emptyList(),
    val error: String? = null,
)

// Real port of screens/feed.js's own renderFeed(): a real retry state
// on failure (matching that file's own renderRetry(), not a silent
// empty list) rather than JellioRepository swallowing the error the
// way most other list endpoints here do, same real reasoning
// getCalendarEntries()'s own header already gives for that call being
// a bare passthrough too.
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        fetch()
    }

    fun retry() = fetch()

    private fun fetch() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { repository.getFeed() }
                .onSuccess { entries -> _uiState.value = FeedUiState(isLoading = false, entries = entries) }
                .onFailure { err -> _uiState.value = FeedUiState(isLoading = false, error = err.message ?: "Could not load the activity feed.") }
        }
    }
}
