package com.jellio.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.prefs.StreamPreferences
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.isPendingTodayRelease
import com.jellio.tv.ui.common.isReleasedToday
import com.jellio.tv.ui.common.isUnreleasedItem
import com.jellio.tv.ui.common.markTodayItemHasStream
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SECOND_SOURCE_DELAY_MS = 1_500L

sealed interface PlayAction {
    data class Direct(val itemId: String, val mediaSourceId: String?) : PlayAction
    data class ShowPicker(val item: BaseItemDto) : PlayAction
}

data class DetailUiState(
    val isLoading: Boolean = true,
    val item: BaseItemDto? = null,
    val error: String? = null,
    val seasons: List<BaseItemDto> = emptyList(),
    val selectedSeasonId: String? = null,
    val selectedSeasonEpisodes: List<BaseItemDto> = emptyList(),
    // The picked season's own details: its description, cast and backdrop
    // replace the show's where it has them.
    val selectedSeason: BaseItemDto? = null,
    val cast: List<com.jellio.tv.data.model.PersonDto> = emptyList(),
    val trailers: List<com.jellio.tv.data.model.TrailerDto> = emptyList(),
    val playLabel: String = com.jellio.tv.ui.i18n.Strings.get("action_play"),
    val isWatchlisted: Boolean = false,
    val isWatched: Boolean = false,
    val likes: Boolean? = null,
    val resolvingPlay: Boolean = false,
    // While loading: art from the row the title was picked in.
    val loadingArt: TitleLoadingArtInfo? = null,
    // Shown under the actions while the stream lookup runs or found nothing.
    val streamStatus: String? = null,
    val findingStreams: Boolean = false,
    // The page is drawn and a slow details request is being asked again.
    val detailsRetrying: Boolean = false,
    // Every attempt failed; what is shown stays and Retry asks again.
    val detailsFailed: Boolean = false,
    val streamVerifiedTick: Int = 0,
)

data class TitleLoadingArtInfo(val art: List<com.jellio.tv.ui.common.ArtCandidate>, val logoUrl: String?, val title: String)

private const val DETAILS_ATTEMPT_TIMEOUT_MS = 9_000L

