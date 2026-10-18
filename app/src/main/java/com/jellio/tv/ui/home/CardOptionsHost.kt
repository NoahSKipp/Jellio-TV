package com.jellio.tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// The card menu for every screen but Home (libraries, Watchlist, Search,
// Studio Hubs): Watchlist and Watched through the screen's own handlers,
// and taking a title off Continue Watching or Up Next here. onItemChanged
// hands back the title with its new state so the screen can redraw it.
// Call it once at the screen's root.
@Composable
fun rememberCardOptionsHost(
    onToggleWatchlist: (BaseItemDto) -> Unit,
    onToggleWatched: (BaseItemDto) -> Unit,
    onItemChanged: (BaseItemDto) -> Unit = {},
    actions: CardActionsViewModel = hiltViewModel(),
): (BaseItemDto) -> Unit {
    var menuTarget by remember { mutableStateOf<BaseItemDto?>(null) }

    menuTarget?.let { item ->
        CardOptionsMenu(
            item = item,
            onToggleWatchlist = { onToggleWatchlist(item) },
            onToggleWatched = { onToggleWatched(item) },
            onClearResume = { actions.clearResume(item, onItemChanged) },
            onHideFromUpNext = { actions.hideFromUpNext(item) },
            onDismiss = { menuTarget = null },
        )
    }

    return { item -> menuTarget = item }
}

@HiltViewModel
class CardActionsViewModel @Inject constructor(
    private val repository: JellioRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    fun clearResume(item: BaseItemDto, onChanged: (BaseItemDto) -> Unit) {
        viewModelScope.launch {
            val userId = sessionManager.sessionFlow.first()?.userId ?: return@launch
            runCatching { repository.clearResumePosition(userId, item.Id) }
                .onSuccess { onChanged(item.copy(UserData = it)) }
        }
    }

    fun hideFromUpNext(item: BaseItemDto) {
        val seriesId = if (item.Type == "Series") item.Id else item.SeriesId ?: return
        viewModelScope.launch { runCatching { repository.hideSeriesFromNextUp(seriesId) } }
    }
}
