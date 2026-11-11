package com.jellio.tv.ui.home

import com.jellio.tv.data.model.ComingSoonHomeRow
import com.jellio.tv.data.model.HomeRow
import com.jellio.tv.data.model.HomeSection
import com.jellio.tv.data.model.PosterHomeRow
import com.jellio.tv.data.model.StudioHubsHomeRow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.catching
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
import com.jellio.tv.ui.common.LoadFailure
import com.jellio.tv.ui.common.loadFailureOf
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

data class HomeUiState(
    val isLoading: Boolean = true,
    val heroItems: List<BaseItemDto> = emptyList(),
    val greeting: String = "",
    val rows: List<HomeRow> = emptyList(),
    val customization: HomeCustomizationDto = HomeCustomizationDto(),
    // Nothing loaded and nothing saved to show instead.
    val error: LoadFailure? = null,
    // Some rows failed; they show their last loaded items if there were any.
    val rowsError: LoadFailure? = null,
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
        title = com.jellio.tv.data.i18n.Strings.get("seasons_halloween"),
        genres = listOf("Horror", "Mystery"),
        searchTerms = listOf("Halloween", "Spooky", "Ghost", "Haunted", "Witch", "Vampire", "Zombie"),
    ),
    "christmas" to SeasonalSpec(
        title = com.jellio.tv.data.i18n.Strings.get("seasons_christmas"),
        genres = listOf("Holiday", "Christmas", "Family"),
        searchTerms = listOf("Christmas", "Holiday", "Santa", "Noel", "Xmas"),
    ),
    "newyear" to SeasonalSpec(
        title = com.jellio.tv.data.i18n.Strings.get("seasons_newyear"),
        genres = listOf("Comedy", "Music", "Musical"),
        searchTerms = listOf("New Year", "Countdown", "Celebration", "Party"),
    ),
    "valentine" to SeasonalSpec(
        title = com.jellio.tv.data.i18n.Strings.get("seasons_valentine"),
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
    com.jellio.tv.data.i18n.catalogDisplayName(name, kind)

private const val COMMUNITY_ROW_KEY = "rec:What Others Are Watching"

private const val HOME_BACKGROUND_RETRIES = 3
// Coming back to Home this soon after a complete load does not ask again.
private const val HOME_FRESH_MS = 10_000L
private const val HOME_RETRY_DELAY_MS = 20_000L
private const val ADDED_BY_WAIT_MS = 2_500L

// Warmed in the background while the viewer is still browsing.
private const val CONTINUE_PREFETCH_COUNT = 6

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val customizationStore: HomeCustomizationStore,
    private val watchNextSyncer: WatchNextSyncer,
    private val recommendationPreferences: RecommendationPreferences,
    private val rowSnapshotStore: RowSnapshotStore,
    val nowPlayingManager: com.jellio.tv.data.nowplaying.NowPlayingManager,
    private val friendsRepository: com.jellio.tv.data.FriendsRepository,
) : ViewModel() {
    val nowPlayingSessions = nowPlayingManager.sessions
    val nowPlayingActiveCount = nowPlayingManager.activeCount
    val nowPlayingLocalPaused = nowPlayingManager.localPaused
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
    private var freshAtMs: Long? = null

    private val failedFetches = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var lastFailure: Throwable? = null
    private var homeRetryJob: kotlinx.coroutines.Job? = null
    private var homeRetryRound = 0
    private var loadJob: kotlinx.coroutines.Job? = null

    // Null when the request failed, so a failure is not mistaken for an empty row.
    private suspend fun <T> orNullOnFailure(block: suspend () -> List<T>): List<T>? =
        try {
            block()
        } catch (err: kotlinx.coroutines.CancellationException) {
            throw err
        } catch (err: Exception) {
            failedFetches.incrementAndGet()
            lastFailure = err
            null
        }

    // Try again on Home: a full page error shows the skeleton meanwhile, a
    // partial one keeps its rows and retry line until the new answer is in.
    fun retry(session: Session) {
        if (loadJob?.isActive == true) return
        homeRetryRound = 0
        if (_uiState.value.rows.isEmpty()) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        load(session, force = true)
    }

    private fun scheduleHomeRetry(session: Session) {
        if (homeRetryRound >= HOME_BACKGROUND_RETRIES) return
        val round = homeRetryRound++
        homeRetryJob = viewModelScope.launch {
            kotlinx.coroutines.delay(HOME_RETRY_DELAY_MS shl round)
            load(session, force = true)
        }
    }

    // Safe to call from every place that shows Home: a load already running
    // for the same user and language, or one finished moments ago, is reused.
    fun load(session: Session, force: Boolean = false) {
        nowPlayingManager.startPolling(session)
        val loadKey = "${session.userId}:${com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage.code}"
        val action = decideHomeLoad(
            key = loadKey,
            loadedKey = loadedForUserId,
            inFlight = loadJob?.isActive == true,
            freshAtMs = freshAtMs,
            nowMs = android.os.SystemClock.elapsedRealtime(),
            freshForMs = HOME_FRESH_MS,
            force = force,
        )
        if (action == HomeLoadAction.SKIP) return
        val isReturningVisit = action == HomeLoadAction.REFRESH
        loadedForUserId = loadKey
        freshAtMs = null
        loadJob?.cancel()
        homeRetryJob?.cancel()
        failedFetches.set(0)
        lastFailure = null
        loadJob = viewModelScope.launch {
            // Read alongside the requests below instead of before them.
            val snapshotRead = if (isReturningVisit) null else async {
                catching { rowSnapshotStore.loadHomeSnapshot(session.userId) }.getOrNull()
            }
            try {
                coroutineScope {
                    val continueWatchingDeferred = async { orNullOnFailure { repository.getContinueWatching(session.userId) } }
                    val upNextDeferred = async { orNullOnFailure { repository.getNextUp(session.userId) } }
                    val comingSoonDeferred = async { orNullOnFailure { repository.getCalendarEntries().take(COMING_SOON_LIMIT) } }
                    val collectionsDeferred = async { orNullOnFailure { repository.getCollections(session.userId) } }
                    val userDeferred = async { catching { repository.getUser(session.userId) }.getOrNull() }
                    val configDeferred = async { catching { repository.getJellioConfig() }.getOrNull() }
                    val customizationDeferred = async { catching { customizationStore.load() }.getOrDefault(HomeCustomizationDto()) }
                    val animeIdsDeferred = async { repository.getAnimeIds() }
                    val watchlistDeferred = async { orNullOnFailure { repository.getWatchlistItems(session.userId) } }
                    // Who added what, held in memory; never holds the rows up for long.
                    val addedByDeferred = async { kotlinx.coroutines.withTimeoutOrNull(ADDED_BY_WAIT_MS) { friendsRepository.refreshAddedBy() } }

                    if (snapshotRead != null) {
                        val cachedSnapshot = snapshotRead.await()
                        if (cachedSnapshot != null && (cachedSnapshot.rows.isNotEmpty() || cachedSnapshot.heroItems.isNotEmpty())) {
                            val restoredRows = cachedSnapshot.rows.map { it.toHomeRow() }
                            restoredRows.forEach { row ->
                                when (row) {
                                    is PosterHomeRow -> repository.seedPreviews(row.section.items)
                                    is ComingSoonHomeRow -> repository.seedPreviews(row.items.values.toList())
                                    else -> {}
                                }
                            }
                            repository.seedPreviews(cachedSnapshot.heroItems)
                            _uiState.value = HomeUiState(
                                isLoading = false,
                                heroItems = cachedSnapshot.heroItems,
                                greeting = cachedSnapshot.greeting.ifEmpty { greetingText(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), null) },
                                rows = restoredRows,
                                loadingMore = true,
                            )
                        } else {
                            _uiState.value = HomeUiState(isLoading = true)
                        }
                    }
                    // A row whose request fails keeps what it showed before (from the
                    // last load or the saved snapshot) instead of disappearing.
                    val previousRows = _uiState.value.rows
                    fun previousSection(key: String): HomeSection? =
                        (previousRows.firstOrNull { it.key == key } as? PosterHomeRow)?.section
                    fun previousSections(prefix: String): List<HomeSection> =
                        previousRows.filterIsInstance<PosterHomeRow>().map { it.section }.filter { it.key.startsWith(prefix) }

                    val upNextFresh = upNextDeferred.await()
                    val continueWatchingFresh = continueWatchingDeferred.await()
                    val upNext = upNextFresh ?: previousSection("up-next")?.items.orEmpty()
                    val continueWatching = continueWatchingFresh?.withoutStaleEpisodes(upNext)
                        ?: previousSection("continue-watching")?.items.orEmpty()
                    if (upNextFresh != null && continueWatchingFresh != null) {
                        launch { catching { watchNextSyncer.sync(session, continueWatching, upNext) } }
                    }
                    repository.prefetchStreamsInTurn(
                        continueWatching.filter { it.Type == "Movie" || it.Type == "Episode" }.take(CONTINUE_PREFETCH_COUNT).map { it.Id },
                    )
                    repository.preloadEpisodeSeasonsInTurn(
                        session.userId,
                        (continueWatching + upNext).filter { it.Type == "Episode" }.take(CONTINUE_PREFETCH_COUNT),
                    )
                    // Anime stays in the Anime library: out of the hero, Coming
                    // Soon and (through recExclude and catalog/genre filtering below) every discovery row.
                    animeIds = animeIdsDeferred.await()
                    // Home has no banner carousel any more, its showcase follows focus.
                    val heroCandidates = emptyList<BaseItemDto>()
                    val user = userDeferred.await()
                    val customization = customizationDeferred.await()
                    if (!isReturningVisit) {
                        val freshTop = buildList<HomeRow> {
                            if (continueWatching.isNotEmpty()) {
                                add(PosterHomeRow(HomeSection(com.jellio.tv.data.i18n.Strings.get("home_continue_watching"), continueWatching, key = "continue-watching"), landscape = true))
                            }
                            if (upNext.isNotEmpty()) {
                                add(PosterHomeRow(HomeSection(com.jellio.tv.data.i18n.Strings.get("home_next_up"), upNext, key = "up-next"), landscape = true))
                            }
                        }
                        _uiState.value = HomeUiState(
                            isLoading = false,
                            heroItems = heroCandidates,
                            greeting = greetingText(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), user?.Name),
                            rows = mergeKeepingPending(freshTop, previousRows, { it.key }) { it != "continue-watching" && it != "up-next" },
                            customization = customization,
                            loadingMore = true,
                        )
                    }
                    // The reader's own Watchlist: anime they follow stays in.
                    val comingSoon = comingSoonDeferred.await().orEmpty()
                    val comingSoonItems = catching { repository.getItemsInfo(session.userId, comingSoon.map { it.ItemId }) }
                        .getOrDefault(emptyMap())
                        .mapKeys { normalizeId(it.key) }
                    val collectionsFresh = collectionsDeferred.await()
                    val collections = collectionsFresh.orEmpty()
                    val config = configDeferred.await()

                    val activeTheme = activeSeasonalTheme(Calendar.getInstance(), config)
                    val seasons = seasonsSpec()
                    val seasonalDeferred = if (activeTheme != null && seasons.containsKey(activeTheme)) {
                        val spec = seasons.getValue(activeTheme)
                        async {
                            orNullOnFailure { repository.getSeasonalItems(session.userId, spec.genres, spec.searchTerms, 60) }
                                ?.let { spec.title to it }
                        }
                    } else {
                        null
                    }

                    val studioHubs = if (collectionsFresh != null) {
                        groupByService(collections).keys.sorted()
                    } else {
                        previousRows.filterIsInstance<StudioHubsHomeRow>().firstOrNull()?.services.orEmpty()
                    }
                    val greeting = greetingText(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), user?.Name)
                    // Titles taken off the watchlist here stay off even if a refresh
                    // still gets the older list back.
                    addedByDeferred.await()
                    val watchlist = friendsRepository.withAddedBy(
                        (
                            watchlistDeferred.await()
                                ?: (previousSection("watchlist-movies")?.items.orEmpty() + previousSection("watchlist-shows")?.items.orEmpty())
                            ).filter { removedFromWatchlist[it.Id] == null },
                    )
                    val existingComingSoon = (_uiState.value.rows + previousRows).filterIsInstance<ComingSoonHomeRow>().firstOrNull()
                    val topRows = buildList<HomeRow> {
                        if (continueWatching.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection(com.jellio.tv.data.i18n.Strings.get("home_continue_watching"), continueWatching, key = "continue-watching"), landscape = true))
                        }
                        if (upNext.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection(com.jellio.tv.data.i18n.Strings.get("home_next_up"), upNext, key = "up-next"), landscape = true))
                        }
                        // The Watchlist, split by kind, in place of its own nav button.
                        val watchlistMovies = watchlist.filter { it.Type == "Movie" }
                        val watchlistSeries = watchlist.filter { it.Type == "Series" }
                        if (watchlistMovies.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection(com.jellio.tv.data.i18n.Strings.get("home_watchlist_movies"), watchlistMovies, key = "watchlist-movies")))
                        }
                        if (watchlistSeries.isNotEmpty()) {
                            add(PosterHomeRow(HomeSection(com.jellio.tv.data.i18n.Strings.get("home_watchlist_shows"), watchlistSeries, key = "watchlist-shows")))
                        }
                        if (comingSoon.isNotEmpty()) {
                            add(ComingSoonHomeRow(comingSoon, comingSoonItems))
                        } else if (existingComingSoon != null) {
                            add(existingComingSoon)
                        }
                        if (studioHubs.isNotEmpty()) add(StudioHubsHomeRow(studioHubs))
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
                    // Until its own answer is in, a row keeps what the snapshot or last load showed.
                    var recommendationsDone = false
                    var communityDone = !isCommunityEnabled
                    var seasonalDone = seasonalDeferred == null
                    // Null until this Home's genres are known.
                    var genresPending: MutableSet<String>? = null
                    val catalogs = chooseCatalogs(collections)
                    val catalogsPending = catalogs.mapTo(mutableSetOf()) { "catalog:${it.Id}" }
                    fun stillLoading(key: String): Boolean = when {
                        key == COMMUNITY_ROW_KEY -> !communityDone
                        finished -> false
                        key.startsWith("rec:") -> !recommendationsDone
                        key.startsWith("seasonal:") -> !seasonalDone
                        key.startsWith("catalog:") -> key in catalogsPending
                        key.startsWith("genre:") -> genresPending?.contains(key) ?: true
                        else -> false
                    }

                    fun assembled(): List<HomeRow> = buildList {
                        val filteredTop = topRows.mapNotNull { row ->
                            when {
                                row is PosterHomeRow && row.section.key == "continue-watching" -> {
                                    val remaining = row.section.items.filterNot { it.Id in removedContinueWatchingIds || it.Id in removedItemIds }
                                    if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
                                }
                                row is PosterHomeRow && row.section.key == "up-next" -> {
                                    val remaining = row.section.items.filterNot { it.Id in removedItemIds || (it.SeriesId != null && it.SeriesId in hiddenSeriesIds) }
                                    if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
                                }
                                row is PosterHomeRow && row.section.key.startsWith("watchlist") -> {
                                    val remaining = row.section.items.filterNot { removedFromWatchlist.containsKey(it.Id) || it.Id in removedItemIds }
                                    if (remaining.isEmpty()) null else row.copy(section = row.section.copy(items = remaining))
                                }
                                row is ComingSoonHomeRow -> {
                                    val remaining = row.entries.filterNot { it.ItemId in hiddenComingSoonIds }
                                    if (remaining.isEmpty()) null else row.copy(entries = remaining)
                                }
                                else -> row
                            }
                        }
                        addAll(filteredTop)
                        val topPicksTitle = com.jellio.tv.data.i18n.Strings.get("home_top_picks")
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
                        val rows = mergeKeepingPending(assembled(), previousRows, { it.key }, ::stillLoading)
                        val failed = finished && failedFetches.get() > 0
                        _uiState.value = HomeUiState(
                            isLoading = false,
                            heroItems = heroCandidates,
                            greeting = greeting,
                            rows = rows,
                            customization = customization,
                            loadingMore = !finished,
                            error = if (failed && rows.isEmpty()) loadFailureOf(lastFailure) else null,
                            rowsError = if (failed && rows.isNotEmpty()) loadFailureOf(lastFailure) else null,
                        )
                    }

                    publish()

                    // Never awaited: a slow community lookup holds nothing up.
                    if (isCommunityEnabled) {
                        launch {
                            val raw = orNullOnFailure { repository.getCommunityWatching(communityExcluded) }
                            communityDone = true
                            if (raw == null) {
                                communitySection = previousSection(COMMUNITY_ROW_KEY) ?: return@launch
                                publish()
                                return@launch
                            }
                            // Lands after the other rows, so filtering against them would usually empty it.
                            val eligible = raw.distinctBy { it.Id }
                            if (eligible.isEmpty()) {
                                publish()
                                return@launch
                            }
                            communitySection = HomeSection(
                                title = com.jellio.tv.data.i18n.Strings.get("home_community_watching"),
                                items = eligible.take(24),
                                key = COMMUNITY_ROW_KEY,
                            )
                            publish()
                        }
                    }

                    val recommendationJob = async {
                        val recs = orNullOnFailure { buildRecommendationRows(recommendationSource, recExclude) }
                        recommendationsDone = true
                        if (recs == null) {
                            recommendationRows = previousSections("rec:").filter { it.key != COMMUNITY_ROW_KEY }
                            publish()
                            return@async
                        }
                        recommendationRows = recs.map { it.copy(key = "rec:${it.title}") }
                        val picks = (recs.firstOrNull { it.title == com.jellio.tv.data.i18n.Strings.get("home_top_picks") } ?: recs.firstOrNull())?.items
                        if (!picks.isNullOrEmpty()) launch { catching { watchNextSyncer.updateTopPicks(session, picks) } }
                        publish()
                    }

                    val seasonalJob = seasonalDeferred?.let { pending ->
                        async {
                            val seasonal = pending.await()
                            seasonalDone = true
                            val (title, rawItems) = seasonal ?: run {
                                seasonalSection = previousSections("seasonal:").firstOrNull() ?: return@async
                                publish()
                                return@async
                            }
                            val eligible = rawItems.filter { it.UserData?.Played != true && !recExclude.contains(it.Id) }
                            eligible.forEach { recExclude.add(it.Id) }
                            if (eligible.isNotEmpty()) {
                                seasonalSection = HomeSection(title = title, items = eligible.take(24), key = "seasonal:$activeTheme")
                                publish()
                            }
                        }
                    }

                    if (collectionsFresh == null) {
                        previousSections("catalog:").forEachIndexed { index, section -> catalogSections[index] = section }
                    }
                    val catalogJobs = catalogs.mapIndexed { index, collection ->
                        async {
                            val kind = repository.collectionKind(collection)
                            val fetched = orNullOnFailure { repository.getCollectionItems(session.userId, collection.Id, kind, CATALOG_ROW_LIMIT) }
                            catalogsPending.remove("catalog:${collection.Id}")
                            if (fetched == null) {
                                previousSection("catalog:${collection.Id}")?.let {
                                    catalogSections[index] = it
                                    publish()
                                }
                                return@async
                            }
                            val items = fetched.filterNot { isAnime(it) }
                            val entry = CatalogRowEntry(title = titleFor(collection.Name, kind), items = items, collectionId = collection.Id, kind = kind)
                            catalogSection(session.userId, entry, catalogAndGenreDeduplicator)?.let {
                                catalogSections[index] = it
                                publish()
                            }
                        }
                    }

                    val genreJob = async {
                        val genres = orNullOnFailure { chooseGenres(session.userId) }
                        if (genres == null) {
                            genresPending = mutableSetOf()
                            previousSections("genre:").forEachIndexed { index, section -> genreSections[index] = section }
                            publish()
                            return@async
                        }
                        genresPending = genres.mapTo(mutableSetOf()) { "genre:$it" }
                        genres.mapIndexed { index, genre ->
                            async {
                                val fetched = orNullOnFailure { repository.getGenreItems(session.userId, null, "Movie,Series", genre, GENRE_ROW_LIMIT) }
                                genresPending?.remove("genre:$genre")
                                if (fetched == null) {
                                    previousSection("genre:$genre")?.let {
                                        genreSections[index] = it
                                        publish()
                                    }
                                    return@async
                                }
                                val items = fetched.filterNot { isAnime(it) }
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
                        freshAtMs = android.os.SystemClock.elapsedRealtime()
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
            } catch (err: kotlinx.coroutines.CancellationException) {
                throw err
            } catch (err: Exception) {
                android.util.Log.w("JellioHome", "home failed: ${err.message}")
                val failure = loadFailureOf(err)
                val state = _uiState.value
                _uiState.value = if (state.rows.isEmpty()) {
                    HomeUiState(isLoading = false, greeting = state.greeting, customization = state.customization, error = failure)
                } else {
                    state.copy(isLoading = false, loadingMore = false, rowsError = failure)
                }
                scheduleHomeRetry(session)
            }
        }
    }

    suspend fun showcaseInfo(session: Session, item: BaseItemDto): ShowcaseInfo =
        loadShowcaseInfo(repository, session.userId, item)

    private val removedFromWatchlist = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val hiddenComingSoonIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val removedItemIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val removedContinueWatchingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val hiddenSeriesIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Gone from the row at once; back if the server says no.
    fun removeFromComingSoon(itemId: String) {
        val before = _uiState.value.rows
        hiddenComingSoonIds.add(itemId)
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
                hiddenComingSoonIds.remove(itemId)
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
                val res = catching { repository.toggleFavorite(session.userId, item) }
                if (res.isFailure || res.getOrNull() == true) {
                    removedFromWatchlist.remove(item.Id)
                    _uiState.value = beforeState
                    persistCurrentSnapshot(session.userId)
                    _toastMessage.value = com.jellio.tv.data.i18n.Strings.get("toast_watchlist_error")
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
                val newRow = PosterHomeRow(HomeSection(com.jellio.tv.data.i18n.Strings.get(rowTitleKey), listOf(updatedItem), key = rowKey))
                updatedRows = insertWatchlistRow(updatedRows, newRow)
            }
            _uiState.value = beforeState.copy(rows = updatedRows, heroItems = updatedHero)
            persistCurrentSnapshot(session.userId)

            viewModelScope.launch {
                val res = catching { repository.toggleFavorite(session.userId, item) }
                if (res.isFailure || res.getOrNull() == false) {
                    _uiState.value = beforeState
                    persistCurrentSnapshot(session.userId)
                    _toastMessage.value = com.jellio.tv.data.i18n.Strings.get("toast_watchlist_error")
                } else {
                    catching {
                        val fresh = friendsRepository.withAddedBy(repository.getWatchlistItems(session.userId).filter { !removedFromWatchlist.containsKey(it.Id) })
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
            val movieRow = PosterHomeRow(HomeSection(com.jellio.tv.data.i18n.Strings.get("home_watchlist_movies"), movies, key = "watchlist-movies"))
            if (updatedRows.any { it.key == "watchlist-movies" }) {
                updatedRows = updatedRows.map { if (it.key == "watchlist-movies") movieRow else it }
            } else {
                updatedRows = insertWatchlistRow(updatedRows, movieRow)
            }
        } else {
            updatedRows = updatedRows.filterNot { it.key == "watchlist-movies" }
        }
        if (series.isNotEmpty()) {
            val seriesRow = PosterHomeRow(HomeSection(com.jellio.tv.data.i18n.Strings.get("home_watchlist_shows"), series, key = "watchlist-shows"))
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

    // The server takes watched titles off the watchlist; its answer says so.
    private fun dropFromWatchlistRows(userId: String, itemId: String) {
        val autoRemoved = setOf(itemId)
        removedFromWatchlist[itemId] = true
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
        val prevPlayed = item.UserData?.Played ?: false
        val next = !prevPlayed
        updateItem(item.Id) { it.copy(UserData = (it.UserData ?: UserItemDataDto()).copy(Played = next)) }
        viewModelScope.launch {
            catching { repository.setPlayed(session.userId, item.Id, next) }
                .onSuccess { updated ->
                    updateItem(item.Id) { it.copy(UserData = updated) }
                    if (next && !updated.IsFavorite) dropFromWatchlistRows(session.userId, item.Id)
                }
                .onFailure {
                    updateItem(item.Id) { it.copy(UserData = (it.UserData ?: UserItemDataDto()).copy(Played = prevPlayed)) }
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
        removedItemIds.add(item.Id)
        removeItemFromRows(item.Id)
        viewModelScope.launch {
            catching { repository.setPlayed(session.userId, item.Id, true) }
                .onSuccess { updated -> if (!updated.IsFavorite) dropFromWatchlistRows(session.userId, item.Id) }
                .onFailure {
                    removedItemIds.remove(item.Id)
                    _uiState.value = _uiState.value.copy(rows = before)
                }
        }
    }

    fun hideShowFromRow(item: BaseItemDto) {
        val seriesId = item.SeriesId ?: return
        val before = _uiState.value.rows
        hiddenSeriesIds.add(seriesId)
        removedItemIds.add(item.Id)
        removeSeriesFromUpNext(seriesId, item.Id)
        viewModelScope.launch {
            catching { repository.hideSeriesFromNextUp(seriesId) }
                .onFailure {
                    hiddenSeriesIds.remove(seriesId)
                    removedItemIds.remove(item.Id)
                    _uiState.value = _uiState.value.copy(rows = before)
                }
        }
    }

    // Off Continue Watching without marking it watched: only its resume
    // position is cleared, so elsewhere it just loses its progress bar.
    fun removeFromContinueWatching(session: Session, item: BaseItemDto) {
        val before = _uiState.value.rows
        removedContinueWatchingIds.add(item.Id)
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
            val updated = catching { repository.clearResumePosition(session.userId, item.Id) }.getOrNull()
            if (updated == null) {
                removedContinueWatchingIds.remove(item.Id)
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
        val custom = recommendationPreferences.getCustomGenres(userId).filter { it.enabled }.map { it.name }
        val base = if (selected.isNotEmpty()) selected else repository.discoverGenres(userId, null, "Movie,Series", GENRE_ROWS)
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (g in base) {
            val key = g.trim().lowercase()
            if (key.isNotEmpty() && seen.add(key)) result.add(g)
        }
        for (g in custom) {
            val key = g.trim().lowercase()
            if (key.isNotEmpty() && seen.add(key)) result.add(g)
        }
        return result
    }

    private fun genreSection(userId: String, entry: GenreRowEntry, deduplicator: SlidingRowDeduplicator): HomeSection? {
        val deduped = deduplicator.dedupe(entry.items, ROW_DISPLAY_LIMIT)
        if (deduped.isEmpty()) return null
        return HomeSection(
            title = com.jellio.tv.data.i18n.genreDisplayName(entry.genre),
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
