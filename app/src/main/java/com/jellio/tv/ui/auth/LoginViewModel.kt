package com.jellio.tv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(val isLoading: Boolean = false, val error: String? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(serverAddress: String, username: String, password: String) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = LoginUiState(isLoading = true)
            when (val result = repository.connectAndLogin(serverAddress, username, password)) {
                // Success needs no state change here: AppViewModel's
                // own real sessionFlow collection is what actually
                // moves the app off this screen once the session is
                // persisted.
                is LoginResult.Success -> Unit
                is LoginResult.Failure -> _uiState.value = LoginUiState(isLoading = false, error = result.message)
            }
        }
    }
}
