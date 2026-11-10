package com.jellio.tv.ui.groupwatch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
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
import com.jellio.tv.data.catching
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
private const val INVITE_NOTICE_MS = 12_000L

// The quick replies the TV can send: no keyboard, no chat window. Each
// one answers the latest message from someone else.
val GROUP_REACTIONS = com.jellio.tv.data.syncplay.PARTY_REACTIONS

// A passing pop-up (a chat message, someone joining): never takes focus.
data class GroupNotice(val id: Long, val title: String, val text: String)

// One that asks something (an invite, the group starting a title): takes
// focus with Join and Not now, like the update prompt.
data class GroupPrompt(val title: String, val text: String, val actionLabel: String, val onAction: suspend () -> Unit)

@HiltViewModel
class GroupWatchViewModel @Inject constructor(
    val syncPlay: SyncPlayManager,
    val party: com.jellio.tv.data.syncplay.PartyManager,
    private val api: JellyfinApi,
) : ViewModel() {
    private val _notices = MutableStateFlow<List<GroupNotice>>(emptyList())
    val notices: StateFlow<List<GroupNotice>> = _notices.asStateFlow()

    private val _prompt = MutableStateFlow<GroupPrompt?>(null)
    val prompt: StateFlow<GroupPrompt?> = _prompt.asStateFlow()

    // Invites not yet answered, newest first, for the Group Watch and
    // Notifications panels.
    private val _invites = MutableStateFlow<List<com.jellio.tv.data.model.GroupWatchInviteDto>>(emptyList())
    val invites: StateFlow<List<com.jellio.tv.data.model.GroupWatchInviteDto>> = _invites.asStateFlow()

    private var noticeId = 0L
    private var lastInviteId = 0L
    private var startedForUserId: String? = null

    suspend fun runWhileStarted(session: Session, isPlaying: (String) -> Boolean, openItem: (String) -> Unit) {
        if (startedForUserId != null && !SyncPlayManager.sameId(startedForUserId, session.userId)) {
            // The last account's invites and questions aren't this one's.
            _invites.value = emptyList()
            _prompt.value = null
        }
        startedForUserId = session.userId
        kotlinx.coroutines.coroutineScope {
            launch { syncPlay.runWhileStarted(session) }
            party.openItem = openItem
            launch { party.runWhileStarted(session.userId) }
            launch {
                party.events.collect { event ->
                    when (event) {
                        is com.jellio.tv.data.syncplay.PartyEvent.AskedHost ->
                            notify(com.jellio.tv.data.i18n.Strings.get("party_title"), com.jellio.tv.data.i18n.Strings.format("party_asked_host", event.hostName ?: com.jellio.tv.data.i18n.Strings.get("party_host")))
                        is com.jellio.tv.data.syncplay.PartyEvent.HostRequest -> hostRequestPrompt(event.request)
                    }
                }
            }
            launch {
                syncPlay.events.collect { event ->
                    when (event) {
                        is GroupEvent.Presence -> notify(com.jellio.tv.data.i18n.Strings.get("nav_group_watch"), com.jellio.tv.data.i18n.Strings.format(if (event.joined) "tv_group_joined" else "tv_group_left", event.name))
                        is GroupEvent.Started -> if (!isPlaying(event.target.itemId)) {
                            val name = itemName(session, event.target.itemId)
                            _prompt.value = GroupPrompt(
                                title = com.jellio.tv.data.i18n.Strings.get("nav_group_watch"),
                                text = com.jellio.tv.data.i18n.Strings.format("tv_group_started", name ?: com.jellio.tv.data.i18n.Strings.get("tv_something")),
                                actionLabel = com.jellio.tv.data.i18n.Strings.get("tv_join"),
                                onAction = { openItem(event.target.itemId) },
                            )
                        }
                        is GroupEvent.Denied -> notify(
                            com.jellio.tv.data.i18n.Strings.get("nav_group_watch"),
                            com.jellio.tv.data.i18n.Strings.get(
                                when {
                                    event.reason == "CreateGroupDenied" -> "tv_group_denied_create"
                                    event.reason == "JoinGroupDenied" -> "tv_group_denied_join"
                                    event.reason == "GroupDoesNotExist" -> "tv_group_ended"
                                    event.inGroup -> "tv_group_denied_library"
                                    else -> "tv_group_denied_library_join"
                                },
                            ),
                        )
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
        val invites = catching { api.getGroupWatchInvites(lastInviteId) }.getOrNull() ?: return
        invites.forEach { invite ->
            lastInviteId = maxOf(lastInviteId, invite.Id)
            _invites.update { list -> (listOf(invite) + list.filterNot { it.GroupId == invite.GroupId }).take(10) }
            // A toast, as on the web, that doesn't take the remote away from
            // whatever is on screen; Join waits in Group Watch and Notifications.
            notify(
                com.jellio.tv.data.i18n.Strings.get("groupwatch_invite_title"),
                com.jellio.tv.data.i18n.Strings.format("party_invite_toast", invite.FromUserName ?: com.jellio.tv.data.i18n.Strings.get("tv_someone"), invite.GroupName ?: com.jellio.tv.data.i18n.Strings.get("tv_group_watch")),
                INVITE_NOTICE_MS,
            )
        }
    }

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
                val messages = catching { api.getGroupWatchMessages(group.groupId, maxOf(after, 0L)) }.getOrNull()
                if (messages != null) {
                    if (after >= 0) {
                        messages.filter { it.ItemId == null && !SyncPlayManager.sameId(it.UserId, session.userId) }
                            .forEach {
                                party.noteIncoming(it)
                                notify(it.UserName ?: com.jellio.tv.data.i18n.Strings.get("tv_someone"), it.Text + replySuffix(it.ReplyToName))
                            }
                    }
                    after = maxOf(after, messages.maxOfOrNull { it.Id } ?: 0L)
                }
            }
            delay(CHAT_POLL_MS)
        }
    }

    private suspend fun itemName(session: Session, itemId: String): String? = catching {
        val item = api.getItem(session.userId, itemId)
        if (item.Type == "Episode" && item.SeriesName != null) item.SeriesName else item.Name
    }.getOrNull()

    private fun notify(title: String, text: String, durationMs: Long = NOTICE_MS) {
        val id = ++noticeId
        _notices.update { (it + GroupNotice(id, title, text)).takeLast(4) }
        viewModelScope.launch {
            delay(durationMs)
            _notices.update { list -> list.filterNot { it.id == id } }
        }
    }

    private fun replySuffix(name: String?) = if (name.isNullOrEmpty()) "" else "  ↪ $name"

    fun react(emoji: String) {
        val group = syncPlay.group.value ?: return
        val replyTo = party.replyTarget()
        viewModelScope.launch {
            catching { api.sendGroupWatchMessage(group.groupId, GroupWatchSendBody(emoji, ReplyToId = replyTo?.Id)) }
            notify(com.jellio.tv.data.i18n.Strings.get("party_you"), emoji + replySuffix(replyTo?.UserName))
        }
    }

    // Host-only party: a guest asks the host, who gets Do it / Not now.
    private fun hostRequestPrompt(request: com.jellio.tv.data.model.PartyRequestDto) {
        val name = request.UserName ?: com.jellio.tv.data.i18n.Strings.get("tv_someone")
        val text = when {
            request.Kind == "seek" && request.PositionTicks != null ->
                com.jellio.tv.data.i18n.Strings.format("party_asks_seek", name, com.jellio.tv.ui.common.formatPlaybackTime(request.PositionTicks / 10_000L))
            request.Kind == "pause" -> com.jellio.tv.data.i18n.Strings.format("party_asks_pause", name)
            else -> com.jellio.tv.data.i18n.Strings.format("party_asks_play", name)
        }
        _prompt.value = GroupPrompt(
            title = com.jellio.tv.data.i18n.Strings.get("party_title"),
            text = text,
            actionLabel = com.jellio.tv.data.i18n.Strings.get("party_do_it"),
            onAction = {
                when (request.Kind) {
                    "pause" -> syncPlay.requestPause()
                    "play" -> syncPlay.requestUnpause()
                    else -> request.PositionTicks?.let { syncPlay.requestSeek(it) }
                }
            },
        )
    }

    suspend fun loadParties(): com.jellio.tv.data.model.PartiesDto? = catching { api.getParties() }.getOrNull()

    fun setupParty(private: Boolean? = null, hostOnly: Boolean? = null) {
        val group = syncPlay.group.value ?: return
        viewModelScope.launch {
            catching { api.setupParty(group.groupId, com.jellio.tv.data.model.PartySetupBody(Private = private, HostOnly = hostOnly)) }
        }
    }

    fun makeHost(userId: String) {
        val group = syncPlay.group.value ?: return
        viewModelScope.launch { catching { api.transferPartyHost(group.groupId, com.jellio.tv.data.model.PartyHostBody(userId)) } }
    }

    suspend fun rsvp(id: String, going: Boolean) {
        catching { api.rsvpParty(id, com.jellio.tv.data.model.PartyRsvpBody(going)) }
    }

    // A scheduled party's Start: a new group under the party's name with
    // its settings, then its title for everyone.
    fun startScheduled(scheduled: com.jellio.tv.data.model.ScheduledPartyDto, fallbackName: String) {
        val name = scheduled.Name?.takeIf { it.isNotBlank() } ?: fallbackName
        viewModelScope.launch {
            val joined = syncPlay.group.value ?: syncPlay.create(name)
            if (joined == null) {
                notify(com.jellio.tv.data.i18n.Strings.get("party_title"), com.jellio.tv.data.i18n.Strings.get("party_create_failed"))
                return@launch
            }
            catching { api.getPartyState(joined.groupId, 0) }
            catching {
                api.setupParty(joined.groupId, com.jellio.tv.data.model.PartySetupBody(scheduled.Private, scheduled.HostOnly, scheduled.Id))
            }
            catching { api.markScheduledPartyStarted(scheduled.Id, com.jellio.tv.data.model.PartyStartedBody(joined.groupId)) }
            // A whole series leaves the episode to pick; anything else plays.
            scheduled.Item?.takeIf { it.Type != "Series" }?.let { party.openItem?.invoke(it.Id) }
        }
    }

    fun announceStarted(itemId: String, name: String) {
        val group = syncPlay.group.value ?: return
        viewModelScope.launch {
            catching { api.sendGroupWatchMessage(group.groupId, GroupWatchSendBody(com.jellio.tv.data.i18n.Strings.format("gw_group_started_watching", name), itemId)) }
        }
    }

    fun answerPrompt(accept: Boolean) {
        val prompt = _prompt.value ?: return
        _prompt.value = null
        if (accept) viewModelScope.launch { prompt.onAction() }
    }

    suspend fun acceptInvite(invite: com.jellio.tv.data.model.GroupWatchInviteDto) {
        _invites.update { list -> list.filterNot { it.Id == invite.Id } }
        joinOrExplain(invite.GroupId)
    }

    fun joinInvite(invite: com.jellio.tv.data.model.GroupWatchInviteDto) {
        viewModelScope.launch { acceptInvite(invite) }
    }

    fun dismissInvite(invite: com.jellio.tv.data.model.GroupWatchInviteDto) {
        _invites.update { list -> list.filterNot { it.Id == invite.Id } }
    }

    fun join(groupId: String) {
        viewModelScope.launch { joinOrExplain(groupId) }
    }

    // A private party refuses anyone without its code or an invite.
    private suspend fun joinOrExplain(groupId: String): Boolean {
        val result = catching { syncPlay.join(groupId) }
        val error = result.exceptionOrNull()
        if (error is retrofit2.HttpException && error.code() == 403) {
            notify(com.jellio.tv.data.i18n.Strings.get("party_title"), com.jellio.tv.data.i18n.Strings.get("party_join_private"))
        }
        return result.isSuccess
    }

    // Joins the party with this code; returns why not, or null once in.
    suspend fun joinByCode(code: String): String? {
        val clean = code.filter { it.isLetterOrDigit() }.uppercase()
        if (clean.isEmpty()) return com.jellio.tv.data.i18n.Strings.get("party_code_unknown")
        val resolved = catching { api.resolvePartyCode(com.jellio.tv.data.model.PartyCodeBody(clean)) }
        val error = resolved.exceptionOrNull()
        if (error is retrofit2.HttpException && error.code() == 429) return com.jellio.tv.data.i18n.Strings.get("party_code_throttled")
        val groupId = resolved.getOrNull()?.GroupId?.takeIf { it.isNotEmpty() } ?: return com.jellio.tv.data.i18n.Strings.get("party_code_unknown")
        return if (joinOrExplain(groupId)) null else com.jellio.tv.data.i18n.Strings.get("party_code_unknown")
    }

    fun leave() {
        viewModelScope.launch { syncPlay.leave() }
    }

    // Null once the TV is in its new group, otherwise why not.
    suspend fun create(name: String): String? =
        if (syncPlay.create(name) != null) null else com.jellio.tv.data.i18n.Strings.get("party_create_failed")

    suspend fun listGroups() = syncPlay.listGroups()
}

// Mounted once at the app root: runs Group Watch while the app is in
// front and draws its pop-ups over every screen, the player included.
@Composable
fun GroupWatchHost(
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
    val center by viewModel.party.center.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
    center?.let { PartyCenterOverlay(it, Modifier.align(Alignment.Center)) }
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
            GroupButton(label = com.jellio.tv.data.i18n.Strings.get("action_not_now"), onClick = { onAnswer(false) })
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

// The ready check and the 3-2-1 everyone in the party sees.
@Composable
private fun PartyCenterOverlay(center: com.jellio.tv.data.syncplay.PartyCenter, modifier: Modifier = Modifier) {
    when (center) {
        is com.jellio.tv.data.syncplay.PartyCenter.Countdown -> Text(
            text = if (center.secondsLeft > 0) center.secondsLeft.toString() else com.jellio.tv.data.i18n.Strings.get("party_go"),
            color = JellioText,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 120.sp,
                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 30f),
            ),
            modifier = modifier,
        )
        is com.jellio.tv.data.syncplay.PartyCenter.ReadyCheck -> Text(
            text = com.jellio.tv.data.i18n.Strings.format("party_ready_check", center.ready, center.total),
            color = JellioText,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun InviteRow(invite: com.jellio.tv.data.model.GroupWatchInviteDto, onJoin: () -> Unit, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(text = com.jellio.tv.data.i18n.Strings.format("tv_invited_you", invite.FromUserName ?: com.jellio.tv.data.i18n.Strings.get("tv_someone")), color = JellioText)
            Text(text = invite.GroupName ?: com.jellio.tv.data.i18n.Strings.get("tv_group_watch"), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        GroupButton(label = com.jellio.tv.ui.i18n.string("gw_join"), primary = true, onClick = onJoin)
        GroupButton(label = com.jellio.tv.ui.i18n.string("action_dismiss"), onClick = onDismiss)
    }
}

// The panels opened from the profile menu: a centered card over the
// current screen that keeps focus until Back closes it.
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SidePanelOverlay(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            delay(50)
            if (runCatching { panelFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { onExit = { FocusRequester.Cancel } }
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .heightIn(max = 460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(JellioBgElevated)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .focusRequester(panelFocus)
                .focusGroup()
                .verticalScroll(rememberScrollState())
                // Room for a focused button to grow without being cut off.
                .padding(horizontal = 8.dp, vertical = 16.dp),
        ) {
            Text(text = title, color = JellioText, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 20.dp))
            content()
        }
    }
}
