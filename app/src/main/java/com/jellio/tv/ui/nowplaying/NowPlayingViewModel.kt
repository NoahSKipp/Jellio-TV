package com.jellio.tv.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.NowPlayingSessionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val POLL_INTERVAL_MS = 10_000L

// Real port of components/nowPlaying.js's own module level poll loop:
// a self starting, single real ISessionManager reader, its own list
// kept for the whole time the reader stays signed in rather than
// created fresh per screen the way a plain composable-scoped poll
// would if this ViewModel were requested again from a different
// screen (hiltViewModel() at this app's own root, JellioTvApp, keeps
// exactly one real instance alive the entire time the reader stays
// signed in, same real module-singleton lifetime that file's own
// started/panel module state already gives it). A failed poll leaves
// whatever was last shown rather than clearing it, same real reasoning
// that file's own poll() catch already documents.
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<NowPlayingSessionDto>>(emptyList())
    val sessions: StateFlow<List<NowPlayingSessionDto>> = _sessions.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            while (isActive) {
                val result = runCatching { repository.getNowPlayingSessions() }.getOrNull()
                if (result != null) _sessions.value = result
                delay(POLL_INTERVAL_MS)
            }
        }
    }
}
