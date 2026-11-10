package com.jellio.tv.data.syncplay

import android.util.Log
import com.jellio.tv.data.catching
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
import javax.inject.Inject
import javax.inject.Singleton

data class SyncGroup(val groupId: String, val groupName: String, val participants: List<String>)

// What the group is playing right now (the play queue's current entry).
data class SyncTarget(val itemId: String, val playlistItemId: String, val startPositionTicks: Long, val isPlaying: Boolean)

// A SyncPlayCommand: Unpause, Pause, Seek or Stop, to apply at whenRemoteMs (server clock).
data class SyncCommand(
    val command: String,
    val whenRemoteMs: Long,
    val positionTicks: Long?,
    val playlistItemId: String?,
    val receivedAtMs: Long = System.currentTimeMillis(),
)

sealed interface GroupEvent {
    data class Presence(val name: String, val joined: Boolean) : GroupEvent
    data class Started(val target: SyncTarget) : GroupEvent

    // The server refused: JoinGroupDenied, CreateGroupDenied,
    // LibraryAccessDenied or GroupDoesNotExist. inGroup tells a refused
    // queue from a refused join.
    data class Denied(val reason: String, val inGroup: Boolean) : GroupEvent
}

const val SYNC_TICKS_PER_MS = 10_000L

private const val TAG = "JellioSyncPlay"
private const val PING_INTERVAL_MS = 30_000L
private const val RECONCILE_INTERVAL_MS = 15_000L
private const val RECONCILE_IDLE_INTERVAL_MS = 60_000L
private const val RECONNECT_DELAY_MS = 5_000L
private const val MAX_MEASUREMENTS = 6
private const val EXPLICIT_LEAVE_GRACE_MS = 20_000L

