package com.jellio.tv.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
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
) {
    val arranged: ArrangedChannels get() = arrangeChannels(filterChannels(channels, query, filters))
    val languages: List<Pair<String, Int>> get() = languageFacets(channels)
}

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

    fun load(session: Session, fresh: Boolean = false) {
        viewModelScope.launch {
            if (fresh) repository.invalidateLiveChannels(session.userId)
            val result = runCatching { repository.getLiveChannels(session.userId) }
            result.onSuccess { items ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = null, channels = mapChannels(items, System.currentTimeMillis()))
            }.onFailure { err ->
                android.util.Log.w("JellioLive", "channels failed: ${err.message}")
                if (_uiState.value.channels.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = com.jellio.tv.ui.i18n.Strings.get("live_load_error"))
                }
            }
        }
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
