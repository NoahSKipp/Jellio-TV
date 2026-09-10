package com.jellio.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject

private const val DEBOUNCE_MS = 400L

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<BaseItemDto> = emptyList(),
    val hasSearched: Boolean = false,
    val error: String? = null,
    val canDeleteItems: Boolean = false,
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
    private var permissionsLoadedFor: String? = null

    // Decoupled from onQueryChange's own debounce below on purpose:
    // real Policy fetched once per real session rather than once per
    // real keystroke.
    fun loadPermissions(session: Session) {
        if (permissionsLoadedFor == session.userId) return
        permissionsLoadedFor = session.userId
        viewModelScope.launch {
            val canDelete = repository.canDeleteItems(session.userId)
            _uiState.value = _uiState.value.copy(canDeleteItems = canDelete)
        }
    }

    // Real bottleneck JellyfinApi.kt's own searchGelatoMovies/
    // searchGelatoSeries header documents: the old single
    // repository.searchItems() call waited on Gelato's own combined
    // movie+series search server side before answering at all. Firing
    // the two halves independently and merging each into results the
    // moment it resolves - regardless of which one lands first - means
    // SearchScreen can paint partial results as soon as the faster half
    // is done rather than both waiting on the slower one; isSearching
    // only clears once both have settled, so SearchScreen still has a
    // "still loading the rest" signal to show alongside partial results.
    fun onQueryChange(session: Session, query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false, hasSearched = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, hasSearched = true, results = emptyList(), error = null)
            delay(DEBOUNCE_MS)
            if (_uiState.value.query != query) return@launch

            val stateLock = Mutex()
            var settledCount = 0
            var fellBack = false

            suspend fun markSettled() {
                stateLock.withLock {
                    settledCount += 1
                    if (settledCount >= 2 && _uiState.value.query == query) {
                        _uiState.value = _uiState.value.copy(isSearching = false)
                    }
                }
            }

            // screens/search.js's own header comment documents the real
            // bug this fixes: a failed request must not "look identical
            // to search doing nothing", real feedback reported live
            // against exactly that. A caught failure now surfaces its
            // own distinct real message rather than folding into the
            // same "No results" state a genuine empty search leaves.
            //
            // Real servers without Gelato installed (or on an older
            // build without these two routes yet) 404 here: fall back to
            // the original combined call so search still works there,
            // same real result that call always gave.
            suspend fun fallBackToCombined() {
                stateLock.withLock {
                    if (fellBack) return
                    fellBack = true
                }
                try {
                    val results = repository.searchItems(session.userId, query)
                    if (_uiState.value.query == query) {
                        _uiState.value = _uiState.value.copy(results = results, isSearching = false, error = null)
                    }
                } catch (err: Exception) {
                    if (_uiState.value.query == query) {
                        _uiState.value = _uiState.value.copy(
                            results = emptyList(),
                            isSearching = false,
                            error = "Could not load search results. Check your connection and try again.",
                        )
                    }
                }
            }

            suspend fun runOne(fetch: suspend () -> List<BaseItemDto>) {
                try {
                    val items = fetch()
                    if (_uiState.value.query == query && !fellBack) {
                        val merged = (_uiState.value.results + items).distinctBy { it.Id }
                        _uiState.value = _uiState.value.copy(results = merged, error = null)
                    }
                    markSettled()
                } catch (err: HttpException) {
                    if (err.code() == 404) fallBackToCombined() else markSettled()
                } catch (err: Exception) {
                    markSettled()
                }
            }

            coroutineScope {
                launch { runOne { repository.searchMovies(query) } }
                launch { runOne { repository.searchSeries(query) } }
            }
        }
    }

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
            runCatching { repository.deleteItem(item.Id) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(results = _uiState.value.results.filterNot { it.Id == item.Id })
                }
        }
    }

    private fun updateItem(itemId: String, transform: (BaseItemDto) -> BaseItemDto) {
        _uiState.value = _uiState.value.copy(
            results = _uiState.value.results.map { if (it.Id == itemId) transform(it) else it },
        )
    }
}
