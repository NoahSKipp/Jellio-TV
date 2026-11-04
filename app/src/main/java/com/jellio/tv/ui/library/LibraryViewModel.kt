package com.jellio.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.i18n.genreDisplayName
import com.jellio.tv.data.model.ShowsEditorial
import com.jellio.tv.data.model.animeTimeOfDay
import com.jellio.tv.data.model.moviesTimeOfDay
import com.jellio.tv.data.model.score
import com.jellio.tv.data.model.showsTimeOfDay
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.model.showsEditorial
import com.jellio.tv.data.cache.LibrarySnapshot
import com.jellio.tv.data.cache.RowSnapshotStore
import com.jellio.tv.data.cache.toCachedRowDto
import com.jellio.tv.data.cache.toHomeSection
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import javax.inject.Inject

private const val GENRE_ROWS = 6
private const val ROW_LIMIT = 20
private const val MAX_ANIME_ROWS = 8
private const val COVERFLOW_MIN_ITEMS = 3
private const val ROW_LIST_LIMIT = 500
// A themed row needs this many matching titles to show.
private const val MIN_THEMED_ITEMS = 6
private const val ANIME_THEME_POOL = 150
// How long one row's request may hold up the page before it is skipped.
private const val ROW_TIMEOUT_MS = 15_000L
private const val THEMED_TIMEOUT_MS = 8_000L

private fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

private val TRENDING_ANIME_NAME = Regex("anilist.*trending|trending.*anilist", RegexOption.IGNORE_CASE)

data class LibrarySortOption(val value: String, val label: String, val key: String = "")

val LIBRARY_SORT_OPTIONS = listOf(
    LibrarySortOption("CommunityRating:Descending", "Top rated", "library_sort_top_rated"),
    LibrarySortOption("PremiereDate:Descending", "Newest release", "library_sort_newest"),
)

