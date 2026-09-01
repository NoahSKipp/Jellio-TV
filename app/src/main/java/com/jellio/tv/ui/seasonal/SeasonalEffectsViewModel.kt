package com.jellio.tv.ui.seasonal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L

@HiltViewModel
class SeasonalEffectsViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    private val _activeTheme = MutableStateFlow<String?>(null)
    val activeTheme: StateFlow<String?> = _activeTheme.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            while (isActive) {
                val config = repository.getJellioConfig()
                _activeTheme.value = activeSeasonalTheme(Calendar.getInstance(), config)
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }
}
