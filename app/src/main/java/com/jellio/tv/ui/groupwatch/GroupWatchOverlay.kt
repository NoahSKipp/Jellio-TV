package com.jellio.tv.ui.groupwatch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.GroupWatchMessageDto
import com.jellio.tv.data.model.SyncPlayGroupDto
import com.jellio.tv.ui.common.JellioTextField
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

private fun groupSubtitle(group: SyncPlayGroupDto): String =
    group.PlayingItemName?.let { "Playing $it" } ?: "Idle"

// Real components/sidebar.js's own buildGroupWatchButton(): this app
// has no sidebar rail to anchor it to (mirrors NowPlayingButton's own
// same real corner-button substitution, see MainActivity.kt's own
// header comment above where this is placed), same real "groups"
// Material icon that button uses.
@Composable
fun GroupWatchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated.copy(alpha = 0.96f),
            contentColor = JellioText,
        ),
        modifier = modifier.size(52.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.Groups, contentDescription = "Group Watch", modifier = Modifier.size(22.dp))
        }
    }
}

// Real port of components/groupWatch.js's own openGroupWatch(): a
// styled panel replacing native jellyfin-web's own hidden
// .headerSyncButton menu, same real Jellyfin SyncPlay REST endpoints
// either drives. See GroupWatchViewModel's own header comment for the
// real scope this covers (membership, chat) and does not (playback
// lockstep).
@Composable
fun GroupWatchOverlay(
    state: GroupWatchUiState,
    currentUserName: String,
    currentUserId: String,
    onRefresh: () -> Unit,
    onCreateGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onLeaveGroup: (String) -> Unit,
    onOpenChat: (SyncPlayGroupDto) -> Unit,
    onCloseChat: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = { if (state.activeChat != null) onCloseChat() else onDismiss() })
    LaunchedEffect(Unit) { onRefresh() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .fillMaxHeight(0.8f)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(32.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {}),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (state.activeChat != null) {
                    Surface(
                        onClick = onCloseChat,
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBg, contentColor = JellioText),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back to groups", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(
                    text = if (state.activeChat != null) "${state.activeChat.GroupName ?: "Group Watch"} · Chat" else "Group Watch",
                    color = JellioText,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = if (state.activeChat != null) 12.dp else 0.dp),
                )
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioBg, contentColor = JellioText),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                    }
                }
            }

            val chat = state.activeChat
            if (chat != null) {
                GroupWatchChatView(
                    messages = state.messages,
                    status = state.chatStatus,
                    isSending = state.isSendingMessage,
                    currentUserId = currentUserId,
                    onSendMessage = onSendMessage,
                    modifier = Modifier.weight(1f).padding(top = 20.dp),
                )
            } else {
                GroupWatchListView(
                    groups = state.groups,
                    status = state.status,
                    busyGroupId = state.busyGroupId,
                    currentUserName = currentUserName,
                    onCreateGroup = onCreateGroup,
                    onJoinGroup = onJoinGroup,
                    onLeaveGroup = onLeaveGroup,
                    onOpenChat = onOpenChat,
                    modifier = Modifier.weight(1f).padding(top = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupWatchListView(
    groups: List<SyncPlayGroupDto>,
    status: String?,
    busyGroupId: String?,
    currentUserName: String,
    onCreateGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onLeaveGroup: (String) -> Unit,
    onOpenChat: (SyncPlayGroupDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (groups.isEmpty() && status == null) {
                item {
                    Text(text = "No groups yet. Start one below.", color = JellioTextSecondary)
                }
            }
            items(groups, key = { it.GroupId }) { group ->
                val isMember = currentUserName.isNotEmpty() && group.Participants.contains(currentUserName)
                GroupWatchRow(
                    group = group,
                    isMember = isMember,
                    busy = busyGroupId == group.GroupId,
                    onChat = { onOpenChat(group) },
                    onAction = { if (isMember) onLeaveGroup(group.GroupId) else onJoinGroup(group.GroupId) },
                )
            }
        }

        var newGroupName by remember { mutableStateOf("") }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            JellioTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it },
                label = "New group name",
                modifier = Modifier.weight(1f),
            )
            GroupWatchActionButton(text = "Start a group", onClick = { onCreateGroup(newGroupName.trim()); newGroupName = "" })
        }

        status?.let {
            Text(text = it, color = JellioTextSecondary, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun GroupWatchRow(
    group: SyncPlayGroupDto,
    isMember: Boolean,
    busy: Boolean,
    onChat: () -> Unit,
    onAction: () -> Unit,
) {
    val participantCount = group.Participants.size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isMember) JellioSecondary.copy(alpha = 0.12f) else JellioBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = group.GroupName ?: "Group Watch", color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = groupSubtitle(group), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "$participantCount " + if (participantCount == 1) "person" else "people",
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isMember) {
                GroupWatchActionButton(text = "Chat", onClick = onChat)
            }
            GroupWatchActionButton(text = if (isMember) "Leave" else "Join", onClick = onAction, enabled = !busy, danger = isMember)
        }
    }
}

@Composable
private fun GroupWatchActionButton(text: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (danger) JellioDanger.copy(alpha = 0.18f) else JellioBgElevated,
            contentColor = if (danger) JellioDanger else JellioText,
        ),
    ) {
        Text(text = text, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GroupWatchChatView(
    messages: List<GroupWatchMessageDto>,
    status: String?,
    isSending: Boolean,
    currentUserId: String,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(messages, key = { it.Id }) { message ->
                GroupWatchChatBubble(message = message, isOwn = currentUserId.isNotEmpty() && message.UserId == currentUserId)
            }
        }
        status?.let {
            Text(text = it, color = JellioTextSecondary, modifier = Modifier.padding(vertical = 8.dp))
        }

        var text by remember { mutableStateOf("") }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            JellioTextField(
                value = text,
                onValueChange = { text = it },
                label = "Message the group…",
                modifier = Modifier.weight(1f),
            )
            GroupWatchActionButton(
                text = "Send",
                enabled = !isSending,
                onClick = { onSendMessage(text); text = "" },
            )
        }
    }
}

@Composable
private fun GroupWatchChatBubble(message: GroupWatchMessageDto, isOwn: Boolean) {
    Column(
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .background(if (isOwn) JellioSecondary.copy(alpha = 0.2f) else JellioBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            if (!isOwn) {
                Text(text = message.UserName ?: "Someone", color = JellioTextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Text(text = message.Text.orEmpty(), color = JellioText)
        }
    }
}
