package com.jellio.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.data.recommend.RecommendationDataSource
import com.jellio.tv.data.recommend.buildRecommendationRows
import com.jellio.tv.data.recommend.titleKey
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeSection(val title: String, val items: List<BaseItemDto>)

data class HomeUiState(
    val isLoading: Boolean = true,
    val heroItem: BaseItemDto? = null,
    // Continue Watching then Up Next, kept separate from `sections`
    // below only so ComingSoonRow can render between the two groups,
    // the real order screens/home.js's own buildHomeSections() uses.
    val leadingSections: List<HomeSection> = emptyList(),
    val comingSoon: List<CalendarEntryDto> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val error: String? = null,
)

private const val COMING_SOON_LIMIT = 12
private const val CATALOG_ROW_LIMIT = 24
private const val MAX_CATALOG_ROWS = 8
private const val MIN_CATALOG_ITEMS = 3
// The anime library has a page of its own carrying every AniList
// catalog. One of them here is a taste of it, more than one is that
// page again in the wrong place.
private const val MAX_ANIME_CATALOG_ROWS = 1

// Catalogs worth leading with, in this order. Anything unlisted keeps
// its own alphabetical order behind them.
private val LEAD = listOf("trending", "popular", "top rated", "new releases")
private val GENERIC_NAME = Regex("^(trending|popular|top rated)$", RegexOption.IGNORE_CASE)

private fun leadIndex(name: String?): Int {
    val index = LEAD.indexOf((name ?: "").lowercase())
    return if (index == -1) LEAD.size else index
}

// "Trending" alone on a page that can carry a movie one and a series
// one of the same name says nothing about which is which: only these
// three generic names get a kind suffix, everything else already has
// a real name (a catalog's own configured title).
private fun titleFor(name: String?, kind: String): String {
    if (name == null || !GENERIC_NAME.matches(name)) return name ?: ""
    return if (kind == "tvshows") "$name Series" else "$name Movies"
}

