package com.jellio.tv.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.common.SidePanel
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay

// "Add to a friend's watchlist": friends as rows to tick, a Send button, then
// one result line per friend.
@Composable
fun FriendWatchlistPicker(
    item: BaseItemDto,
    viewModel: FriendsViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val firstFocus = remember { FocusRequester() }
    val results = state.shareResults

    LaunchedEffect(Unit) {
        viewModel.refresh()
        repeat(10) {
            delay(50)
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.clearShare() } }

    SidePanel(title = Strings.get("friends_picker_title"), width = 520.dp, onDismiss = onDismiss) {
        Text(
            text = item.Name.orEmpty(),
            color = JellioTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
        )
        if (state.friends.isEmpty()) {
            Text(
                text = Strings.get("friends_empty"),
                color = JellioTextSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                items(state.friends, key = { it.UserId }) { friend ->
                    val isSelected = friend.UserId in selected
                    val result = results?.get(friend.UserId)
                    Surface(
                        onClick = {
                            if (results == null) {
                                selected = if (isSelected) selected - friend.UserId else selected + friend.UserId
                            }
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.28f),
                            focusedContentColor = JellioText,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp)
                            .then(if (friend.UserId == state.friends.first().UserId) Modifier.focusRequester(firstFocus) else Modifier),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            FriendAvatar(url = viewModel.avatarUrl(friend.UserId, friend.PrimaryImageTag), name = friend.UserName, size = 40.dp)
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(text = friend.UserName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (result != null) {
                                    Text(
                                        text = Strings.get(addResultKey(result)),
                                        color = if (isAddSuccess(result)) JellioSecondary else JellioDanger,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (result == null && isSelected) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                if (results == null) {
                    FriendPill(
                        label = Strings.get(if (state.sharing) "friends_sending" else "friends_send"),
                        enabled = selected.isNotEmpty() && !state.sharing,
                        onClick = { viewModel.share(item.Id, selected.toList()) },
                    )
                } else {
                    FriendPill(label = Strings.get("friends_done"), onClick = onDismiss)
                }
            }
            state.messageKey?.let { key ->
                Text(
                    text = Strings.get(key),
                    color = JellioDanger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

// The picker in its own window, for screens that cannot lay it over their content.
@Composable
fun FriendWatchlistPickerDialog(
    item: BaseItemDto,
    viewModel: FriendsViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        FriendWatchlistPicker(item = item, viewModel = viewModel, onDismiss = onDismiss)
    }
}
