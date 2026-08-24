package com.jellio.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.ShowsEditorial
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.model.showsEditorial
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

private const val GENRE_ROWS = 6
private const val ROW_LIMIT = 20
private const val MAX_ANIME_ROWS = 8
private const val COVERFLOW_MIN_ITEMS = 3
// Real screens/library.js's own ROW_LIST_LIMIT: components/
// rowListModal.js's own "browse everything" cap, that file's own
// independently redeclared constant, kept the same way here rather
// than reusing HomeViewModel's or ServiceViewModel's own private copy.
private const val ROW_LIST_LIMIT = 500

// Only a real anime catalog collection whose own name actually says
// "trending" earns the coverflow below: JellioRepository's own
// isAnimeCollection() elsewhere just answers "does this collection
// belong on the Anime page at all", a much narrower real question.
private val TRENDING_ANIME_NAME = Regex("anilist.*trending|trending.*anilist", RegexOption.IGNORE_CASE)

private const val ANIME_EMPTY_MESSAGE =
    "No anime catalogs are configured on this server yet. Enable CreateCollection on an AniList catalog in Gelato to see it here."

// Real port of screens/library.js's own SORT_OPTIONS: governs only
// this page's own top row, the per genre rows below stay fixed
// "browse by genre" shortcuts either way, same real reason that
// file's own header comment gives.
data class LibrarySortOption(val value: String, val label: String)

val LIBRARY_SORT_OPTIONS = listOf(
    LibrarySortOption("DateCreated:Descending", "Recently added"),
    LibrarySortOption("SortName:Ascending", "Name (A-Z)"),
    LibrarySortOption("SortName:Descending", "Name (Z-A)"),
    LibrarySortOption("CommunityRating:Descending", "Top rated"),
    LibrarySortOption("PremiereDate:Descending", "Newest release"),
)

data class LibraryUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val coverflowItems: List<BaseItemDto> = emptyList(),
    val coverflowBadge: String? = null,
    val editorial: ShowsEditorial? = null,
    // The main, sort/genre-filterable row: kept apart from the fixed
    // per genre rows in sections below so changeSort()/changeGenre()
    // below can re-fetch just this one row, same real scope
    // screens/library.js's own loadMainRow() keeps.
    val mainRow: HomeSection? = null,
    val selectedSort: String = LIBRARY_SORT_OPTIONS.first().value,
    val selectedGenre: String? = null,
    val genreOptions: List<String> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val emptyMessage: String? = null,
    val canDeleteItems: Boolean = false,
)

