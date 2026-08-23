package com.jellio.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val session: Session) : AuthState
}

// Owns whatever every screen needs regardless of which one is active:
// the real session state that gates Login vs the rest of the app, and
// the real library list the top nav pill's own Library entries come
// from.
@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = repository.sessionFlow
        .map { session -> if (session != null) AuthState.LoggedIn(session) else AuthState.LoggedOut }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Loading)

    private val _libraries = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val libraries: StateFlow<List<BaseItemDto>> = _libraries.asStateFlow()

    init {
        viewModelScope.launch {
            authState.collectLatest { state ->
                if (state is AuthState.LoggedIn) {
                    _libraries.value = try {
                        repository.getLibraries(state.session.userId)
                    } catch (err: Exception) {
                        emptyList()
                    }
                } else {
                    _libraries.value = emptyList()
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }

    fun imageUrl(session: Session, item: BaseItemDto, imageType: String, maxWidth: Int): String {
        val tag = if (imageType == "Backdrop") {
            item.BackdropImageTags?.firstOrNull()
        } else {
            item.ImageTags?.get(imageType)
        }
        return repository.imageUrl(session.serverAddress, item.Id, tag, imageType, maxWidth)
    }
}