// Real per-library rows plus Continue Watching/Up Next/Coming Soon/
// recommendation/catalog rows, mirroring screens/home.js's own
// buildHomeSections() at a reduced but real scale: Continue Watching,
// Up Next, Coming Soon (real CalendarController answer, same
// GET Jellio/calendar endpoint screens/calendar.js's own full page
// already uses), RecommendationEngine's own real rows next, then real
// Gelato catalog rows (Trending/Popular/Top Rated/a service's own row,
// ChildCount>=3, capped at MAX_CATALOG_ROWS with at most
// MAX_ANIME_CATALOG_ROWS from the anime library), per-library rows
// last, this app's own stand in for screens/home.js's own real Studio
// Hub strip and genre rows until those earn their own real row here.
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadedForUserId: String? = null

    fun load(session: Session) {
        if (loadedForUserId == session.userId) return
        loadedForUserId = session.userId
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)
            try {
                val libraries = repository.getLibraries(session.userId)
                    .filter { it.CollectionType == "movies" || it.CollectionType == "tvshows" }

                val continueWatching = repository.getContinueWatching(session.userId)
                val upNext = try {
                    repository.getNextUp(session.userId)
                } catch (err: Exception) {
                    emptyList()
                }

                // Real web hero (runtime/api.js's own getHeroCandidates())
                // never draws from Continue Watching at all, only a
                // random real Movie/Series: fetched independently rather
                // than reusing whatever led the rows above.
                val heroCandidates = try {
                    repository.getHeroCandidates(session.userId)
                } catch (err: Exception) {
                    emptyList()
                }

                val comingSoon = try {
                    repository.getCalendarEntries().take(COMING_SOON_LIMIT)
                } catch (err: Exception) {
                    emptyList()
                }

                val recommendationSource = RecommendationDataSource(
                    getRecentlyCompleted = { limit -> repository.getRecentlyCompleted(session.userId, limit) },
                    getNextUp = { limit -> repository.getNextUp(session.userId, limit) },
                    getRecommendationCandidates = { seed, limit -> repository.getRecommendationCandidates(session.userId, seed, limit) },
                    getGenreItems = { genre, limit -> repository.getGenreItems(session.userId, null, "Movie,Series", genre, limit) },
                    getPersonItems = { personId, limit -> repository.getPersonItems(session.userId, personId, limit) },
                )
                // Shared across recommendation and per-library rows, same
                // real reasoning screens/home.js's own shared `seen` object
                // documents: a title one row already picked should not
                // also turn up further down the same page.
                val exclude = mutableSetOf<String>()
                val recommendationRows = try {
                    buildRecommendationRows(recommendationSource, exclude)
                } catch (err: Exception) {
                    emptyList()
                }

                val catalogRows = try {
                    buildCatalogRows(session.userId, exclude)
                } catch (err: Exception) {
                    emptyList()
                }

                val libraryRows = libraries.map { library ->
                    val items = repository.getLibraryItems(session.userId, library.Id)
                        .filter { !exclude.contains(it.Id) && !exclude.contains(titleKey(it)) }
                    HomeSection(title = library.Name ?: "Library", items = items)
                }

                val leadingSections = buildList {
                    if (continueWatching.isNotEmpty()) {
                        add(HomeSection("Continue Watching", continueWatching))
                    }
                    if (upNext.isNotEmpty()) {
                        add(HomeSection("Up Next", upNext))
                    }
                }

                val sections = buildList {
                    addAll(recommendationRows)
                    addAll(catalogRows)
                    addAll(libraryRows.filter { it.items.isNotEmpty() })
                }

                val hero = heroCandidates.firstOrNull()

                _uiState.value = HomeUiState(
                    isLoading = false,
                    heroItem = hero,
                    leadingSections = leadingSections,
                    comingSoon = comingSoon,
                    sections = sections,
                )
            } catch (err: Exception) {
                _uiState.value = HomeUiState(isLoading = false, error = err.message ?: "Could not load Home")
            }
        }
    }

    // Mirrors screens/home.js's own fetchCatalogRows()/buildCatalogRows():
    // real Gelato catalog collections (Trending, Popular, Top Rated, a
    // service's own row, ...), led by LEAD's own real order then by
    // ChildCount, at most MAX_ANIME_CATALOG_ROWS of them from the anime
    // library (that library has a page of its own carrying every one of
    // those), capped at MAX_CATALOG_ROWS total. A service's own
    // collection is not filtered out here yet (screens/home.js's own
    // real reason is that it already has a tile in the Studio Hub strip,
    // not built here yet either); once that strip lands here too, this
    // filter lands with it rather than hiding those catalogs with
    // nowhere else on this screen to find them in the meantime.
    private suspend fun buildCatalogRows(userId: String, exclude: MutableSet<String>): List<HomeSection> {
        val collections = repository.getCollections(userId)
        var usable = collections.filter { (it.ChildCount ?: 0) >= MIN_CATALOG_ITEMS }
        usable = usable.sortedWith(
            compareBy<BaseItemDto> { leadIndex(it.Name) }.thenByDescending { it.ChildCount ?: 0 },
        )

        var animeSeen = 0
        usable = usable.filter { collection ->
            if (!repository.isAnimeCollection(collection)) return@filter true
            animeSeen += 1
            animeSeen <= MAX_ANIME_CATALOG_ROWS
        }

        usable = usable.take(MAX_CATALOG_ROWS)

        val sections = mutableListOf<HomeSection>()
        usable.forEach { collection ->
            val kind = repository.collectionKind(collection)
            val items = try {
                repository.getCollectionItems(userId, collection.Id, kind, CATALOG_ROW_LIMIT)
            } catch (err: Exception) {
                emptyList()
            }
            val deduped = items.filter { !exclude.contains(it.Id) && !exclude.contains(titleKey(it)) }
            deduped.forEach { item ->
                exclude.add(item.Id)
                exclude.add(titleKey(item))
            }
            if (deduped.isNotEmpty()) {
                sections.add(HomeSection(title = titleFor(collection.Name, kind), items = deduped))
            }
        }
        return sections
    }
}
