package com.jellio.tv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.LoginResult
import com.jellio.tv.data.model.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginMode { CHECKING, PROFILE_PICKER, MANUAL }

data class RememberedProfile(val userId: String, val name: String, val primaryImageTag: String?)

data class LoginUiState(
    val mode: LoginMode = LoginMode.CHECKING,
    val serverAddress: String = "",
    val remembered: List<RememberedProfile> = emptyList(),
    val publicOnly: List<UserDto> = emptyList(),
    val busyUserId: String? = null,
    val manualPrefillUsername: String = "",
    val canReturnToProfiles: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

// Real port of screens/login.js's own renderProfilePicker()/
// showManual() state machine: a device with a known server and at
// least one real remembered or public profile opens onto "Who's
// watching?" (PROFILE_PICKER), the exact same real fallback to a bare
// manual form that file's own header comment documents for a true
// first run (no server known yet) or a server with neither kind of
// profile to show.
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        loadProfiles(_uiState.value.serverAddress)
    }

    // Real port of that file's own rerender(): re-fetches both real
    // lists fresh rather than trusting local state, the same real
    // reason a forgotten profile or a server side "Display on login
    // screen" toggle flip both have to actually show up without a
    // fresh app launch.
    private fun loadProfiles(knownServerAddress: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(mode = LoginMode.CHECKING)
            val serverAddress = knownServerAddress.ifEmpty { repository.knownServerAddress().orEmpty() }
            if (serverAddress.isEmpty()) {
                _uiState.value = LoginUiState(mode = LoginMode.MANUAL, serverAddress = "")
                return@launch
            }

            val remembered = repository.getRememberedUsers(serverAddress).map { (userId, entry) ->
                RememberedProfile(userId, entry.name, entry.primaryImageTag)
            }
            val publicUsers = repository.getPublicUsers(serverAddress)
            val rememberedIds = remembered.map { it.userId }.toSet()
            val publicOnly = publicUsers.filter { it.Id !in rememberedIds }

            _uiState.value = if (remembered.isEmpty() && publicOnly.isEmpty()) {
                LoginUiState(mode = LoginMode.MANUAL, serverAddress = serverAddress)
            } else {
                LoginUiState(
                    mode = LoginMode.PROFILE_PICKER,
                    serverAddress = serverAddress,
                    remembered = remembered,
                    publicOnly = publicOnly,
                    canReturnToProfiles = true,
                )
            }
        }
    }

    fun quickSignIn(userId: String) {
        val serverAddress = _uiState.value.serverAddress
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyUserId = userId)
            when (val result = repository.quickSignIn(serverAddress, userId)) {
                // Success needs no state change here: AppViewModel's own
                // real sessionFlow collection is what actually moves the
                // app off this screen once the session is persisted.
                is LoginResult.Success -> Unit
                is LoginResult.Failure -> {
                    val name = _uiState.value.remembered.firstOrNull { it.userId == userId }?.name.orEmpty()
                    _uiState.value = _uiState.value.copy(
                        mode = LoginMode.MANUAL,
                        busyUserId = null,
                        manualPrefillUsername = name,
                        error = result.message,
                        remembered = _uiState.value.remembered.filterNot { it.userId == userId },
                    )
                }
            }
        }
    }

    fun forgetRememberedUser(userId: String) {
        val serverAddress = _uiState.value.serverAddress
        _uiState.value = _uiState.value.copy(remembered = _uiState.value.remembered.filterNot { it.userId == userId })
        viewModelScope.launch { repository.forgetRememberedUser(serverAddress, userId) }
    }

    // Real port of that file's own buildPublicUserTile() click handler:
    // a passwordless account signs straight in the same real
    // AuthenticateByName(Pw: '') a native passwordless tile already
    // sends, everyone else falls to the manual form prefilled with
    // their own real username rather than asked to type it twice.
    fun selectPublicUser(user: UserDto) {
        val hasPassword = user.HasPassword != false && user.HasConfiguredPassword != false
        if (!hasPassword) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(busyUserId = user.Id)
                when (val result = repository.connectAndLogin(_uiState.value.serverAddress, user.Name, "")) {
                    is LoginResult.Success -> Unit
                    is LoginResult.Failure -> _uiState.value = _uiState.value.copy(
                        mode = LoginMode.MANUAL,
                        busyUserId = null,
                        manualPrefillUsername = user.Name,
                        error = result.message,
                    )
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(mode = LoginMode.MANUAL, manualPrefillUsername = user.Name, error = null)
        }
    }

    fun avatarUrl(userId: String, tag: String?): String = repository.userImageUrl(_uiState.value.serverAddress, userId, tag)

    fun showManualForm() {
        _uiState.value = _uiState.value.copy(mode = LoginMode.MANUAL, manualPrefillUsername = "", error = null)
    }

    fun backToProfiles() {
        loadProfiles(_uiState.value.serverAddress)
    }

    fun login(serverAddress: String, username: String, password: String) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.connectAndLogin(serverAddress, username, password)) {
                is LoginResult.Success -> Unit
                is LoginResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
            }
        }
    }
}