data class LibraryUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val coverflowItems: List<BaseItemDto> = emptyList(),
    val coverflowBadge: String? = null,
    val editorial: ShowsEditorial? = null,
    val mainRow: HomeSection? = null,
    val selectedSort: String = LIBRARY_SORT_OPTIONS.first().value,
    val selectedGenre: String? = null,
    val genreOptions: List<String> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val emptyMessage: String? = null,
    val canDeleteItems: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val rowSnapshotStore: RowSnapshotStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var loadedFor: String? = null
    private var currentLibrary: BaseItemDto? = null
    private var currentItemType: String? = null

    fun load(session: Session, library: BaseItemDto) {
        val key = "${session.userId}:${library.Id}:${com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage.code}"
        val isReturningVisit = loadedFor == key
        loadedFor = key
        currentLibrary = library
        currentItemType = if (library.CollectionType == "movies") "Movie" else "Series"
        viewModelScope.launch {
            val libTitle = if (repository.isAnimeLibrary(library)) {
                com.jellio.tv.ui.i18n.Strings.get("library_anime_title")
            } else {
                com.jellio.tv.ui.i18n.libraryDisplayName(library.Name, library.CollectionType)
            }
            if (!isReturningVisit) {
                val cached = rowSnapshotStore.loadLibrarySnapshot(session.userId, library.Id)
                if (cached != null && (cached.coverflowItems.isNotEmpty() || cached.sections.isNotEmpty())) {
                    _uiState.value = LibraryUiState(
                        isLoading = false,
                        title = cached.title.ifEmpty { libTitle },
                        coverflowItems = cached.coverflowItems,
                        coverflowBadge = cached.coverflowBadge,
                        mainRow = cached.mainRowTitle?.let { HomeSection(it, cached.mainRowItems) },
                        sections = cached.sections.map { it.toHomeSection() },
                        genreOptions = cached.genreOptions,
                        selectedSort = cached.selectedSort,
                        selectedGenre = cached.selectedGenre,
                    )
                } else {
                    _uiState.value = LibraryUiState(isLoading = true, title = libTitle)
                }
            }
            if (repository.isAnimeLibrary(library)) {
                loadAnime(session, library.Id)
            } else {
                loadLibrary(session, library)
            }
        }
    }

    private suspend fun loadLibrary(session: Session, library: BaseItemDto) = coroutineScope {
        val itemType = if (library.CollectionType == "movies") "Movie" else "Series"

        val excludeIdsDeferred = async { repository.getAnimeIds() }
        val allGenresDeferred = async {
            runCatching { repository.getAllGenres(session.userId, library.Id, itemType) }.getOrDefault(emptyList())
        }
        val coverflowItemsDeferred = async {
            runCatching { repository.getHeroCandidates(session.userId, limit = 8, parentId = library.Id) }.getOrDefault(emptyList())
        }
        val theme = if (library.CollectionType == "movies") moviesTimeOfDay(currentHour()) else showsTimeOfDay(currentHour())
        val themedDeferred = async {
            runCatching {
                repository.getLibraryItems(
                    session.userId,
                    library.Id,
                    limit = ROW_LIMIT * 2,
                    includeItemTypes = itemType,
                    sortBy = "Random",
                    sortOrder = "Ascending",
                    genre = theme.genres.joinToString("|"),
                )
            }.getOrDefault(emptyList())
        }
        val defaultSort = LIBRARY_SORT_OPTIONS.first().value.split(":")
        val mainItemsDeferred = async {
            runCatching {
                repository.getLibraryItems(session.userId, library.Id, limit = ROW_LIMIT, includeItemTypes = itemType, sortBy = defaultSort[0], sortOrder = defaultSort[1])
            }.getOrDefault(emptyList())
        }
        val genresDeferred = async {
            runCatching { repository.discoverGenres(session.userId, library.Id, itemType, GENRE_ROWS) }.getOrDefault(emptyList())
        }
        val progressive = _uiState.value.isLoading

        val excludeIds = withTimeoutOrNull(ROW_TIMEOUT_MS) { excludeIdsDeferred.await() }.orEmpty()
        animeIds = excludeIds
        val mainItems = withTimeoutOrNull(ROW_TIMEOUT_MS) { mainItemsDeferred.await() }.orEmpty().filterNot { isAnime(it) }
        val mainRow = HomeSection(
            sortLabel(LIBRARY_SORT_OPTIONS.first().value),
            mainItems,
            fetchAll = {
                repository.getLibraryItems(
                    session.userId,
                    library.Id,
                    limit = ROW_LIST_LIMIT,
                    includeItemTypes = itemType,
                    sortBy = defaultSort[0],
                    sortOrder = defaultSort[1],
                ).filterNot { isAnime(it) }
            },
        )
        val libTitle = com.jellio.tv.ui.i18n.libraryDisplayName(library.Name, library.CollectionType)
        // Nothing on screen yet: rows go up as they arrive instead of all at
        // the end, and any one request that hangs is skipped.
        fun publish(coverflow: List<BaseItemDto>, badge: String?, sections: List<HomeSection>) {
            if (!progressive) return
            _uiState.value = LibraryUiState(
                isLoading = false,
                title = libTitle,
                coverflowItems = coverflow,
                coverflowBadge = badge,
                mainRow = mainRow,
                sections = sections,
            )
        }
        publish(emptyList(), null, emptyList())

        val themed = withTimeoutOrNull(THEMED_TIMEOUT_MS) { themedDeferred.await() }.orEmpty().filterNot { isAnime(it) }.take(ROW_LIMIT)
        val useThemed = themed.size >= MIN_THEMED_ITEMS
        val coverflowItems = if (useThemed) themed else withTimeoutOrNull(THEMED_TIMEOUT_MS) { coverflowItemsDeferred.await() }.orEmpty().filterNot { isAnime(it) }
        val featuredTitle = if (useThemed) theme.title else null
        publish(coverflowItems, featuredTitle, emptyList())

        val genres = withTimeoutOrNull(ROW_TIMEOUT_MS) { genresDeferred.await() }.orEmpty()
        val genreItemsDeferred = genres.map { genre ->
            genre to async {
                runCatching { repository.getGenreItems(session.userId, library.Id, itemType, genre, ROW_LIMIT) }.getOrDefault(emptyList())
            }
        }
        val sections = mutableListOf<HomeSection>()
        genreItemsDeferred.forEach { (genre, deferred) ->
            val items = withTimeoutOrNull(ROW_TIMEOUT_MS) { deferred.await() }.orEmpty().filterNot { isAnime(it) }
            if (items.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        genreDisplayName(genre),
                        items,
                        fetchAll = { repository.getGenreItems(session.userId, library.Id, itemType, genre, ROW_LIST_LIMIT).filterNot { isAnime(it) } },
                        fetchPage = { start, size -> repository.getGenreItemsPage(session.userId, library.Id, itemType, genre, start, size).filterNot { isAnime(it) } },
                    ),
                )
                publish(coverflowItems, featuredTitle, sections.toList())
            }
        }

        val allGenres = withTimeoutOrNull(ROW_TIMEOUT_MS) { allGenresDeferred.await() }.orEmpty().ifEmpty { genres }
        _uiState.value = LibraryUiState(
            isLoading = false,
            title = libTitle,
            coverflowItems = coverflowItems,
            coverflowBadge = featuredTitle,
            mainRow = mainRow,
            // Every genre the library has, not just the few with rows.
            genreOptions = allGenres,
            sections = sections,
        )
        val snapshot = LibrarySnapshot(
            title = libTitle,
            coverflowItems = coverflowItems,
            coverflowBadge = featuredTitle,
            mainRowTitle = mainRow?.title,
            mainRowItems = mainRow?.items ?: emptyList(),
            sections = sections.map { it.toCachedRowDto() },
            genreOptions = allGenres,
        )
        rowSnapshotStore.saveLibrarySnapshot(session.userId, library.Id, snapshot)
    }

    private var animeIds: Set<String> = emptySet()

    private fun isAnime(item: BaseItemDto): Boolean =
        animeIds.isNotEmpty() && item.Id.replace("-", "").lowercase() in animeIds

    private fun sortLabel(value: String): String = LIBRARY_SORT_OPTIONS.firstOrNull { it.value == value }?.let { com.jellio.tv.ui.i18n.Strings.get(it.key) } ?: com.jellio.tv.ui.i18n.Strings.get("library_sort_browse")

    fun changeSort(session: Session, sort: String) {
        val library = currentLibrary ?: return
        _uiState.value = _uiState.value.copy(selectedSort = sort)
        reloadMainRow(session, library, sort, _uiState.value.selectedGenre)
    }

    fun changeGenre(session: Session, genre: String?) {
        val library = currentLibrary ?: return
        _uiState.value = _uiState.value.copy(selectedGenre = genre)
        reloadMainRow(session, library, _uiState.value.selectedSort, genre)
    }

    private fun reloadMainRow(session: Session, library: BaseItemDto, sort: String, genre: String?) {
        val itemType = currentItemType ?: return
        val parts = sort.split(":")
        val sortBy = parts.getOrElse(0) { "CommunityRating" }
        val sortOrder = parts.getOrElse(1) { "Descending" }
        viewModelScope.launch {
            val items = runCatching {
                repository.getLibraryItems(session.userId, library.Id, limit = ROW_LIMIT, includeItemTypes = itemType, sortBy = sortBy, sortOrder = sortOrder, genre = genre)
            }.getOrDefault(emptyList()).filterNot { isAnime(it) }
            _uiState.value = _uiState.value.copy(
                mainRow = HomeSection(
                    genre?.let { genreDisplayName(it) } ?: sortLabel(sort),
                    items,
                    fetchAll = {
                        repository.getLibraryItems(session.userId, library.Id, limit = ROW_LIST_LIMIT, includeItemTypes = itemType, sortBy = sortBy, sortOrder = sortOrder, genre = genre)
                            .filterNot { isAnime(it) }
                    },
                ),
            )
        }
    }

    private suspend fun loadAnime(session: Session, libraryId: String) = coroutineScope {
        val collections = runCatching { repository.getCollections(session.userId) }
            .getOrDefault(emptyList())
            .filter { repository.isAnimeCollection(it) }

        if (collections.isEmpty()) {
            _uiState.value = LibraryUiState(isLoading = false, title = com.jellio.tv.ui.i18n.Strings.get("library_anime_title"), emptyMessage = com.jellio.tv.ui.i18n.Strings.get("library_anime_empty"))
            return@coroutineScope
        }

        val picked = collections.take(MAX_ANIME_ROWS)
        val theme = animeTimeOfDay(currentHour())
        val animeIdsDeferred = async { repository.getAnimeIds() }
        val themedPoolDeferred = async {
            runCatching {
                repository.getLibraryItems(
                    session.userId,
                    libraryId,
                    limit = ANIME_THEME_POOL,
                    includeItemTypes = "Series",
                    sortBy = "Random",
                    sortOrder = "Ascending",
                    genre = theme.genres.joinToString("|"),
                )
            }.getOrDefault(emptyList())
        }
        val progressive = _uiState.value.isLoading
        val fetches = picked.map { collection ->
            collection to async { runCatching { repository.getCollectionItems(session.userId, collection.Id, "tvshows", ROW_LIMIT) }.getOrDefault(emptyList()) }
        }

        // Rows go up one by one in catalog order as each answers, AniList
        // Trending first as its own row; a catalog that hangs is skipped.
        var coverflowSource = emptyList<BaseItemDto>()
        val itemLists = mutableListOf<Pair<BaseItemDto, List<BaseItemDto>>>()
        val sections = mutableListOf<HomeSection>()
        fetches.forEach { (collection, deferred) ->
            val items = withTimeoutOrNull(ROW_TIMEOUT_MS) { deferred.await() }.orEmpty()
            itemLists += collection to items
            if (items.isEmpty()) return@forEach
            if (coverflowSource.isEmpty() && TRENDING_ANIME_NAME.containsMatchIn(collection.Name ?: "") && items.size >= COVERFLOW_MIN_ITEMS) {
                coverflowSource = items
            } else {
                sections += HomeSection(
                    collection.Name ?: "",
                    items,
                    fetchAll = { repository.getCollectionItems(session.userId, collection.Id, "tvshows", ROW_LIST_LIMIT) },
                    fetchPage = { start, size -> repository.getCollectionItems(session.userId, collection.Id, "tvshows", size, start) },
                )
            }
            if (progressive) {
                _uiState.value = LibraryUiState(
                    isLoading = false,
                    title = com.jellio.tv.ui.i18n.Strings.get("library_anime_title"),
                    coverflowItems = coverflowSource,
                    coverflowBadge = if (coverflowSource.isNotEmpty()) com.jellio.tv.ui.i18n.Strings.get("library_trending_anilist") else null,
                    sections = sections.toList(),
                )
            }
        }

        val ids = withTimeoutOrNull(ROW_TIMEOUT_MS) { animeIdsDeferred.await() }.orEmpty()
        val genreMatched = withTimeoutOrNull(THEMED_TIMEOUT_MS) { themedPoolDeferred.await() }.orEmpty()
            .filter { it.Id.replace("-", "").lowercase() in ids }
        val genreMatchedIds = genreMatched.map { it.Id }.toSet()
        val pool = (genreMatched + itemLists.flatMap { it.second }).distinctBy { it.Id }
            .shuffled(kotlin.random.Random(Calendar.getInstance().get(Calendar.DAY_OF_YEAR)))
        val themed = pool.map { it to theme.score(it) + if (it.Id in genreMatchedIds) 1 else 0 }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(ROW_LIMIT)
        val sectionsWithTheme = if (themed.size >= MIN_THEMED_ITEMS) listOf(HomeSection(theme.title, themed)) + sections else sections.toList()

        if (sectionsWithTheme.isEmpty() && coverflowSource.isEmpty()) {
            _uiState.value = LibraryUiState(isLoading = false, title = com.jellio.tv.ui.i18n.Strings.get("library_anime_title"), emptyMessage = com.jellio.tv.ui.i18n.Strings.get("library_anime_empty"))
            return@coroutineScope
        }

        val badge = if (coverflowSource.isNotEmpty()) com.jellio.tv.ui.i18n.Strings.get("library_trending_anilist") else null
        _uiState.value = LibraryUiState(
            isLoading = false,
            title = com.jellio.tv.ui.i18n.Strings.get("library_anime_title"),
            coverflowItems = coverflowSource,
            coverflowBadge = badge,
            sections = sectionsWithTheme,
        )
        val snapshot = LibrarySnapshot(
            title = com.jellio.tv.ui.i18n.Strings.get("library_anime_title"),
            coverflowItems = coverflowSource,
            coverflowBadge = badge,
            sections = sectionsWithTheme.map { it.toCachedRowDto() },
        )
        rowSnapshotStore.saveLibrarySnapshot(session.userId, libraryId, snapshot)
    }

    suspend fun showcaseInfo(session: Session, item: BaseItemDto): com.jellio.tv.ui.home.ShowcaseInfo =
        com.jellio.tv.ui.home.loadShowcaseInfo(repository, session.userId, item)

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
            runCatching { repository.deleteItem(item.Id) }.onSuccess { removeItemFromSections(item.Id) }
        }
    }

    private fun updateItem(itemId: String, transform: (BaseItemDto) -> BaseItemDto) {
        val state = _uiState.value
        _uiState.value = state.copy(
            mainRow = state.mainRow?.let { row ->
                if (row.items.any { it.Id == itemId }) row.copy(items = row.items.map { if (it.Id == itemId) transform(it) else it }) else row
            },
            sections = state.sections.map { section ->
                if (section.items.any { it.Id == itemId }) {
                    section.copy(items = section.items.map { if (it.Id == itemId) transform(it) else it })
                } else {
                    section
                }
            },
        )
    }

    private fun removeItemFromSections(itemId: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            mainRow = state.mainRow?.let { row ->
                if (row.items.any { it.Id == itemId }) row.copy(items = row.items.filterNot { it.Id == itemId }) else row
            },
            sections = state.sections.map { section ->
                if (section.items.any { it.Id == itemId }) section.copy(items = section.items.filterNot { it.Id == itemId }) else section
            },
        )
    }
}
