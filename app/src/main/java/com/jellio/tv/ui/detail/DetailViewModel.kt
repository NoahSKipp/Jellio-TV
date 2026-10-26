package com.jellio.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.prefs.StreamPreferences
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// screens/detail.js's own Play button: a real picker with nothing to
// pick between skips itself entirely, and a remembered choice
// (components/streamPicker.js's own isRememberStreamEnabled()) skips
// it too as long as that source is still actually offered.
sealed interface PlayAction {
    data class Direct(val itemId: String, val mediaSourceId: String?) : PlayAction
    data class ShowPicker(val item: BaseItemDto) : PlayAction
}

data class SeasonUiState(val season: BaseItemDto, val episodes: List<BaseItemDto> = emptyList())

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
)

data class TitleLoadingArtInfo(val art: List<com.jellio.tv.ui.common.ArtCandidate>, val logoUrl: String?, val title: String)

private const val DETAILS_ATTEMPT_TIMEOUT_MS = 9_000L

private class DetailsTimeout : Exception("Timed out")

// Real behavior ported from screens/detail.js: a series has no video
// of its own, its own hero Play button lazily resolves which episode
// (resolveSeriesPlayTarget, JellioRepository's own real port of it)
// before ever opening a stream picker, everything else (Watchlist,
// Mark Watched, thumbs) reads/writes the real item itself, or, for an
// Episode, the series it belongs to (a personal rating is a whole
// show's own opinion, not one episode's, same real feedback that file
// ported from).
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val streamPreferences: StreamPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var loadedItemId: String? = null
    private var ratingTargetId: String? = null
    // A series' own Play button (screens/detail.js's own real
    // targetPromise) awaits this exact real deferred rather than
    // reading a plain field synchronously: a tap that lands before
    // resolveSeriesPlayTarget() has actually resolved yet still plays
    // once it does, real behavior a synchronous null-if-not-ready-yet
    // read silently dropped before.
    private var seriesPlayDeferred: Deferred<BaseItemDto?>? = null

    fun load(session: Session, itemId: String) {
        if (loadedItemId == itemId) return
        loadedItemId = itemId
        loadInternal(session, itemId)
    }

    // screens/detail.js's own real Retry (components/networkState.js's
    // own renderRetry(), reached from renderDetailError()): a bad
    // connection just needs asking the same real lookup again, not a
    // trip back to a whole different screen first, the same real
    // resetting of load()'s own dedupe guard so this actually re-fetches
    // rather than a no-op against whatever itemId already "loaded".
    fun retry(session: Session, itemId: String) {
        loadedItemId = itemId
        loadInternal(session, itemId)
    }

    private fun loadInternal(session: Session, itemId: String) {
        seriesPlayDeferred = null
        viewModelScope.launch {
            // The player's loader, with art from the row the title was
            // picked in, until the full details arrive.
            val preview = repository.previewItem(itemId)
            val preloaded = repository.preloadedDetails(itemId) ?: preview
            _uiState.value = if (preloaded != null) {
                DetailUiState(
                    isLoading = false,
                    item = preloaded,
                    cast = preloaded.People?.filter { it.Type == "Actor" }?.take(20) ?: emptyList(),
                    trailers = preloaded.RemoteTrailers?.filter { !it.Url.isNullOrEmpty() } ?: emptyList(),
                    isWatchlisted = preloaded.UserData?.IsFavorite ?: false,
                    isWatched = preloaded.UserData?.Played ?: false,
                )
            } else {
                DetailUiState(isLoading = true, loadingArt = preview?.let { loadingArt(session, it) })
            }
            // The stream lookup does not wait for the details when the type is already known.
            var streamsStarted = false
            if (preloaded?.Type == "Movie" || preloaded?.Type == "Episode") {
                lookForStreams(session, itemId)
                streamsStarted = true
            }
            try {
                val started = System.currentTimeMillis()
                // A series drawn from what was last seen starts on its seasons now.
                if (preloaded?.Type == "Series") {
                    loadSeasons(session, preloaded.Id)
                    seriesPlayDeferred = viewModelScope.async { resolveSeriesPlay(session, preloaded.Id) }
                }
                // Two retries with backoff; the art drawn from memory stays up meanwhile.
                val item = withRetries(
                    onRetry = { if (_uiState.value.item != null) _uiState.value = _uiState.value.copy(detailsRetrying = true, detailsFailed = false) },
                ) { repository.getItemDetails(session.userId, itemId) }
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

                if (!streamsStarted && (item.Type == "Movie" || item.Type == "Episode")) lookForStreams(session, item.Id)

                if (item.Type == "Episode" && item.SeriesId != null) {
                    viewModelScope.launch {
                        runCatching { repository.getItem(session.userId, item.SeriesId) }.getOrNull()?.let { series ->
                            ratingTargetId = series.Id
                            _uiState.value = _uiState.value.copy(likes = series.UserData?.Likes)
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(likes = item.UserData?.Likes)
                }

                if (item.Type == "Series" && preloaded?.Type != "Series") {
                    loadSeasons(session, item.Id)
                    // screens/detail.js's own real targetPromise: started
                    // once here, not per click, a tap on Play before this
                    // resolves still awaits this exact same real deferred
                    // in resolvePlayTarget() below rather than reading a
                    // plain field that might still be null.
                    seriesPlayDeferred = viewModelScope.async { resolveSeriesPlay(session, item.Id) }
                }
            } catch (err: Exception) {
                android.util.Log.w("JellioDetail", "details for $itemId failed: ${err.message}")
                // A page already drawn from memory stays up, with a Retry.
                if (_uiState.value.item != null) {
                    _uiState.value = _uiState.value.copy(detailsRetrying = false, detailsFailed = true)
                } else {
                    _uiState.value = DetailUiState(isLoading = false, error = com.jellio.tv.ui.i18n.Strings.get("detail_load_error"))
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
        if (target.resume) {
            _uiState.value = _uiState.value.copy(
                playLabel = com.jellio.tv.ui.i18n.Strings.format("detail_resume_episode", target.episode.ParentIndexNumber ?: 0, target.episode.IndexNumber ?: 0),
            )
        }
        // The episode Play will start: its details and streams load now, so
        // pressing Play starts right away.
        repository.preloadDetails(target.episode.Id)
        repository.prefetchStreams(target.episode.Id)
        return target.episode
    }

    private fun loadSeasons(session: Session, seriesId: String) {
        fun ordered(list: List<BaseItemDto>) = list.sortedBy { if (repository.isSpecialsSeason(it)) 1 else 0 }
        repository.recentSeasons(seriesId)?.let { known ->
            _uiState.value = _uiState.value.copy(seasons = ordered(known))
            ordered(known).firstOrNull()?.let { selectSeason(session, seriesId, it.Id) }
        }
        viewModelScope.launch {
            val seasons = ordered(runCatching { repository.getSeasons(seriesId, session.userId) }.getOrDefault(emptyList()))
            if (seasons.isEmpty() && _uiState.value.seasons.isNotEmpty()) return@launch
            _uiState.value = _uiState.value.copy(seasons = seasons)
            val selected = _uiState.value.selectedSeasonId
            if (selected == null || seasons.none { it.Id == selected }) {
                seasons.firstOrNull()?.let { selectSeason(session, seriesId, it.Id) }
            } else {
                selectSeason(session, seriesId, selected)
            }
        }
    }

    fun selectSeason(session: Session, seriesId: String, seasonId: String) {
        val known = repository.recentEpisodes(seriesId, seasonId)
        val showing = _uiState.value.selectedSeasonId == seasonId && _uiState.value.selectedSeasonEpisodes.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            selectedSeasonId = seasonId,
            selectedSeasonEpisodes = known ?: if (showing) _uiState.value.selectedSeasonEpisodes else emptyList(),
        )
        viewModelScope.launch {
            val season = runCatching { repository.getItemDetails(session.userId, seasonId) }.getOrNull() ?: return@launch
            if (_uiState.value.selectedSeasonId != seasonId) return@launch
            val seriesCast = _uiState.value.item?.People?.filter { it.Type == "Actor" }?.take(20).orEmpty()
            val seasonCast = season.People?.filter { it.Type == "Actor" }?.take(20).orEmpty()
            _uiState.value = _uiState.value.copy(selectedSeason = season, cast = seasonCast.ifEmpty { seriesCast })
        }
        viewModelScope.launch {
            val episodes = runCatching { repository.getEpisodes(seriesId, session.userId, seasonId) }.getOrNull() ?: return@launch
            if (_uiState.value.selectedSeasonId == seasonId) {
                repository.seedPreviews(episodes)
                _uiState.value = _uiState.value.copy(selectedSeasonEpisodes = episodes)
            }
        }
    }

    fun preloadEpisode(itemId: String) {
        repository.preloadDetails(itemId)
    }

    // The one real item this screen's own Play button should hand off
    // to a stream picker/player for: itself for a movie or an episode,
    // whichever real episode resolveSeriesPlay above already resolved
    // for a series (a series has no video of its own). Suspends on the
    // real deferred rather than reading a plain field synchronously,
    // same real reason seriesPlayDeferred's own comment gives.
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
            _uiState.value = _uiState.value.copy(isWatched = updated?.Played ?: next)
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
    private fun lookForStreams(session: Session, itemId: String) {
        _uiState.value = _uiState.value.copy(findingStreams = true, streamStatus = null)
        viewModelScope.launch {
            val sources = runCatching { repository.getMediaSources(session.userId, itemId, reason = "detail") }
            if (_uiState.value.item?.Id != itemId) return@launch
            _uiState.value = _uiState.value.copy(
                findingStreams = false,
                streamStatus = when {
                    sources.isFailure -> com.jellio.tv.ui.i18n.Strings.get("stream_load_error")
                    sources.getOrNull().isNullOrEmpty() -> com.jellio.tv.ui.i18n.Strings.get("stream_none_found")
                    else -> null
                },
            )
        }
    }

    suspend fun getMediaSources(session: Session, itemId: String): List<MediaSourceDto> =
        runCatching { repository.getMediaSources(session.userId, itemId) }.getOrDefault(emptyList())

    // forceChoice skips the remembered shortcut below.
    // If a stream choice is remembered, play directly. Otherwise show
    // the stream picker so the user can verify or pick the source.
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

    // Real port of screens/detail.js's own openEpisodeOptionsMenu(): a
    // hold/right-click there, a real options button here (no direct
    // Compose equivalent for a hold gesture worth trusting untested),
    // same three real actions against the exact season track already
    // in state rather than a second real fetch, context.episodes/
    // context.onChanged's own real job here.
    fun toggleEpisodeWatched(session: Session, episode: BaseItemDto) {
        val next = !(episode.UserData?.Played ?: false)
        viewModelScope.launch {
            val updated = runCatching { repository.setPlayed(session.userId, episode.Id, next) }.getOrNull()
            applyEpisodeUpdate(episode.Id, updated)
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
        }
    }

    fun markSeasonWatched(session: Session) {
        val episodes = _uiState.value.selectedSeasonEpisodes
        viewModelScope.launch {
            val updates = episodes.map { ep ->
                async { ep.Id to runCatching { repository.setPlayed(session.userId, ep.Id, true) }.getOrNull() }
            }.awaitAll()
            applyEpisodeUpdates(updates)
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
