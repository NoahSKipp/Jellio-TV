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
import kotlinx.coroutines.CancellationException
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

    // Results fill in as each answer lands: titles already in the library
    // (near instant) first, then each addon search's results appended
    // after them, nothing shown twice and nothing reordered under the
    // focus. isSearching stays on, with placeholder cards after the
    // results, until every search has answered.
    //
    // Servers without Gelato (or an older build without the per-type
    // routes) 404 on the addon searches and match nothing on "local:":
    // the plain combined search answers instead, same as it always did.
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
            val seen = HashSet<String>()
            var pending = 3
            var failures = 0
            var fellBack = false

            suspend fun add(items: List<BaseItemDto>) {
                stateLock.withLock {
                    if (_uiState.value.query != query) return
                    val fresh = items.filter { item ->
                        val keys = identityKeys(item)
                        if (keys.any { it in seen }) false else { seen.addAll(keys); true }
                    }
                    if (fresh.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(results = _uiState.value.results + fresh, error = null)
                    }
                }
            }

            suspend fun settle(count: Int, failed: Boolean) {
                stateLock.withLock {
                    pending -= count
                    if (failed) failures += count
                    if (_uiState.value.query != query || pending > 0) return
                    val nothing = _uiState.value.results.isEmpty()
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        error = if (nothing && failures >= 3) "Could not load search results. Check your connection and try again." else null,
                    )
                }
            }

            suspend fun fallBackToCombined() {
                val first = stateLock.withLock { if (fellBack) false else { fellBack = true; true } }
                // The other addon search's 404 is settled with this one.
                if (!first) return
                val ok = try {
                    add(repository.searchItems(session.userId, query))
                    true
                } catch (err: CancellationException) {
                    throw err
                } catch (err: Exception) {
                    false
                }
                settle(2, !ok)
            }

            suspend fun runAddon(fetch: suspend () -> List<BaseItemDto>) {
                try {
                    val items = fetch()
                    if (!fellBack) add(items)
                    settle(1, false)
                } catch (err: CancellationException) {
                    throw err
                } catch (err: HttpException) {
                    if (err.code() == 404) fallBackToCombined() else settle(1, true)
                } catch (err: Exception) {
                    settle(1, true)
                }
            }

            coroutineScope {
                launch {
                    val ok = try {
                        add(repository.searchLibrary(session.userId, query))
                        true
                    } catch (err: CancellationException) {
                        throw err
                    } catch (err: Exception) {
                        false
                    }
                    settle(1, !ok)
                }
                launch { runAddon { repository.searchMovies(query) } }
                launch { runAddon { repository.searchSeries(query) } }
            }
        }
    }

    // The same title from the library and from an addon search: Gelato
    // answers owned titles with the library's own item, so the id
    // usually matches; provider ids and name plus year catch the rest.
    private fun identityKeys(item: BaseItemDto): List<String> = buildList {
        add("id:" + item.Id.replace("-", "").lowercase())
        item.ProviderIds?.forEach { (provider, value) ->
            if (value.isNotBlank() && provider.lowercase() in setOf("tmdb", "imdb", "tvdb")) add("${item.Type}:${provider.lowercase()}:$value")
        }
        val name = item.Name?.trim()?.lowercase()
        if (!name.isNullOrEmpty() && item.ProductionYear != null) add("${item.Type}:name:$name:${item.ProductionYear}")
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
