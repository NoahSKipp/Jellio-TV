package com.jellio.tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jellio.tv.data.model.BaseItemDto

// The card menu for every screen but Home (libraries, Watchlist, Search,
// Studio Hubs): Watchlist and Watched through the screen's own handlers.
// Call it once at the screen's root.
@Composable
fun rememberCardOptionsHost(
    onToggleWatchlist: (BaseItemDto) -> Unit,
    onToggleWatched: (BaseItemDto) -> Unit,
): (BaseItemDto) -> Unit {
    var menuTarget by remember { mutableStateOf<BaseItemDto?>(null) }

    menuTarget?.let { item ->
        CardOptionsMenu(
            item = item,
            onToggleWatchlist = { onToggleWatchlist(item) },
            onToggleWatched = { onToggleWatched(item) },
            onDismiss = { menuTarget = null },
        )
    }

    return { item -> menuTarget = item }
}
