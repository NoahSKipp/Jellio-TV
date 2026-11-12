package com.jellio.tv.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.data.i18n.displayLocale
import com.jellio.tv.data.model.FriendEntryDto
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.nowplaying.DrawerActionButton
import com.jellio.tv.ui.nowplaying.QuickHubHighlight
import com.jellio.tv.ui.nowplaying.rememberQuickHubRowFocus
import com.jellio.tv.ui.theme.JellioDanger
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// The Friends tab of the Quick Hub: browse friends, answer requests, add
// people. The profile panel stays the full manager (mute, remove).
@Composable
fun QuickHubFriends(
    session: Session,
    sessions: List<NowPlayingSessionDto>,
    viewModel: FriendsViewModel,
    adding: Boolean,
    onAddingChange: (Boolean) -> Unit,
    onOpenFriend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val addFocus = remember { FocusRequester() }
    val requestRows = rememberQuickHubRowFocus()
    val incomingIds = state.incoming.map { it.RequestId }
    val listState = rememberLazyListState()

    val watching = remember(sessions) {
        watchingByUser(sessions.map { it.UserId to it.Item?.let { item -> watchingTitle(item.Type, item.Name, item.SeriesName) } })
    }
    val locale = displayLocale()
    val monthPattern = remember(locale) { android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMM") }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(adding) { if (adding) viewModel.loadPeople("") }
    LaunchedEffect(state.messageKey) {
        if (state.messageKey != null) {
            delay(4000)
            viewModel.clearMessage()
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.clearMessage() } }

    Column(modifier = modifier.fillMaxSize()) {
        // The header and its action stay in place in every mode, so the pressed
        // button never leaves the screen: "Add friend" turns into "Back".
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
            Text(
                text = when {
                    adding -> Strings.get("friends_tab_find")
                    state.loaded -> Strings.format("friends_section_title", state.friends.size)
                    else -> Strings.get("friends_title")
                },
                color = JellioText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FriendPill(
                label = Strings.get(if (adding) "nav_back" else "friends_add"),
                icon = if (adding) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.PersonAdd,
                onClick = { onAddingChange(!adding) },
                modifier = Modifier.focusRequester(addFocus),
            )
            if (!adding && state.incoming.isNotEmpty()) {
                FriendPill(
                    label = Strings.format("friends_requests_count", state.incoming.size),
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                            state.incoming.firstOrNull()?.let { runCatching { requestRows.requesterFor(it.RequestId).requestFocus() } }
                        }
                    },
                )
            }
        }
        state.messageKey?.let { key ->
            Text(
                text = Strings.get(key),
                color = JellioDanger,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (adding) {
            AddFriendList(
                session = session,
                viewModel = viewModel,
                anchorFocus = addFocus,
            )
        } else if (state.loaded && state.friends.isEmpty() && state.incoming.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Filled.People,
                    contentDescription = null,
                    tint = JellioTextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = Strings.get("friends_empty"),
                    color = JellioTextSecondary,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.incoming.isNotEmpty()) {
                    item(key = "header-in") { SectionLabel(Strings.get("friends_incoming")) }
                    items(state.incoming, key = { "in:" + it.RequestId }) { request ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                        ) {
                            FriendAvatar(
                                url = viewModel.avatarUrl(session, request.UserId, request.PrimaryImageTag),
                                name = request.UserName,
                                size = 40.dp,
                            )
                            Text(
                                text = request.UserName,
                                color = JellioText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            )
                            DrawerActionButton(
                                label = Strings.get("friends_accept"),
                                primary = true,
                                onClick = {
                                    requestRows.moveAfterRemoval(incomingIds, request.RequestId, addFocus)
                                    viewModel.accept(request.RequestId)
                                },
                                modifier = requestRows.modifierFor(request.RequestId),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DrawerActionButton(
                                label = Strings.get("friends_decline"),
                                primary = false,
                                onClick = {
                                    requestRows.moveAfterRemoval(incomingIds, request.RequestId, addFocus)
                                    viewModel.decline(request.RequestId)
                                },
                            )
                        }
                    }
                    item(key = "header-friends") { SectionLabel(Strings.get("friends_tab_friends")) }
                }
                if (state.friends.isEmpty()) {
                    item(key = "friends-none") {
                        Text(
                            text = Strings.get(if (state.loaded) "friends_empty" else "status_loading"),
                            color = JellioTextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                items(state.friends, key = { "f:" + it.UserId }) { friend ->
                    FriendRow(
                        friend = friend,
                        avatarUrl = viewModel.avatarUrl(session, friend.UserId, friend.PrimaryImageTag),
                        watching = watching[friendIdKey(friend.UserId)],
                        monthPattern = monthPattern,
                        onClick = { onOpenFriend(friend.UserId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRow(
    friend: FriendEntryDto,
    avatarUrl: String?,
    watching: String?,
    monthPattern: String,
    onClick: () -> Unit,
) {
    val locale = displayLocale()
    val since = remember(friend.Since, monthPattern, locale) { formatMonthYear(friend.Since, monthPattern, locale) }
    val subtitle = when (val value = friendSubtitle(watching, since)) {
        is FriendSubtitle.Watching -> Strings.format("friends_watching_now", value.title)
        is FriendSubtitle.Since -> Strings.format("friends_since", value.label)
        FriendSubtitle.Empty -> null
    }
    QuickRow(name = friend.UserName, avatarUrl = avatarUrl, subtitle = subtitle, onClick = onClick) {
        if (friend.Muted) {
            Icon(
                imageVector = Icons.Filled.NotificationsOff,
                contentDescription = Strings.get("friends_muted"),
                tint = JellioTextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// Find people, in place of the friends list. No search box: the server's list.
@Composable
private fun AddFriendList(
    session: Session,
    viewModel: FriendsViewModel,
    anchorFocus: FocusRequester,
) {
    val state by viewModel.state.collectAsState()
    val people = state.people?.filter { isFindable(it.Status) }
    val personRows = rememberQuickHubRowFocus()
    val personIds = people?.map { it.UserId }.orEmpty()

    when {
        people == null -> Text(text = Strings.get("status_loading"), color = JellioTextSecondary, modifier = Modifier.padding(vertical = 8.dp))
        people.isEmpty() -> Text(text = Strings.get("friends_people_empty"), color = JellioTextSecondary, modifier = Modifier.padding(vertical = 8.dp))
        else -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(people, key = { "p:" + it.UserId }) { person ->
                val sent = relationOf(person.Status) == FriendRelation.Outgoing
                QuickRow(
                    name = person.UserName,
                    avatarUrl = viewModel.avatarUrl(session, person.UserId, person.PrimaryImageTag),
                    subtitle = null,
                    onClick = {
                        if (!sent) {
                            // A pending request from this person becomes a friendship and the row goes away.
                            if (relationOf(person.Status) == FriendRelation.Incoming) {
                                personRows.moveAfterRemoval(personIds, person.UserId, anchorFocus)
                            }
                            viewModel.sendRequest(person.UserId)
                        }
                    },
                    modifier = personRows.modifierFor(person.UserId),
                ) {
                    // One row stays focused through "Add friend" to "Request sent".
                    Icon(
                        imageVector = if (sent) Icons.Filled.Check else Icons.Filled.PersonAdd,
                        contentDescription = null,
                        tint = if (sent) JellioTextSecondary else JellioSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Strings.get(if (sent) "friends_request_sent" else "friends_add"),
                        color = if (sent) JellioTextSecondary else JellioSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickRow(
    name: String,
    avatarUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = QuickHubHighlight.RowFocusScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = QuickHubHighlight.focusBorder(shape),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            FriendAvatar(url = avatarUrl, name = name, size = 40.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(text = name, color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = JellioTextSecondary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
    )
}
