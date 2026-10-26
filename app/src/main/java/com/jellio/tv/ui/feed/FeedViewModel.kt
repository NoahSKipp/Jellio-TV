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

private const val FEED_ATTEMPTS = 3
private const val FEED_TIMEOUT_MS = 12_000L
private const val FEED_BACKGROUND_RETRIES = 3

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

    private var fetchJob: kotlinx.coroutines.Job? = null

    // A busy server (a nightly database task can hold it for a minute) gets
    // a few bounded attempts before the retry state shows; entries already
    // on screen stay, and a failed load tries again quietly later.
    private fun fetch(round: Int = 0) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            val showing = _uiState.value.entries
            if (showing.isEmpty()) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            var lastError: Throwable? = null
            repeat(FEED_ATTEMPTS) { attempt ->
                val result = kotlinx.coroutines.withTimeoutOrNull(FEED_TIMEOUT_MS) { runCatching { repository.getFeed() } }
                if (result != null && result.isSuccess) {
                    _uiState.value = FeedUiState(isLoading = false, entries = result.getOrThrow())
                    return@launch
                }
                lastError = result?.exceptionOrNull()
                if (attempt < FEED_ATTEMPTS - 1) kotlinx.coroutines.delay(2_000L shl attempt)
            }
            android.util.Log.w("JellioFeed", "feed failed: ${lastError?.message ?: "timed out"}")
            _uiState.value = if (showing.isNotEmpty()) {
                FeedUiState(isLoading = false, entries = showing)
            } else {
                FeedUiState(isLoading = false, error = com.jellio.tv.ui.i18n.Strings.get("feed_load_error"))
            }
            if (round < FEED_BACKGROUND_RETRIES) {
                kotlinx.coroutines.delay(30_000L shl round)
                fetch(round + 1)
            }
        }
    }
}
