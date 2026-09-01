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
import com.jellio.tv.data.cache.HomeSnapshot
import com.jellio.tv.data.cache.RowSnapshotStore
import com.jellio.tv.data.cache.toCachedRowDto
import com.jellio.tv.data.cache.toHomeRow
import com.jellio.tv.data.prefs.RecommendationPreferences
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.watchnext.WatchNextSyncer
import com.jellio.tv.ui.seasonal.activeSeasonalTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@androidx.compose.runtime.Immutable
data class HomeSection(
    val title: String,
    val items: List<BaseItemDto>,
    val fetchAll: (suspend () -> List<BaseItemDto>)? = null,
    val key: String = title,
    // One page (start, size) of the full row, so View all keeps loading
    // as it is scrolled instead of fetching everything up front.
    val fetchPage: (suspend (Int, Int) -> List<BaseItemDto>)? = null,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val heroItems: List<BaseItemDto> = emptyList(),
    val greeting: String = "",
    val rows: List<HomeRow> = emptyList(),
    val customization: HomeCustomizationDto = HomeCustomizationDto(),
    val canDeleteItems: Boolean = false,
    val error: String? = null,
    // More rows are still loading below the ones shown (skeletons there).
    val loadingMore: Boolean = false,
)

// Everything the web Calendar screen listed, as on the web row.
private const val COMING_SOON_LIMIT = 60
// Fetched deeper than shown: titles already in an earlier row are dropped,
// which used to leave catalog and genre rows with only a few cards.
private const val CATALOG_ROW_LIMIT = 60
private const val ROW_DISPLAY_LIMIT = 24
private const val ROW_LIST_LIMIT = 500
private const val MAX_CATALOG_ROWS = 8
private const val MIN_CATALOG_ITEMS = 3
private const val MAX_ANIME_CATALOG_ROWS = 0

private const val GENRE_ROWS = 4
private const val GENRE_ROW_LIMIT = 60

private data class SeasonalSpec(
    val title: String,
    val genres: List<String>,
    val searchTerms: List<String>,
)

// Matches Jellio Web runtime/seasonalRecommendations.js SEASONS_SPEC:
// Maps active theme to its seasonal title, genres, and holiday terms.
private fun seasonsSpec(): Map<String, SeasonalSpec> = mapOf(
    "halloween" to SeasonalSpec(
        title = com.jellio.tv.ui.i18n.Strings.get("seasons_halloween"),
        genres = listOf("Horror", "Mystery"),
        searchTerms = listOf("Halloween", "Spooky", "Ghost", "Haunted", "Witch", "Vampire", "Zombie"),
    ),
    "christmas" to SeasonalSpec(
        title = com.jellio.tv.ui.i18n.Strings.get("seasons_christmas"),
        genres = listOf("Holiday", "Christmas", "Family"),
        searchTerms = listOf("Christmas", "Holiday", "Santa", "Noel", "Xmas"),
    ),
    "newyear" to SeasonalSpec(
        title = com.jellio.tv.ui.i18n.Strings.get("seasons_newyear"),
        genres = listOf("Comedy", "Music", "Musical"),
        searchTerms = listOf("New Year", "Countdown", "Celebration", "Party"),
    ),
    "valentine" to SeasonalSpec(
        title = com.jellio.tv.ui.i18n.Strings.get("seasons_valentine"),
        genres = listOf("Romance"),
        searchTerms = listOf("Valentine", "Love", "Romance", "Romantic"),
    ),
)

// Catalogs worth leading with, in this order. Anything unlisted keeps
// its own alphabetical order behind them.
private val LEAD = listOf("trending", "popular", "top rated", "new releases")

private fun leadIndex(name: String?): Int {
    val index = LEAD.indexOf((name ?: "").lowercase())
    return if (index == -1) LEAD.size else index
}

private fun titleFor(name: String?, kind: String): String =
    com.jellio.tv.ui.i18n.catalogDisplayName(name, kind)

private const val HOME_BACKGROUND_RETRIES = 3
private const val HOME_RETRY_DELAY_MS = 20_000L

