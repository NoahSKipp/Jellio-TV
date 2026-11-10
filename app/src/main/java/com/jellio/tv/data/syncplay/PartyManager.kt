package com.jellio.tv.data.syncplay

import com.jellio.tv.data.catching
import com.jellio.tv.data.model.GroupWatchMessageDto
import com.jellio.tv.data.model.PartyControlBody
import com.jellio.tv.data.model.PartyCountdownBody
import com.jellio.tv.data.model.PartyItemBody
import com.jellio.tv.data.model.PartyReadyBody
import com.jellio.tv.data.model.PartyRequestDto
import com.jellio.tv.data.model.PartyStateDto
import com.jellio.tv.data.network.JellyfinApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val STATE_POLL_MS = 2_000L
private const val TICK_MS = 250L
const val PARTY_COUNTDOWN_SECONDS = 5
const val PARTY_READY_WAIT_MS = 20_000L

// The emoji roster the TV answers chat with: no keyboard on a remote.
val PARTY_REACTIONS = listOf("❤️", "👍", "👎", "😂", "😮", "😢", "🔥", "🎉")

sealed interface PartyEvent {
    // A host-only party: someone asks the host to pause, play or jump.
    data class HostRequest(val request: PartyRequestDto) : PartyEvent
    data class AskedHost(val hostName: String?) : PartyEvent
}

// What the middle of the screen shows: the ready check, then 3-2-1.
sealed interface PartyCenter {
    data class ReadyCheck(val ready: Int, val total: Int) : PartyCenter
    data class Countdown(val secondsLeft: Int) : PartyCenter
}

