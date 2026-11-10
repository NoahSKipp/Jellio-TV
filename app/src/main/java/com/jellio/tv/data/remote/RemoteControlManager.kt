package com.jellio.tv.data.remote

import android.util.Log
import com.jellio.tv.data.catching
import com.jellio.tv.data.model.ClientCapabilitiesDto
import com.jellio.tv.data.network.JellyfinApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class RemotePlayCommand(
    val itemId: String,
    val playCommand: String = "PlayNow",
    val startPositionTicks: Long = 0L,
    val mediaSourceId: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
)

data class RemotePlaystateCommand(
    val command: String,
    val seekPositionTicks: Long? = null,
)

data class RemoteGeneralCommand(
    val name: String,
    val arguments: Map<String, String> = emptyMap(),
)

private const val TAG = "RemoteControlManager"

@Singleton
class RemoteControlManager @Inject constructor(
    private val api: JellyfinApi,
    private val moshi: Moshi,
) {
    private val _playCommands = MutableSharedFlow<RemotePlayCommand>(replay = 1, extraBufferCapacity = 16)
    val playCommands: SharedFlow<RemotePlayCommand> = _playCommands.asSharedFlow()

    private val _playstateCommands = MutableSharedFlow<RemotePlaystateCommand>(extraBufferCapacity = 16)
    val playstateCommands: SharedFlow<RemotePlaystateCommand> = _playstateCommands.asSharedFlow()

    private val _generalCommands = MutableSharedFlow<RemoteGeneralCommand>(extraBufferCapacity = 16)
    val generalCommands: SharedFlow<RemoteGeneralCommand> = _generalCommands.asSharedFlow()

    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
    )

    suspend fun reportCapabilities(): Result<Unit> = catching {
        api.postCapabilities(ClientCapabilitiesDto())
    }

    fun handleWebSocketMessage(message: JSONObject): Boolean {
        return handleWebSocketMessage(message.toString())
    }

    fun handleWebSocketMessage(text: String): Boolean {
        val root = runCatching { mapAdapter.fromJson(text) }.getOrNull() ?: return false
        val type = root["MessageType"] as? String ?: return false
        val data = root["Data"] as? Map<*, *> ?: return false

        when (type) {
            "Play" -> {
                val itemIds = data["ItemIds"] as? List<*>
                val rawId = itemIds?.firstOrNull()?.toString()
                    ?: data["ItemId"]?.toString()
                    ?: return false

                val cleanId = rawId.replace("-", "").trim()
                if (cleanId.isEmpty()) return false

                val playCommand = (data["PlayCommand"] as? String)?.ifEmpty { "PlayNow" } ?: "PlayNow"
                val startPositionTicks = (data["StartPositionTicks"] as? Number)?.toLong() ?: 0L
                val mediaSourceId = (data["MediaSourceId"] as? String)?.ifEmpty { null }
                val audioStreamIndex = (data["AudioStreamIndex"] as? Number)?.toInt()
                val subtitleStreamIndex = (data["SubtitleStreamIndex"] as? Number)?.toInt()

                val cmd = RemotePlayCommand(
                    itemId = cleanId,
                    playCommand = playCommand,
                    startPositionTicks = startPositionTicks,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                )
                runCatching { Log.d(TAG, "Received Play command: $cmd") }
                _playCommands.tryEmit(cmd)
                return true
            }
            "Playstate" -> {
                val command = (data["Command"] as? String)?.ifEmpty { null } ?: return false
                val seekPositionTicks = (data["SeekPositionTicks"] as? Number)?.toLong()

                val cmd = RemotePlaystateCommand(
                    command = command,
                    seekPositionTicks = seekPositionTicks,
                )
                runCatching { Log.d(TAG, "Received Playstate command: $cmd") }
                _playstateCommands.tryEmit(cmd)
                return true
            }
            "GeneralCommand" -> {
                val name = (data["Name"] as? String)?.ifEmpty { null } ?: return false
                val argsMap = mutableMapOf<String, String>()
                val rawArgs = data["Arguments"] as? Map<*, *>
                rawArgs?.forEach { (k, v) ->
                    if (k != null && v != null) {
                        argsMap[k.toString()] = v.toString()
                    }
                }
                val cmd = RemoteGeneralCommand(
                    name = name,
                    arguments = argsMap,
                )
                runCatching { Log.d(TAG, "Received GeneralCommand: $cmd") }
                _generalCommands.tryEmit(cmd)
                return true
            }
        }
        return false
    }
}