@Singleton
class SyncPlayManager @Inject constructor(
    private val client: OkHttpClient,
    private val api: JellyfinApi,
    private val remoteControlManager: com.jellio.tv.data.remote.RemoteControlManager,
) {
    private val _group = MutableStateFlow<SyncGroup?>(null)
    val group: StateFlow<SyncGroup?> = _group.asStateFlow()

    private val _target = MutableStateFlow<SyncTarget?>(null)
    val target: StateFlow<SyncTarget?> = _target.asStateFlow()

    // The latest command is replayed to a player that starts listening late
    // (its queue item only became known after the command arrived).
    private val _commands = MutableSharedFlow<SyncCommand>(replay = 1, extraBufferCapacity = 16)
    val commands: SharedFlow<SyncCommand> = _commands.asSharedFlow()

    private val _events = MutableSharedFlow<GroupEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GroupEvent> = _events.asSharedFlow()

    @Volatile var myUserName: String = ""
        private set

    private val measurements = ArrayDeque<Pair<Double, Long>>() // offset ms, delay ms
    private var lastNotifiedPlaylistItemId: String? = null
    private var explicitLeaveUntil = 0L
    @Volatile private var lastDeniedAtMs = 0L
    @Volatile private var activeUserId: String? = null

    @Volatile private var myGroupId: String? = null
    @Volatile private var pendingCreateName: String? = null
    @Volatile private var pendingCreateUntil = 0L

    // Runs while the app is in front: keeps the socket open (reopening it
    // when it drops), the clock in sync and group membership honest.
    suspend fun runWhileStarted(session: Session) = coroutineScope {
        val previousUserId = activeUserId
        activeUserId = session.userId
        if (previousUserId != null && !sameId(previousUserId, session.userId)) {
            // Another account on this TV. The server keeps one session per
            // device, so the group the last account was in is left now.
            if (_group.value != null || myGroupId != null) catching { api.syncPlayLeave() }
            resetGroup()
            pendingCreateName = null
            explicitLeaveUntil = 0L
            synchronized(measurements) { measurements.clear() }
        }
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
                val interval = if (_group.value != null) RECONCILE_INTERVAL_MS else RECONCILE_IDLE_INTERVAL_MS
                delay(interval)
            }
        }
        while (isActive) {
            val closed = CompletableDeferred<Unit>()
            var keepAlive: Job? = null
            val socket = catching {
                client.newWebSocket(socketRequest(session), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        launch {
                            reconcile()
                            catching { remoteControlManager.reportCapabilities() }
                        }
                    }
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

                    // A close from the server is only finished once this side answers;
                    // without it onClosed never comes and the socket is never reopened.
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                        closed.complete(Unit)
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
            "Play", "Playstate", "GeneralCommand" -> {
                remoteControlManager.handleWebSocketMessage(message)
            }
        }
    }

    private fun handleGroupUpdate(update: JSONObject) {
        when (update.optString("Type")) {
            "GroupJoined" -> update.optJSONObject("Data")?.let { data ->
                _group.value = parseGroup(data)
                myGroupId = _group.value?.groupId
                pendingCreateName = null
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
            "NotInGroup", "GroupLeft" -> resetGroup()
            // Only ever the answer to a join: the group (an old invite's) has ended.
            "GroupDoesNotExist" -> {
                resetGroup()
                _events.tryEmit(GroupEvent.Denied("GroupDoesNotExist", inGroup = false))
            }
            "JoinGroupDenied", "CreateGroupDenied", "LibraryAccessDenied" -> {
                val inGroup = _group.value != null
                if (!inGroup) {
                    // A refused join or create: stop reconcile retrying it.
                    lastDeniedAtMs = System.currentTimeMillis()
                    myGroupId = null
                    pendingCreateName = null
                }
                _events.tryEmit(GroupEvent.Denied(update.optString("Type"), inGroup))
            }
        }
    }

    private fun resetGroup() {
        myGroupId = null
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

    private suspend fun reconcile() {
        val before = _group.value?.groupId
        val groups = catching { api.getSyncPlayGroups() }.getOrNull() ?: return
        // A join or leave landed while the list was on its way: it is stale.
        if (_group.value?.groupId != before) return
        val stored = myGroupId
        if (stored != null && groups.none { sameId(it.GroupId, stored) }) myGroupId = null
        val now = System.currentTimeMillis()
        val action = decideReconcile(
            groups = groups,
            storedGroupId = stored,
            current = _group.value,
            myUserName = myUserName,
            pendingCreateName = pendingCreateName?.takeIf { now < pendingCreateUntil },
            leaveGraceActive = now < explicitLeaveUntil,
        )
        when (action) {
            ReconcileAction.None -> Unit
            ReconcileAction.Reset -> resetGroup()
            is ReconcileAction.Join -> {
                val mine = action.group
                val startedAt = System.currentTimeMillis()
                val failure = catching { api.syncPlayJoin(SyncPlayJoinBody(mine.GroupId)) }.exceptionOrNull()
                if (failure != null) {
                    // Refused (a private party): stop believing this TV is in it.
                    if (failure is retrofit2.HttpException && sameId(_group.value?.groupId, mine.GroupId)) resetGroup()
                    return
                }
                if (lastDeniedAtMs >= startedAt) return
                myGroupId = mine.GroupId
                delay(3_000)
                if (_group.value == null && sameId(myGroupId, mine.GroupId)) {
                    _group.value = SyncGroup(mine.GroupId, mine.GroupName ?: "Group Watch", mine.Participants)
                }
            }
        }
    }

    suspend fun listGroups() = catching { api.getSyncPlayGroups() }.getOrDefault(emptyList())

    suspend fun join(groupId: String) {
        explicitLeaveUntil = 0L
        val startedAt = System.currentTimeMillis()
        api.syncPlayJoin(SyncPlayJoinBody(groupId))
        // The refusal comes over the socket while the request still runs.
        if (lastDeniedAtMs < startedAt) myGroupId = groupId
    }

    // Starts a group and waits until this TV is in it: the socket's
    // GroupJoined usually says so, otherwise the group list does.
    suspend fun create(name: String): SyncGroup? {
        explicitLeaveUntil = 0L
        noteCreating(name)
        if (catching { api.syncPlayNew(com.jellio.tv.data.model.SyncPlayNewBody(name)) }.isFailure) {
            pendingCreateName = null
            return null
        }
        repeat(32) { attempt ->
            _group.value?.let { return it }
            // Cleared by GroupJoined (handled above) or by CreateGroupDenied.
            if (pendingCreateName == null) return _group.value
            if (attempt % 4 == 3) {
                val found = catching { api.getSyncPlayGroups() }.getOrNull()
                    ?.firstOrNull { it.GroupName == name && (myUserName.isEmpty() || myUserName in it.Participants) }
                if (found != null) {
                    myGroupId = found.GroupId
                    pendingCreateName = null
                    val joined = SyncGroup(found.GroupId, found.GroupName ?: name, found.Participants)
                    if (_group.value == null) _group.value = joined
                    return _group.value
                }
            }
            delay(250)
        }
        return _group.value
    }

    // Lets reconcile adopt a group this TV creates if its push is lost.
    fun noteCreating(name: String) {
        pendingCreateName = name
        pendingCreateUntil = System.currentTimeMillis() + 15_000L
    }

    suspend fun leave() {
        explicitLeaveUntil = System.currentTimeMillis() + EXPLICIT_LEAVE_GRACE_MS
        myGroupId = null
        catching { api.syncPlayLeave() }
        resetGroup()
    }

    // The token is gone, so the server can't be told; only this TV forgets the group.
    fun leaveLocally() {
        pendingCreateName = null
        resetGroup()
    }

    suspend fun requestPause() = catching { api.syncPlayPause() }
    suspend fun requestUnpause() = catching { api.syncPlayUnpause() }
    suspend fun requestSeek(positionTicks: Long) = catching { api.syncPlaySeek(SyncPlaySeekBody(positionTicks)) }

    suspend fun notifyBuffering(positionTicks: Long, isPlaying: Boolean, playlistItemId: String) = catching {
        api.syncPlayBuffering(SyncPlayStateBody(remoteNowIso(), positionTicks, isPlaying, playlistItemId))
    }

    suspend fun notifyReady(positionTicks: Long, isPlaying: Boolean, playlistItemId: String) = catching {
        api.syncPlayReady(SyncPlayStateBody(remoteNowIso(), positionTicks, isPlaying, playlistItemId))
    }

    suspend fun publishQueue(itemId: String, startPositionTicks: Long) = catching {
        api.syncPlaySetNewQueue(SyncPlayQueueBody(listOf(itemId), 0, startPositionTicks))
    }

    private suspend fun pingServerTime() {
        val sent = System.currentTimeMillis()
        val time = catching { api.getUtcTime() }.getOrNull() ?: return
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

    private fun parseTime(value: String?): Long? = PartyManager.parseTime(value)

    companion object {
        fun sameId(a: String?, b: String?): Boolean =
            a != null && b != null && a.replace("-", "").equals(b.replace("-", ""), ignoreCase = true)
    }
}
