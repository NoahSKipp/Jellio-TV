package com.jellio.tv.ui.groupwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.GroupWatchMessageDto
import com.jellio.tv.data.model.SyncPlayGroupDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CHAT_POLL_MS = 3000L

data class GroupWatchUiState(
    val groups: List<SyncPlayGroupDto> = emptyList(),
    val status: String? = null,
    val busyGroupId: String? = null,
    val activeChat: SyncPlayGroupDto? = null,
    val messages: List<GroupWatchMessageDto> = emptyList(),
    val chatStatus: String? = null,
    val isSendingMessage: Boolean = false,
)

// Real port of components/groupWatch.js's own openGroupWatch(): real
// SyncPlay group membership (create/join/leave) driven over the same
// real REST endpoints native jellyfin-web's own hidden SyncPlay menu
// already called, plus a polled chat per group. Deliberately scoped
// the same real way that file's own header states: this covers real
// membership and chat, not keeping this app's own player in lockstep
// with a group's own playback position, a separate, larger real
// feature real Jellyfin drives over a WebSocket connection
// PlayerScreen never opens (see JellyfinModels.kt's own
// SleepTimerStatusDto header for the same real constraint documented
// elsewhere in this codebase).
@HiltViewModel
class GroupWatchViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupWatchUiState())
    val uiState: StateFlow<GroupWatchUiState> = _uiState.asStateFlow()

    private var chatPollJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = "Loading groups…")
            val groups = runCatching { repository.getSyncPlayGroups() }.getOrNull()
            if (groups == null) {
                _uiState.value = _uiState.value.copy(status = "Could not load groups.")
                return@launch
            }
            _uiState.value = _uiState.value.copy(groups = groups, status = null)
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = "Starting group…")
            val ok = runCatching { repository.createSyncPlayGroup(name.ifBlank { "Group Watch" }) }.isSuccess
            if (!ok) {
                _uiState.value = _uiState.value.copy(status = "Could not start a group.")
                return@launch
            }
            refresh()
        }
    }

    fun joinGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyGroupId = groupId)
            val ok = runCatching { repository.joinSyncPlayGroup(groupId) }.isSuccess
            _uiState.value = _uiState.value.copy(busyGroupId = null)
            if (!ok) {
                _uiState.value = _uiState.value.copy(status = "Could not join that group.")
                return@launch
            }
            refresh()
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyGroupId = groupId)
            val ok = runCatching { repository.leaveSyncPlayGroup() }.isSuccess
            _uiState.value = _uiState.value.copy(busyGroupId = null)
            if (!ok) {
                _uiState.value = _uiState.value.copy(status = "Could not leave that group.")
                return@launch
            }
            refresh()
        }
    }

    fun openChat(group: SyncPlayGroupDto) {
        chatPollJob?.cancel()
        _uiState.value = _uiState.value.copy(activeChat = group, messages = emptyList(), chatStatus = "Loading messages…")
        chatPollJob = viewModelScope.launch {
            while (isActive) {
                val lastId = _uiState.value.messages.lastOrNull()?.Id ?: 0L
                val result = runCatching { repository.getGroupWatchMessages(group.GroupId, lastId) }.getOrNull()
                if (result == null) {
                    _uiState.value = _uiState.value.copy(chatStatus = "Could not load messages.")
                } else {
                    _uiState.value = _uiState.value.copy(
                        chatStatus = null,
                        messages = if (result.isEmpty()) _uiState.value.messages else _uiState.value.messages + result,
                    )
                }
                delay(CHAT_POLL_MS)
            }
        }
    }

    fun closeChat() {
        chatPollJob?.cancel()
        chatPollJob = null
        _uiState.value = _uiState.value.copy(activeChat = null, messages = emptyList(), chatStatus = null)
    }

    fun sendMessage(text: String) {
        val group = _uiState.value.activeChat ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingMessage = true)
            val message = runCatching { repository.sendGroupWatchMessage(group.GroupId, trimmed) }.getOrNull()
            _uiState.value = _uiState.value.copy(isSendingMessage = false)
            if (message == null) {
                _uiState.value = _uiState.value.copy(chatStatus = "Could not send that message.")
            } else {
                _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + message)
            }
        }
    }

    override fun onCleared() {
        chatPollJob?.cancel()
        super.onCleared()
    }
}
