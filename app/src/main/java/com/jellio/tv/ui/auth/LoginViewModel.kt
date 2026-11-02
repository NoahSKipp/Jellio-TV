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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import javax.inject.Inject

enum class LoginMode { CHECKING, SERVER_ENTRY, PROFILE_PICKER, MANUAL, FORGOT_USERNAME, FORGOT_PIN, QUICK_CONNECT }

data class RememberedProfile(val userId: String, val name: String, val primaryImageTag: String?, val accessToken: String? = null)

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
    val quickConnectCode: String? = null,
    val quickConnectSecret: String? = null,
    val isQuickConnectPolling: Boolean = false,
    val forgotPasswordUsername: String = "",
    val isRequestingReset: Boolean = false,
    val isRedeemingReset: Boolean = false,
    val resetStatus: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun start() {
        loadProfiles(_uiState.value.serverAddress)
    }

    private fun loadProfiles(knownServerAddress: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(mode = LoginMode.CHECKING)
            val serverAddress = knownServerAddress.ifEmpty { repository.knownServerAddress().orEmpty() }
            if (serverAddress.isEmpty()) {
                _uiState.value = LoginUiState(mode = LoginMode.SERVER_ENTRY, serverAddress = "")
                return@launch
            }

            val remembered = repository.getRememberedUsers(serverAddress).map { (userId, entry) ->
                RememberedProfile(userId, entry.name, entry.primaryImageTag, entry.accessToken)
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
            }
            val rememberedIds = liveRemembered.map { it.userId }.toSet()
            val publicOnly = publicUsers.filter { it.Id !in rememberedIds }

            val handoff = LoginHandoff.take()
            _uiState.value = if (handoff != null) {
                LoginUiState(
                    mode = LoginMode.MANUAL,
                    serverAddress = serverAddress,
                    remembered = liveRemembered,
                    publicOnly = publicOnly,
                    manualPrefillUsername = handoff,
                    canReturnToProfiles = liveRemembered.isNotEmpty() || publicOnly.isNotEmpty(),
                )
            } else if (liveRemembered.isEmpty() && publicOnly.isEmpty()) {
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

    fun submitServerAddress(serverAddress: String) {
        if (serverAddress.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter your server address")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(mode = LoginMode.CHECKING, error = null)
            repository.resolveServerAddress(serverAddress)
                .onSuccess { loadProfiles(it) }
                .onFailure {
                    _uiState.value = LoginUiState(mode = LoginMode.SERVER_ENTRY, serverAddress = serverAddress, error = it.message)
                }
        }
    }

    fun quickSignIn(userId: String) {
        val serverAddress = _uiState.value.serverAddress
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busyUserId = userId)
            when (val result = repository.quickSignIn(serverAddress, userId)) {
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

    fun avatarUrl(userId: String, tag: String?): String {
        val token = _uiState.value.remembered.firstOrNull { it.userId == userId }?.accessToken
        return repository.userImageUrl(_uiState.value.serverAddress, userId, tag, accessToken = token)
    }

    fun showManualForm() {
        _uiState.value = _uiState.value.copy(mode = LoginMode.MANUAL, manualPrefillUsername = "", error = null)
    }

    fun backToProfiles() {
        loadProfiles(_uiState.value.serverAddress)
    }

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
private var quickConnectJob: kotlinx.coroutines.Job? = null

    fun startQuickConnect() {
        val serverAddress = _uiState.value.serverAddress
        if (serverAddress.isBlank()) return
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                mode = LoginMode.QUICK_CONNECT,
                isLoading = true,
                error = null,
                quickConnectCode = null,
                quickConnectSecret = null,
                isQuickConnectPolling = false,
            )
            val result = repository.initiateQuickConnect(serverAddress)
            if (result == null || result.Code.isNullOrBlank() || result.Secret.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Could not start Quick Connect. Make sure Quick Connect is enabled on your Jellyfin server.",
                )
                return@launch
            }
            val secret = result.Secret
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                quickConnectCode = result.Code,
                quickConnectSecret = secret,
                isQuickConnectPolling = true,
            )
            while (isActive) {
                kotlinx.coroutines.delay(2000L)
                val check = repository.checkQuickConnect(secret)
                if (check?.Authenticated == true) {
                    _uiState.value = _uiState.value.copy(isQuickConnectPolling = false, isLoading = true)
                    when (val authResult = repository.authenticateWithQuickConnect(serverAddress, secret)) {
                        is LoginResult.Success -> {
                            return@launch
                        }
                        is LoginResult.Failure -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = authResult.message,
                            )
                            return@launch
                        }
                    }
                }
            }
        }
    }

    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        _uiState.value = _uiState.value.copy(
            mode = if (_uiState.value.canReturnToProfiles) LoginMode.PROFILE_PICKER else LoginMode.MANUAL,
            isQuickConnectPolling = false,
            isLoading = false,
            quickConnectCode = null,
            quickConnectSecret = null,
            error = null,
        )
    }

    override fun onCleared() {
        super.onCleared()
        quickConnectJob?.cancel()
    }
}object LoginHandoff {
    @Volatile private var username: String? = null

    fun put(name: String) {
        username = name
    }

    fun take(): String? = username.also { username = null }
}