// Warmed in the background while the viewer is still browsing.
private const val CONTINUE_PREFETCH_COUNT = 6

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val customizationStore: HomeCustomizationStore,
    private val watchNextSyncer: WatchNextSyncer,
    private val recommendationPreferences: RecommendationPreferences,
    private val rowSnapshotStore: RowSnapshotStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun persistCurrentSnapshot(userId: String) {
        val state = _uiState.value
        viewModelScope.launch {
            rowSnapshotStore.saveHomeSnapshot(
                userId,
                HomeSnapshot(
                    greeting = state.greeting,
                    heroItems = state.heroItems,
                    rows = state.rows.map { it.toCachedRowDto() },
                ),
            )
        }
    }

    private var loadedForUserId: String? = null

    private val failedFetches = java.util.concurrent.atomic.AtomicInteger()
    private var homeRetryJob: kotlinx.coroutines.Job? = null
    private var homeRetryRound = 0

    private suspend fun <T> orEmptyOnFailure(block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (err: kotlinx.coroutines.CancellationException) {
            throw err
        } catch (err: Exception) {
            failedFetches.incrementAndGet()
            emptyList()
        }

    private fun scheduleHomeRetry(session: Session) {
        if (homeRetryRound >= HOME_BACKGROUND_RETRIES) return
        val round = homeRetryRound++
        homeRetryJob = viewModelScope.launch {
            kotlinx.coroutines.delay(HOME_RETRY_DELAY_MS shl round)
            load(session)
        }
    }

    fun load(session: Session) {
        val loadKey = "${session.userId}:${com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage.code}"
        val isReturningVisit = loadedForUserId == loadKey
        loadedForUserId = loadKey
        homeRetryJob?.cancel()
        failedFetches.set(0)
        viewModelScope.launch {
            if (!isReturningVisit) {
                val cachedSnapshot = runCatching { rowSnapshotStore.loadHomeSnapshot(session.userId) }.getOrNull()
                if (cachedSnapshot != null && (cachedSnapshot.rows.isNotEmpty() || cachedSnapshot.heroItems.isNotEmpty())) {
                    _uiState.value = HomeUiState(
                        isLoading = false,
                        heroItems = cachedSnapshot.heroItems,
                        greeting = cachedSnapshot.greeting.ifEmpty { greetingText(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), null) },
                        rows = cachedSnapshot.rows.map { it.toHomeRow() },
                        loadingMore = true,
                    )
                } else {
                    _uiState.value = HomeUiState(isLoading = true)
                }
            }
            try {
                coroutineScope {
                    val continueWatchingDeferred = async { orEmptyOnFailure { repository.getContinueWatching(session.userId) } }
                    val upNextDeferred = async { orEmptyOnFailure { repository.getNextUp(session.userId) } }
                    val comingSoonDeferred = async { orEmptyOnFailure { repository.getCalendarEntries().take(COMING_SOON_LIMIT) } }
                    val collectionsDeferred = async { orEmptyOnFailure { repository.getCollections(session.userId) } }
                    val userDeferred = async { runCatching { repository.getUser(session.userId) }.getOrNull() }
                    val configDeferred = async { runCatching { repository.getJellioConfig() }.getOrNull() }
                    val customizationDeferred = async { runCatching { customizationStore.load() }.getOrDefault(HomeCustomizationDto()) }
                    val animeIdsDeferred = async { repository.getAnimeIds() }
                    val watchlistDeferred = async { orEmptyOnFailure { repository.getWatchlistItems(session.userId) } }

                    val upNext = upNextDeferred.await()
                    val continueWatching = continueWatchingDeferred.await().withoutStaleEpisodes(upNext)
                    launch { runCatching { watchNextSyncer.sync(session, continueWatching, upNext) } }
                    repository.prefetchStreamsInTurn(
                        continueWatching.filter { it.Type == "Movie" || it.Type == "Episode" }.take(CONTINUE_PREFETCH_COUNT).map { it.Id },
                    )
                    // Anime stays in the Anime library: out of the hero, Coming
                    // Soon and (through recExclude and catalog/genre filtering below) every discovery row.
                    animeIds = animeIdsDeferred.await()
                    // Home has no banner carousel any more, its showcase follows focus.
                    val heroCandidates = emptyList<BaseItemDto>()
                    val user = userDeferred.await()
                    val customization = customizationDeferred.await()
                    if (!isReturningVisit) {
                        _uiState.value = HomeUiState(
                            isLoading = false,
                            heroItems = heroCandidates,
                            greeting = greetingText(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), user?.Name),
                            rows = buildList {
                                if (continueWatching.isNotEmpty()) {
                                    add(PosterHomeRow(HomeSection(com.jellio.tv.ui.i18n.Strings.get("home_continue_watching"), continueWatching, key = "continue-watching"), landscape = true))
                                }
                                if (upNext.isNotEmpty()) {
                                    add(PosterHomeRow(HomeSection(com.jellio.tv.ui.i18n.Strings.get("home_next_up"), upNext, key = "up-next"), landscape = true))
                                }
                            },
                            customization = customization,
                            loadingMore = true,
                        )
                    }
                    // The reader's own Watchlist: anime they follow stays in.
                    val comingSoon = comingSoonDeferred.await()
                    val comingSoonItems = runCatching { repository.getItemsInfo(session.userId, comingSoon.map { it.ItemId }) }
                        .getOrDefault(emptyMap())
                        .mapKeys { normalizeId(it.key) }
                    val collections = collectionsDeferred.await()
                    val config = configDeferred.await()

                    val activeTheme = activeSeasonalTheme(Calendar.getInstance(), config)
                    val seasons = seasonsSpec()
                    val seasonalDeferred = if (activeTheme != null && seasons.containsKey(activeTheme)) {
                        val spec = seasons.getValue(activeTheme)
                        async {
                            runCatching {
                                val raw = repository.getSeasonalItems(session.userId, spec.genres, spec.searchTerms, 60)
                                spec.title to raw
                            }.getOrNull()
                        }
                    } else {
                        null
                    }

                    val studioHubs = groupByService(collections).keys.sorted()
                    val greeting = greetingText(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), user?.Name)
                    // Titles taken off the watchlist here stay off even if a refresh
                    // still gets the older list back.
                    val watchlist = watchlistDeferred.await().filter { removedFromWatchlist[it.Id] == null }
                    val policy = user?.Policy
                    val canDeleteItems = policy != null && (policy.IsAdministrator || policy.EnableContentDeletion)
                    val topRows = buildList<HomeRow> {
                        if (continueWatching.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection(com.jellio.tv.ui.i18n.Strings.get("home_continue_watching"), continueWatching, key = "continue-watching"), landscape = true))
                        }
                        if (upNext.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection(com.jellio.tv.ui.i18n.Strings.get("home_next_up"), upNext, key = "up-next"), landscape = true))
                        }
                        // The Watchlist, split by kind, in place of its own nav button.
                        val watchlistMovies = watchlist.filter { it.Type == "Movie" }
                        val watchlistSeries = watchlist.filter { it.Type == "Series" }
                        if (watchlistMovies.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection(com.jellio.tv.ui.i18n.Strings.get("home_watchlist_movies"), watchlistMovies, key = "watchlist-movies")))
                        }
                        if (watchlistSeries.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection(com.jellio.tv.ui.i18n.Strings.get("home_watchlist_shows"), watchlistSeries, key = "watchlist-shows")))
                        }
                        if (comingSoon.isNotEmpty()) add(ComingSoonHomeRow(comingSoon, comingSoonItems))
                        if (studioHubs.isNotEmpty()) add(StudioHubsHomeRow(studioHubs))
                    }
                    if (!isReturningVisit) {
                        _uiState.value = HomeUiState(
                            isLoading = false,
                            heroItems = heroCandidates,
                            greeting = greeting,
                            rows = topRows,
                            customization = customization,
                            canDeleteItems = canDeleteItems,
                            loadingMore = true,
                        )
                    }

                    val recommendationSource = RecommendationDataSource(
                        getRecentlyCompleted = { limit -> repository.getRecentlyCompleted(session.userId, limit) },
                        getNextUp = { limit -> repository.getNextUp(session.userId, limit) },
                        getRecommendationCandidates = { seed, limit -> repository.getRecommendationCandidates(session.userId, seed, limit) },
                        getGenreItems = { genre, limit -> repository.getGenreItems(session.userId, null, "Movie,Series", genre, limit).filterNot { isAnime(it) } },
                        getGenreItemsPage = { genre, start, size -> repository.getGenreItemsPage(session.userId, null, "Movie,Series", genre, start, size).filterNot { isAnime(it) } },
                    )
                    val recExclude = mutableSetOf<String>().apply { addAll(animeIds) }
                    val catalogAndGenreDeduplicator = SlidingRowDeduplicator(windowSize = 2, viewportLimit = 6)
                    val isCommunityEnabled = recommendationPreferences.isCommunityRowEnabled(session.userId)
                    val communityExcluded = recommendationPreferences.getCommunityExcludedUsers(session.userId).toList()

                    var recommendationRows: List<HomeSection> = emptyList()
                    var seasonalSection: HomeSection? = null
                    var communitySection: HomeSection? = null
                    val catalogSections = sortedMapOf<Int, HomeSection>()
                    val genreSections = sortedMapOf<Int, HomeSection>()
                    var finished = false

                    fun assembled(): List<HomeRow> = buildList {
                        val topRowKeys = setOf("continue-watching", "up-next", "watchlist-movies", "watchlist-shows", "coming-soon", "studio-hubs")
                        val currentTop = _uiState.value.rows.filter { it.key in topRowKeys }.ifEmpty { topRows }
                        val filteredTop = currentTop.mapNotNull { row ->
                            if (row is PosterHomeRow && row.section.key.startsWith("watchlist")) {
                                val remaining = row.section.items.filterNot { removedFromWatchlist.containsKey(it.Id) }
                                if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
                            } else {
                                row
                            }
                        }
                        addAll(filteredTop)
                        val topPicksTitle = com.jellio.tv.ui.i18n.Strings.get("home_top_picks")
                        val (topPicks, perTitle) = recommendationRows.partition { it.title == topPicksTitle }
                        topPicks.forEach { add(PosterHomeRow(it)) }
                        seasonalSection?.let { add(PosterHomeRow(it)) }
                        communitySection?.let { add(PosterHomeRow(it)) }
                        perTitle.forEach { add(PosterHomeRow(it)) }
                        catalogSections.values.forEach { add(PosterHomeRow(it)) }
                        genreSections.values.forEach { add(PosterHomeRow(it)) }
                    }

                    fun publish() {
                        if (isReturningVisit && !finished) return
                        _uiState.value = HomeUiState(
                            isLoading = false,
                            heroItems = heroCandidates,
                            greeting = greeting,
                            rows = assembled(),
                            customization = customization,
                            canDeleteItems = canDeleteItems,
                            loadingMore = !finished,
                        )
                    }

                    // Never awaited: a slow community lookup holds nothing up.
                    if (isCommunityEnabled) {
                        launch {
                            val raw = orEmptyOnFailure { repository.getCommunityWatching(communityExcluded) }
                            // Lands after the other rows, so filtering against them would usually empty it.
                            val eligible = raw.distinctBy { it.Id }
                            if (eligible.isEmpty()) return@launch
                            communitySection = HomeSection(
                                title = com.jellio.tv.ui.i18n.Strings.get("home_community_watching"),
                                items = eligible.take(24),
                                key = "rec:What Others Are Watching",
                            )
                            publish()
                        }
                    }

                    val recommendationJob = async {
                        val recs = orEmptyOnFailure { buildRecommendationRows(recommendationSource, recExclude) }
                        recommendationRows = recs.map { it.copy(key = "rec:${it.title}") }
                        val picks = (recs.firstOrNull { it.title == com.jellio.tv.ui.i18n.Strings.get("home_top_picks") } ?: recs.firstOrNull())?.items
                        if (!picks.isNullOrEmpty()) launch { runCatching { watchNextSyncer.updateTopPicks(session, picks) } }
                        publish()
                    }

                    val seasonalJob = seasonalDeferred?.let { pending ->
                        async {
                            val (title, rawItems) = pending.await() ?: return@async
                            val eligible = rawItems.filter { it.UserData?.Played != true && !recExclude.contains(it.Id) }
                            eligible.forEach { recExclude.add(it.Id) }
                            if (eligible.isNotEmpty()) {
                                seasonalSection = HomeSection(title = title, items = eligible.take(24), key = "seasonal:$activeTheme")
                                publish()
                            }
                        }
                    }

                    val catalogJobs = chooseCatalogs(collections).mapIndexed { index, collection ->
                        async {
                            val kind = repository.collectionKind(collection)
                            val items = orEmptyOnFailure { repository.getCollectionItems(session.userId, collection.Id, kind, CATALOG_ROW_LIMIT) }.filterNot { isAnime(it) }
                            val entry = CatalogRowEntry(title = titleFor(collection.Name, kind), items = items, collectionId = collection.Id, kind = kind)
                            catalogSection(session.userId, entry, catalogAndGenreDeduplicator)?.let {
                                catalogSections[index] = it
                                publish()
                            }
                        }
                    }

                    val genreJob = async {
                        val genres = orEmptyOnFailure { chooseGenres(session.userId) }
                        genres.mapIndexed { index, genre ->
                            async {
                                val items = orEmptyOnFailure { repository.getGenreItems(session.userId, null, "Movie,Series", genre, GENRE_ROW_LIMIT) }.filterNot { isAnime(it) }
                                genreSection(session.userId, GenreRowEntry(genre = genre, items = items), catalogAndGenreDeduplicator)?.let {
                                    genreSections[index] = it
                                    publish()
                                }
                            }
                        }.awaitAll()
                    }

                    recommendationJob.await()
                    seasonalJob?.await()
                    catalogJobs.awaitAll()
                    genreJob.await()
                    finished = true
                    publish()
                    val finalRows = assembled()
                    // A row lost to a failed request is not saved as gone;
                    // Home asks again shortly instead.
                    if (failedFetches.get() > 0) {
                        android.util.Log.w("JellioHome", "${failedFetches.get()} home requests failed, retrying later")
                        scheduleHomeRetry(session)
                    } else {
                        homeRetryRound = 0
                        launch {
                            rowSnapshotStore.saveHomeSnapshot(
                                session.userId,
                                HomeSnapshot(
                                    greeting = greeting,
                                    heroItems = heroCandidates,
                                    rows = finalRows.map { it.toCachedRowDto() },
                                ),
                            )
                        }
                    }
                }
            } catch (err: Exception) {
                if (_uiState.value.loadingMore) _uiState.value = _uiState.value.copy(loadingMore = false)
                if (!isReturningVisit && _uiState.value.rows.isEmpty()) {
                    _uiState.value = HomeUiState(isLoading = false, error = err.message ?: "Could not load Home")
                }
                scheduleHomeRetry(session)
            }
        }
    }

    suspend fun showcaseInfo(session: Session, item: BaseItemDto): ShowcaseInfo =
        loadShowcaseInfo(repository, session.userId, item)

    private val removedFromWatchlist = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    // Gone from the row at once; back if the server says no.
    fun removeFromComingSoon(itemId: String) {
        val before = _uiState.value.rows
        _uiState.value = _uiState.value.copy(
            rows = before.mapNotNull { row ->
                if (row is ComingSoonHomeRow) {
                    val left = row.entries.filterNot { it.ItemId == itemId }
                    if (left.isEmpty()) null else row.copy(entries = left)
                } else {
                    row
                }
            },
        )
        viewModelScope.launch {
            if (!repository.hideFromComingSoon(itemId)) {
                _uiState.value = _uiState.value.copy(rows = before)
            }
        }
    }

    fun toggleWatchlist(session: Session, item: BaseItemDto) {
        val currentlyFavorite = item.UserData?.IsFavorite == true || _uiState.value.rows.any { row ->
            row is PosterHomeRow && row.section.key.startsWith("watchlist") && row.section.items.any { it.Id == item.Id }
        }
        val willBeFavorite = !currentlyFavorite
        val beforeState = _uiState.value

        if (!willBeFavorite) {
            removedFromWatchlist[item.Id] = true
            val updatedHero = beforeState.heroItems.map {
                if (it.Id == item.Id) it.copy(UserData = (it.UserData ?: UserItemDataDto()).copy(IsFavorite = false)) else it
            }
            val updatedRows = beforeState.rows.mapNotNull { row ->
                when {
                    row is PosterHomeRow && row.section.key.startsWith("watchlist") -> {
                        val remaining = row.section.items.filterNot { it.Id == item.Id }
                        if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
                    }
                    row is PosterHomeRow -> {
                        if (row.section.items.any { it.Id == item.Id }) {
                            row.copy(section = row.section.copy(items = row.section.items.map {
                                if (it.Id == item.Id) it.copy(UserData = (it.UserData ?: UserItemDataDto()).copy(IsFavorite = false)) else it
                            }))
                        } else {
                            row
                        }
                    }
                    else -> row
                }
            }
            _uiState.value = beforeState.copy(rows = updatedRows, heroItems = updatedHero)
            persistCurrentSnapshot(session.userId)

            viewModelScope.launch {
                val res = runCatching { repository.toggleFavorite(session.userId, item) }
                if (res.isFailure || res.getOrNull() == true) {
                    removedFromWatchlist.remove(item.Id)
                    _uiState.value = beforeState
                    persistCurrentSnapshot(session.userId)
                    _toastMessage.value = com.jellio.tv.ui.i18n.Strings.get("toast_watchlist_error")
                }
            }
        } else {
            removedFromWatchlist.remove(item.Id)
            val updatedItem = item.copy(UserData = (item.UserData ?: UserItemDataDto()).copy(IsFavorite = true))
            val updatedHero = beforeState.heroItems.map { if (it.Id == item.Id) updatedItem else it }
            var updatedRows = beforeState.rows.map { row ->
                if (row is PosterHomeRow && row.section.items.any { it.Id == item.Id }) {
                    row.copy(section = row.section.copy(items = row.section.items.map { if (it.Id == item.Id) updatedItem else it }))
                } else {
                    row
                }
            }
            val isSeries = item.Type == "Series" || item.SeriesId != null
            val rowKey = if (isSeries) "watchlist-shows" else "watchlist-movies"
            val rowTitleKey = if (isSeries) "home_watchlist_shows" else "home_watchlist_movies"
            val existingRow = updatedRows.firstOrNull { it.key == rowKey } as? PosterHomeRow
            if (existingRow != null) {
                val newItems = listOf(updatedItem) + existingRow.section.items.filterNot { it.Id == item.Id }
                updatedRows = updatedRows.map { if (it.key == rowKey) existingRow.copy(section = existingRow.section.copy(items = newItems)) else it }
            } else {
                val newRow = PosterHomeRow(HomeSection(com.jellio.tv.ui.i18n.Strings.get(rowTitleKey), listOf(updatedItem), key = rowKey))
                updatedRows = insertWatchlistRow(updatedRows, newRow)
            }
            _uiState.value = beforeState.copy(rows = updatedRows, heroItems = updatedHero)
            persistCurrentSnapshot(session.userId)

            viewModelScope.launch {
                val res = runCatching { repository.toggleFavorite(session.userId, item) }
                if (res.isFailure || res.getOrNull() == false) {
                    _uiState.value = beforeState
                    persistCurrentSnapshot(session.userId)
                    _toastMessage.value = com.jellio.tv.ui.i18n.Strings.get("toast_watchlist_error")
                } else {
                    runCatching {
                        val fresh = repository.getWatchlistItems(session.userId).filter { !removedFromWatchlist.containsKey(it.Id) }
                        val movies = fresh.filter { it.Type == "Movie" }
                        val series = fresh.filter { it.Type == "Series" }
                        updateWatchlistRows(movies, series)
                        persistCurrentSnapshot(session.userId)
                    }
                }
            }
        }
    }

    private fun insertWatchlistRow(rows: List<HomeRow>, newRow: PosterHomeRow): List<HomeRow> {
        val key = newRow.section.key
        val targetIndex = if (key == "watchlist-movies") {
            val upNextIdx = rows.indexOfFirst { it.key == "up-next" }
            if (upNextIdx >= 0) upNextIdx + 1 else {
                val cwIdx = rows.indexOfFirst { it.key == "continue-watching" }
                if (cwIdx >= 0) cwIdx + 1 else 0
            }
        } else {
            val wmIdx = rows.indexOfFirst { it.key == "watchlist-movies" }
            if (wmIdx >= 0) wmIdx + 1 else {
                val upNextIdx = rows.indexOfFirst { it.key == "up-next" }
                if (upNextIdx >= 0) upNextIdx + 1 else {
                    val cwIdx = rows.indexOfFirst { it.key == "continue-watching" }
                    if (cwIdx >= 0) cwIdx + 1 else 0
                }
            }
        }
        val result = rows.toMutableList()
        result.add(targetIndex.coerceAtMost(result.size), newRow)
        return result
    }

    private fun updateWatchlistRows(movies: List<BaseItemDto>, series: List<BaseItemDto>) {
        val state = _uiState.value
        var updatedRows = state.rows
        if (movies.isNotEmpty()) {
            val movieRow = PosterHomeRow(HomeSection(com.jellio.tv.ui.i18n.Strings.get("home_watchlist_movies"), movies, key = "watchlist-movies"))
            if (updatedRows.any { it.key == "watchlist-movies" }) {
                updatedRows = updatedRows.map { if (it.key == "watchlist-movies") movieRow else it }
            } else {
                updatedRows = insertWatchlistRow(updatedRows, movieRow)
            }
        } else {
            updatedRows = updatedRows.filterNot { it.key == "watchlist-movies" }
        }
        if (series.isNotEmpty()) {
            val seriesRow = PosterHomeRow(HomeSection(com.jellio.tv.ui.i18n.Strings.get("home_watchlist_shows"), series, key = "watchlist-shows"))
            if (updatedRows.any { it.key == "watchlist-shows" }) {
                updatedRows = updatedRows.map { if (it.key == "watchlist-shows") seriesRow else it }
            } else {
                updatedRows = insertWatchlistRow(updatedRows, seriesRow)
            }
        } else {
            updatedRows = updatedRows.filterNot { it.key == "watchlist-shows" }
        }
        _uiState.value = state.copy(rows = updatedRows)
    }

    private fun handleAutoRemovedWatchlist(userId: String) {
        val autoRemoved = repository.lastAutoRemovedWatchlistIds.value
        if (autoRemoved.isEmpty()) return
        autoRemoved.forEach { removedFromWatchlist[it] = true }
        val state = _uiState.value
        val updatedRows = state.rows.mapNotNull { row ->
            if (row is PosterHomeRow && row.section.key.startsWith("watchlist")) {
                val remaining = row.section.items.filterNot { autoRemoved.contains(it.Id) }
                if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
            } else {
                row
            }
        }
        _uiState.value = state.copy(rows = updatedRows)
        persistCurrentSnapshot(userId)
    }

    fun toggleWatched(session: Session, item: BaseItemDto) {
        val next = !(item.UserData?.Played ?: false)
        viewModelScope.launch {
            val updated = runCatching { repository.setPlayed(session.userId, item.Id, next) }.getOrNull() ?: return@launch
            updateItem(item.Id) { it.copy(UserData = updated) }
            if (next) {
                handleAutoRemovedWatchlist(session.userId)
            }
        }
    }

    private fun updateItem(itemId: String, transform: (BaseItemDto) -> BaseItemDto) {
        val state = _uiState.value
        val updatedRows = state.rows.map { row ->
            if (row is PosterHomeRow && row.section.items.any { it.Id == itemId }) {
                row.copy(section = row.section.copy(items = row.section.items.map { if (it.Id == itemId) transform(it) else it }))
            } else {
                row
            }
        }
        val updatedHero = state.heroItems.map { if (it.Id == itemId) transform(it) else it }
        _uiState.value = state.copy(rows = updatedRows, heroItems = updatedHero)
    }

    fun removeFromRow(session: Session, item: BaseItemDto) {
        val before = _uiState.value.rows
        removeItemFromRows(item.Id)
        viewModelScope.launch {
            runCatching { repository.setPlayed(session.userId, item.Id, true) }
                .onSuccess { handleAutoRemovedWatchlist(session.userId) }
                .onFailure { _uiState.value = _uiState.value.copy(rows = before) }
        }
    }

    fun hideShowFromRow(item: BaseItemDto) {
        val seriesId = item.SeriesId ?: return
        val before = _uiState.value.rows
        removeSeriesFromUpNext(seriesId, item.Id)
        viewModelScope.launch {
            runCatching { repository.hideSeriesFromNextUp(seriesId) }
                .onFailure { _uiState.value = _uiState.value.copy(rows = before) }
        }
    }

    fun deleteItem(item: BaseItemDto) {
        viewModelScope.launch {
            runCatching { repository.deleteItem(item.Id) }
                .onSuccess { removeItemFromRows(item.Id) }
        }
    }

    // Off Continue Watching without marking it watched: only its resume
    // position is cleared, so elsewhere it just loses its progress bar.
    fun removeFromContinueWatching(session: Session, item: BaseItemDto) {
        val before = _uiState.value.rows
        _uiState.value = _uiState.value.copy(
            rows = before.map { row ->
                if (row is PosterHomeRow && row.section.key == "continue-watching") {
                    row.copy(section = row.section.copy(items = row.section.items.filterNot { it.Id == item.Id }))
                } else {
                    row
                }
            },
        )
        viewModelScope.launch {
            val updated = runCatching { repository.clearResumePosition(session.userId, item.Id) }.getOrNull()
            if (updated == null) {
                _uiState.value = _uiState.value.copy(rows = before)
                return@launch
            }
            val state = _uiState.value
            _uiState.value = state.copy(
                rows = state.rows.map { row ->
                    when {
                        row !is PosterHomeRow -> row
                        row.section.key == "continue-watching" ->
                            row.copy(section = row.section.copy(items = row.section.items.filterNot { it.Id == item.Id }))
                        row.section.items.any { it.Id == item.Id } ->
                            row.copy(section = row.section.copy(items = row.section.items.map { if (it.Id == item.Id) it.copy(UserData = updated) else it }))
                        else -> row
                    }
                },
            )
            handleAutoRemovedWatchlist(session.userId)
        }
    }

    private fun removeSeriesFromUpNext(seriesId: String, itemId: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            rows = state.rows.map { row ->
                if (row is PosterHomeRow && row.section.key == "up-next") {
                    row.copy(section = row.section.copy(items = row.section.items.filterNot { it.SeriesId == seriesId }))
                } else {
                    row
                }
            },
        )
        removeItemFromRows(itemId)
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

    private fun chooseCatalogs(collections: List<BaseItemDto>): List<BaseItemDto> {
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

        return usable.take(MAX_CATALOG_ROWS)
    }

    // One catalog row once its items arrive: deduplicated using sliding row window.
    private fun catalogSection(userId: String, entry: CatalogRowEntry, deduplicator: SlidingRowDeduplicator): HomeSection? {
        val deduped = deduplicator.dedupe(entry.items, ROW_DISPLAY_LIMIT)
        if (deduped.isEmpty()) return null
        return HomeSection(
            title = entry.title,
            items = deduped,
            fetchAll = { repository.getCollectionItems(userId, entry.collectionId, entry.kind, ROW_LIST_LIMIT).filterNot { isAnime(it) } },
            fetchPage = { start, size -> repository.getCollectionItems(userId, entry.collectionId, entry.kind, size, start).filterNot { isAnime(it) } },
            key = "catalog:${entry.collectionId}",
        )
    }

    private var animeIds: Set<String> = emptySet()

    private fun normalizeId(id: String?): String = id.orEmpty().replace("-", "").lowercase()

    private fun isAnime(item: BaseItemDto): Boolean =
        animeIds.isNotEmpty() && (normalizeId(item.Id) in animeIds || (item.SeriesId != null && normalizeId(item.SeriesId) in animeIds))

    private data class GenreRowEntry(val genre: String, val items: List<BaseItemDto>)

    private suspend fun chooseGenres(userId: String): List<String> {
        val selected = recommendationPreferences.getSelectedGenres(userId)
        return if (selected.isNotEmpty()) selected else repository.discoverGenres(userId, null, "Movie,Series", GENRE_ROWS)
    }

    private fun genreSection(userId: String, entry: GenreRowEntry, deduplicator: SlidingRowDeduplicator): HomeSection? {
        val deduped = deduplicator.dedupe(entry.items, ROW_DISPLAY_LIMIT)
        if (deduped.isEmpty()) return null
        return HomeSection(
            title = com.jellio.tv.ui.i18n.genreDisplayName(entry.genre),
            items = deduped,
            fetchAll = { repository.getGenreItems(userId, null, "Movie,Series", entry.genre, ROW_LIST_LIMIT).filterNot { isAnime(it) } },
            fetchPage = { start, size -> repository.getGenreItemsPage(userId, null, "Movie,Series", entry.genre, start, size).filterNot { isAnime(it) } },
            key = "genre:${entry.genre}",
        )
    }
}

