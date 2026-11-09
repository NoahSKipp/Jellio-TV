package com.jellio.tv.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.catching
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.LoadFailure
import com.jellio.tv.ui.common.loadFailureOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonUiState(
    val isLoading: Boolean = true,
    val error: LoadFailure? = null,
    val person: BaseItemDto? = null,
    val filmography: List<BaseItemDto> = emptyList(),
    val filmographyError: LoadFailure? = null,
)

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    private var loadedPersonId: String? = null

    private var loadJob: kotlinx.coroutines.Job? = null

    fun load(session: Session, personId: String) {
        if (loadedPersonId == personId) return
        loadedPersonId = personId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PersonUiState(isLoading = true)
            try {
                val person = repository.getPerson(session.userId, personId)
                _uiState.value = PersonUiState(isLoading = false, person = person)
                loadFilmography(session, personId)
            } catch (err: kotlinx.coroutines.CancellationException) {
                throw err
            } catch (err: Exception) {
                loadedPersonId = null
                _uiState.value = PersonUiState(isLoading = false, error = loadFailureOf(err))
            }
        }
    }

    fun retry(session: Session, personId: String) {
        loadedPersonId = null
        load(session, personId)
    }

    fun retryFilmography(session: Session, personId: String) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(filmographyError = null)
            loadFilmography(session, personId)
        }
    }

    private suspend fun loadFilmography(session: Session, personId: String) {
        catching { repository.getPersonFilmography(session.userId, personId) }
            .onSuccess { _uiState.value = _uiState.value.copy(filmography = it, filmographyError = null) }
            .onFailure { _uiState.value = _uiState.value.copy(filmographyError = loadFailureOf(it)) }
    }
}
