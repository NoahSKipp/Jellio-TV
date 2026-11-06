package com.jellio.tv.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val person: BaseItemDto? = null,
    val filmography: List<BaseItemDto> = emptyList(),
)

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    private var loadedPersonId: String? = null

    fun load(session: Session, personId: String) {
        if (loadedPersonId == personId) return
        loadedPersonId = personId
        viewModelScope.launch {
            _uiState.value = PersonUiState(isLoading = true)
            try {
                val person = repository.getPerson(session.userId, personId)
                _uiState.value = PersonUiState(isLoading = false, person = person)
                val filmography = runCatching { repository.getPersonFilmography(session.userId, personId) }.getOrDefault(emptyList())
                _uiState.value = _uiState.value.copy(filmography = filmography)
            } catch (err: Exception) {
                _uiState.value = PersonUiState(isLoading = false, error = err.message ?: com.jellio.tv.ui.i18n.Strings.get("person_load_error"))
            }
        }
    }
}
