package com.jellio.tv.ui.groupwatch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.GroupWatchSendBody
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.syncplay.GroupEvent
import com.jellio.tv.data.syncplay.SyncPlayManager
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val INVITE_POLL_MS = 5_000L
private const val CHAT_POLL_MS = 3_000L
private const val NOTICE_MS = 6_000L

// The quick replies the TV can send: no keyboard, no chat window.
val GROUP_REACTIONS = listOf("👍", "😂", "😮", "😢", "😍", "🔥", "🍿", "👏")

// A passing pop-up (a chat message, someone joining): never takes focus.
data class GroupNotice(val id: Long, val title: String, val text: String)

// One that asks something (an invite, the group starting a title): takes
// focus with Join and Not now, like the update prompt.
data class GroupPrompt(val title: String, val text: String, val actionLabel: String, val onAction: suspend () -> Unit)

// Group Watch on the TV, kept simple on purpose: it follows the group
// (SyncPlay does the syncing, ui/player/PlayerScreen.kt applies it),
// shows invites and the group's chat as pop-ups, and answers with the
// preset reactions above. Typing, the full chat and the pick-a-title
// vote stay on Jellio for desktop and the web.
@HiltViewModel
class GroupWatchViewModel @Inject constructor(
    val syncPlay: SyncPlayManager,
    private val api: JellyfinApi,
) : ViewModel() {
    private val _notices = MutableStateFlow<List<GroupNotice>>(emptyList())
    val notices: StateFlow<List<GroupNotice>> = _notices.asStateFlow()

    private val _prompt = MutableStateFlow<GroupPrompt?>(null)
    val prompt: StateFlow<GroupPrompt?> = _prompt.asStateFlow()

    private var noticeId = 0L
    private var lastInviteId = 0L

    suspend fun runWhileStarted(session: Session, isPlaying: (String) -> Boolean, openItem: (String) -> Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { syncPlay.runWhileStarted(session) }
            launch {
                syncPlay.events.collect { event ->
                    when (event) {
                        is GroupEvent.Presence -> notify("Group Watch", event.name + if (event.joined) " joined the group" else " left the group")
                        is GroupEvent.Started -> if (!isPlaying(event.target.itemId)) {
                            val name = itemName(session, event.target.itemId)
                            _prompt.value = GroupPrompt(
                                title = "Group Watch",
                                text = "The group started watching " + (name ?: "something"),
                                actionLabel = "Join",
                                onAction = { openItem(event.target.itemId) },
                            )
                        }
                    }
                }
            }
            launch {
                while (isActive) {
                    pollInvites()
                    delay(INVITE_POLL_MS)
                }
            }
            launch { pollChat(session) }
        }
    }

    private suspend fun pollInvites() {
        val invites = runCatching { api.getGroupWatchInvites(lastInviteId) }.getOrNull() ?: return
        invites.forEach { invite ->
            lastInviteId = maxOf(lastInviteId, invite.Id)
            _prompt.value = GroupPrompt(
                title = "Group Watch invite",
                text = (invite.FromUserName ?: "Someone") + " invited you to join " + (invite.GroupName ?: "Group Watch"),
                actionLabel = "Join",
                onAction = { runCatching { syncPlay.join(invite.GroupId) } },
            )
        }
    }

    // Only messages that arrive while this TV is in the group; the history
    // before it joined isn't replayed. Lines with an ItemId are the "started
    // watching" announcements, already covered by the Join prompt.
    private suspend fun pollChat(session: Session) {
        var groupId: String? = null
        var after = -1L
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val group = syncPlay.group.value
            if (group == null) {
                groupId = null
            } else {
                if (!SyncPlayManager.sameId(groupId, group.groupId)) {
                    groupId = group.groupId
                    after = -1L
                }
                val messages = runCatching { api.getGroupWatchMessages(group.groupId, maxOf(after, 0L)) }.getOrNull()
                if (messages != null) {
                    if (after >= 0) {
                        messages.filter { it.ItemId == null && !SyncPlayManager.sameId(it.UserId, session.userId) }
                            .forEach { notify(it.UserName ?: "Someone", it.Text) }
                    }
                    after = maxOf(after, messages.maxOfOrNull { it.Id } ?: 0L)
                }
            }
            delay(CHAT_POLL_MS)
        }
    }

    private suspend fun itemName(session: Session, itemId: String): String? = runCatching {
        val item = api.getItem(session.userId, itemId)
        if (item.Type == "Episode" && item.SeriesName != null) item.SeriesName else item.Name
    }.getOrNull()

    private fun notify(title: String, text: String) {
        val id = ++noticeId
        _notices.update { (it + GroupNotice(id, title, text)).takeLast(4) }
        viewModelScope.launch {
            delay(NOTICE_MS)
            _notices.update { list -> list.filterNot { it.id == id } }
        }
    }

    fun react(emoji: String) {
        val group = syncPlay.group.value ?: return
        viewModelScope.launch {
            runCatching { api.sendGroupWatchMessage(group.groupId, GroupWatchSendBody(emoji)) }
            notify("You", emoji)
        }
    }

    fun announceStarted(itemId: String, name: String) {
        val group = syncPlay.group.value ?: return
        viewModelScope.launch {
            runCatching { api.sendGroupWatchMessage(group.groupId, GroupWatchSendBody("The group started watching $name", itemId)) }
        }
    }

    fun answerPrompt(accept: Boolean) {
        val prompt = _prompt.value ?: return
        _prompt.value = null
        if (accept) viewModelScope.launch { prompt.onAction() }
    }

    fun join(groupId: String) {
        viewModelScope.launch { runCatching { syncPlay.join(groupId) } }
    }

    fun leave() {
        viewModelScope.launch { syncPlay.leave() }
    }

    fun create(name: String) {
        viewModelScope.launch { runCatching { api.syncPlayNew(com.jellio.tv.data.model.SyncPlayNewBody(name)) } }
    }

    suspend fun listGroups() = syncPlay.listGroups()
}

