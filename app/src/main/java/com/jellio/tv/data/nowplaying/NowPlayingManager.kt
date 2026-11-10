package com.jellio.tv.data.nowplaying

import com.jellio.tv.data.catching
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NowPlayingManager @Inject constructor(
    private val api: JellyfinApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var lastSession: Session? = null

    private val _sessions = MutableStateFlow<List<NowPlayingSessionDto>>(emptyList())
    val sessions: StateFlow<List<NowPlayingSessionDto>> = _sessions.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    data class LocalPausedMedia(
        val itemId: String,
        val title: String,
        val subtitle: String? = null,
        val positionTicks: Long = 0L,
        val runTimeTicks: Long? = null,
    )

    private val _localPaused = MutableStateFlow<LocalPausedMedia?>(null)
    val localPaused: StateFlow<LocalPausedMedia?> = _localPaused.asStateFlow()

    fun setLocalPaused(media: LocalPausedMedia?) {
        _localPaused.value = media
    }

    private var pollIntervalMs = 12000L
    @Volatile private var inBackground = false

    fun startPolling(session: Session, intervalMs: Long = 12000L) {
        lastSession = session
        pollIntervalMs = intervalMs
        if (session.accessToken.isEmpty() || inBackground) return
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                fetchNowPlaying()
                delay(intervalMs)
            }
        }
    }

    // The app left the screen: stop asking the server until it is back.
    fun onAppStopped() {
        inBackground = true
        pollJob?.cancel()
        pollJob = null
    }

    fun onAppStarted() {
        inBackground = false
        lastSession?.let { startPolling(it, pollIntervalMs) }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        lastSession = null
        _sessions.value = emptyList()
        _activeCount.value = 0
    }

    fun refresh() {
        if (lastSession == null) return
        scope.launch { fetchNowPlaying() }
    }

    private suspend fun fetchNowPlaying() {
        catching {
            val list = api.getNowPlaying()
            val active = list.filter { it.Item != null && !it.Item.Name.isNullOrBlank() }
            _sessions.value = active
            _activeCount.value = active.size
        }
    }
}
