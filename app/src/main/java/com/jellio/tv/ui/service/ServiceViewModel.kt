package com.jellio.tv.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.cache.CachedServiceRowDto
import com.jellio.tv.data.cache.RowSnapshotStore
import com.jellio.tv.data.cache.ServiceSnapshot
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.model.groupByService
import com.jellio.tv.data.model.rowTitle
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val SERVICE_ROW_TIMEOUT_MS = 15_000L
private const val SERVICE_ROW_ATTEMPTS = 3
private const val SERVICE_BACKGROUND_RETRIES = 3
private const val SERVICE_RETRY_DELAY_MS = 30_000L
private const val SERVICE_ROW_LIMIT = 24
private const val TOP_GENRES_LIMIT = 10
private const val MIN_GENRE_COUNT = 3
private const val GENRE_ROW_COUNT = 4
private const val GENRE_ROW_MIN_ITEMS = 8
// A row sharing this much of its titles with one above it is dropped.
private const val DUPLICATE_ROW_OVERLAP = 0.8
private val TOP_ROW = Regex("top ?10|trending|popular", RegexOption.IGNORE_CASE)
private val NEW_ROW = Regex("\\bnew\\b|latest|recent|just added", RegexOption.IGNORE_CASE)

// Real screens/service.js's own ROW_LIST_LIMIT: components/
// rowListModal.js's own "browse everything" cap, that file's own
// independently redeclared constant (not shared with screens/home.js's
// own copy of the same real value), kept the same way here rather
// than reusing HomeViewModel's own private one.
private const val ROW_LIST_LIMIT = 500

// A row's own real kind (movies/tvshows) travels with it now, not
// just its title/items: screens/service.js's own applyFilter() reads
// a row's real kind to decide whether the Movies/TV Shows chip
// matches it at all, real state HomeSection alone has no room for.
// collectionId is this row's own real backing BoxSet, real
// screens/service.js's own makeRowTitleClickable() fetchAll needs it
// (getCollectionItems(row.collection.Id, row.kind, ROW_LIST_LIMIT)) to
// browse this row's full real catalog past whatever SERVICE_ROW_LIMIT
// already trimmed it to.
data class ServiceRow(val title: String, val kind: String, val items: List<BaseItemDto>, val collectionId: String)

// Genre rows built from the hub's own titles hold movies and shows alike
// and have no collection behind them.
const val MIXED_KIND = "mixed"

data class ServiceUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val serviceName: String = "",
    val heroItem: BaseItemDto? = null,
    val rows: List<ServiceRow> = emptyList(),
    // Real screens/service.js's own topGenres(): every real genre
    // count>=3 across every matched collection, led by real count,
    // capped at ten, same real chip bar that file's own buildChips()
    // renders after All/Movies/TV Shows.
    val topGenres: List<String> = emptyList(),
    val canDeleteItems: Boolean = false,
)

