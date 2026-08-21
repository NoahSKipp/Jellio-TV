package com.jellio.tv.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.CalendarEntryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CalendarUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val entries: List<CalendarEntryDto> = emptyList(),
)

// Real endpoint, Jellio's own CalendarController (GET /Jellio/calendar):
// upcoming episode air dates and movie digital release dates for
// whatever is on the reader's own real Watchlist, same real source
// screens/calendar.js already reads.
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _uiState.value = CalendarUiState(isLoading = true)
            try {
                val entries = repository.getCalendarEntries()
                _uiState.value = CalendarUiState(isLoading = false, entries = entries)
            } catch (err: Exception) {
                loaded = false
                _uiState.value = CalendarUiState(isLoading = false, error = err.message ?: "Could not load the calendar")
            }
        }
    }
}
