package com.jellio.tv.ui.live

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.OpenedLiveStream
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val TAG = "JellioLive"
private const val OPEN_TIMEOUT_MS = 25_000L
private const val STALL_NOTICE_MS = 15_000L
private const val STALL_RESTART_MS = 30_000L
private const val MAX_RESTARTS = 2
private const val SWITCH_DELAY_MS = 600L

sealed interface LiveStatus {
    data object Starting : LiveStatus
    data object Playing : LiveStatus
    data object Reconnecting : LiveStatus
    data class Failed(val message: String) : LiveStatus
}

data class LivePlayerUiState(
    val channel: LiveChannel? = null,
    val status: LiveStatus = LiveStatus.Starting,
    val streamUrl: String? = null,
    val mimeType: String? = null,
    // Null: the server's own auth headers. A link to another site never
    // gets the Jellyfin token.
    val requestHeaders: Map<String, String>? = null,
    val now: LiveProgramme? = null,
    val next: LiveProgramme? = null,
    val infoVersion: Int = 0,
)

@HiltViewModel
class LivePlayerViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LivePlayerUiState())
    val uiState: StateFlow<LivePlayerUiState> = _uiState.asStateFlow()
    val deviceId: String get() = repository.deviceId

    // Closing must still happen when this screen's scope is already gone.
    private val closer = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = LiveLifecycle<OpenedLiveStream>(
        open = { channelId ->
            val session = this.session ?: throw IllegalStateException("Not signed in")
            val link = parseLinkChannelId(channelId)
            withTimeout(OPEN_TIMEOUT_MS) {
                if (link != null) {
                    repository.playStreamLink(channelId, link.linkId, link.entry)
                } else {
                    repository.openLiveChannel(session.userId, channelId)
                }
            }
        },
        close = { stream -> stream.liveStreamId?.let { repository.closeLiveStream(it) } },
    )

    private var session: Session? = null
    private var channels: List<LiveChannel> = emptyList()
    private var openJob: Job? = null
    private var switchJob: Job? = null
    private var watchdog: Job? = null
    private var restarts = 0
    private var openStartedAt = 0L
    private var openedAt = 0L
    private var bufferingSince = 0L
    private var stopped = false
    private var onFallback = false

    fun start(session: Session, channelId: String) {
        this.session = session
        if (_uiState.value.channel?.id == channelId && !stopped) return
        stopped = false
        viewModelScope.launch {
            val fromLink = parseLinkChannelId(channelId) != null
            channels = runCatching {
                if (fromLink) {
                    // My streams keep their own order.
                    filterChannels(flattenStreamLinks(repository.getStreamLinks()), LiveTvMemory.query, LiveTvMemory.filters)
                } else {
                    val marked = applyHealth(mapChannels(repository.getLiveChannels(session.userId), System.currentTimeMillis()), repository.getBrokenLiveChannels())
                    arrangeChannels(filterChannels(marked, LiveTvMemory.query, LiveTvMemory.filters)).let { it.all + it.broken }
                }
            }.getOrDefault(emptyList())
            val channel = channels.firstOrNull { it.id == channelId } ?: LiveChannel(channelId, "", "", null, false, null, userStream = fromLink)
            play(channel)
        }
    }

    private fun play(channel: LiveChannel) {
        LiveTvMemory.lastChannelId = channel.id
        bufferingSince = 0
        onFallback = false
        _uiState.value = _uiState.value.copy(channel = channel, status = LiveStatus.Starting, streamUrl = null, now = channel.now, next = null, infoVersion = _uiState.value.infoVersion + 1)
        loadNowNext(channel)
        // Not cancelled midway: a request cut off after the server opened
        // the stream would leave a tuner held with no id to close it. The
        // lifecycle closes an open that finishes after it was superseded.
        openJob = closer.launch {
            openStartedAt = System.currentTimeMillis()
            val stream = try {
                lifecycle.start(channel.id)
            } catch (err: kotlinx.coroutines.CancellationException) {
                throw err
            } catch (err: com.jellio.tv.data.StreamLinkException) {
                Log.w(TAG, "link ${channel.id} failed: ${err.code}")
                report(channel, ok = false, reason = err.code)
                _uiState.value = _uiState.value.copy(status = LiveStatus.Failed(com.jellio.tv.ui.i18n.Strings.get(streamLinkErrorKey(err.code))))
                return@launch
            } catch (err: Exception) {
                Log.w(TAG, "open ${channel.id} failed: ${err.javaClass.simpleName}")
                if (countsAsChannelFailure(err.message)) report(channel, ok = false, reason = err.message ?: err.javaClass.simpleName)
                _uiState.value = _uiState.value.copy(status = LiveStatus.Failed(describeOpenFailure(err.message)))
                return@launch
            } ?: return@launch
            openedAt = System.currentTimeMillis()
            Log.i(TAG, "opened ${channel.id} in ${openedAt - openStartedAt} ms")
            if (stopped || _uiState.value.channel?.id != channel.id) return@launch
            _uiState.value = _uiState.value.copy(streamUrl = stream.streamUrl, mimeType = stream.mimeType, requestHeaders = stream.requestHeaders)
        }
    }

    private fun loadNowNext(channel: LiveChannel) {
        if (channel.userStream) return
        val session = session ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val programs = runCatching { repository.getLivePrograms(session.userId, channel.id, now, now + 6 * 3_600_000L) }.getOrNull() ?: return@launch
            if (_uiState.value.channel?.id != channel.id) return@launch
            val (current, next) = nowAndNext(programs, System.currentTimeMillis())
            _uiState.value = _uiState.value.copy(now = current ?: _uiState.value.now, next = next)
        }
    }

    fun zap(delta: Int) {
        val current = _uiState.value.channel ?: return
        val next = playableNeighbour(channels, current.id, delta) ?: return
        if (next.id == current.id) return
        restarts = 0
        _uiState.value = _uiState.value.copy(channel = next, status = LiveStatus.Starting, streamUrl = null, now = next.now, next = null, infoVersion = _uiState.value.infoVersion + 1)
        switchJob?.cancel()
        // Rapid presses settle on the last channel before anything opens.
        switchJob = viewModelScope.launch {
            delay(SWITCH_DELAY_MS)
            play(next)
        }
    }

    suspend fun loadGuide(channelId: String, nowMs: Long): List<LiveProgramme> {
        val session = session ?: return emptyList()
        val programs = repository.getLivePrograms(session.userId, channelId, nowMs, nowMs + GUIDE_HOURS * 3_600_000L)
        return guideWindow(programs, nowMs)
    }

    // Tells the server how the channel did, so it can be marked for everyone.
    private fun report(channel: LiveChannel, ok: Boolean, reason: String?) {
        closer.launch {
            val link = parseLinkChannelId(channel.id)
            if (link != null) {
                repository.reportStreamLinkPlay(link.linkId, ok)
            } else {
                repository.reportLiveChannel(channel.id, ok, reason)
            }
        }
    }

    fun showInfo() {
        _uiState.value = _uiState.value.copy(infoVersion = _uiState.value.infoVersion + 1)
    }

    fun retry() {
        restarts = 0
        _uiState.value.channel?.let { play(it) }
    }

    fun onFirstFrame() {
        val channel = _uiState.value.channel ?: return
        val firstFrame = System.currentTimeMillis()
        Log.i(TAG, "first frame ${channel.id}: open ${openedAt - openStartedAt} ms, first frame ${firstFrame - openStartedAt} ms, restarts $restarts")
        report(channel, ok = true, reason = null)
        _uiState.value = _uiState.value.copy(status = LiveStatus.Playing)
    }

    // A few seconds of missing video is normal for this source: only a
    // long stall is shown, and only a very long one reopens the channel.
    fun onBuffering(buffering: Boolean) {
        if (!buffering) {
            bufferingSince = 0
            watchdog?.cancel()
            if (_uiState.value.status == LiveStatus.Reconnecting) _uiState.value = _uiState.value.copy(status = LiveStatus.Playing)
            return
        }
        if (_uiState.value.status != LiveStatus.Playing && _uiState.value.status != LiveStatus.Reconnecting) return
        if (bufferingSince != 0L) return
        bufferingSince = System.currentTimeMillis()
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            delay(STALL_NOTICE_MS)
            _uiState.value = _uiState.value.copy(status = LiveStatus.Reconnecting)
            delay(STALL_RESTART_MS - STALL_NOTICE_MS)
            restart("stalled ${(System.currentTimeMillis() - bufferingSince) / 1000} s")
        }
    }

    fun onPlayerError(message: String?) {
        // A link the TV can't reach itself goes through the server instead.
        val fallback = lifecycle.currentStream?.fallbackUrl
        if (fallback != null && !onFallback && !stopped) {
            onFallback = true
            Log.w(TAG, "direct link failed (${message.orEmpty()}), using the server")
            _uiState.value = _uiState.value.copy(streamUrl = fallback, requestHeaders = emptyMap())
            return
        }
        restart("player error ${message.orEmpty()}")
    }

    private fun restart(reason: String) {
        val channel = _uiState.value.channel ?: return
        if (restarts >= MAX_RESTARTS) {
            report(channel, ok = false, reason = "stalled")
            _uiState.value = _uiState.value.copy(status = LiveStatus.Failed(describeOpenFailure(reason)))
            return
        }
        restarts++
        Log.w(TAG, "restarting ${channel.id} ($reason), attempt $restarts")
        play(channel)
    }

    // Back, leaving the screen and the app going to the background all
    // release the tuner slot.
    fun stop() {
        stopped = true
        switchJob?.cancel()
        watchdog?.cancel()
        _uiState.value = _uiState.value.copy(streamUrl = null)
        closer.launch { lifecycle.stop() }
    }

    fun resume() {
        if (!stopped) return
        stopped = false
        restarts = 0
        _uiState.value.channel?.let { play(it) }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