// Real port of screens/service.js: every real catalog collection
// matched to this service, one row per collection, a real hero, and
// the real genre/type filter chip bar.
@HiltViewModel
class ServiceViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val rowSnapshotStore: RowSnapshotStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceUiState())
    val uiState: StateFlow<ServiceUiState> = _uiState.asStateFlow()

    private var loadedService: String? = null

    fun load(session: Session, serviceName: String) {
        if (loadedService == serviceName) return
        loadedService = serviceName
        refresh(session, serviceName, retryRound = 0)
    }

    fun retry(session: Session, serviceName: String) {
        loadedService = serviceName
        refresh(session, serviceName, retryRound = 0)
    }

    // A timed out or failed collection is not an empty one: it is asked
    // again with backoff, never cached as empty, and a page that lost rows
    // to a busy server keeps what it showed before and tries again later.
    private fun refresh(session: Session, serviceName: String, retryRound: Int) {
        viewModelScope.launch {
            // The last visit's rows show at once; the refresh replaces them
            // in one go when it's done so nothing moves while browsing.
            val showing = _uiState.value.takeIf { it.serviceName == serviceName && it.rows.isNotEmpty() }
            val cached = if (showing != null) null else runCatching { rowSnapshotStore.loadServiceSnapshot(session.userId, serviceName) }.getOrNull()
                ?.takeIf { it.rows.isNotEmpty() }
            val baseline = showing ?: cached?.let {
                ServiceUiState(
                    isLoading = false,
                    serviceName = serviceName,
                    heroItem = it.heroItem,
                    rows = it.rows.map { row -> ServiceRow(row.title, row.kind, row.items, row.collectionId) },
                    topGenres = it.topGenres,
                )
            }
            _uiState.value = baseline ?: ServiceUiState(isLoading = true, serviceName = serviceName)
            try {
                val collections = withBackoff { repository.getCollections(session.userId) }
                    ?: throw IllegalStateException("collections timed out")
                val matching = groupByService(collections)[serviceName].orEmpty()

                // Slots keep the collections' order while each row goes up as
                // soon as its own answer arrives, not after the ones above it.
                val slots = arrayOfNulls<ServiceRow>(matching.size)
                var failedRows = 0
                var heroItem: BaseItemDto? = null
                var heroRating = -1.0

                matching.mapIndexed { index, collection ->
                    launch {
                        val kind = repository.collectionKind(collection)
                        val items = withBackoff {
                            repository.getCollectionItems(session.userId, collection.Id, kind, SERVICE_ROW_LIMIT)
                        }
                        if (items == null) {
                            failedRows++
                            return@launch
                        }
                        if (items.isEmpty()) return@launch
                        slots[index] = ServiceRow(title = rowTitle(collection, serviceName, kind), kind = kind, items = items, collectionId = collection.Id)
                        // The best rated item with a backdrop across every
                        // collection leads the page (screens/service.js's pickHeroItem()).
                        items.forEach { item ->
                            if (item.BackdropImageTags.isNullOrEmpty()) return@forEach
                            val rating = item.CommunityRating ?: 0.0
                            if (heroItem == null || rating > heroRating) {
                                heroItem = item
                                heroRating = rating
                            }
                        }
                        if (baseline != null) return@launch
                        _uiState.value = ServiceUiState(
                            isLoading = false,
                            serviceName = serviceName,
                            heroItem = heroItem,
                            rows = arrange(slots.filterNotNull()),
                            topGenres = topGenres(slots.filterNotNull()),
                        )
                    }
                }.joinAll()
                val rows = slots.filterNotNull()

                val genres = topGenres(rows)
                val finalRows = arrange(rows) + genreRows(rows, genres, serviceName)
                val complete = failedRows == 0
                if (complete || baseline == null || finalRows.size >= baseline.rows.size) {
                    _uiState.value = if (rows.isEmpty() && !complete) {
                        ServiceUiState(isLoading = false, error = com.jellio.tv.ui.i18n.Strings.format("service_load_error", serviceName), serviceName = serviceName)
                    } else {
                        ServiceUiState(
                            isLoading = false,
                            serviceName = serviceName,
                            heroItem = heroItem,
                            rows = finalRows,
                            topGenres = genres,
                        )
                    }
                }
                if (complete && rows.isNotEmpty()) {
                    rowSnapshotStore.saveServiceSnapshot(
                        session.userId,
                        serviceName,
                        ServiceSnapshot(
                            heroItem = heroItem,
                            rows = finalRows.map { CachedServiceRowDto(it.title, it.kind, it.items, it.collectionId) },
                            topGenres = genres,
                        ),
                    )
                }
                if (!complete) {
                    android.util.Log.w("JellioService", "$serviceName: $failedRows of ${matching.size} rows failed, retrying later")
                    scheduleRetry(session, serviceName, retryRound)
                }
            } catch (err: Exception) {
                if (err is kotlinx.coroutines.CancellationException) throw err
                android.util.Log.w("JellioService", "$serviceName failed to load: ${err.message}")
                if (baseline == null) {
                    _uiState.value = ServiceUiState(isLoading = false, error = com.jellio.tv.ui.i18n.Strings.format("service_load_error", serviceName), serviceName = serviceName)
                }
                scheduleRetry(session, serviceName, retryRound)
            }
        }
    }

    private fun scheduleRetry(session: Session, serviceName: String, retryRound: Int) {
        if (retryRound >= SERVICE_BACKGROUND_RETRIES) {
            // Leaving and reopening the hub asks again.
            if (loadedService == serviceName) loadedService = null
            return
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(SERVICE_RETRY_DELAY_MS shl retryRound)
            if (loadedService == serviceName) refresh(session, serviceName, retryRound + 1)
        }
    }

    // Null when every attempt failed or timed out; an empty list is a real answer.
    private suspend fun <T> withBackoff(block: suspend () -> T): T? {
        repeat(SERVICE_ROW_ATTEMPTS) { attempt ->
            val result = withTimeoutOrNull(SERVICE_ROW_TIMEOUT_MS) { runCatching { block() } }
            if (result != null && result.isSuccess) return result.getOrNull()
            if (attempt < SERVICE_ROW_ATTEMPTS - 1) kotlinx.coroutines.delay(2_000L shl attempt)
        }
        return null
    }

    // Real port of screens/service.js's own makeRowTitleClickable()
    // fetchAll callback: this row's own full real collection, past
    // whatever SERVICE_ROW_LIMIT already trimmed the row itself to.
    suspend fun fetchAllForRow(session: Session, row: ServiceRow): List<BaseItemDto> =
        if (row.collectionId.isEmpty()) row.items
        else repository.getCollectionItems(session.userId, row.collectionId, row.kind, ROW_LIST_LIMIT)

    // Top 10 and trending catalogs first, then what's new, then the rest,
    // without rows that mostly repeat one above them.
    private fun arrange(rows: List<ServiceRow>): List<ServiceRow> {
        val ordered = rows.sortedBy { row ->
            when {
                TOP_ROW.containsMatchIn(row.title) -> 0
                NEW_ROW.containsMatchIn(row.title) -> 1
                else -> 2
            }
        }
        val kept = mutableListOf<ServiceRow>()
        ordered.forEach { row ->
            val ids = row.items.map { it.Id }.toSet()
            val duplicate = kept.any { earlier ->
                val shared = earlier.items.count { it.Id in ids }
                ids.isNotEmpty() && shared >= ids.size * DUPLICATE_ROW_OVERLAP
            }
            if (!duplicate) kept.add(row)
        }
        return kept
    }

    // "Comedy on Netflix" and the like, from the hub's own titles, best rated first.
    private fun genreRows(rows: List<ServiceRow>, genres: List<String>, serviceName: String): List<ServiceRow> {
        val titles = rows.flatMap { it.items }.distinctBy { it.Id }
        return genres.take(GENRE_ROW_COUNT).mapNotNull { genre ->
            val items = titles
                .filter { it.Genres?.contains(genre) == true }
                .sortedByDescending { it.CommunityRating ?: 0.0 }
                .take(SERVICE_ROW_LIMIT)
            if (items.size < GENRE_ROW_MIN_ITEMS) null
            else ServiceRow(title = com.jellio.tv.ui.i18n.Strings.format("service_genre_on", genre, serviceName), kind = MIXED_KIND, items = items, collectionId = "")
        }
    }

    suspend fun showcaseInfo(session: Session, item: BaseItemDto): com.jellio.tv.ui.home.ShowcaseInfo =
        com.jellio.tv.ui.home.loadShowcaseInfo(repository, session.userId, item)

    private fun topGenres(rows: List<ServiceRow>): List<String> {
        val counts = linkedMapOf<String, Int>()
        rows.forEach { row ->
            row.items.forEach { item ->
                item.Genres?.forEach { genre -> counts[genre] = (counts[genre] ?: 0) + 1 }
            }
        }
        return counts.filterValues { it >= MIN_GENRE_COUNT }
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(TOP_GENRES_LIMIT)
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
            runCatching { repository.deleteItem(item.Id) }.onSuccess { removeItemFromRows(item.Id) }
        }
    }

    private fun updateItem(itemId: String, transform: (BaseItemDto) -> BaseItemDto) {
        val state = _uiState.value
        _uiState.value = state.copy(
            rows = state.rows.map { row -> row.copy(items = row.items.map { if (it.Id == itemId) transform(it) else it }) },
        )
    }

    private fun removeItemFromRows(itemId: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            rows = state.rows.map { row -> row.copy(items = row.items.filterNot { it.Id == itemId }) },
        )
    }
}
