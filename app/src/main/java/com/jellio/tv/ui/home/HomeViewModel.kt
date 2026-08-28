package com.jellio.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.model.greetingText
import com.jellio.tv.data.model.groupByService
import com.jellio.tv.data.model.serviceOf
import com.jellio.tv.data.recommend.RecommendationDataSource
import com.jellio.tv.data.recommend.buildRecommendationRows
import com.jellio.tv.data.recommend.titleKey
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.watchnext.WatchNextSyncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

// fetchAll mirrors components/rowListModal.js's own options.fetchAll:
// a real second, unbounded request for this exact same row, only ever
// set for a catalog or genre row (a real Gelato collection or a real
// discovered genre, either one worth a real "browse everything" beyond
// its own real ROW_LIMIT), null for Continue Watching/Up Next and every
// recommendation row, same real distinction buildRow()'s own callers
// already draw.
// key mirrors components/homeCustomizer.js's own real row key scheme
// (wrapRowForCustomization's own second argument at every real call
// site in screens/home.js): 'continue-watching', 'up-next',
// 'catalog:<id>', 'genre:<name>', 'rec:<title>', stable across a
// reload the same way that file's own real keys are, title alone
// defaulting for a caller (Library, Service) that never sets one and
// has no customization concept to key against.
data class HomeSection(
    val title: String,
    val items: List<BaseItemDto>,
    val fetchAll: (suspend () -> List<BaseItemDto>)? = null,
    val key: String = title,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val heroItems: List<BaseItemDto> = emptyList(),
    // Real screens/home.js's own greetingText(): local device clock,
    // the reader's own real name when it resolves, "Welcome back"
    // real feedback found always wrong the moment it was actually
    // ever anything else.
    val greeting: String = "",
    // Every real row this screen can show, in this file's own real
    // build order (Continue Watching, Up Next, Coming Soon, Streaming
    // Services, then recommendation/catalog/genre rows), the same real
    // order screens/home.js's own wrapRowForCustomization() call sites
    // wrap in. One flat list, not four separate typed ones, so
    // HomeCustomization's reorder/hide below can work across all of
    // them the same way that file's own single #rows container does.
    val rows: List<HomeRow> = emptyList(),
    val customization: HomeCustomizationDto = HomeCustomizationDto(),
    // Real gate components/cardOptionsMenu.js's own header documents:
    // Jellyfin's own DELETE /Items/{id} only actually succeeds for an
    // admin or a user with their own real
    // Policy.EnableContentDeletion, fetched once here alongside the
    // rest of this screen's own load() rather than per card.
    val canDeleteItems: Boolean = false,
    val error: String? = null,
)

private const val COMING_SOON_LIMIT = 12
private const val CATALOG_ROW_LIMIT = 24
// Real screens/home.js's own ROW_LIST_LIMIT: components/rowListModal.js's
// own real "browse everything" fetchAll, a catalog or genre row's own
// full real depth rather than the row's own small ROW_LIMIT.
private const val ROW_LIST_LIMIT = 500
private const val MAX_CATALOG_ROWS = 8
private const val MIN_CATALOG_ITEMS = 3
// The anime library has a page of its own carrying every AniList
// catalog. One of them here is a taste of it, more than one is that
// page again in the wrong place.
private const val MAX_ANIME_CATALOG_ROWS = 1

private const val GENRE_ROWS = 4
private const val GENRE_ROW_LIMIT = 24

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

