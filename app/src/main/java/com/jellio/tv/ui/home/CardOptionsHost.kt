package com.jellio.tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.friends.FriendWatchlistPickerDialog
import com.jellio.tv.ui.friends.FriendsViewModel
import com.jellio.tv.ui.friends.canShareType

@Composable
fun rememberCardOptionsHost(
    onToggleWatchlist: (BaseItemDto) -> Unit,
    onToggleWatched: (BaseItemDto) -> Unit,
    friendsViewModel: FriendsViewModel = hiltViewModel(),
): (BaseItemDto) -> Unit {
    var menuTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    var shareTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    val friends by friendsViewModel.state.collectAsState()

    menuTarget?.let { item ->
        CardOptionsMenu(
            item = item,
            onToggleWatchlist = { onToggleWatchlist(item) },
            onToggleWatched = { onToggleWatched(item) },
            onAddToFriendWatchlist = if (canShareType(item.Type) && friends.available && friends.friends.isNotEmpty()) {
                { shareTarget = item }
            } else {
                null
            },
            onDismiss = { menuTarget = null },
        )
    }
    shareTarget?.let { item ->
        FriendWatchlistPickerDialog(item = item, viewModel = friendsViewModel, onDismiss = { shareTarget = null })
    }

    return { item -> menuTarget = item }
}
