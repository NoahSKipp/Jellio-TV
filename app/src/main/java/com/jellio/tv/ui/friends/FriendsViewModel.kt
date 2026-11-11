package com.jellio.tv.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.FriendRequestException
import com.jellio.tv.data.FriendFailure
import com.jellio.tv.data.FriendsRepository
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.catching
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.FriendEntryDto
import com.jellio.tv.data.model.FriendPersonDto
import com.jellio.tv.data.model.FriendRequestDto
import com.jellio.tv.data.model.FriendStatusDto
import com.jellio.tv.data.model.FriendsSettingsDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    // False once the plugin answered 404: every friends entry point hides.
    val available: Boolean = true,
    val loaded: Boolean = false,
    val friends: List<FriendEntryDto> = emptyList(),
    val incoming: List<FriendRequestDto> = emptyList(),
    val outgoing: List<FriendRequestDto> = emptyList(),
    val people: List<FriendPersonDto>? = null,
    // A string key for the last refusal, shown calmly in the panel.
    val messageKey: String? = null,
    // Result per friend id after "Add to a friend's watchlist"; null until sent.
    val shareResults: Map<String, String>? = null,
    val sharing: Boolean = false,
    val settings: FriendsSettingsDto? = null,
)

// Activity-scoped: the nav badge, the profile, the panel, the picker and the
// Quick Hub all read the same lists.
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friends: FriendsRepository,
    private val repository: JellioRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    private val _viewed = MutableStateFlow<FriendStatusDto?>(null)
    val viewedStatus: StateFlow<FriendStatusDto?> = _viewed.asStateFlow()
    private var viewedUserId: String? = null

    private var forUser: String? = null
    private var listJob: Job? = null

    init {
        viewModelScope.launch {
            friends.available.collect { ok -> _state.update { it.copy(available = ok) } }
        }
    }

    // Null without a picture, so the avatar falls back to the name's first letter.
    fun avatarUrl(session: Session, userId: String, tag: String?): String? =
        if (tag.isNullOrEmpty()) null else repository.userImageUrl(session.serverAddress, userId, tag, 200, session.accessToken)

    private var session: Session? = null

    // The signed-in user's picture host, for screens without a session in hand.
    fun avatarUrl(userId: String, tag: String?): String? = session?.let { avatarUrl(it, userId, tag) }

    // A new account starts clean and asks the server again.
    fun onSession(current: Session) {
        session = current
        val userId = current.userId
        if (forUser == userId) return
        forUser = userId
        friends.reset()
        _state.value = FriendsUiState()
        _viewed.value = null
        refresh()
    }

    fun refresh() {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            friends.list().onSuccess { list ->
                _state.update {
                    it.copy(loaded = true, friends = list.Friends, incoming = list.Incoming, outgoing = list.Outgoing)
                }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(messageKey = null) }

    private fun fail(error: Throwable) {
        val failure = (error as? FriendRequestException)?.failure ?: FriendFailure.Failed
        _state.update { it.copy(messageKey = failureKey(failure)) }
        // A request that is gone: the lists were out of date.
        if (failure == FriendFailure.Gone) refresh()
    }

    fun loadPeople(query: String) {
        viewModelScope.launch {
            friends.people(query.trim()).onSuccess { list -> _state.update { it.copy(people = list) } }
                .onFailure { _state.update { s -> s.copy(people = s.people ?: emptyList()) } }
        }
    }

    fun sendRequest(userId: String) {
        viewModelScope.launch {
            friends.sendRequest(userId)
                .onSuccess { status ->
                    _state.update { s ->
                        s.copy(people = s.people?.map { if (it.UserId == userId) it.copy(Status = status) else it })
                    }
                    if (viewedUserId == userId) loadStatus(userId)
                    refresh()
                }
                .onFailure { fail(it) }
        }
    }

    fun accept(requestId: String) = act { friends.accept(requestId) }

    fun decline(requestId: String) = act { friends.decline(requestId) }

    fun cancel(requestId: String) = act { friends.cancel(requestId) }

    private fun act(call: suspend () -> Result<String>) {
        viewModelScope.launch {
            call().onSuccess {
                refresh()
                viewedUserId?.let { id -> loadStatus(id) }
            }.onFailure { fail(it) }
        }
    }

    fun remove(userId: String) {
        viewModelScope.launch {
            friends.remove(userId).onSuccess {
                _state.update { s -> s.copy(friends = s.friends.filterNot { it.UserId == userId }) }
                refresh()
                if (viewedUserId == userId) loadStatus(userId)
            }.onFailure { fail(it) }
        }
    }

    fun toggleMute(userId: String) {
        val current = _state.value.friends.firstOrNull { it.UserId == userId }?.Muted ?: return
        val next = nextMuted(current)
        viewModelScope.launch {
            friends.setMuted(userId, next).onSuccess { muted ->
                _state.update { s -> s.copy(friends = s.friends.map { if (it.UserId == userId) it.copy(Muted = muted) else it }) }
            }.onFailure { fail(it) }
        }
    }

    // The status for the profile on screen; null hides the button.
    fun loadStatus(userId: String) {
        if (viewedUserId != userId) _viewed.value = null
        viewedUserId = userId
        viewModelScope.launch {
            friends.status(userId).onSuccess { status ->
                if (viewedUserId == userId) _viewed.value = status
            }.onFailure { if (viewedUserId == userId) _viewed.value = null }
        }
    }

    fun clearViewed() {
        viewedUserId = null
        _viewed.value = null
    }

    fun clearShare() = _state.update { it.copy(shareResults = null, sharing = false) }

    fun share(itemId: String, userIds: List<String>) {
        if (userIds.isEmpty() || _state.value.sharing) return
        _state.update { it.copy(sharing = true, messageKey = null) }
        viewModelScope.launch {
            friends.addToWatchlists(itemId, userIds)
                .onSuccess { results ->
                    val byUser = results.associate { it.UserId.replace("-", "").lowercase() to it.Result }
                    val mapped = userIds.associateWith { id -> byUser[id.replace("-", "").lowercase()] ?: "" }
                    _state.update { it.copy(sharing = false, shareResults = mapped) }
                }
                .onFailure {
                    _state.update { s -> s.copy(sharing = false, messageKey = "friends_result_failed") }
                }
        }
    }

    // Takes a title a friend added back off the reader's own watchlist.
    fun undoWatchlistAdd(session: Session, itemId: String, done: () -> Unit) {
        viewModelScope.launch {
            val onList = BaseItemDto(Id = itemId, UserData = UserItemDataDto(IsFavorite = true))
            catching { repository.toggleFavorite(session.userId, onList) }
                .onSuccess { done() }
                .onFailure { _state.update { s -> s.copy(messageKey = "friends_err_failed") } }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            friends.settings().onSuccess { value -> _state.update { it.copy(settings = value) } }
        }
    }

    fun setCanAdd(enabled: Boolean) {
        val before = _state.value.settings ?: return
        _state.update { it.copy(settings = before.copy(FriendsCanAddToWatchlist = enabled)) }
        viewModelScope.launch {
            friends.saveSettings(enabled, null)
                .onSuccess { value -> _state.update { it.copy(settings = value) } }
                .onFailure { _state.update { s -> s.copy(settings = before) } }
        }
    }

    fun setRequestsFrom(value: String) {
        val before = _state.value.settings ?: return
        _state.update { it.copy(settings = before.copy(FriendRequestsFrom = value)) }
        viewModelScope.launch {
            friends.saveSettings(null, value)
                .onSuccess { saved -> _state.update { it.copy(settings = saved) } }
                .onFailure { _state.update { s -> s.copy(settings = before) } }
        }
    }
}
