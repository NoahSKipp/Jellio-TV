package com.jellio.tv.data.syncplay

import android.util.Log
import com.jellio.tv.data.model.SyncPlayJoinBody
import com.jellio.tv.data.model.SyncPlayQueueBody
import com.jellio.tv.data.model.SyncPlaySeekBody
import com.jellio.tv.data.model.SyncPlayStateBody
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.session.Session
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class SyncGroup(val groupId: String, val groupName: String, val participants: List<String>)

// What the group is playing right now (the play queue's current entry).
data class SyncTarget(val itemId: String, val playlistItemId: String, val startPositionTicks: Long, val isPlaying: Boolean)

// A SyncPlayCommand: Unpause, Pause, Seek or Stop, to apply at whenRemoteMs (server clock).
data class SyncCommand(val command: String, val whenRemoteMs: Long, val positionTicks: Long?, val playlistItemId: String?)

sealed interface GroupEvent {
    data class Presence(val name: String, val joined: Boolean) : GroupEvent
    data class Started(val target: SyncTarget) : GroupEvent
}

const val SYNC_TICKS_PER_MS = 10_000L

private const val TAG = "JellioSyncPlay"
private const val PING_INTERVAL_MS = 30_000L
private const val RECONCILE_INTERVAL_MS = 15_000L
private const val RECONNECT_DELAY_MS = 5_000L
private const val MAX_MEASUREMENTS = 6
private const val EXPLICIT_LEAVE_GRACE_MS = 20_000L

