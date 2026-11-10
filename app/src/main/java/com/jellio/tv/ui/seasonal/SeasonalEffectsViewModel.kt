package com.jellio.tv.ui.seasonal

import androidx.lifecycle.ViewModel
import com.jellio.tv.data.JellioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.util.Calendar
import javax.inject.Inject

private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L

@HiltViewModel
class SeasonalEffectsViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    private val _activeTheme = MutableStateFlow<String?>(null)
    val activeTheme: StateFlow<String?> = _activeTheme.asStateFlow()

    private var lastRefreshAt = 0L

    // Runs only while the app is in the foreground.
    suspend fun runWhileStarted() {
        while (currentCoroutineContext().isActive) {
            val since = android.os.SystemClock.elapsedRealtime() - lastRefreshAt
            if (lastRefreshAt != 0L && since < REFRESH_INTERVAL_MS) {
                delay(REFRESH_INTERVAL_MS - since)
                continue
            }
            lastRefreshAt = android.os.SystemClock.elapsedRealtime()
            val config = repository.getJellioConfig()
            _activeTheme.value = activeSeasonalTheme(Calendar.getInstance(), config)
        }
    }
}
