package com.jellio.tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jellio.tv.data.model.BaseItemDto

// Every real grid outside Home (Library, Watchlist, Search, Service)
// only ever needs CardOptionsMenu's own plain "every other card"
// branch (Watchlist/Mark Watched/Remove from Library), never the
// Continue Watching/Up Next ones HomeScreen's own inline wiring still
// owns directly: this is that plain branch's own state and both real
// overlays (the menu itself, RemoveFromLibraryConfirm) in one place,
// so a screen that only needs it wires three callbacks instead of
// duplicating HomeScreen's own cardMenuTarget/pendingDeleteItem pair
// and both composable calls by hand. Called once, unconditionally, at
// a screen's own root, same real reason HomeScreen renders its own
// overlays at that screen's own root rather than inside a row/item.
@Composable
fun rememberCardOptionsHost(
    canDeleteItems: Boolean,
    onToggleWatchlist: (BaseItemDto) -> Unit,
    onToggleWatched: (BaseItemDto) -> Unit,
    onDeleteItem: (BaseItemDto) -> Unit,
): (BaseItemDto) -> Unit {
    var menuTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    var pendingDelete by remember { mutableStateOf<BaseItemDto?>(null) }

    menuTarget?.let { item ->
        CardOptionsMenu(
            item = item,
            canDelete = canDeleteItems,
            onToggleWatchlist = { onToggleWatchlist(item) },
            onToggleWatched = { onToggleWatched(item) },
            onDeleteItem = { pendingDelete = item },
            onDismiss = { menuTarget = null },
        )
    }
    pendingDelete?.let { item ->
        RemoveFromLibraryConfirm(
            item = item,
            onConfirm = {
                pendingDelete = null
                onDeleteItem(item)
            },
            onCancel = { pendingDelete = null },
        )
    }

    return { item -> menuTarget = item }
}
