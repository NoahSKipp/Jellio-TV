package com.jellio.tv.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.SidePanel
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay

private enum class FriendsTab { FRIENDS, REQUESTS, FIND }

// Friends | Requests | Find people, in the shared side panel.
@Composable
fun FriendsPanel(
    session: Session,
    viewModel: FriendsViewModel,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(FriendsTab.FRIENDS) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var panelHasFocus by remember { mutableStateOf(true) }
    val tabFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        repeat(10) {
            if (runCatching { tabFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
    LaunchedEffect(tab) {
        confirmRemove = null
        if (tab == FriendsTab.FIND) viewModel.loadPeople("")
    }
    LaunchedEffect(state.messageKey) {
        if (state.messageKey != null) {
            delay(4000)
            viewModel.clearMessage()
        }
    }
    // A row that leaves the list (accepted, removed) takes focus with it.
    LaunchedEffect(state.friends.size, state.incoming.size, state.outgoing.size, state.people?.size) {
        delay(150)
        if (!panelHasFocus) runCatching { tabFocus.requestFocus() }
    }
    DisposableEffect(Unit) { onDispose { viewModel.clearMessage() } }

    SidePanel(title = Strings.get("friends_title"), width = 580.dp, onDismiss = onDismiss) {
        Column(modifier = Modifier.weight(1f).onFocusChanged { panelHasFocus = it.hasFocus }) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                FriendsTab.entries.forEach { entry ->
                    val label = when (entry) {
                        FriendsTab.FRIENDS -> Strings.get("friends_tab_friends")
                        FriendsTab.REQUESTS -> Strings.get("friends_tab_requests")
                        FriendsTab.FIND -> Strings.get("friends_tab_find")
                    }
                    TabPill(
                        label = label,
                        count = if (entry == FriendsTab.REQUESTS) state.incoming.size else 0,
                        selected = entry == tab,
                        onFocus = { tab = entry },
                        modifier = if (entry == tab) Modifier.focusRequester(tabFocus) else Modifier,
                    )
                }
            }
            state.messageKey?.let { key ->
                Text(
                    text = Strings.get(key),
                    color = JellioDanger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            when (tab) {
                FriendsTab.FRIENDS -> {
                    if (state.friends.isEmpty()) {
                        EmptyLine(Strings.get(if (state.loaded) "friends_empty" else "status_loading"))
                    } else {
                        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                            items(state.friends, key = { it.UserId }) { friend ->
                                val confirming = confirmRemove == friend.UserId
                                PersonRow(
                                    name = friend.UserName,
                                    avatarUrl = viewModel.avatarUrl(session, friend.UserId, friend.PrimaryImageTag),
                                    subtitle = if (friend.Muted) Strings.get("friends_muted") else null,
                                    onOpen = {
                                        onDismiss()
                                        onOpenProfile(friend.UserId)
                                    },
                                ) {
                                    if (confirming) {
                                        FriendPill(label = Strings.get("action_cancel"), onClick = { confirmRemove = null })
                                    } else {
                                        FriendPill(
                                            label = Strings.get(if (friend.Muted) "friends_unmute" else "friends_mute"),
                                            onClick = { viewModel.toggleMute(friend.UserId) },
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FriendPill(
                                        label = Strings.get(if (confirming) "friends_remove_confirm" else "friends_remove"),
                                        danger = true,
                                        onClick = {
                                            if (confirming) {
                                                confirmRemove = null
                                                viewModel.remove(friend.UserId)
                                            } else {
                                                confirmRemove = friend.UserId
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                FriendsTab.REQUESTS -> {
                    if (state.incoming.isEmpty() && state.outgoing.isEmpty()) {
                        EmptyLine(Strings.get(if (state.loaded) "friends_requests_empty" else "status_loading"))
                    } else {
                        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                            if (state.incoming.isNotEmpty()) {
                                item(key = "header-in") { SectionLabel(Strings.get("friends_incoming")) }
                                items(state.incoming, key = { "in:" + it.RequestId }) { request ->
                                    PersonRow(
                                        name = request.UserName,
                                        avatarUrl = viewModel.avatarUrl(session, request.UserId, request.PrimaryImageTag),
                                        subtitle = null,
                                        onOpen = {
                                            onDismiss()
                                            onOpenProfile(request.UserId)
                                        },
                                    ) {
                                        FriendPill(label = Strings.get("friends_accept"), onClick = { viewModel.accept(request.RequestId) })
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FriendPill(label = Strings.get("friends_decline"), danger = true, onClick = { viewModel.decline(request.RequestId) })
                                    }
                                }
                            }
                            if (state.outgoing.isNotEmpty()) {
                                item(key = "header-out") { SectionLabel(Strings.get("friends_outgoing")) }
                                items(state.outgoing, key = { "out:" + it.RequestId }) { request ->
                                    PersonRow(
                                        name = request.UserName,
                                        avatarUrl = viewModel.avatarUrl(session, request.UserId, request.PrimaryImageTag),
                                        subtitle = null,
                                        onOpen = null,
                                    ) {
                                        FriendPill(label = Strings.get("friends_cancel_request"), onClick = { viewModel.cancel(request.RequestId) })
                                    }
                                }
                            }
                        }
                    }
                }
                FriendsTab.FIND -> {
                    val people = state.people
                    if (people == null) {
                        EmptyLine(Strings.get("status_loading"))
                    } else if (people.isEmpty()) {
                        EmptyLine(Strings.get("friends_people_empty"))
                    } else {
                        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                            items(people, key = { it.UserId }) { person ->
                                PersonRow(
                                    name = person.UserName,
                                    avatarUrl = viewModel.avatarUrl(session, person.UserId, person.PrimaryImageTag),
                                    subtitle = null,
                                    onOpen = {
                                        onDismiss()
                                        onOpenProfile(person.UserId)
                                    },
                                ) {
                                    if (relationOf(person.Status) == FriendRelation.Outgoing) {
                                        FriendPill(label = Strings.get("friends_request_sent"), enabled = false, onClick = {})
                                    } else {
                                        FriendPill(label = Strings.get("friends_add"), onClick = { viewModel.sendRequest(person.UserId) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(label: String, count: Int, selected: Boolean, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onFocus,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
            contentColor = JellioText,
            focusedContainerColor = Color.White,
            focusedContentColor = JellioBg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(20.dp).background(JellioDanger, CircleShape),
                ) {
                    Text(text = count.toString(), color = JellioBg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = JellioTextSecondary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        color = JellioTextSecondary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
    )
}

@Composable
private fun PersonRow(
    name: String,
    avatarUrl: String?,
    subtitle: String?,
    onOpen: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val who: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                FriendAvatar(url = avatarUrl, name = name, size = 40.dp)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = name, color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(text = subtitle, color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (onOpen != null) {
            Surface(
                onClick = onOpen,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.18f),
                    focusedContentColor = JellioText,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.weight(1f),
            ) { who() }
        } else {
            Box(modifier = Modifier.weight(1f)) { who() }
        }
        Spacer(modifier = Modifier.width(8.dp))
        actions()
    }
}