// Real port of screens/home.js's own buildHomeSections(), the same
// real order: Continue Watching, Up Next, Coming Soon (real
// CalendarController answer, same GET Jellio/calendar endpoint
// screens/calendar.js's own full page already uses), the Studio Hub
// strip (real service tiles, one per real service
// groupByService(collections) actually found, each opening its own
// real ServiceScreen), RecommendationEngine's own real rows,
// real Gelato catalog rows (Trending/Popular/Top Rated/..., a
// service's own collection filtered out since it already has a hub
// tile, ChildCount>=3, capped at MAX_CATALOG_ROWS with at most
// MAX_ANIME_CATALOG_ROWS from the anime library), then real genre rows
// (discoverGenres()/getGenreItems() scanning the whole real library,
// no per-library scope). No per-library "Recently Added" row anywhere
// on this screen, same real reason this whole file's own header
// comment gives: DateCreated means nothing on a Gelato server, every
// import lands at once, so real native home's own per-library row
// says nothing worth showing here either.
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val customizationStore: HomeCustomizationStore,
    private val watchNextSyncer: WatchNextSyncer,
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
                coroutineScope {
                    // Real port of screens/home.js's own buildHomeSections():
                    // every real fetch with no dependency on another's own
                    // result fires together (that file's own first
                    // Promise.allSettled), not one after another. A real
                    // perf bug found live testing on device: awaiting these
                    // one at a time used to add real seconds to every Home
                    // load, reported live as "loading takes forever".
                    val continueWatchingDeferred = async { runCatching { repository.getContinueWatching(session.userId) }.getOrDefault(emptyList()) }
                    val upNextDeferred = async { runCatching { repository.getNextUp(session.userId) }.getOrDefault(emptyList()) }
                    // Real web hero (runtime/api.js's own getHeroCandidates())
                    // never draws from Continue Watching at all, only a
                    // random real Movie/Series: fetched independently rather
                    // than reusing whatever led the rows above.
                    val heroCandidatesDeferred = async { runCatching { repository.getHeroCandidates(session.userId) }.getOrDefault(emptyList()) }
                    val comingSoonDeferred = async { runCatching { repository.getCalendarEntries().take(COMING_SOON_LIMIT) }.getOrDefault(emptyList()) }
                    val collectionsDeferred = async { runCatching { repository.getCollections(session.userId) }.getOrDefault(emptyList()) }
                    val userDeferred = async { runCatching { repository.getUser(session.userId) }.getOrNull() }
                    val customizationDeferred = async { runCatching { customizationStore.load() }.getOrDefault(HomeCustomizationDto()) }

                    val continueWatching = continueWatchingDeferred.await()
                    // Fired, not awaited: Google TV's own home Watch
                    // Next row is real background bookkeeping, not
                    // something this screen's own load has any reason
                    // to wait on before it can render.
                    launch { runCatching { watchNextSyncer.sync(session, continueWatching) } }
                    val upNext = upNextDeferred.await()
                    val heroCandidates = heroCandidatesDeferred.await()
                    val comingSoon = comingSoonDeferred.await()
                    val collections = collectionsDeferred.await()
                    val user = userDeferred.await()
                    val customization = customizationDeferred.await()

                    val studioHubs = groupByService(collections).keys.sorted()
                    val greeting = greetingText(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), user?.Name)

                    val recommendationSource = RecommendationDataSource(
                        getRecentlyCompleted = { limit -> repository.getRecentlyCompleted(session.userId, limit) },
                        getNextUp = { limit -> repository.getNextUp(session.userId, limit) },
                        getRecommendationCandidates = { seed, limit -> repository.getRecommendationCandidates(session.userId, seed, limit) },
                        getGenreItems = { genre, limit -> repository.getGenreItems(session.userId, null, "Movie,Series", genre, limit) },
                        getPersonItems = { personId, limit -> repository.getPersonItems(session.userId, personId, limit) },
                    )
                    // Shared across recommendation, catalog and genre rows,
                    // same real reasoning screens/home.js's own shared
                    // `seen` object documents: a title one row already
                    // picked should not also turn up further down the same
                    // page. Only the synchronous dedupe step below actually
                    // needs the three phases' own real priority order
                    // though, none of their own real network fetches depend
                    // on the other two at all, so all three fire together
                    // next, same real port of that file's own second
                    // Promise.all.
                    val exclude = mutableSetOf<String>()
                    val recommendationDeferred = async { runCatching { buildRecommendationRows(recommendationSource, exclude) }.getOrDefault(emptyList()) }
                    val catalogDataDeferred = async { runCatching { fetchCatalogRowData(session.userId, collections) }.getOrDefault(emptyList()) }
                    val genreDataDeferred = async { runCatching { fetchGenreRowData(session.userId) }.getOrDefault(emptyList()) }

                    val recommendationRows = recommendationDeferred.await()
                    val catalogData = catalogDataDeferred.await()
                    val genreData = genreDataDeferred.await()

                    val catalogRows = buildCatalogRowsFromData(session.userId, catalogData, exclude)
                    val genreRows = buildGenreRowsFromData(session.userId, genreData, exclude)

                    // Real port of screens/home.js's own real
                    // wrapRowForCustomization() call order: Continue
                    // Watching, Up Next, Coming Soon, Streaming
                    // Services, then every recommendation/catalog/genre
                    // row, same real sequence that file's own header
                    // comments document at each real call site.
                    val rows = buildList<HomeRow> {
                        if (continueWatching.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection("Continue Watching", continueWatching, key = "continue-watching"), landscape = true))
                        }
                        if (upNext.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection("Up Next", upNext, key = "up-next"), landscape = true))
                        }
                        if (comingSoon.isNotEmpty()) add(ComingSoonHomeRow(comingSoon))
                        if (studioHubs.isNotEmpty()) add(StudioHubsHomeRow(studioHubs))
                        recommendationRows.forEach { add(PosterHomeRow(it.copy(key = "rec:${it.title}"))) }
                        catalogRows.forEach { add(PosterHomeRow(it)) }
                        genreRows.forEach { add(PosterHomeRow(it)) }
                    }

                    val policy = user?.Policy
                    val canDeleteItems = policy != null && (policy.IsAdministrator || policy.EnableContentDeletion)

                    _uiState.value = HomeUiState(
                        isLoading = false,
                        heroItems = heroCandidates,
                        greeting = greeting,
                        rows = rows,
                        customization = customization,
                        canDeleteItems = canDeleteItems,
                    )
                }
            } catch (err: Exception) {
                _uiState.value = HomeUiState(isLoading = false, error = err.message ?: "Could not load Home")
            }
        }
    }

    // Real port of components/card.js's own buildCardActions() handlers:
    // an optimistic-free round trip to the real server, then patched
    // back into whichever real row(s) actually hold this item, same
    // real reasoning updateItem() below documents.
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

    // A real item can appear in more than one real row at once (a
    // recommendation row and a genre row both drawing from the same
    // real library), so a toggle patches every real row holding it
    // rather than just the one the real options menu was opened from.
    private fun updateItem(itemId: String, transform: (BaseItemDto) -> BaseItemDto) {
        val state = _uiState.value
        val updatedRows = state.rows.map { row ->
            if (row is PosterHomeRow && row.section.items.any { it.Id == itemId }) {
                row.copy(section = row.section.copy(items = row.section.items.map { if (it.Id == itemId) transform(it) else it }))
            } else {
                row
            }
        }
        _uiState.value = state.copy(rows = updatedRows)
    }

    // Real port of components/cardOptionsMenu.js's own toggleWatched()
    // real Continue Watching/Up Next branch: a real Remove there is
    // always a real mark-played call, never a toggle, since neither
    // row is reachable at all once its own title actually is played
    // (that same real call is also the only way real Jellyfin ever
    // clears a saved resume position or drops a title off NextUp, see
    // that function's own header). Dropped from this screen's own row
    // state directly rather than waiting on next real reload.
    fun removeFromRow(session: Session, item: BaseItemDto) {
        viewModelScope.launch {
            runCatching { repository.setPlayed(session.userId, item.Id, true) }.getOrNull() ?: return@launch
            removeItemFromRows(item.Id)
        }
    }

    // Real port of components/cardOptionsMenu.js's own "Remove Show from
    // Up Next": hides the whole series server side (Controllers/
    // NextUpHiddenController.cs) instead of removeFromRow's own
    // mark-played call above, which only ever advances this same series
    // to its own next episode rather than actually leaving Up Next. See
    // JellioRepository.getNextUp()'s own header for the real gap this
    // closes.
    fun hideShowFromRow(item: BaseItemDto) {
        val seriesId = item.SeriesId ?: return
        viewModelScope.launch {
            runCatching { repository.hideSeriesFromNextUp(seriesId) }.onSuccess {
                removeItemFromRows(item.Id)
            }
        }
    }

    // Real port of components/cardOptionsMenu.js's own Remove from
    // Library option: the confirm step itself already happened
    // (HomeScreen's own RemoveFromLibraryConfirm), this only fires
    // the real DELETE call and drops the item from every row holding
    // it, same real reasoning removeItemFromRows() above already
    // documents for a toggle.
    fun deleteItem(item: BaseItemDto) {
        viewModelScope.launch {
            runCatching { repository.deleteItem(item.Id) }
                .onSuccess { removeItemFromRows(item.Id) }
        }
    }

    private fun removeItemFromRows(itemId: String) {
        val state = _uiState.value
        val updatedRows = state.rows.map { row ->
            if (row is PosterHomeRow && row.section.items.any { it.Id == itemId }) {
                row.copy(section = row.section.copy(items = row.section.items.filterNot { it.Id == itemId }))
            } else {
                row
            }
        }
        _uiState.value = state.copy(rows = updatedRows)
    }

    // Real port of components/cardOptionsMenu.js's own
    // restartFromBeginning(): no real Jellyfin endpoint clears a saved
    // resume position on its own, marking played and immediately
    // unplayed again is the only real way to reset it back to 0
    // without leaving the title stuck flagged watched, same real gap
    // that function's own header (and removeFromRow's above) already
    // documents. Returns whether it actually succeeded so the caller
    // only navigates into real playback on a real success, same real
    // order that function's own .then(navigateTo) already keeps.
    suspend fun restartFromBeginning(session: Session, item: BaseItemDto): Boolean {
        return try {
            repository.setPlayed(session.userId, item.Id, true)
            val updated = repository.setPlayed(session.userId, item.Id, false)
            updateItem(item.Id) { it.copy(UserData = updated) }
            true
        } catch (err: Exception) {
            false
        }
    }

    // Real port of components/homeCustomizer.js's own real
    // buildRowBar() button handlers: HomeCustomization's own pure
    // effectiveOrder()/moveKey()/toggleHidden() do the actual work,
    // this just persists the result the same real way that file's own
    // saveHomeCustomization() does after every single call.
    fun moveRow(key: String, delta: Int) {
        val liveKeys = _uiState.value.rows.map { it.key }
        val updated = HomeCustomization.moveKey(_uiState.value.customization, liveKeys, key, delta)
        _uiState.value = _uiState.value.copy(customization = updated)
        viewModelScope.launch { customizationStore.save(updated) }
    }

    fun toggleRowHidden(key: String) {
        val updated = HomeCustomization.toggleHidden(_uiState.value.customization, key)
        _uiState.value = _uiState.value.copy(customization = updated)
        viewModelScope.launch { customizationStore.save(updated) }
    }

    fun resetCustomization() {
        val updated = HomeCustomizationDto()
        _uiState.value = _uiState.value.copy(customization = updated)
        viewModelScope.launch { customizationStore.reset() }
    }

    private data class CatalogRowEntry(val title: String, val items: List<BaseItemDto>, val collectionId: String, val kind: String)

    // Mirrors screens/home.js's own fetchCatalogRows(): the real fetch
    // phase only, no exclude touched here at all (real port of that
    // file's own comment: "fetching has no dependency on seen/exclude
    // at all"), so every real Gelato catalog collection's own real
    // network round trip fires together via Promise.all rather than
    // the second collection waiting on the first one's own response.
    // Real Gelato catalog collections (Trending, Popular, Top Rated,
    // ...), led by LEAD's own real order then by ChildCount, at most
    // MAX_ANIME_CATALOG_ROWS of them from the anime library (that
    // library has a page of its own carrying every one of those),
    // capped at MAX_CATALOG_ROWS total. A service's own collection is
    // filtered out here (serviceOf(name) != null): it already has a
    // real tile in the Studio Hub strip above and a real page of its
    // own behind that tile, same real reason fetchCatalogRows() itself
    // excludes it, a Netflix row directly under the Netflix tile would
    // be the same content twice.
    private suspend fun fetchCatalogRowData(userId: String, collections: List<BaseItemDto>): List<CatalogRowEntry> = coroutineScope {
        var usable = collections.filter { serviceOf(it.Name) == null && (it.ChildCount ?: 0) >= MIN_CATALOG_ITEMS }
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

        usable.map { collection ->
            async {
                val kind = repository.collectionKind(collection)
                val items = runCatching { repository.getCollectionItems(userId, collection.Id, kind, CATALOG_ROW_LIMIT) }.getOrDefault(emptyList())
                CatalogRowEntry(title = titleFor(collection.Name, kind), items = items, collectionId = collection.Id, kind = kind)
            }
        }.awaitAll()
    }

    // Mirrors screens/home.js's own buildCatalogRows(): the real
    // synchronous dedupe step, run only after fetchCatalogRowData's own
    // real network phase above (and its sibling recommendation/genre
    // phases) have all already resolved, same real priority order that
    // file's own comment documents.
    private fun buildCatalogRowsFromData(userId: String, data: List<CatalogRowEntry>, exclude: MutableSet<String>): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        data.forEach { entry ->
            val deduped = entry.items.filter { !exclude.contains(it.Id) && !exclude.contains(titleKey(it)) }
            deduped.forEach { item ->
                exclude.add(item.Id)
                exclude.add(titleKey(item))
            }
            if (deduped.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        title = entry.title,
                        items = deduped,
                        fetchAll = { repository.getCollectionItems(userId, entry.collectionId, entry.kind, ROW_LIST_LIMIT) },
                        key = "catalog:${entry.collectionId}",
                    ),
                )
            }
        }
        return sections
    }

    private data class GenreRowEntry(val genre: String, val items: List<BaseItemDto>)

    // Mirrors screens/home.js's own fetchGenreRows(): real genres
    // discoverGenres() finds enough of across the whole real library
    // (no parentId, unlike LibraryScreen's own per-library call), every
    // genre's own real item fetch fired together via Promise.all once
    // discoverGenres() itself resolves (a real data dependency, unlike
    // the catalog collections above), not one genre waiting on the
    // last.
    private suspend fun fetchGenreRowData(userId: String): List<GenreRowEntry> = coroutineScope {
        val genres = repository.discoverGenres(userId, null, "Movie,Series", GENRE_ROWS)
        genres.map { genre ->
            async {
                val items = runCatching { repository.getGenreItems(userId, null, "Movie,Series", genre, GENRE_ROW_LIMIT) }.getOrDefault(emptyList())
                GenreRowEntry(genre = genre, items = items)
            }
        }.awaitAll()
    }

    // Mirrors screens/home.js's own buildGenreRows(): last in line for
    // the shared exclude set, same real priority order that file's own
    // comment documents (recommendation rows first pick, then catalog,
    // genre rows last).
    private fun buildGenreRowsFromData(userId: String, data: List<GenreRowEntry>, exclude: MutableSet<String>): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        data.forEach { entry ->
            val deduped = entry.items.filter { !exclude.contains(it.Id) && !exclude.contains(titleKey(it)) }
            deduped.forEach { item ->
                exclude.add(item.Id)
                exclude.add(titleKey(item))
            }
            if (deduped.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        title = entry.genre,
                        items = deduped,
                        fetchAll = { repository.getGenreItems(userId, null, "Movie,Series", entry.genre, ROW_LIST_LIMIT) },
                        key = "genre:${entry.genre}",
                    ),
                )
            }
        }
        return sections
    }
}
