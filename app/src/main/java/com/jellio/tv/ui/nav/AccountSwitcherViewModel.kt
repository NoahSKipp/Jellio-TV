package com.jellio.tv.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.LoginResult
import com.jellio.tv.data.catching
import com.jellio.tv.ui.auth.message
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
                catching { repository.getPublicUsers(session.serverAddress) }.getOrNull()
            }
            if (fresh != null) publicUsersCache[session.serverAddress] = fresh
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                publicOnly = publicOnly(fresh ?: known.orEmpty()),
            )
        }
    }

    // onNeedsPassword: the saved sign-in ended on the server, so the password form opens for it.
    fun quickSignIn(session: Session, userId: String, onNeedsPassword: () -> Unit) {
        if (_uiState.value.busyUserId != null) return
        viewModelScope.launch {
            val saved = repository.getRememberedUsers(session.serverAddress)[userId]
            if (saved != null && saved.accessToken.isBlank()) {
                saved.name.takeIf { it.isNotBlank() }?.let(com.jellio.tv.ui.auth.LoginHandoff::put)
                com.jellio.tv.ui.auth.LoginHandoff.putNotice(com.jellio.tv.ui.i18n.Strings.get("auth_session_expired"))
                onNeedsPassword()
                return@launch
            }
            _uiState.value = _uiState.value.copy(busyUserId = userId, error = null)
            val result = kotlinx.coroutines.withTimeoutOrNull(QUICK_SIGN_IN_TIMEOUT_MS) {
                repository.quickSignIn(session.serverAddress, userId)
            } ?: LoginResult.Failure(com.jellio.tv.data.LoginFailure.TIMEOUT)
            when (result) {
                is LoginResult.Success -> _uiState.value = _uiState.value.copy(busyUserId = null)
                is LoginResult.Failure -> {
                    android.util.Log.w("JellioSwitch", "quick sign in failed: ${result.reason}")
                    // A saved sign-in the server turned down has been forgotten.
                    val stillSaved = repository.getRememberedUsers(session.serverAddress).containsKey(userId)
                    _uiState.value = _uiState.value.copy(
                        busyUserId = null,
                        error = result.reason.message(),
                        remembered = if (stillSaved) _uiState.value.remembered else _uiState.value.remembered.filter { it.userId != userId },
                    )
                }
            }
        }
    }

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
                    error = com.jellio.tv.ui.i18n.Strings.get("auth_switch_profile_error"),
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