@Singleton
class PartyManager @Inject constructor(
    private val api: JellyfinApi,
    private val syncPlay: SyncPlayManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<PartyStateDto?>(null)
    val state: StateFlow<PartyStateDto?> = _state.asStateFlow()

    private val _center = MutableStateFlow<PartyCenter?>(null)
    val center: StateFlow<PartyCenter?> = _center.asStateFlow()

    private val _events = MutableSharedFlow<PartyEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PartyEvent> = _events.asSharedFlow()

    // The newest chat line from someone else: what a reaction answers.
    private val _lastIncoming = MutableStateFlow<GroupWatchMessageDto?>(null)
    @Volatile private var lastIncomingAtMs = 0L

    // Set by the app root so a party card can open its title.
    @Volatile var openItem: ((String) -> Unit)? = null

    @Volatile private var skewMs = 0L
    private var lastRequestId = 0L
    private var stateGroupId: String? = null
    private var startPending = false
    private var startPendingSince = 0L
    private var countdownFiredFor: String? = null
    private val readyReported = HashSet<String>()
    private var playlistItemId: String? = null

    suspend fun runWhileStarted(userId: String) {
        kotlinx.coroutines.coroutineScope {
            launch {
                while (isActive) {
                    poll(userId)
                    delay(STATE_POLL_MS)
                }
            }
            launch {
                while (isActive) {
                    tick()
                    delay(TICK_MS)
                }
            }
        }
    }

    private suspend fun poll(userId: String) {
        val group = syncPlay.group.value
        if (group == null) {
            if (stateGroupId != null) reset()
            return
        }
        if (!SyncPlayManager.sameId(stateGroupId, group.groupId)) {
            reset()
            stateGroupId = group.groupId
        }
        val sentAt = System.currentTimeMillis()
        val fresh = catching { api.getPartyState(group.groupId, lastRequestId) }.getOrNull() ?: return
        val receivedAt = System.currentTimeMillis()
        parseTime(fresh.ServerNow)?.let { skewMs = it - (sentAt + receivedAt) / 2 }
        _state.value = fresh
        fresh.Requests.forEach { request ->
            if (request.Id > lastRequestId) {
                lastRequestId = request.Id
                if (fresh.IsHost && !SyncPlayManager.sameId(request.UserId, userId) && isFreshPartyRequest(request.At, fresh.ServerNow)) {
                    _events.tryEmit(PartyEvent.HostRequest(request))
                }
            }
        }
        maybeStartGroup()
    }

    private fun reset() {
        stateGroupId = null
        _state.value = null
        _center.value = null
        lastRequestId = 0L
        startPending = false
        countdownFiredFor = null
        readyReported.clear()
        _lastIncoming.value = null
    }

    private fun tick() {
        val countdown = countdownSecondsLeft()
        _center.value = when {
            countdown != null -> PartyCenter.Countdown(countdown)
            startPending -> readyCount()?.let { PartyCenter.ReadyCheck(it.first, it.second) }
            else -> null
        }
    }

    // Seconds until the group starts, 0 for the "Go!" moment, null when
    // no countdown runs for the title on screen.
    fun countdownSecondsLeft(): Int? {
        val current = _state.value ?: return null
        val endsAt = parseTime(current.CountdownEndsAt) ?: return null
        if (current.CountdownPlaylistItemId == null || current.CountdownPlaylistItemId != playlistItemId) return null
        val leftMs = endsAt - (System.currentTimeMillis() + skewMs)
        if (leftMs < -1_000L) return null
        return if (leftMs <= 0) 0 else ((leftMs + 999) / 1000).toInt()
    }

    private fun readyCount(): Pair<Int, Int>? {
        val current = _state.value ?: return null
        val id = playlistItemId ?: return null
        val total = (syncPlay.group.value?.participants?.size ?: 1).coerceAtLeast(1)
        val ready = current.Ready.count { it.PlaylistItemId == id }.coerceAtMost(total)
        return ready to total
    }

    private fun maybeStartGroup() {
        val id = playlistItemId ?: return
        val groupId = syncPlay.group.value?.groupId ?: return
        if (!startPending || id !in readyReported) return
        val count = readyCount() ?: return
        if (count.first < count.second && System.currentTimeMillis() - startPendingSince < PARTY_READY_WAIT_MS) return
        startPending = false
        scope.launch {
            val result = catching { api.startPartyCountdown(groupId, PartyCountdownBody(id, PARTY_COUNTDOWN_SECONDS)) }.getOrNull()
            val endsAt = parseTime(result?.EndsAt)
            parseTime(result?.ServerNow)?.let { skewMs = it - System.currentTimeMillis() }
            countdownFiredFor = id
            if (endsAt != null) delay((endsAt - skewMs - System.currentTimeMillis()).coerceAtLeast(0L))
            if (playlistItemId == id) syncPlay.requestUnpause()
        }
    }

    // The player shows the group's title under this queue item.
    fun setPlaylistItem(id: String?) {
        playlistItemId = id
    }

    // This TV just put a title on the group's queue: it runs the ready
    // check and unpauses everyone when the countdown ends.
    fun published(itemId: String): Boolean {
        val groupId = syncPlay.group.value?.groupId ?: return false
        scope.launch { catching { api.setPartyItem(groupId, PartyItemBody(itemId)) } }
        startPending = true
        startPendingSince = System.currentTimeMillis()
        return true
    }

    fun markReady(playlistItemId: String) {
        val groupId = syncPlay.group.value?.groupId ?: return
        this.playlistItemId = playlistItemId
        if (!readyReported.add(playlistItemId)) return
        scope.launch {
            catching { api.setPartyReady(groupId, PartyReadyBody(playlistItemId, true)) }
            maybeStartGroup()
        }
    }

    fun shouldAsk(): Boolean {
        val current = _state.value ?: return false
        return current.HostOnly && !current.IsHost && syncPlay.group.value != null
    }

    fun ask(kind: String, positionTicks: Long? = null) {
        val groupId = syncPlay.group.value?.groupId ?: return
        scope.launch {
            if (catching { api.requestPartyControl(groupId, PartyControlBody(kind, positionTicks)) }.isSuccess) {
                _events.tryEmit(PartyEvent.AskedHost(_state.value?.HostName))
            }
        }
    }

    fun noteIncoming(message: GroupWatchMessageDto) {
        _lastIncoming.value = message
        lastIncomingAtMs = System.currentTimeMillis()
    }

    // A reaction answers the latest message if it is recent enough to
    // still be on everyone's mind.
    fun replyTarget(): GroupWatchMessageDto? =
        _lastIncoming.value?.takeIf { System.currentTimeMillis() - lastIncomingAtMs < 120_000L }

    companion object {
        fun parseTime(value: String?): Long? = com.jellio.tv.data.JellyfinDates.parseEpochMs(value)
    }
}
