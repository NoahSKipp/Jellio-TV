package com.jellio.tv.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.LoginResult
import com.jellio.tv.data.model.UserDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.auth.RememberedProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountSwitcherUiState(
    val isLoading: Boolean = true,
    val remembered: List<RememberedProfile> = emptyList(),
    val publicOnly: List<UserDto> = emptyList(),
    val busyUserId: String? = null,
    val error: String? = null,
)

// Real components/accountSwitcher.js's own real job: the exact same
// real remembered/public user split LoginViewModel.kt's own
// PROFILE_PICKER already builds, just built while already signed in
// instead of before, so this is its own real ViewModel rather than a
// second real caller of that one (LoginViewModel's own real state
// machine is built around exactly one login flow, gaining a second
// caller of that scope invites disturbing the real screen this app
// actually opens signed out).
@HiltViewModel
class AccountSwitcherViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountSwitcherUiState())
    val uiState: StateFlow<AccountSwitcherUiState> = _uiState.asStateFlow()

    // Profiles saved on this TV show at once; the server's public list
    // (last answer kept for the next open) fills in behind them.
    fun load(session: Session) {
        viewModelScope.launch {
            val remembered = repository.getRememberedUsers(session.serverAddress)
                .map { (userId, entry) -> RememberedProfile(userId, entry.name, entry.primaryImageTag) }
                .filter { it.userId != session.userId }
            val rememberedIds = remembered.map { it.userId }.toSet()
            fun publicOnly(users: List<UserDto>) =
                users.filter { it.Id != null && it.Id != session.userId && it.Id !in rememberedIds }
            val known = publicUsersCache[session.serverAddress]
            _uiState.value = AccountSwitcherUiState(
                isLoading = remembered.isEmpty() && known == null,
                remembered = remembered,
                publicOnly = publicOnly(known.orEmpty()),
            )
            val fresh = kotlinx.coroutines.withTimeoutOrNull(PUBLIC_USERS_TIMEOUT_MS) {
                runCatching { repository.getPublicUsers(session.serverAddress) }.getOrNull()
            }
            if (fresh != null) publicUsersCache[session.serverAddress] = fresh
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                publicOnly = publicOnly(fresh ?: known.orEmpty()),
            )
        }
    }

    // Real components/accountSwitcher.js's own switchToUser(): success
    // needs no state change here at all, the exact same real reason
    // LoginViewModel.kt's own quickSignIn() doesn't either.
    // AppViewModel's own real sessionFlow collection is what actually
    // swaps this whole app over to the newly signed in session, tearing
    // this entire overlay (and the screen underneath it) down with it.
    fun quickSignIn(session: Session, userId: String) {
        if (_uiState.value.busyUserId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyUserId = userId, error = null)
            val result = kotlinx.coroutines.withTimeoutOrNull(QUICK_SIGN_IN_TIMEOUT_MS) {
                repository.quickSignIn(session.serverAddress, userId)
            } ?: LoginResult.Failure("The server took too long to answer. Try again.")
            when (result) {
                is LoginResult.Success -> _uiState.value = _uiState.value.copy(busyUserId = null)
                is LoginResult.Failure -> {
                    android.util.Log.w("JellioSwitch", "quick sign in failed: ${result.message}")
                    // A saved sign-in the server turned down has been forgotten.
                    val stillSaved = repository.getRememberedUsers(session.serverAddress).containsKey(userId)
                    _uiState.value = _uiState.value.copy(
                        busyUserId = null,
                        error = result.message,
                        remembered = if (stillSaved) _uiState.value.remembered else _uiState.value.remembered.filter { it.userId != userId },
                    )
                }
            }
        }
    }

    // Real components/accountSwitcher.js's own buildProfileTile() click
    // handler for a passwordless public user: signs straight in the
    // same real AuthenticateByName(Pw: '') LoginViewModel.kt's own
    // selectPublicUser() already sends. A user that actually needs a
    // password gets no credential form of its own in this overlay
    // (same real reason that file signs the whole device out to the
    // real login screen instead): onNeedsPassword signs this session
    // out, same real effect, AppBootGate's own real LoggedOut branch
    // then hands them the exact same manual/profile picker flow either
    // way.
    fun selectPublicUser(session: Session, user: UserDto, onNeedsPassword: () -> Unit) {
        val hasPassword = user.HasPassword != false && user.HasConfiguredPassword != false
        if (hasPassword) {
            user.Name?.takeIf { it.isNotBlank() }?.let(com.jellio.tv.ui.auth.LoginHandoff::put)
            onNeedsPassword()
            return
        }
        if (_uiState.value.busyUserId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyUserId = user.Id, error = null)
            when (repository.connectAndLogin(session.serverAddress, user.Name.orEmpty(), "")) {
                is LoginResult.Success -> Unit
                is LoginResult.Failure -> _uiState.value = _uiState.value.copy(
                    busyUserId = null,
                    error = "Could not sign in as that user.",
                )
            }
        }
    }

    companion object {
        private const val PUBLIC_USERS_TIMEOUT_MS = 8_000L
        private const val QUICK_SIGN_IN_TIMEOUT_MS = 15_000L
        private val publicUsersCache = java.util.concurrent.ConcurrentHashMap<String, List<UserDto>>()
    }

    fun avatarUrl(session: Session, userId: String, tag: String?): String =
        repository.userImageUrl(session.serverAddress, userId, tag, accessToken = session.accessToken)
}
