package com.jellio.tv.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.data.plainId
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay

// The button beside another user's name: add, request sent, answer, or a
// small friends menu. Nothing for yourself, private or closed profiles, or
// a plugin without friends.
@Composable
fun FriendProfileButton(userId: String, viewModel: FriendsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val status by viewModel.viewedStatus.collectAsState()
    var menuOpen by remember(userId) { mutableStateOf(false) }
    var confirmRemove by remember(userId) { mutableStateOf(false) }
    var refocus by remember(userId) { mutableStateOf(false) }
    val primary = remember { FocusRequester() }

    LaunchedEffect(userId) { viewModel.loadStatus(userId) }
    DisposableEffect(userId) { onDispose { viewModel.clearViewed() } }
    // The button that was pressed is replaced by the next state's: keep focus on the row.
    LaunchedEffect(status?.Status, status?.RequestId) {
        if (refocus) {
            delay(150)
            runCatching { primary.requestFocus() }
            refocus = false
        }
    }

    if (!state.available) return
    val current = status ?: return
    val requestId = current.RequestId
    when (buttonStateOf(relationOf(current.Status), current.CanRequest)) {
        FriendButtonState.Hidden -> Unit
        FriendButtonState.Add -> FriendPill(
            label = Strings.get("friends_add"),
            icon = Icons.Filled.PersonAdd,
            onClick = {
                refocus = true
                viewModel.sendRequest(userId)
            },
            modifier = modifier.focusRequester(primary),
        )
        FriendButtonState.RequestSent -> FriendPill(
            label = Strings.get("friends_request_sent"),
            icon = Icons.Filled.Check,
            onClick = {
                if (requestId != null) {
                    refocus = true
                    viewModel.cancel(requestId)
                }
            },
            modifier = modifier.focusRequester(primary),
        )
        FriendButtonState.Respond -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
            FriendPill(
                label = Strings.get("friends_accept"),
                onClick = {
                    if (requestId != null) {
                        refocus = true
                        viewModel.accept(requestId)
                    }
                },
                modifier = Modifier.focusRequester(primary),
            )
            FriendPill(
                label = Strings.get("friends_decline"),
                danger = true,
                onClick = {
                    if (requestId != null) {
                        refocus = true
                        viewModel.decline(requestId)
                    }
                },
            )
        }
        FriendButtonState.Friends -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier,
        ) {
            FriendPill(
                label = Strings.get("friends_are_friends"),
                icon = Icons.Filled.Check,
                onClick = {
                    menuOpen = !menuOpen
                    confirmRemove = false
                },
                modifier = Modifier.focusRequester(primary),
            )
            if (menuOpen) {
                val muted = state.friends.firstOrNull { plainId(it.UserId) == plainId(userId) }?.Muted == true
                FriendPill(
                    label = Strings.get(if (muted) "friends_unmute" else "friends_mute"),
                    onClick = { viewModel.toggleMute(state.friends.firstOrNull { plainId(it.UserId) == plainId(userId) }?.UserId ?: userId) },
                )
                FriendPill(
                    label = Strings.get(if (confirmRemove) "friends_remove_confirm" else "friends_remove"),
                    danger = true,
                    onClick = {
                        if (confirmRemove) {
                            refocus = true
                            menuOpen = false
                            viewModel.remove(state.friends.firstOrNull { plainId(it.UserId) == plainId(userId) }?.UserId ?: userId)
                        } else {
                            confirmRemove = true
                        }
                    },
                )
            }
        }
    }
}

// "Friends (n)" with a row of their avatars on your own profile; selecting it
// opens the friends panel.
@Composable
fun FriendsSection(
    session: Session,
    viewModel: FriendsViewModel,
    onOpen: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    if (!state.available || !state.loaded) return
    val shown = state.friends.take(6)
    val overflow = state.friends.size - shown.size
    Column(modifier = modifier.padding(horizontal = 48.dp, vertical = 8.dp)) {
        Surface(
            onClick = onOpen,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.06f),
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.2f),
                focusedContentColor = JellioText,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Strings.format("friends_section_title", state.friends.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (state.incoming.isNotEmpty()) Strings.format("friends_section_requests", state.incoming.size) else Strings.get("friends_section_hint"),
                        color = if (state.incoming.isNotEmpty()) JellioDanger else JellioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), verticalAlignment = Alignment.CenterVertically) {
                    shown.forEach { friend ->
                        FriendAvatar(url = viewModel.avatarUrl(session, friend.UserId, friend.PrimaryImageTag), name = friend.UserName, size = 40.dp)
                    }
                    if (overflow > 0) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.2f), CircleShape),
                        ) {
                            Text(text = "+$overflow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.padding(start = 12.dp).size(24.dp))
            }
        }
    }
}
