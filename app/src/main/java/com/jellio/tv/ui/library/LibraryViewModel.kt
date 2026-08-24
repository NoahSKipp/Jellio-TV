package com.jellio.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.HomeSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val GENRE_ROWS = 6
private const val ROW_LIMIT = 20
private const val MAX_ANIME_ROWS = 8
private const val COVERFLOW_MIN_ITEMS = 3

// Only a real anime catalog collection whose own name actually says
// "trending" earns the coverflow below: JellioRepository's own
// isAnimeCollection() elsewhere just answers "does this collection
// belong on the Anime page at all", a much narrower real question.
private val TRENDING_ANIME_NAME = Regex("anilist.*trending|trending.*anilist", RegexOption.IGNORE_CASE)

private const val ANIME_EMPTY_MESSAGE =
    "No anime catalogs are configured on this server yet. Enable CreateCollection on an AniList catalog in Gelato to see it here."

data class LibraryUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val coverflowItems: List<BaseItemDto> = emptyList(),
    val coverflowBadge: String? = null,
    val sections: List<HomeSection> = emptyList(),
    val emptyMessage: String? = null,
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

    fun load(session: Session, library: BaseItemDto) {
        val key = library.Id + (library.Name ?: "")
        if (loadedFor == key) return
        loadedFor = key
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

        val sections = mutableListOf(HomeSection("Recently Added", mainItems))
        genreItemsDeferred.forEach { (genre, deferred) ->
            val items = deferred.await().filterNot { excludeIds.contains(it.Id) }
            if (items.isNotEmpty()) sections.add(HomeSection(genre, items))
        }

        _uiState.value = LibraryUiState(
            isLoading = false,
            title = library.Name ?: "Library",
            coverflowItems = coverflowItems,
            sections = sections,
        )
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
        val collections = runCatching { repository.getCollections(session.userId) }
            .getOrDefault(emptyList())
            .filter { repository.isAnimeCollection(it) }

        if (collections.isEmpty()) {
            _uiState.value = LibraryUiState(isLoading = false, title = "Anime", emptyMessage = ANIME_EMPTY_MESSAGE)
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

        val sections = itemLists.mapNotNull { (collection, items) ->
            if (collection.Id == trendingId || items.isEmpty()) null else HomeSection(collection.Name ?: "", items)
        }

        if (sections.isEmpty() && !coverflowIsTrending) {
            _uiState.value = LibraryUiState(isLoading = false, title = "Anime", emptyMessage = ANIME_EMPTY_MESSAGE)
            return@coroutineScope
        }

        _uiState.value = LibraryUiState(
            isLoading = false,
            title = "Anime",
            coverflowItems = coverflowSource,
            coverflowBadge = if (coverflowIsTrending) "Trending on AniList" else null,
            sections = sections,
        )
    }
}
