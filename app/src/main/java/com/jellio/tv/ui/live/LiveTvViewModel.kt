package com.jellio.tv.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveTvUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val channels: List<LiveChannel> = emptyList(),
    val query: String = "",
    val filters: LiveFilters = LiveFilters(),
    val guideChannel: LiveChannel? = null,
    val guide: List<LiveProgramme>? = null,
    val links: List<com.jellio.tv.data.model.StreamLinkDto> = emptyList(),
    val pairing: LivePairing? = null,
    val pendingPlay: String? = null,
) {
    val userChannels: List<LiveChannel> get() = flattenStreamLinks(links)
    val arranged: ArrangedChannels get() = arrangeChannels(filterChannels(channels, query, filters))
    val myStreams: List<LiveChannel> get() = filterChannels(userChannels, query, filters)
    val languages: List<Pair<String, Int>> get() = languageFacets(channels + userChannels)
}

// The code a phone scans to send links to this TV.
data class LivePairing(
    val code: String,
    val url: String,
    val expired: Boolean = false,
    val addedName: String? = null,
)

// The query and the last channel survive leaving and coming back.
internal object LiveTvMemory {
    @Volatile var query: String = ""
    @Volatile var filters: LiveFilters = LiveFilters()
    @Volatile var lastChannelId: String? = null
}

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState(query = LiveTvMemory.query, filters = LiveTvMemory.filters))
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private var pairingJob: Job? = null

    fun load(session: Session, fresh: Boolean = false) {
        viewModelScope.launch {
            if (fresh) repository.invalidateLiveChannels(session.userId)
            // My streams failing never hides the server's channels, and the
            // other way round.
            val links = async { runCatching { repository.getStreamLinks() } }
            val result = runCatching { repository.getLiveChannels(session.userId) }
            links.await().onSuccess { _uiState.value = _uiState.value.copy(links = it) }
            result.onSuccess { items ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = null, channels = mapChannels(items, System.currentTimeMillis()))
            }.onFailure { err ->
                android.util.Log.w("JellioLive", "channels failed: ${err.message}")
                if (_uiState.value.channels.isEmpty() && _uiState.value.links.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = com.jellio.tv.ui.i18n.Strings.get("live_load_error"))
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    private suspend fun reloadLinks() {
        runCatching { repository.getStreamLinks() }.onSuccess { _uiState.value = _uiState.value.copy(links = it) }
    }

    fun removeLink(linkId: String) {
        viewModelScope.launch {
            runCatching { repository.removeStreamLink(linkId) }
            reloadLinks()
        }
    }

    fun refreshLink(linkId: String) {
        viewModelScope.launch {
            runCatching { repository.refreshStreamLink(linkId) }
            reloadLinks()
        }
    }

    // Shows a code and waits for links sent with it; a single stream that
    // arrives starts playing straight away.
    fun openPairing(session: Session) {
        pairingJob?.cancel()
        _uiState.value = _uiState.value.copy(pairing = LivePairing("", ""))
        pairingJob = viewModelScope.launch {
            val created = runCatching { repository.createStreamPairing() }.getOrElse {
                android.util.Log.w("JellioLive", "pairing failed: ${it.message}")
                _uiState.value = _uiState.value.copy(pairing = LivePairing("", "", expired = true))
                return@launch
            }
            val server = com.jellio.tv.di.normalizeServerAddress(session.serverAddress)
            val path = created.Path ?: ("Jellio/add?c=" + created.Code)
            _uiState.value = _uiState.value.copy(pairing = LivePairing(created.Code, "$server/$path"))
            var lastLinkId: String? = null
            while (true) {
                delay(2_000)
                val result = runCatching { repository.streamPairingStatus(created.Code) }
                if (result.isFailure) continue
                val status = result.getOrNull()
                if (status == null) {
                    _uiState.value.pairing?.let { _uiState.value = _uiState.value.copy(pairing = it.copy(expired = true)) }
                    return@launch
                }
                val linkId = status.LinkId ?: continue
                if (linkId == lastLinkId) continue
                lastLinkId = linkId
                reloadLinks()
                val link = _uiState.value.links.firstOrNull { it.Id == linkId }
                _uiState.value.pairing?.let { _uiState.value = _uiState.value.copy(pairing = it.copy(addedName = status.LinkName ?: link?.Name)) }
                if (link != null && link.Kind != "playlist") {
                    _uiState.value = _uiState.value.copy(pendingPlay = linkChannelId(link.Id, null))
                }
            }
        }
    }

    fun closePairing() {
        pairingJob?.cancel()
        pairingJob = null
        _uiState.value = _uiState.value.copy(pairing = null)
    }

    fun consumePendingPlay() {
        _uiState.value = _uiState.value.copy(pendingPlay = null)
    }

    fun setQuery(query: String) {
        LiveTvMemory.query = query
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun setFilters(filters: LiveFilters) {
        LiveTvMemory.filters = filters
        _uiState.value = _uiState.value.copy(filters = filters)
    }

    fun toggleFavorite(session: Session, channel: LiveChannel) {
        val next = !channel.favorite
        update(channel.id) { it.copy(favorite = next) }
        viewModelScope.launch {
            val ok = runCatching { repository.setChannelFavorite(session.userId, channel.id, next) }.isSuccess
            if (!ok) update(channel.id) { it.copy(favorite = !next) }
        }
    }

    fun openGuide(session: Session, channel: LiveChannel) {
        _uiState.value = _uiState.value.copy(guideChannel = channel, guide = null)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val programs = runCatching {
                repository.getLivePrograms(session.userId, channel.id, now, now + GUIDE_HOURS * 3_600_000L)
            }.getOrDefault(emptyList())
            if (_uiState.value.guideChannel?.id == channel.id) {
                _uiState.value = _uiState.value.copy(guide = guideWindow(programs, System.currentTimeMillis()))
            }
        }
    }

    fun closeGuide() {
        _uiState.value = _uiState.value.copy(guideChannel = null, guide = null)
    }

    private fun update(id: String, change: (LiveChannel) -> LiveChannel) {
        _uiState.value = _uiState.value.copy(channels = _uiState.value.channels.map { if (it.id == id) change(it) else it })
    }
}