// Mounted once at the app root: runs Group Watch while the app is in
// front and draws its pop-ups over every screen, the player included.
@Composable
fun BoxScope.GroupWatchHost(
    session: Session,
    isPlaying: (String) -> Boolean,
    openItem: (String) -> Unit,
    viewModel: GroupWatchViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, session.userId) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.runWhileStarted(session, isPlaying, openItem)
        }
    }
    val notices by viewModel.notices.collectAsState()
    val prompt by viewModel.prompt.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp),
    ) {
        notices.forEach { notice ->
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(JellioBgElevated.copy(alpha = 0.94f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(text = notice.title, color = JellioTextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(text = notice.text, color = JellioText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    prompt?.let { current ->
        GroupPromptCard(
            prompt = current,
            onAnswer = { viewModel.answerPrompt(it) },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun GroupPromptCard(prompt: GroupPrompt, onAnswer: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(prompt) { runCatching { actionFocus.requestFocus() } }
    BackHandler { onAnswer(false) }
    Column(
        modifier = modifier
            .padding(end = 48.dp, bottom = 40.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(JellioBgElevated.copy(alpha = 0.98f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(22.dp),
    ) {
        Text(text = prompt.title, color = JellioTextSecondary, style = MaterialTheme.typography.labelMedium)
        Text(text = prompt.text, color = JellioText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupButton(label = prompt.actionLabel, primary = true, onClick = { onAnswer(true) }, modifier = Modifier.focusRequester(actionFocus))
            GroupButton(label = "Not now", onClick = { onAnswer(false) })
        }
    }
}

@Composable
fun GroupButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) JellioSecondary else Color.White.copy(alpha = 0.1f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.3f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(999.dp)),
        ),
        modifier = modifier,
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    }
}

// Settings' Group Watch section: the group this TV is in (with Leave),
// or the server's groups to join, plus starting a new one. Inviting
// people and the full chat stay on desktop and the web.
@Composable
fun GroupWatchSettings(userName: String, viewModel: GroupWatchViewModel = hiltViewModel()) {
    val group by viewModel.syncPlay.group.collectAsState()
    var groups by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.jellio.tv.data.model.SyncPlayGroupDto>>(emptyList()) }
    var refreshKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(group?.groupId, refreshKey) {
        groups = viewModel.listGroups()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val current = group
        if (current != null) {
            Text(text = "You're in " + current.groupName, color = JellioText, style = MaterialTheme.typography.titleSmall)
            Text(
                text = current.participants.joinToString(", ").ifEmpty { "Just you" },
                color = JellioTextSecondary,
            )
            Text(
                text = "Whatever the group plays opens here too. Use React in the player to send a quick emoji.",
                color = JellioTextSecondary,
            )
            GroupButton(label = "Leave group", onClick = { viewModel.leave() })
        } else {
            Text(
                text = "Watch in sync with others on Jellio, Jellio TV or Jellyfin. Invites from other users pop up here too.",
                color = JellioTextSecondary,
            )
            groups.forEach { candidate ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(text = candidate.GroupName ?: "Group Watch", color = JellioText)
                        Text(text = candidate.Participants.joinToString(", "), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    GroupButton(label = "Join", primary = true, onClick = { viewModel.join(candidate.GroupId) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GroupButton(
                    label = "Start a group",
                    onClick = {
                        viewModel.create("$userName's group")
                        refreshKey++
                    },
                )
                GroupButton(label = "Refresh", onClick = { refreshKey++ })
            }
        }
    }
}
