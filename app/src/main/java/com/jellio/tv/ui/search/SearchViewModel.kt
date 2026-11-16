package com.jellio.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.catching
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.LoadFailure
import com.jellio.tv.ui.common.loadFailureOf
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
private const val SEARCH_WARM_COUNT = 3

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<BaseItemDto> = emptyList(),
    val hasSearched: Boolean = false,
    val error: LoadFailure? = null,
    // Nothing found: related titles in the library, the way Netflix answers a
    // title it doesn't carry.
    val suggestions: com.jellio.tv.data.model.SearchSuggestionsDto? = null,
    val correctedQuery: String? = null,
    val isSuggestionsLoading: Boolean = false,
    val similarResults: List<BaseItemDto> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var warmJob: Job? = null
    private var batchJob: Job? = null
    private var warmSession: Session? = null

    // The top results' stream lists are warmed once they stop changing; a
    // new query drops what the last one had pending.
    private val warmer = com.jellio.tv.data.streams.SearchWarmer<BaseItemDto>(
        scheduler = { delayMs, block ->
            val job = viewModelScope.launch {
                delay(delayMs)
                block()
            }
            com.jellio.tv.data.streams.Cancellable { job.cancel() }
        },
        onSettled = { items -> warmTopResults(items) },
        onFocused = { id -> repository.prefetchStreams(id) },
        onQueryChanged = {
            warmJob?.cancel()
            warmJob = null
            batchJob?.cancel()
            batchJob = null
        },
    )

    fun onResultFocused(itemId: String) {
        warmer.focus(itemId)
    }

    private fun warmTopResults(items: List<BaseItemDto>) {
        val session = warmSession ?: return
        val top = items.filter { it.Type == "Movie" || it.Type == "Episode" || it.Type == "Series" }.take(SEARCH_WARM_COUNT)
        warmJob?.cancel()
        warmJob = viewModelScope.launch {
            // A series plays its next (or first) episode; one not in the
            // library yet is sent as is, which adds it.
            val ids = top.map { item ->
                if (item.Type != "Series") item.Id
                else catching { repository.getSeriesNextUp(item.Id, session.userId)?.Id }.getOrNull() ?: item.Id
            }
            batchJob?.cancel()
            batchJob = repository.prefetchStreamsBatch(ids, probe = true)
        }
    }

    fun onQueryChange(session: Session, query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        warmSession = session
        warmer.query()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = emptyList(),
                isSearching = false,
                hasSearched = false,
                error = null,
                suggestions = null,
                correctedQuery = null,
                isSuggestionsLoading = false,
                similarResults = emptyList(),
            )
            return
        }
        searchJob = viewModelScope.launch {
            val variants = com.jellio.tv.data.SearchQueryNormalizer.expandVariants(query)
            val expandedAlias = variants.firstOrNull { it.lowercase() != query.trim().lowercase() }
            val primaryQuery = expandedAlias ?: query

            _uiState.value = _uiState.value.copy(
                isSearching = true,
                hasSearched = true,
                results = emptyList(),
                error = null,
                suggestions = null,
                correctedQuery = expandedAlias,
                isSuggestionsLoading = false,
                similarResults = emptyList(),
            )
            delay(DEBOUNCE_MS)
            if (_uiState.value.query != query) return@launch

            val stateLock = Mutex()
            val seen = HashSet<String>()
            var pending = 4
            var failures = 0
            var fellBack = false
            // The library is the main source: with it down, nothing found is not "no results".
            var libraryFailed = false
            var lastError: Throwable? = null
            var fuzzyCorrectedQuery: String? = null
            var hasRunCorrectedPass = false
            var suggestionsRequested = false

            suspend fun add(items: List<BaseItemDto>) {
                stateLock.withLock {
                    if (_uiState.value.query != query) return
                    val fresh = items.filter { item ->
                        val keys = identityKeys(item)
                        if (keys.any { it in seen }) false else { seen.addAll(keys); true }
                    }
                    if (fresh.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(results = _uiState.value.results + fresh, error = null)
                        warmer.results(_uiState.value.results)
                    }
                }
            }

            fun pickBestPrimaryHit(): BaseItemDto? {
                val combined = _uiState.value.results
                if (combined.isEmpty()) return null
                val compareQuery = fuzzyCorrectedQuery ?: primaryQuery
                return combined.maxByOrNull { com.jellio.tv.data.SearchQueryNormalizer.fuzzyScore(compareQuery, it.Name.orEmpty()) }
            }

            suspend fun showSuggestions() {
                if (suggestionsRequested) return
                suggestionsRequested = true
                val topHit = pickBestPrimaryHit()
                val topTmdb = topHit?.ProviderIds?.get("Tmdb") ?: topHit?.ProviderIds?.get("tmdb")
                val topMediaType = if (topHit != null) (if (topHit.Type == "Series") "tv" else "movie") else null
                val suggestQuery = topHit?.Name ?: fuzzyCorrectedQuery ?: primaryQuery

                _uiState.value = _uiState.value.copy(isSuggestionsLoading = true)
                val answer = catching {
                    repository.getSearchSuggestions(
                        query = suggestQuery,
                        tmdbId = topTmdb,
                        mediaType = topMediaType,
                        enableVirtual = true,
                        userId = session.userId,
                    )
                }.getOrNull()

                if (_uiState.value.query != query) return
                if (answer != null && answer.Rows.isNotEmpty()) {
                    val rawSuggestions = answer.Rows.flatMap { it.Items }
                    val filteredSimilar = rawSuggestions.filter { item ->
                        val keys = identityKeys(item)
                        keys.none { it in seen }
                    }.distinctBy { it.Id }
                    _uiState.value = _uiState.value.copy(
                        suggestions = answer,
                        similarResults = filteredSimilar,
                        isSuggestionsLoading = false,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isSuggestionsLoading = false)
                }
            }

            suspend fun runSpellCorrectedPass(corrected: String) {
                hasRunCorrectedPass = true
                stateLock.withLock {
                    _uiState.value = _uiState.value.copy(correctedQuery = corrected)
                    pending += 2
                }
                coroutineScope {
                    launch {
                        try {
                            val items = repository.searchMovies(corrected)
                            add(items)
                        } catch (_: Exception) {} finally {
                            settle(1, false)
                        }
                    }
                    launch {
                        try {
                            val items = repository.searchSeries(corrected)
                            add(items)
                        } catch (_: Exception) {} finally {
                            settle(1, false)
                        }
                    }
                }
            }

            suspend fun settle(count: Int, failed: Boolean) {
                var shouldRunCorrection: String? = null
                var shouldShowSuggestions = false
                stateLock.withLock {
                    pending -= count
                    if (failed) failures += count
                    if (_uiState.value.query != query || pending > 0) return
                    val nothing = _uiState.value.results.isEmpty()
                    val failedAll = nothing && (failures >= 4 || libraryFailed)
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        error = if (failedAll) loadFailureOf(lastError) else null,
                    )
                    if (nothing && !failedAll && fuzzyCorrectedQuery != null && !hasRunCorrectedPass && !fuzzyCorrectedQuery.equals(primaryQuery, ignoreCase = true)) {
                        shouldRunCorrection = fuzzyCorrectedQuery
                    } else if (!failedAll) {
                        shouldShowSuggestions = true
                    }
                }
                if (shouldRunCorrection != null) {
                    runSpellCorrectedPass(shouldRunCorrection!!)
                } else if (shouldShowSuggestions) {
                    showSuggestions()
                }
            }

            suspend fun fallBackToCombined() {
                val first = stateLock.withLock { if (fellBack) false else { fellBack = true; true } }
                if (!first) return
                val ok = try {
                    add(repository.searchItems(session.userId, primaryQuery))
                    true
                } catch (err: CancellationException) {
                    throw err
                } catch (err: Exception) {
                    lastError = err
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
                    if (err.code() == 404) {
                        fallBackToCombined()
                    } else {
                        lastError = err
                        settle(1, true)
                    }
                } catch (err: Exception) {
                    lastError = err
                    settle(1, true)
                }
            }

            coroutineScope {
                launch {
                    val ok = try {
                        add(repository.searchLibrary(session.userId, primaryQuery))
                        true
                    } catch (err: CancellationException) {
                        throw err
                    } catch (err: Exception) {
                        lastError = err
                        libraryFailed = true
                        false
                    }
                    settle(1, !ok)
                }
                launch {
                    val fuzzyResult = catching { repository.searchFuzzy(session, query, 24) }.getOrNull()
                    if (_uiState.value.query == query && fuzzyResult != null) {
                        if (!fuzzyResult.CorrectedQuery.isNullOrBlank()) {
                            fuzzyCorrectedQuery = fuzzyResult.CorrectedQuery
                            if (fuzzyResult.Items.isNotEmpty()) {
                                stateLock.withLock {
                                    _uiState.value = _uiState.value.copy(correctedQuery = fuzzyResult.CorrectedQuery)
                                }
                            }
                        }
                        if (fuzzyResult.Items.isNotEmpty()) {
                            add(fuzzyResult.Items)
                        }
                    }
                    settle(1, false)
                }
                launch { runAddon { repository.searchMovies(primaryQuery) } }
                launch { runAddon { repository.searchSeries(primaryQuery) } }
            }
        }
    }

    fun retry(session: Session) {
        val query = _uiState.value.query
        if (query.isNotBlank()) onQueryChange(session, query)
    }

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
            val newValue = catching { repository.toggleFavorite(session.userId, item) }.getOrNull() ?: return@launch
            updateItem(item.Id) { it.copy(UserData = (it.UserData ?: UserItemDataDto()).copy(IsFavorite = newValue)) }
        }
    }

    fun toggleWatched(session: Session, item: BaseItemDto) {
        val next = !(item.UserData?.Played ?: false)
        viewModelScope.launch {
            val updated = catching { repository.setPlayed(session.userId, item.Id, next) }.getOrNull() ?: return@launch
            updateItem(item.Id) { it.copy(UserData = updated) }
        }
    }

    private fun updateItem(itemId: String, transform: (BaseItemDto) -> BaseItemDto) {
        _uiState.value = _uiState.value.copy(
            results = _uiState.value.results.map { if (it.Id == itemId) transform(it) else it },
        )
    }
}