private class DetailsTimeout : Exception("Timed out")

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val streamPreferences: StreamPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var loadedItemId: String? = null
    private var ratingTargetId: String? = null
    private var loadJob: Job? = null
    private var seasonsJob: Job? = null
    private var episodesJob: Job? = null
    private var streamLookupJob: Job? = null
    private var seriesPlayDeferred: Deferred<BaseItemDto?>? = null

    private fun cancelActiveWork() {
        loadJob?.cancel()
        loadJob = null
        seasonsJob?.cancel()
        seasonsJob = null
        episodesJob?.cancel()
        episodesJob = null
        streamLookupJob?.cancel()
        streamLookupJob = null
        secondSourceJob?.cancel()
        secondSourceJob = null
        playPrefetchJob?.cancel()
        playPrefetchJob = null
        seriesPlayDeferred?.cancel()
        seriesPlayDeferred = null
    }

    private fun isCurrentSeries(seriesId: String, ownerItemId: String? = loadedItemId): Boolean =
        ownerItemId != null && loadedItemId == ownerItemId && (loadedItemId == seriesId || _uiState.value.item?.SeriesId == seriesId)

    fun load(session: Session, itemId: String) {
        if (loadedItemId == itemId && !_uiState.value.isLoading && _uiState.value.item?.Id == itemId && !_uiState.value.detailsFailed) return
        cancelActiveWork()
        loadedItemId = itemId
        ratingTargetId = null
        val preview = repository.previewItem(itemId)
        val preloaded = repository.preloadedDetails(itemId) ?: preview
        _uiState.value = if (preloaded != null) {
            DetailUiState(
                isLoading = false,
                item = preloaded,
                loadingArt = loadingArt(session, preloaded),
                cast = preloaded.People?.filter { it.Type == "Actor" }?.take(20) ?: emptyList(),
                trailers = preloaded.RemoteTrailers?.filter { !it.Url.isNullOrEmpty() } ?: emptyList(),
                isWatchlisted = preloaded.UserData?.IsFavorite ?: false,
                isWatched = preloaded.UserData?.Played ?: false,
            )
        } else {
            DetailUiState(
                isLoading = true,
                item = null,
                loadingArt = preview?.let { loadingArt(session, it) },
            )
        }
        loadInternal(session, itemId)
    }

    fun retry(session: Session, itemId: String) {
        cancelActiveWork()
        loadedItemId = itemId
        ratingTargetId = null
        loadInternal(session, itemId)
    }

    private fun loadInternal(session: Session, itemId: String) {
        loadJob?.cancel()
        seriesPlayDeferred?.cancel()
        seriesPlayDeferred = null
        loadJob = viewModelScope.launch {
            if (loadedItemId != itemId) return@launch
            val preloaded = _uiState.value.item?.takeIf { it.Id == itemId }
            // The stream lookup does not wait for the details when the type is already known.
            var streamsStarted = false
            if ((preloaded?.Type == "Movie" || preloaded?.Type == "Episode") && (!isUnreleasedItem(preloaded) || isReleasedToday(preloaded))) {
                lookForStreams(session, itemId)
                streamsStarted = true
            }
            try {
                val started = System.currentTimeMillis()
                // A series drawn from what was last seen starts on its seasons now.
                if (preloaded?.Type == "Series") {
                    loadSeasons(session, preloaded.Id)
                    seriesPlayDeferred = viewModelScope.async { resolveSeriesPlay(session, preloaded.Id) }
                } else if (preloaded?.Type == "Episode" && preloaded.SeriesId != null) {
                    loadSeasons(session, preloaded.SeriesId, preferredSeasonId = preloaded.SeasonId, preferredSeasonNumber = preloaded.ParentIndexNumber)
                }
                // Two retries with backoff; the art drawn from memory stays up meanwhile.
                val item = withRetries(
                    onRetry = {
                        if (loadedItemId == itemId && _uiState.value.item?.Id == itemId) {
                            _uiState.value = _uiState.value.copy(detailsRetrying = true, detailsFailed = false)
                        }
                    },
                ) { repository.getItemDetails(session.userId, itemId) }
                if (loadedItemId != itemId) return@launch
                android.util.Log.i("JellioDetail", "info for $itemId in ${System.currentTimeMillis() - started} ms" + if (preloaded != null) " (drawn from memory first)" else "")
                ratingTargetId = item.Id
                // Updates the page in place, keeping seasons and stream state.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    item = item,
                    error = null,
                    detailsRetrying = false,
                    detailsFailed = false,
                    cast = item.People?.filter { it.Type == "Actor" }?.take(20) ?: emptyList(),
                    trailers = item.RemoteTrailers?.filter { !it.Url.isNullOrEmpty() } ?: emptyList(),
                    isWatchlisted = item.UserData?.IsFavorite ?: false,
                    isWatched = item.UserData?.Played ?: false,
                )

                if (!streamsStarted && (item.Type == "Movie" || item.Type == "Episode") && (!isUnreleasedItem(item) || isReleasedToday(item))) lookForStreams(session, item.Id)

                if (item.Type == "Episode" && item.SeriesId != null) {
                    viewModelScope.launch {
                        runCatching { repository.getItem(session.userId, item.SeriesId) }.getOrNull()?.let { series ->
                            if (loadedItemId != itemId) return@launch
                            ratingTargetId = series.Id
                            _uiState.value = _uiState.value.copy(likes = series.UserData?.Likes)
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(likes = item.UserData?.Likes)
                }

                if (item.Type == "Series" && preloaded?.Type != "Series") {
                    loadSeasons(session, item.Id)
                    seriesPlayDeferred = viewModelScope.async { resolveSeriesPlay(session, item.Id) }
                } else if (item.Type == "Episode" && item.SeriesId != null) {
                    if (preloaded?.Type != "Episode" || _uiState.value.seasons.isEmpty()) {
                        loadSeasons(session, item.SeriesId, preferredSeasonId = item.SeasonId, preferredSeasonNumber = item.ParentIndexNumber)
                    }
                }
            } catch (err: kotlinx.coroutines.CancellationException) {
                throw err
            } catch (err: Exception) {
                if (loadedItemId != itemId) return@launch
                android.util.Log.w("JellioDetail", "details for $itemId failed: ${err.message}")
                // A page already drawn from memory stays up, with a Retry.
                val currentItem = _uiState.value.item?.takeIf { it.Id == itemId }
                if (currentItem != null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, item = currentItem, detailsRetrying = false, detailsFailed = true)
                } else {
                    _uiState.value = DetailUiState(isLoading = false, item = null, error = com.jellio.tv.ui.i18n.Strings.get("detail_load_error"))
                }
            }
        }
    }

    private fun loadingArt(session: Session, item: BaseItemDto): TitleLoadingArtInfo {
        val isEpisode = item.Type == "Episode"
        val art = com.jellio.tv.ui.common.titleArtCandidates(
            item,
            { id, tag, type, width -> repository.imageUrl(session.serverAddress, id, tag, type, width, session.accessToken) },
            seriesFirst = isEpisode,
            backdropWidth = 1600,
        )
        val logoUrl = if (isEpisode) {
            val tag = item.ParentLogoImageTag
            val id = item.ParentLogoItemId ?: item.SeriesId
            if (tag != null && id != null) repository.imageUrl(session.serverAddress, id, tag, "Logo", 800, session.accessToken) else null
        } else {
            item.ImageTags?.get("Logo")?.let { repository.imageUrl(session.serverAddress, item.Id, it, "Logo", 800, session.accessToken) }
        }
        return TitleLoadingArtInfo(art, logoUrl, if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty())
    }

    private suspend fun resolveSeriesPlay(session: Session, seriesId: String): BaseItemDto? {
        val target = runCatching { repository.resolveSeriesPlayTarget(seriesId, session.userId) }.getOrNull() ?: return null
        if (loadedItemId != seriesId) return null
        if (target.resume) {
            _uiState.value = _uiState.value.copy(
                playLabel = com.jellio.tv.ui.i18n.Strings.format("detail_resume_episode", target.episode.ParentIndexNumber ?: 0, target.episode.IndexNumber ?: 0),
            )
        }
        // The episode Play will start: its details and streams load now, so
        // pressing Play starts right away.
        repository.preloadDetails(target.episode.Id)
        if (!isUnreleasedItem(target.episode)) {
            repository.prefetchStreams(target.episode.Id)
        }
        return target.episode
    }

    private fun loadSeasons(session: Session, seriesId: String, preferredSeasonId: String? = null, preferredSeasonNumber: Int? = null) {
        val ownerItemId = loadedItemId ?: return
        if (!isCurrentSeries(seriesId, ownerItemId)) return
        fun ordered(list: List<BaseItemDto>) = list.sortedBy { if (repository.isSpecialsSeason(it)) 1 else 0 }
        fun pickSeason(list: List<BaseItemDto>): BaseItemDto? {
            if (preferredSeasonId != null) {
                list.firstOrNull { it.Id == preferredSeasonId }?.let { return it }
            }
            if (preferredSeasonNumber != null) {
                list.firstOrNull { it.IndexNumber == preferredSeasonNumber }?.let { return it }
            }
            return list.firstOrNull()
        }
        repository.recentSeasons(seriesId)?.let { known ->
            if (!isCurrentSeries(seriesId, ownerItemId)) return
            val seasons = ordered(known)
            _uiState.value = _uiState.value.copy(seasons = seasons)
            pickSeason(seasons)?.let { selectSeason(session, seriesId, it.Id) }
        }
        seasonsJob?.cancel()
        seasonsJob = viewModelScope.launch {
            val seasons = ordered(runCatching { repository.getSeasons(seriesId, session.userId) }.getOrDefault(emptyList()))
            if (!isCurrentSeries(seriesId, ownerItemId)) return@launch
            if (seasons.isEmpty() && _uiState.value.seasons.isNotEmpty()) return@launch
            _uiState.value = _uiState.value.copy(seasons = seasons)
            val selected = _uiState.value.selectedSeasonId
            val toSelect = if (selected != null && seasons.any { it.Id == selected }) {
                seasons.firstOrNull { it.Id == selected }
            } else {
                pickSeason(seasons)
            }
            toSelect?.let { selectSeason(session, seriesId, it.Id) }
        }
    }

    fun selectSeason(session: Session, seriesId: String, seasonId: String) {
        val ownerItemId = loadedItemId ?: return
        if (!isCurrentSeries(seriesId, ownerItemId)) return
        val known = repository.recentEpisodes(seriesId, seasonId)
        val showing = _uiState.value.selectedSeasonId == seasonId && _uiState.value.selectedSeasonEpisodes.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            selectedSeasonId = seasonId,
            selectedSeasonEpisodes = known ?: if (showing) _uiState.value.selectedSeasonEpisodes else emptyList(),
        )
        if (known != null) {
            checkTodayEpisodeStreams(session, known)
        }
        episodesJob?.cancel()
        episodesJob = viewModelScope.launch {
            launch {
                val season = runCatching { repository.getItemDetails(session.userId, seasonId) }.getOrNull() ?: return@launch
                if (!isCurrentSeries(seriesId, ownerItemId) || _uiState.value.selectedSeasonId != seasonId) return@launch
                val isEpisode = _uiState.value.item?.Type == "Episode"
                val itemCast = _uiState.value.item?.People?.filter { it.Type == "Actor" }?.take(20).orEmpty()
                val seasonCast = season.People?.filter { it.Type == "Actor" }?.take(20).orEmpty()
                _uiState.value = _uiState.value.copy(
                    selectedSeason = season,
                    cast = if (isEpisode && itemCast.isNotEmpty()) itemCast else seasonCast.ifEmpty { itemCast },
                )
            }
            launch {
                val episodes = runCatching { repository.getEpisodes(seriesId, session.userId, seasonId) }.getOrNull() ?: return@launch
                if (!isCurrentSeries(seriesId, ownerItemId) || _uiState.value.selectedSeasonId != seasonId) return@launch
                repository.seedPreviews(episodes)
                _uiState.value = _uiState.value.copy(selectedSeasonEpisodes = episodes)
                checkTodayEpisodeStreams(session, episodes)
            }
        }
    }

    private fun checkTodayEpisodeStreams(session: Session, episodes: List<BaseItemDto>) {
        val todayEpisodes = episodes.filter { isPendingTodayRelease(it) && !isUnreleasedItem(it) }
        if (todayEpisodes.isEmpty()) return
        viewModelScope.launch {
            val results = todayEpisodes.map { ep ->
                async { repository.hasValidStreamForTodayItem(session.userId, ep) }
            }.awaitAll()
            if (results.any { it }) {
                _uiState.value = _uiState.value.copy(streamVerifiedTick = _uiState.value.streamVerifiedTick + 1)
            }
        }
    }

    fun preloadEpisode(itemId: String) {
        repository.preloadDetails(itemId)
    }

    suspend fun resolvePlayTarget(): BaseItemDto? {
        val item = _uiState.value.item ?: return null
        return if (item.Type == "Series") seriesPlayDeferred?.await() else item
    }

    private var playPrefetchJob: kotlinx.coroutines.Job? = null
    private val playPrefetched = HashSet<String>()

    // Resting on Play asks the server for the stream it will start, so the
    // player finds it ready. Once per title, after a short dwell.
    fun onPlayFocusChanged(session: Session, focused: Boolean) {
        playPrefetchJob?.cancel()
        if (!focused) return
        playPrefetchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1_000)
            val target = resolvePlayTarget() ?: return@launch
            if (target.Type != "Movie" && target.Type != "Episode") return@launch
            if (isUnreleasedItem(target)) return@launch
            if (!playPrefetched.add(target.Id)) return@launch
            val remembered = if (streamPreferences.isRememberEnabled()) streamPreferences.rememberedMediaSourceId(target.Id) else null
            repository.prefetchPlayback(session.userId, target.Id, remembered, target.UserData?.PlaybackPositionTicks ?: 0)
        }
    }

    fun setResolvingPlay(resolving: Boolean) {
        _uiState.value = _uiState.value.copy(resolvingPlay = resolving)
    }

    fun toggleWatchlist(session: Session) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            val newValue = runCatching { repository.toggleFavorite(session.userId, item) }.getOrDefault(_uiState.value.isWatchlisted)
            _uiState.value = _uiState.value.copy(isWatchlisted = newValue)
        }
    }

    fun toggleWatched(session: Session) {
        val item = _uiState.value.item ?: return
        val next = !_uiState.value.isWatched
        viewModelScope.launch {
            val updated = runCatching { repository.setPlayed(session.userId, item.Id, next) }.getOrNull()
            val wasAutoRemoved = item.Id in repository.lastAutoRemovedWatchlistIds.value
            _uiState.value = _uiState.value.copy(
                isWatched = updated?.Played ?: next,
                isWatchlisted = if (wasAutoRemoved) false else _uiState.value.isWatchlisted,
            )
        }
    }

    fun setLike(session: Session, likes: Boolean) {
        val targetId = ratingTargetId ?: return
        val next = if (_uiState.value.likes == likes) null else likes
        viewModelScope.launch {
            val updated = runCatching { repository.setRating(session.userId, targetId, next) }.getOrNull()
            _uiState.value = _uiState.value.copy(likes = updated?.Likes ?: next)
        }
    }

    // Streams are looked up only once the page is drawn; Play reuses this
    // same lookup (the repository shares it and caches the answer).
    private var secondSourceJob: kotlinx.coroutines.Job? = null

    // Leaving the page cancels active warmups.
    fun onScreenHidden(itemId: String? = null) {
        secondSourceJob?.cancel()
        secondSourceJob = null
        playPrefetchJob?.cancel()
        playPrefetchJob = null
    }

    private fun lookForStreams(session: Session, itemId: String) {
        if (loadedItemId != itemId) return
        repository.prefetchStreams(itemId)
        val openedAt = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(findingStreams = true, streamStatus = null)
        streamLookupJob?.cancel()
        streamLookupJob = viewModelScope.launch {
            val sources = runCatching { repository.getMediaSources(session.userId, itemId, reason = "detail") }
            if (loadedItemId != itemId || _uiState.value.item?.Id != itemId) return@launch
            val currentItem = _uiState.value.item
            if (currentItem?.Type == "Episode" && isReleasedToday(currentItem)) {
                val hasStream = !sources.getOrNull().isNullOrEmpty()
                markTodayItemHasStream(itemId, hasStream)
            }
            warmSecondSource(session, itemId, sources.getOrNull().orEmpty(), openedAt)
            _uiState.value = _uiState.value.copy(
                findingStreams = false,
                streamVerifiedTick = _uiState.value.streamVerifiedTick + 1,
                streamStatus = when {
                    sources.isFailure -> com.jellio.tv.ui.i18n.Strings.get("stream_load_error")
                    sources.getOrNull().isNullOrEmpty() -> com.jellio.tv.ui.i18n.Strings.get("stream_none_found")
                    else -> null
                },
            )
        }
    }

    // Still on the page after a moment: the next source in line is the likely
    // fallback, so have its probe done too.
    private fun warmSecondSource(session: Session, itemId: String, sources: List<MediaSourceDto>, openedAt: Long) {
        if (loadedItemId != itemId) return
        secondSourceJob?.cancel()
        secondSourceJob = viewModelScope.launch {
            kotlinx.coroutines.delay((SECOND_SOURCE_DELAY_MS - (System.currentTimeMillis() - openedAt)).coerceAtLeast(0L))
            if (loadedItemId != itemId || _uiState.value.item?.Id != itemId) return@launch
            val bad = sources.filter { com.jellio.tv.ui.player.BadStreams.isBad(itemId, it) }
            val second = ((sources - bad.toSet()) + bad).getOrNull(1)?.Id ?: return@launch
            val warm = repository.getWarmSources(itemId).orEmpty().keys.map { com.jellio.tv.data.streams.normalizeSourceId(it) }
            if (com.jellio.tv.data.streams.normalizeSourceId(second) !in warm) {
                repository.probeSource(session.userId, itemId, second)
            }
        }
    }

    suspend fun getMediaSources(session: Session, itemId: String): List<MediaSourceDto> =
        runCatching { repository.getMediaSources(session.userId, itemId) }.getOrDefault(emptyList())

    suspend fun resolvePlayAction(session: Session, item: BaseItemDto, forceChoice: Boolean = false): PlayAction {
        if (!forceChoice && streamPreferences.isRememberEnabled()) {
            val remembered = streamPreferences.rememberedMediaSourceId(item.Id)
            if (remembered != null) {
                val sources = getMediaSources(session, item.Id)
                if (sources.any { it.Id == remembered && !com.jellio.tv.ui.player.BadStreams.isBad(item.Id, it) }) {
                    return PlayAction.Direct(item.Id, remembered)
                }
            }
        }
        return PlayAction.ShowPicker(item)
    }

    suspend fun rememberStreamChoice(itemId: String, mediaSourceId: String) {
        if (streamPreferences.isRememberEnabled()) streamPreferences.remember(itemId, mediaSourceId)
    }

    fun toggleEpisodeWatched(session: Session, episode: BaseItemDto) {
        val next = !(episode.UserData?.Played ?: false)
        viewModelScope.launch {
            val updated = runCatching { repository.setPlayed(session.userId, episode.Id, next) }.getOrNull()
            applyEpisodeUpdate(episode.Id, updated)
            checkAutoRemovedWatchlist()
        }
    }

    fun markPreviousWatched(session: Session, episode: BaseItemDto) {
        val episodes = _uiState.value.selectedSeasonEpisodes
        val index = episodes.indexOfFirst { it.Id == episode.Id }
        if (index <= 0) return
        val previous = episodes.subList(0, index)
        viewModelScope.launch {
            val updates = previous.map { prev ->
                async { prev.Id to runCatching { repository.setPlayed(session.userId, prev.Id, true) }.getOrNull() }
            }.awaitAll()
            applyEpisodeUpdates(updates)
            checkAutoRemovedWatchlist()
        }
    }

    fun markSeasonWatched(session: Session) {
        val episodes = _uiState.value.selectedSeasonEpisodes
        viewModelScope.launch {
            val updates = episodes.map { ep ->
                async { ep.Id to runCatching { repository.setPlayed(session.userId, ep.Id, true) }.getOrNull() }
            }.awaitAll()
            applyEpisodeUpdates(updates)
            checkAutoRemovedWatchlist()
        }
    }

    private fun checkAutoRemovedWatchlist() {
        val currentItemId = _uiState.value.item?.Id
        if (currentItemId != null && currentItemId in repository.lastAutoRemovedWatchlistIds.value) {
            _uiState.value = _uiState.value.copy(isWatchlisted = false)
        }
    }

    private fun applyEpisodeUpdate(episodeId: String, userData: UserItemDataDto?) {
        if (userData == null) return
        applyEpisodeUpdates(listOf(episodeId to userData))
    }

    private fun applyEpisodeUpdates(updates: List<Pair<String, UserItemDataDto?>>) {
        val byId = updates.mapNotNull { (id, data) -> data?.let { id to it } }.toMap()
        if (byId.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            selectedSeasonEpisodes = _uiState.value.selectedSeasonEpisodes.map { episode ->
                byId[episode.Id]?.let { episode.copy(UserData = it) } ?: episode
            },
        )
    }

    // Each attempt is bounded, so a server busy with a database task turns
    // into a retry instead of a page that waits a minute.
    private suspend fun <T> withRetries(onRetry: () -> Unit, block: suspend () -> T): T {
        suspend fun attempt(): T =
            kotlinx.coroutines.withTimeoutOrNull(DETAILS_ATTEMPT_TIMEOUT_MS) { block() } ?: throw DetailsTimeout()
        repeat(2) { n ->
            try {
                return attempt()
            } catch (err: kotlinx.coroutines.CancellationException) {
                throw err
            } catch (err: Exception) {
                onRetry()
                kotlinx.coroutines.delay(1_000L shl n)
            }
        }
        return attempt()
    }
}
