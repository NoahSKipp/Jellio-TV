package com.jellio.tv.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.PersonCreditItem
import com.jellio.tv.data.model.RichPersonDetails
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.LoadFailure
import com.jellio.tv.ui.common.loadFailureOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonUiState(
    val isLoading: Boolean = true,
    val error: LoadFailure? = null,
    val details: RichPersonDetails? = null,
    val selectedDepartment: String = "All",
    val filmography: List<PersonCreditItem> = emptyList(),
)

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonUiState())
    val uiState: StateFlow<PersonUiState> = _uiState.asStateFlow()

    private var loadedPersonId: String? = null
    private var loadJob: Job? = null

    fun load(session: Session, personId: String) {
        if (loadedPersonId == personId && !_uiState.value.isLoading && _uiState.value.details != null) return
        loadedPersonId = personId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PersonUiState(isLoading = true)
            try {
                val details = repository.getRichPersonDetails(session, personId)
                _uiState.value = PersonUiState(
                    isLoading = false,
                    details = details,
                    selectedDepartment = "All",
                    filmography = filterCreditsForDepartment("All", details.allCredits),
                )
            } catch (err: CancellationException) {
                throw err
            } catch (err: Exception) {
                loadedPersonId = null
                _uiState.value = PersonUiState(isLoading = false, error = loadFailureOf(err))
            }
        }
    }

    fun selectDepartment(departmentKey: String) {
        val details = _uiState.value.details ?: return
        val filtered = filterCreditsForDepartment(departmentKey, details.allCredits)
        _uiState.value = _uiState.value.copy(
            selectedDepartment = departmentKey,
            filmography = filtered,
        )
    }

    private fun filterCreditsForDepartment(departmentKey: String, allCredits: List<PersonCreditItem>): List<PersonCreditItem> {
        return if (departmentKey.equals("All", ignoreCase = true)) {
            // Deduplicate by tmdbId so each title appears once under "All", prioritizing Acting roles
            allCredits
                .groupBy { it.tmdbId }
                .values
                .map { list ->
                    list.firstOrNull { it.department.equals("Acting", ignoreCase = true) }
                        ?: list.firstOrNull { it.department.equals("Directing", ignoreCase = true) }
                        ?: list.firstOrNull { it.department.equals("Writing", ignoreCase = true) }
                        ?: list.maxByOrNull { it.popularity }
                        ?: list.first()
                }
                .sortedByDescending { it.releaseDate ?: "${it.year ?: 0}" }
        } else {
            allCredits
                .filter { it.department.equals(departmentKey, ignoreCase = true) }
                .distinctBy { it.tmdbId.toString() + ":" + (it.role ?: "") }
                .sortedByDescending { it.releaseDate ?: "${it.year ?: 0}" }
        }
    }

    fun retry(session: Session, personId: String) {
        loadedPersonId = null
        load(session, personId)
    }
}
