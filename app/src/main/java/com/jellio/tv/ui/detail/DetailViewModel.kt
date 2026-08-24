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
    val cast: List<com.jellio.tv.data.model.PersonDto> = emptyList(),
    val trailers: List<com.jellio.tv.data.model.TrailerDto> = emptyList(),
    val playLabel: String = "Play",
    val isWatchlisted: Boolean = false,
    val isWatched: Boolean = false,
    val likes: Boolean? = null,
    val resolvingPlay: Boolean = false,
)

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
            _uiState.value = DetailUiState(isLoading = true)
            try {
                val item = repository.getItemDetails(session.userId, itemId)
                ratingTargetId = item.Id
                _uiState.value = DetailUiState(
                    isLoading = false,
                    item = item,
                    cast = item.People?.filter { it.Type == "Actor" }?.take(20) ?: emptyList(),
                    trailers = item.RemoteTrailers?.filter { !it.Url.isNullOrEmpty() } ?: emptyList(),
                    isWatchlisted = item.UserData?.IsFavorite ?: false,
                    isWatched = item.UserData?.Played ?: false,
                )

                if (item.Type == "Episode" && item.SeriesId != null) {
                    runCatching { repository.getItem(session.userId, item.SeriesId) }.getOrNull()?.let { series ->
                        ratingTargetId = series.Id
                        _uiState.value = _uiState.value.copy(likes = series.UserData?.Likes)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(likes = item.UserData?.Likes)
                }

                if (item.Type == "Series") {
                    loadSeasons(session, item.Id)
                    // screens/detail.js's own real targetPromise: started
                    // once here, not per click, a tap on Play before this
                    // resolves still awaits this exact same real deferred
                    // in resolvePlayTarget() below rather than reading a
                    // plain field that might still be null.
                    seriesPlayDeferred = viewModelScope.async { resolveSeriesPlay(session, item.Id) }
                }
            } catch (err: Exception) {
                _uiState.value = DetailUiState(isLoading = false, error = err.message ?: "Could not load this title")
            }
        }
    }

    private suspend fun resolveSeriesPlay(session: Session, seriesId: String): BaseItemDto? {
        val target = runCatching { repository.resolveSeriesPlayTarget(seriesId, session.userId) }.getOrNull() ?: return null
        if (target.resume) {
            _uiState.value = _uiState.value.copy(
                playLabel = "Resume S${target.episode.ParentIndexNumber} E${target.episode.IndexNumber}",
            )
        }
        return target.episode
    }

    private fun loadSeasons(session: Session, seriesId: String) {
        viewModelScope.launch {
            val seasons = runCatching { repository.getSeasons(seriesId, session.userId) }.getOrDefault(emptyList())
                .sortedBy { if (repository.isSpecialsSeason(it)) 1 else 0 }
            _uiState.value = _uiState.value.copy(seasons = seasons)
            seasons.firstOrNull()?.let { selectSeason(session, seriesId, it.Id) }
        }
    }

    fun selectSeason(session: Session, seriesId: String, seasonId: String) {
        _uiState.value = _uiState.value.copy(selectedSeasonId = seasonId, selectedSeasonEpisodes = emptyList())
        viewModelScope.launch {
            val episodes = runCatching { repository.getEpisodes(seriesId, session.userId, seasonId) }.getOrDefault(emptyList())
            if (_uiState.value.selectedSeasonId == seasonId) {
                _uiState.value = _uiState.value.copy(selectedSeasonEpisodes = episodes)
            }
        }
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

    suspend fun getMediaSources(session: Session, itemId: String): List<MediaSourceDto> =
        runCatching { repository.getMediaSources(session.userId, itemId) }.getOrDefault(emptyList())

    // forceChoice mirrors screens/detail.js's own Change Stream button:
    // it only ever skips the remembered shortcut below, a title with
    // one real source still has nothing to change to either way.
    suspend fun resolvePlayAction(session: Session, item: BaseItemDto, forceChoice: Boolean = false): PlayAction {
        val sources = getMediaSources(session, item.Id)
        if (sources.size <= 1) return PlayAction.Direct(item.Id, sources.firstOrNull()?.Id)

        if (!forceChoice && streamPreferences.isRememberEnabled()) {
            val remembered = streamPreferences.rememberedMediaSourceId(item.Id)
            if (remembered != null && sources.any { it.Id == remembered }) {
                return PlayAction.Direct(item.Id, remembered)
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
}
