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

enum class LoginMode { CHECKING, SERVER_ENTRY, PROFILE_PICKER, MANUAL, FORGOT_USERNAME, FORGOT_PIN }

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
    // Real port of screens/login.js's own forgot password state: the
    // username step 2 (FORGOT_PIN) carries forward, same real reason
    // that file's own renderForgotPassword() passes it straight into
    // buildForgotPinForm() rather than asking for it twice.
    val forgotPasswordUsername: String = "",
    val isRequestingReset: Boolean = false,
    val isRedeemingReset: Boolean = false,
    val resetStatus: String? = null,
)

// Real port of native jellyfin-web's own #/login flow this plugin
// only ever themes rather than replaces (Jellio-Plugin ships no
// login.js of its own, loginTheme.js's own header confirms: a plain
// class toggle for css/login.css to hook, nothing else): server
// address first (SERVER_ENTRY), then "Who's watching?" once that
// server answers back with at least one real remembered or public
// profile (PROFILE_PICKER), a bare username/password form
// (MANUAL) only as the real fallback for a true first run once a
// server is known but has neither kind of profile to show, or for a
// profile this device has no passwordless/remembered shortcut for.
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
                _uiState.value = LoginUiState(mode = LoginMode.SERVER_ENTRY, serverAddress = "")
                return@launch
            }

            // Real bug, found live against a real screenshot: a
            // remembered profile kept showing up here even after an
            // admin later flipped that same real user's own "Display
            // this user on the login screen" toggle off server side
            // (UserPolicy.IsHidden), since nothing here ever
            // cross-checked a remembered profile against a fresh real
            // answer once it was remembered. getPublicUsers() already
            // comes back with IsHidden already enforced (its own
            // header explains why), so a remembered id no longer
            // present in it is exactly a user who either got hidden or
            // got deleted since this device last remembered them:
            // forgotten outright here, not just filtered out of this
            // one render, so this screen stops re-showing it on every
            // real future launch too. Only reached once the fetch
            // below actually succeeds (repository.getPublicUsers() now
            // throws instead of quietly returning empty on a failed
            // request, its own header explains why): a device offline
            // or a server briefly down must never wipe out its own
            // only real way back in over a request that never even
            // reached the server.
            val remembered = repository.getRememberedUsers(serverAddress).map { (userId, entry) ->
                RememberedProfile(userId, entry.name, entry.primaryImageTag)
            }
            var publicUsers = emptyList<UserDto>()
            var liveRemembered = remembered
            try {
                publicUsers = repository.getPublicUsers(serverAddress)
                val publicIds = publicUsers.mapNotNull { it.Id }.toSet()
                val stale = remembered.filterNot { it.userId in publicIds }
                stale.forEach { repository.forgetRememberedUser(serverAddress, it.userId) }
                liveRemembered = remembered - stale.toSet()
            } catch (err: Exception) {
                // A device offline or a server briefly down still gets
                // its own remembered list, real endpoint or not.
            }
            val rememberedIds = liveRemembered.map { it.userId }.toSet()
            val publicOnly = publicUsers.filter { it.Id !in rememberedIds }

            _uiState.value = if (liveRemembered.isEmpty() && publicOnly.isEmpty()) {
                LoginUiState(mode = LoginMode.MANUAL, serverAddress = serverAddress)
            } else {
                LoginUiState(
                    mode = LoginMode.PROFILE_PICKER,
                    serverAddress = serverAddress,
                    remembered = liveRemembered,
                    publicOnly = publicOnly,
                    canReturnToProfiles = true,
                )
            }
        }
    }

    // SERVER_ENTRY's own "Continue": loadProfiles() above already
    // branches on whatever this server actually answers back with, the
    // exact same real PROFILE_PICKER/MANUAL decision backToProfiles()
    // makes on a device that already knew its server.
    fun submitServerAddress(serverAddress: String) {
        val normalized = serverAddress.trim().trimEnd('/')
        if (normalized.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Enter your server address")
            return
        }
        loadProfiles(normalized)
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

    // Real port of screens/login.js's own onForgotPassword hook off the
    // manual form: opens step 1 with whatever the reader had already
    // typed into the username field prefilled, same real reasoning
    // that file's own buildManualForm() documents for not asking for
    // it twice.
    fun showForgotPassword(prefillUsername: String) {
        _uiState.value = _uiState.value.copy(
            mode = LoginMode.FORGOT_USERNAME,
            forgotPasswordUsername = prefillUsername,
            resetStatus = null,
        )
    }

    fun requestPasswordReset(username: String) {
        if (username.isBlank()) {
            _uiState.value = _uiState.value.copy(resetStatus = "Enter your username.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRequestingReset = true, resetStatus = "Sending…")
            val ok = repository.requestPasswordReset(_uiState.value.serverAddress, username)
            _uiState.value = if (ok) {
                _uiState.value.copy(
                    mode = LoginMode.FORGOT_PIN,
                    forgotPasswordUsername = username,
                    isRequestingReset = false,
                    resetStatus = "If that account exists, a reset code has been emailed to it. Enter it below with a new password.",
                )
            } else {
                _uiState.value.copy(isRequestingReset = false, resetStatus = "Could not request a reset code. Try again later.")
            }
        }
    }

    fun redeemPasswordReset(pin: String, newPassword: String, confirmPassword: String) {
        if (pin.isBlank() || newPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(resetStatus = "Enter the reset code and a new password.")
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(resetStatus = "New passwords do not match.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRedeemingReset = true, resetStatus = "Resetting…")
            when (val result = repository.redeemPasswordReset(_uiState.value.serverAddress, _uiState.value.forgotPasswordUsername, pin, newPassword)) {
                // Success needs no state change here: AppViewModel's own
                // real sessionFlow collection is what actually moves the
                // app off this screen once the session is persisted.
                is LoginResult.Success -> Unit
                is LoginResult.Failure -> _uiState.value = _uiState.value.copy(isRedeemingReset = false, resetStatus = result.message)
            }
        }
    }

    fun cancelForgotPassword() {
        _uiState.value = _uiState.value.copy(
            mode = LoginMode.MANUAL,
            manualPrefillUsername = _uiState.value.forgotPasswordUsername,
            resetStatus = null,
            error = null,
        )
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