// Real port of screens/library.js: a coverflow carousel (real random
// Movie/Series candidates scoped to this library, same real
// getHeroCandidates() call, below its own real MIN_SLIDES floor the
// carousel just does not mount) over a main "Recently added" row plus
// one row per real genre the library actually has enough of
// (discoverGenres, the same sampled-and-counted approach that file's
// own runtime/api.js sibling uses). Anime gets its own real dedicated
// path (loadAnime below), same real reasoning renderAnime()'s own
// header there documents.
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var loadedFor: String? = null
    // Kept for changeSort()/changeGenre() below, called well after
    // load()'s own real call: neither one wants to force a full real
    // reload (coverflow, genre rows, canDeleteItems) just to
    // re-fetch this one row with a different real sort/genre.
    private var currentLibrary: BaseItemDto? = null
    private var currentItemType: String? = null

    fun load(session: Session, library: BaseItemDto) {
        val key = library.Id + (library.Name ?: "")
        if (loadedFor == key) return
        loadedFor = key
        currentLibrary = library
        currentItemType = if (library.CollectionType == "movies") "Movie" else "Series"
        viewModelScope.launch {
            _uiState.value = LibraryUiState(isLoading = true, title = library.Name ?: "Library")
            if (repository.isAnimeLibrary(library)) {
                loadAnime(session)
            } else {
                loadLibrary(session, library)
            }
        }
    }

    // Real perf bug found live testing on device, fixed the same way
    // screens/library.js's own renderLibrary() already does (its own
    // Promise.all([getLibraryItems(...), excludeAnimeIds]) and
    // Promise.all([Promise.allSettled(genres.map(getGenreItems)),
    // excludeAnimeIds])): coverflow candidates, the main row, the
    // anime exclude set and every real genre's own item fetch now all
    // fire together instead of one after another, real seconds shaved
    // off every real library load that used to await each of these in
    // turn.
    private suspend fun loadLibrary(session: Session, library: BaseItemDto) = coroutineScope {
        val itemType = if (library.CollectionType == "movies") "Movie" else "Series"

        // Anime has no library of its own (see isAnimeLibrary's own
        // comment), so this is the only library kind with any real
        // overlap to worry about: real feedback's own direct ask was
        // "if possible show no anime at all in the Shows hub".
        val excludeIdsDeferred = async {
            if (library.CollectionType == "tvshows") repository.getAnimeItemIds(session.userId) else emptySet()
        }
        val coverflowItemsDeferred = async {
            runCatching { repository.getHeroCandidates(session.userId, limit = 8, parentId = library.Id) }.getOrDefault(emptyList())
        }
        val mainItemsDeferred = async {
            runCatching {
                repository.getLibraryItems(session.userId, library.Id, limit = ROW_LIMIT, includeItemTypes = itemType, sortBy = "DateCreated", sortOrder = "Descending")
            }.getOrDefault(emptyList())
        }
        val canDeleteDeferred = async { repository.canDeleteItems(session.userId) }
        // discoverGenres is a real data dependency for the per-genre
        // fetch below, same real reason it stays its own real await
        // before that one fires, unlike the three above.
        val genres = runCatching { repository.discoverGenres(session.userId, library.Id, itemType, GENRE_ROWS) }.getOrDefault(emptyList())
        val genreItemsDeferred = genres.map { genre ->
            genre to async {
                runCatching { repository.getGenreItems(session.userId, library.Id, itemType, genre, ROW_LIMIT) }.getOrDefault(emptyList())
            }
        }

        val excludeIds = excludeIdsDeferred.await()
        val coverflowItems = coverflowItemsDeferred.await()
        val mainItems = mainItemsDeferred.await().filterNot { excludeIds.contains(it.Id) }

        // Real port of screens/library.js's own buildRow(...,
        // fetchAll) calls for the main row and each genre row: neither
        // one re-applies the anime exclude set inside its own fetchAll
        // closure there either (only the initial row fetch above does),
        // same real (if perhaps unintended) behaviour kept here rather
        // than "fixed" beyond what that file actually does.
        val mainRow = HomeSection(
            sortLabel(LIBRARY_SORT_OPTIONS.first().value),
            mainItems,
            fetchAll = {
                repository.getLibraryItems(
                    session.userId,
                    library.Id,
                    limit = ROW_LIST_LIMIT,
                    includeItemTypes = itemType,
                    sortBy = "DateCreated",
                    sortOrder = "Descending",
                )
            },
        )
        val sections = mutableListOf<HomeSection>()
        genreItemsDeferred.forEach { (genre, deferred) ->
            val items = deferred.await().filterNot { excludeIds.contains(it.Id) }
            if (items.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        genre,
                        items,
                        fetchAll = { repository.getGenreItems(session.userId, library.Id, itemType, genre, ROW_LIST_LIMIT) },
                    ),
                )
            }
        }

        // Real feedback pointed at Harbor's own Shows tab, a mood-led
        // line above its carousel that changes with the reader's own
        // time of day: only the Shows library carries one, Movies has
        // no equivalent real reference screenshot behind it.
        val editorial = if (library.CollectionType == "tvshows") {
            showsEditorial(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        } else {
            null
        }

        _uiState.value = LibraryUiState(
            isLoading = false,
            title = library.Name ?: "Library",
            coverflowItems = coverflowItems,
            editorial = editorial,
            mainRow = mainRow,
            genreOptions = genres,
            sections = sections,
            canDeleteItems = canDeleteDeferred.await(),
        )
    }

    private fun sortLabel(value: String): String = LIBRARY_SORT_OPTIONS.firstOrNull { it.value == value }?.label ?: "Browse"

    // Real port of screens/library.js's own loadMainRow(): only this
    // one row re-fetches, the coverflow and every fixed per genre row
    // below stay exactly as load() above already built them, same
    // real scope that file's own header comment documents.
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
        val sortBy = parts.getOrElse(0) { "DateCreated" }
        val sortOrder = parts.getOrElse(1) { "Descending" }
        viewModelScope.launch {
            val items = runCatching {
                repository.getLibraryItems(session.userId, library.Id, limit = ROW_LIMIT, includeItemTypes = itemType, sortBy = sortBy, sortOrder = sortOrder, genre = genre)
            }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                mainRow = HomeSection(
                    genre ?: sortLabel(sort),
                    items,
                    fetchAll = {
                        repository.getLibraryItems(session.userId, library.Id, limit = ROW_LIST_LIMIT, includeItemTypes = itemType, sortBy = sortBy, sortOrder = sortOrder, genre = genre)
                    },
                ),
            )
        }
    }

    // Real port of screens/library.js's own renderAnime(): Anime has
    // no library of its own (Gelato resolves one global SeriesPath
    // for every series import), so only real anime/anilist catalog
    // collections can speak for this page, one row per collection,
    // same real "no fallback to the shared TV library" rule that
    // file's own header documents. Falling back used to mean the
    // whole TV catalog rendered here under an "Anime" heading (every
    // non-anime show included), reported live against a screenshot;
    // showing nothing is the honest outcome when no catalog is
    // configured, not a copy of the Shows page.
    private suspend fun loadAnime(session: Session) = coroutineScope {
        val canDeleteDeferred = async { repository.canDeleteItems(session.userId) }
        val collections = runCatching { repository.getCollections(session.userId) }
            .getOrDefault(emptyList())
            .filter { repository.isAnimeCollection(it) }

        if (collections.isEmpty()) {
            _uiState.value = LibraryUiState(isLoading = false, title = "Anime", emptyMessage = ANIME_EMPTY_MESSAGE, canDeleteItems = canDeleteDeferred.await())
            return@coroutineScope
        }

        val picked = collections.take(MAX_ANIME_ROWS)
        // Real perf bug found live, same real fix renderAnime()'s own
        // Promise.allSettled(animeCollections.map(...)) already uses:
        // every real anime catalog's own item fetch fires together
        // instead of the second catalog waiting on the first one's own
        // response.
        val itemLists = picked.map { collection ->
            collection to async { runCatching { repository.getCollectionItems(session.userId, collection.Id, "tvshows", ROW_LIMIT) }.getOrDefault(emptyList()) }
        }.map { (collection, deferred) -> collection to deferred.await() }

        // Real feedback: the coverflow used to feature whichever
        // catalog actually had the most real items behind it, no real
        // tie to whatever "Trending on AniList" badge happened to
        // land on a row further down. The real Trending catalog, when
        // this server actually has one, gets the coverflow now
        // exclusively, and is skipped as its own row below once it
        // becomes the hero instead (real feedback's own "two
        // carousels" complaint), same real COVERFLOW_MIN_ITEMS floor
        // components/libraryCoverflow.js's own MIN_SLIDES already
        // enforces so a coverflow never mounts with nothing behind it.
        var coverflowSource = emptyList<BaseItemDto>()
        var coverflowIsTrending = false
        var trendingId: String? = null
        itemLists.forEach { (collection, items) ->
            val name = collection.Name ?: ""
            if (TRENDING_ANIME_NAME.containsMatchIn(name) && items.size >= COVERFLOW_MIN_ITEMS) {
                trendingId = collection.Id
                coverflowSource = items
                coverflowIsTrending = true
            } else if (!coverflowIsTrending && items.size > coverflowSource.size) {
                coverflowSource = items
            }
        }

        // Real port of that file's own buildRow(collectionName, items,
        // null, fetchAll) call for each anime catalog row: real
        // getCollectionItems(collectionId, collectionType,
        // ROW_LIST_LIMIT), collectionType fixed to "tvshows" the same
        // way the initial per-catalog fetch above already is.
        val sections = itemLists.mapNotNull { (collection, items) ->
            if (collection.Id == trendingId || items.isEmpty()) {
                null
            } else {
                HomeSection(
                    collection.Name ?: "",
                    items,
                    fetchAll = { repository.getCollectionItems(session.userId, collection.Id, "tvshows", ROW_LIST_LIMIT) },
                )
            }
        }

        if (sections.isEmpty() && !coverflowIsTrending) {
            _uiState.value = LibraryUiState(isLoading = false, title = "Anime", emptyMessage = ANIME_EMPTY_MESSAGE, canDeleteItems = canDeleteDeferred.await())
            return@coroutineScope
        }

        _uiState.value = LibraryUiState(
            isLoading = false,
            title = "Anime",
            coverflowItems = coverflowSource,
            coverflowBadge = if (coverflowIsTrending) "Trending on AniList" else null,
            sections = sections,
            canDeleteItems = canDeleteDeferred.await(),
        )
    }

    // Real port of components/cardOptionsMenu.js's own Watchlist/Mark
    // Watched/Remove from Library options, same real handlers
    // HomeViewModel's own toggleWatchlist()/toggleWatched()/
    // deleteItem() already give the home screen, this screen's own
    // sections just live as a plain List<HomeSection> rather than the
    // sealed HomeRow hierarchy Home's own rows need for Continue
    // Watching/Up Next's own real different card shapes.
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