internal class SlidingRowDeduplicator(
    private val windowSize: Int = 2,
    private val viewportLimit: Int = 6,
) {
    private val recentRows = ArrayDeque<Set<String>>()

    @Synchronized
    fun dedupe(items: List<BaseItemDto>, limit: Int = 20): List<BaseItemDto> {
        val windowSeen = recentRows.flatten().toSet()
        val rowSeen = mutableSetOf<String>()
        val kept = mutableListOf<BaseItemDto>()

        for (item in items) {
            val id = item.Id
            val title = titleKey(item)
            if (rowSeen.contains(id) || rowSeen.contains(title)) continue

            // For the first viewportLimit items, avoid items seen in the last windowSize rows
            if (kept.size < viewportLimit && (windowSeen.contains(id) || windowSeen.contains(title))) {
                continue
            }

            rowSeen.add(id)
            rowSeen.add(title)
            kept.add(item)
            if (kept.size >= limit) break
        }

        if (kept.isNotEmpty()) {
            recentRows.addLast(rowSeen)
            while (recentRows.size > windowSize) {
                recentRows.removeFirst()
            }
        }

        return kept
    }
}

internal fun List<BaseItemDto>.withoutStaleEpisodes(upNext: List<BaseItemDto>): List<BaseItemDto> {
    fun order(item: BaseItemDto) = (item.ParentIndexNumber ?: 0) * 10_000 + (item.IndexNumber ?: 0)
    val nextBySeries = upNext.filter { it.SeriesId != null }.associateBy { it.SeriesId }
    return filter { item ->
        if (item.UserData?.Played == true) return@filter false
        val next = item.SeriesId?.let { nextBySeries[it] } ?: return@filter true
        order(item) >= order(next)
    }
}