// Jellyfin SyncPlay for the TV, the same protocol runtime/syncPlay.js
// drives on the web: the server's WebSocket pushes group updates and
// timed playback commands, REST calls ask for pause/seek/unpause, and a
// small NTP-style exchange against /GetUtcTime lines this clock up with
// the server's so a command lands at the same moment everywhere.
@Singleton
class SyncPlayManager @Inject constructor(
    private val client: OkHttpClient,
    private val api: JellyfinApi,
) {
    private val _group = MutableStateFlow<SyncGroup?>(null)
    val group: StateFlow<SyncGroup?> = _group.asStateFlow()

    private val _target = MutableStateFlow<SyncTarget?>(null)
    val target: StateFlow<SyncTarget?> = _target.asStateFlow()

    private val _commands = MutableSharedFlow<SyncCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<SyncCommand> = _commands.asSharedFlow()

    private val _events = MutableSharedFlow<GroupEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GroupEvent> = _events.asSharedFlow()

    @Volatile var myUserName: String = ""
        private set

    private val measurements = ArrayDeque<Pair<Double, Long>>() // offset ms, delay ms
    private var lastNotifiedPlaylistItemId: String? = null
    private var explicitLeaveUntil = 0L

    // Runs while the app is in front: keeps the socket open (reopening it
    // when it drops), the clock in sync and group membership honest.
    suspend fun runWhileStarted(session: Session) = coroutineScope {
        myUserName = session.userName
        launch {
            while (isActive) {
                pingServerTime()
                delay(PING_INTERVAL_MS)
            }
        }
        launch {
            while (isActive) {
                reconcile()
                delay(RECONCILE_INTERVAL_MS)
            }
        }
        while (isActive) {
            val closed = CompletableDeferred<Unit>()
            var keepAlive: Job? = null
            val socket = runCatching {
                client.newWebSocket(socketRequest(session), object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                        if (message.optString("MessageType") == "ForceKeepAlive") {
                            val seconds = message.optLong("Data", 60L).coerceAtLeast(10L)
                            keepAlive?.cancel()
                            keepAlive = launch {
                                while (isActive) {
                                    webSocket.send("{\"MessageType\":\"KeepAlive\"}")
                                    delay(seconds * 1000 / 2)
                                }
                            }
                        } else {
                            handleMessage(message)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "socket failed", t)
                        closed.complete(Unit)
                    }
                })
            }.getOrNull()
            try {
                if (socket != null) closed.await()
            } finally {
                keepAlive?.cancel()
                socket?.close(1000, null)
            }
            delay(RECONNECT_DELAY_MS)
        }
    }

    // Host and any base path are filled in by the client's own base URL
    // interceptor, the same as every REST call, along with the auth header.
    private fun socketRequest(session: Session): Request =
        Request.Builder().url("http://localhost/socket?ApiKey=" + URLEncoder.encode(session.accessToken, "UTF-8")).build()

    private fun handleMessage(message: JSONObject) {
        when (message.optString("MessageType")) {
            "SyncPlayGroupUpdate" -> message.optJSONObject("Data")?.let { handleGroupUpdate(it) }
            "SyncPlayCommand" -> message.optJSONObject("Data")?.let { data ->
                val command = data.optString("Command")
                if (command.isEmpty()) return
                _commands.tryEmit(
                    SyncCommand(
                        command = command,
                        whenRemoteMs = parseTime(data.optString("When")) ?: System.currentTimeMillis(),
                        positionTicks = if (data.isNull("PositionTicks")) null else data.optLong("PositionTicks"),
                        playlistItemId = data.optString("PlaylistItemId").ifEmpty { null },
                    ),
                )
            }
        }
    }

    private fun handleGroupUpdate(update: JSONObject) {
        when (update.optString("Type")) {
            "GroupJoined" -> update.optJSONObject("Data")?.let { data ->
                _group.value = parseGroup(data)
                _target.value = null
                lastNotifiedPlaylistItemId = null
            }
            "GroupUpdate" -> update.optJSONObject("Data")?.let { _group.value = parseGroup(it) }
            "UserJoined" -> {
                val name = update.optString("Data")
                _group.value?.let { _group.value = it.copy(participants = it.participants + name) }
                if (name.isNotEmpty() && name != myUserName) _events.tryEmit(GroupEvent.Presence(name, joined = true))
            }
            "UserLeft" -> {
                val name = update.optString("Data")
                _group.value?.let { _group.value = it.copy(participants = it.participants - name) }
                if (name.isNotEmpty() && name != myUserName) _events.tryEmit(GroupEvent.Presence(name, joined = false))
            }
            "PlayQueue" -> update.optJSONObject("Data")?.let { queue ->
                val target = parseTarget(queue)
                _target.value = target
                if (target != null && target.playlistItemId != lastNotifiedPlaylistItemId) {
                    lastNotifiedPlaylistItemId = target.playlistItemId
                    _events.tryEmit(GroupEvent.Started(target))
                }
            }
            "NotInGroup", "GroupLeft", "GroupDoesNotExist" -> resetGroup()
        }
    }

    private fun resetGroup() {
        _group.value = null
        _target.value = null
        lastNotifiedPlaylistItemId = null
    }

    private fun parseGroup(data: JSONObject): SyncGroup {
        val names = data.optJSONArray("Participants") ?: JSONArray()
        return SyncGroup(
            groupId = data.optString("GroupId"),
            groupName = data.optString("GroupName").ifEmpty { "Group Watch" },
            participants = (0 until names.length()).map { names.optString(it) },
        )
    }

    private fun parseTarget(queue: JSONObject): SyncTarget? {
        val playlist = queue.optJSONArray("Playlist") ?: return null
        val index = queue.optInt("PlayingItemIndex", -1)
        val entry = playlist.optJSONObject(index) ?: return null
        val itemId = entry.optString("ItemId").replace("-", "")
        val playlistItemId = entry.optString("PlaylistItemId")
        if (itemId.isEmpty() || playlistItemId.isEmpty()) return null
        return SyncTarget(itemId, playlistItemId, queue.optLong("StartPositionTicks", 0L), queue.optBoolean("IsPlaying", false))
    }

    // The same rejoin/cleanup runtime/syncPlay.js's reconcileGroupMembership
    // does: a missed GroupJoined push, or a group that ended while the
    // socket was down, still settles within one interval.
    private suspend fun reconcile() {
        val groups = runCatching { api.getSyncPlayGroups() }.getOrNull() ?: return
        val mine = groups.firstOrNull { myUserName in it.Participants }
        val current = _group.value
        if (mine != null && System.currentTimeMillis() < explicitLeaveUntil) return
        if (mine != null && (current == null || !sameId(current.groupId, mine.GroupId))) {
            runCatching { api.syncPlayJoin(SyncPlayJoinBody(mine.GroupId)) }
            delay(3_000)
            if (_group.value == null) {
                _group.value = SyncGroup(mine.GroupId, mine.GroupName ?: "Group Watch", mine.Participants)
            }
        } else if (mine == null && current != null) {
            resetGroup()
        }
    }

    suspend fun listGroups() = runCatching { api.getSyncPlayGroups() }.getOrDefault(emptyList())

    suspend fun join(groupId: String) {
        explicitLeaveUntil = 0L
        api.syncPlayJoin(SyncPlayJoinBody(groupId))
    }

    suspend fun leave() {
        explicitLeaveUntil = System.currentTimeMillis() + EXPLICIT_LEAVE_GRACE_MS
        runCatching { api.syncPlayLeave() }
        resetGroup()
    }

    suspend fun requestPause() = runCatching { api.syncPlayPause() }
    suspend fun requestUnpause() = runCatching { api.syncPlayUnpause() }
    suspend fun requestSeek(positionTicks: Long) = runCatching { api.syncPlaySeek(SyncPlaySeekBody(positionTicks)) }

    suspend fun notifyBuffering(positionTicks: Long, isPlaying: Boolean, playlistItemId: String) = runCatching {
        api.syncPlayBuffering(SyncPlayStateBody(remoteNowIso(), positionTicks, isPlaying, playlistItemId))
    }

    suspend fun notifyReady(positionTicks: Long, isPlaying: Boolean, playlistItemId: String) = runCatching {
        api.syncPlayReady(SyncPlayStateBody(remoteNowIso(), positionTicks, isPlaying, playlistItemId))
    }

    suspend fun publishQueue(itemId: String, startPositionTicks: Long) = runCatching {
        api.syncPlaySetNewQueue(SyncPlayQueueBody(listOf(itemId), 0, startPositionTicks))
    }

    private suspend fun pingServerTime() {
        val sent = System.currentTimeMillis()
        val time = runCatching { api.getUtcTime() }.getOrNull() ?: return
        val received = System.currentTimeMillis()
        val serverIn = parseTime(time.RequestReceptionTime) ?: return
        val serverOut = parseTime(time.ResponseTransmissionTime) ?: return
        val offset = ((serverIn - sent) + (serverOut - received)) / 2.0
        val roundTrip = (received - sent) - (serverOut - serverIn)
        synchronized(measurements) {
            measurements.addLast(offset to roundTrip)
            while (measurements.size > MAX_MEASUREMENTS) measurements.removeFirst()
        }
    }

    // Server clock minus this one, from the measurement with the least delay.
    fun offsetMs(): Long = synchronized(measurements) {
        measurements.minByOrNull { it.second }?.first?.toLong() ?: 0L
    }

    fun remoteToLocalMs(remoteMs: Long): Long = remoteMs - offsetMs()

    // Where a position reported at whenRemoteMs has got to by now.
    fun estimateTicksNow(positionTicks: Long, whenRemoteMs: Long): Long =
        positionTicks + (System.currentTimeMillis() + offsetMs() - whenRemoteMs) * SYNC_TICKS_PER_MS

    private fun remoteNowIso(): String = Instant.ofEpochMilli(System.currentTimeMillis() + offsetMs()).toString()

    private fun parseTime(value: String?): Long? {
        if (value.isNullOrEmpty()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(value + "Z").toEpochMilli() }.getOrNull()
    }

    companion object {
        fun sameId(a: String?, b: String?): Boolean =
            a != null && b != null && a.replace("-", "").equals(b.replace("-", ""), ignoreCase = true)
    }
}
