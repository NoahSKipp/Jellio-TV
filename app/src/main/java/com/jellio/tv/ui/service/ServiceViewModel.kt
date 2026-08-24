package com.jellio.tv.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.model.groupByService
import com.jellio.tv.data.model.rowTitle
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SERVICE_ROW_LIMIT = 24
private const val TOP_GENRES_LIMIT = 10
private const val MIN_GENRE_COUNT = 3

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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceUiState())
    val uiState: StateFlow<ServiceUiState> = _uiState.asStateFlow()

    private var loadedService: String? = null

    fun load(session: Session, serviceName: String) {
        if (loadedService == serviceName) return
        loadedService = serviceName
        viewModelScope.launch {
            _uiState.value = ServiceUiState(isLoading = true, serviceName = serviceName)
            try {
                val collections = repository.getCollections(session.userId)
                val matching = groupByService(collections)[serviceName].orEmpty()

                val rows = mutableListOf<ServiceRow>()
                var heroItem: BaseItemDto? = null
                var heroRating = -1.0

                matching.forEach { collection ->
                    val kind = repository.collectionKind(collection)
                    val items = try {
                        repository.getCollectionItems(session.userId, collection.Id, kind, SERVICE_ROW_LIMIT)
                    } catch (err: Exception) {
                        emptyList()
                    }
                    if (items.isEmpty()) return@forEach
                    rows.add(ServiceRow(title = rowTitle(collection, serviceName, kind), kind = kind, items = items, collectionId = collection.Id))
                    // Real screens/service.js's own pickHeroItem(): whichever
                    // real item across every matched collection carries a
                    // real backdrop and rates highest, not always row
                    // zero's own first card.
                    items.forEach { item ->
                        if (item.BackdropImageTags.isNullOrEmpty()) return@forEach
                        val rating = item.CommunityRating ?: 0.0
                        if (heroItem == null || rating > heroRating) {
                            heroItem = item
                            heroRating = rating
                        }
                    }
                }

                _uiState.value = ServiceUiState(
                    isLoading = false,
                    serviceName = serviceName,
                    heroItem = heroItem,
                    rows = rows,
                    topGenres = topGenres(rows),
                    canDeleteItems = repository.canDeleteItems(session.userId),
                )
            } catch (err: Exception) {
                _uiState.value = ServiceUiState(isLoading = false, error = err.message ?: "Could not load $serviceName", serviceName = serviceName)
            }
        }
    }

    // Real port of screens/service.js's own makeRowTitleClickable()
    // fetchAll callback: this row's own full real collection, past
    // whatever SERVICE_ROW_LIMIT already trimmed the row itself to.
    suspend fun fetchAllForRow(session: Session, row: ServiceRow): List<BaseItemDto> =
        repository.getCollectionItems(session.userId, row.collectionId, row.kind, ROW_LIST_LIMIT)

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
