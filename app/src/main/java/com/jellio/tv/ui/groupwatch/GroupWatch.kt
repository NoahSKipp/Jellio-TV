package com.jellio.tv.ui.groupwatch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
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

// The quick replies the TV can send: no keyboard, no chat window. Each
// one answers the latest message from someone else.
val GROUP_REACTIONS = com.jellio.tv.data.syncplay.PARTY_REACTIONS

// 41:40, or 1:02:05 past an hour.
fun partyClock(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val sec = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

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

    suspend fun runWhileStarted(session: Session, isPlaying: (String) -> Boolean, openItem: (String) -> Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { syncPlay.runWhileStarted(session) }
            party.openItem = openItem
            launch { party.runWhileStarted(session.userId) }
            launch {
                party.events.collect { event ->
                    when (event) {
                        is com.jellio.tv.data.syncplay.PartyEvent.AskedHost ->
                            notify(com.jellio.tv.ui.i18n.Strings.get("party_title"), com.jellio.tv.ui.i18n.Strings.format("party_asked_host", event.hostName ?: com.jellio.tv.ui.i18n.Strings.get("party_host")))
                        is com.jellio.tv.data.syncplay.PartyEvent.HostRequest -> hostRequestPrompt(event.request)
                    }
                }
            }
            launch {
                syncPlay.events.collect { event ->
                    when (event) {
                        is GroupEvent.Presence -> notify("Group Watch", event.name + if (event.joined) " joined the group" else " left the group")
                        is GroupEvent.Started -> if (!isPlaying(event.target.itemId)) {
                            val name = itemName(session, event.target.itemId)
                            _prompt.value = GroupPrompt(
                                title = com.jellio.tv.ui.i18n.Strings.get("nav_group_watch"),
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
            _invites.update { list -> (listOf(invite) + list.filterNot { it.GroupId == invite.GroupId }).take(10) }
            _prompt.value = GroupPrompt(
                title = com.jellio.tv.ui.i18n.Strings.get("groupwatch_invite_title"),
                text = (invite.FromUserName ?: "Someone") + " invited you to join " + (invite.GroupName ?: "Group Watch"),
                actionLabel = "Join",
                onAction = { acceptInvite(invite) },
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
                            .forEach {
                                party.noteIncoming(it)
                                notify(it.UserName ?: "Someone", it.Text + replySuffix(it.ReplyToName))
                            }
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

    private fun replySuffix(name: String?) = if (name.isNullOrEmpty()) "" else "  ↪ $name"

    fun react(emoji: String) {
        val group = syncPlay.group.value ?: return
        val replyTo = party.replyTarget()
        viewModelScope.launch {
            runCatching { api.sendGroupWatchMessage(group.groupId, GroupWatchSendBody(emoji, ReplyToId = replyTo?.Id)) }
            notify(com.jellio.tv.ui.i18n.Strings.get("party_you"), emoji + replySuffix(replyTo?.UserName))
        }
    }

    // Host-only party: a guest asks the host, who gets Do it / Not now.
    private fun hostRequestPrompt(request: com.jellio.tv.data.model.PartyRequestDto) {
        val name = request.UserName ?: "Someone"
        val text = when {
            request.Kind == "seek" && request.PositionTicks != null ->
                com.jellio.tv.ui.i18n.Strings.format("party_asks_seek", name, partyClock(request.PositionTicks / 10_000_000L))
            request.Kind == "pause" -> com.jellio.tv.ui.i18n.Strings.format("party_asks_pause", name)
            else -> com.jellio.tv.ui.i18n.Strings.format("party_asks_play", name)
        }
        _prompt.value = GroupPrompt(
            title = com.jellio.tv.ui.i18n.Strings.get("party_title"),
            text = text,
            actionLabel = com.jellio.tv.ui.i18n.Strings.get("party_do_it"),
            onAction = {
                when (request.Kind) {
                    "pause" -> syncPlay.requestPause()
                    "play" -> syncPlay.requestUnpause()
                    else -> request.PositionTicks?.let { syncPlay.requestSeek(it) }
                }
            },
        )
    }

    suspend fun loadParties(): com.jellio.tv.data.model.PartiesDto? = runCatching { api.getParties() }.getOrNull()

    fun setupParty(private: Boolean? = null, hostOnly: Boolean? = null) {
        val group = syncPlay.group.value ?: return
        viewModelScope.launch {
            runCatching { api.setupParty(group.groupId, com.jellio.tv.data.model.PartySetupBody(Private = private, HostOnly = hostOnly)) }
        }
    }

    fun makeHost(userId: String) {
        val group = syncPlay.group.value ?: return
        viewModelScope.launch { runCatching { api.transferPartyHost(group.groupId, com.jellio.tv.data.model.PartyHostBody(userId)) } }
    }

    suspend fun rsvp(id: String, going: Boolean) {
        runCatching { api.rsvpParty(id, com.jellio.tv.data.model.PartyRsvpBody(going)) }
    }

    // A scheduled party's Start: a new group under the party's name with
    // its settings, then its title for everyone.
    fun startScheduled(scheduled: com.jellio.tv.data.model.ScheduledPartyDto, fallbackName: String) {
        val name = scheduled.Name?.takeIf { it.isNotBlank() } ?: fallbackName
        viewModelScope.launch {
            if (syncPlay.group.value == null) {
                syncPlay.noteCreating(name)
                runCatching { api.syncPlayNew(com.jellio.tv.data.model.SyncPlayNewBody(name)) }
            }
            var group = syncPlay.group.value
            repeat(40) {
                if (group != null) return@repeat
                delay(250)
                group = syncPlay.group.value
            }
            val joined = group ?: return@launch
            runCatching { api.getPartyState(joined.groupId, 0) }
            runCatching {
                api.setupParty(joined.groupId, com.jellio.tv.data.model.PartySetupBody(scheduled.Private, scheduled.HostOnly, scheduled.Id))
            }
            runCatching { api.markScheduledPartyStarted(scheduled.Id, com.jellio.tv.data.model.PartyStartedBody(joined.groupId)) }
            // A whole series leaves the episode to pick; anything else plays.
            scheduled.Item?.takeIf { it.Type != "Series" }?.let { party.openItem?.invoke(it.Id) }
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

    suspend fun acceptInvite(invite: com.jellio.tv.data.model.GroupWatchInviteDto) {
        _invites.update { list -> list.filterNot { it.Id == invite.Id } }
        runCatching { syncPlay.join(invite.GroupId) }
    }

    fun joinInvite(invite: com.jellio.tv.data.model.GroupWatchInviteDto) {
        viewModelScope.launch { acceptInvite(invite) }
    }

    fun dismissInvite(invite: com.jellio.tv.data.model.GroupWatchInviteDto) {
        _invites.update { list -> list.filterNot { it.Id == invite.Id } }
    }

    fun join(groupId: String) {
        viewModelScope.launch { runCatching { syncPlay.join(groupId) } }
    }

    fun leave() {
        viewModelScope.launch { syncPlay.leave() }
    }

    fun create(name: String) {
        syncPlay.noteCreating(name)
        viewModelScope.launch { runCatching { api.syncPlayNew(com.jellio.tv.data.model.SyncPlayNewBody(name)) } }
    }

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
            GroupButton(label = com.jellio.tv.ui.i18n.Strings.get("action_not_now"), onClick = { onAnswer(false) })
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
            text = if (center.secondsLeft > 0) center.secondsLeft.toString() else com.jellio.tv.ui.i18n.Strings.get("party_go"),
            color = JellioText,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 120.sp,
                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 30f),
            ),
            modifier = modifier,
        )
        is com.jellio.tv.data.syncplay.PartyCenter.ReadyCheck -> Text(
            text = com.jellio.tv.ui.i18n.Strings.format("party_ready_check", center.ready, center.total),
            color = JellioText,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

private fun partyStateLabel(state: String?) = com.jellio.tv.ui.i18n.Strings.get(
    when (state) {
        "Playing" -> "party_state_playing"
        "Waiting" -> "party_state_waiting"
        "Paused" -> "party_state_paused"
        else -> "party_state_idle"
    },
)

private fun partyItemTitle(item: com.jellio.tv.data.model.PartyItemDto?): String? {
    item ?: return null
    if (item.Type == "Episode" && item.SeriesName != null) {
        val code = if (item.SeasonNumber != null && item.EpisodeNumber != null) " S${item.SeasonNumber}E${item.EpisodeNumber}" else ""
        return item.SeriesName + code
    }
    return item.Name
}

private fun partyWhen(startsAt: String?): String {
    val at = com.jellio.tv.data.syncplay.PartyManager.parseTime(startsAt) ?: return ""
    val clock = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(at))
    val minutes = (at - System.currentTimeMillis()) / 60_000L
    val relative = when {
        minutes <= 0 -> com.jellio.tv.ui.i18n.Strings.get("party_due_now")
        minutes < 60 -> com.jellio.tv.ui.i18n.Strings.format("party_in_minutes", minutes)
        else -> com.jellio.tv.ui.i18n.Strings.format("party_in_hours", (minutes + 30) / 60)
    }
    return "$clock · $relative"
}

// One party: its title's art, what it's watching and how far in, who's
// there and who hosts.
@Composable
private fun PartyCard(
    title: String,
    item: com.jellio.tv.data.model.PartyItemDto?,
    lines: List<String>,
    session: Session?,
    progress: Float? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(10.dp),
    ) {
        val art = item?.ImageItemId?.takeIf { it.isNotEmpty() }
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (art != null && session != null) {
                coil3.compose.AsyncImage(
                    model = com.jellio.tv.di.normalizeServerAddress(session.serverAddress) + "/Items/$art/Images/Primary?maxWidth=160&quality=85&ApiKey=" + session.accessToken,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = JellioText, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            lines.filter { it.isNotBlank() }.forEach {
                Text(text = it, color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(JellioText),
                    )
                }
            }
        }
        actions()
    }
}

private fun liveLines(party: com.jellio.tv.data.model.LivePartyDto): List<String> {
    val title = partyItemTitle(party.Item)
    val position = party.PositionTicks?.let { partyClock(it / 10_000_000L) }
    val status = listOfNotNull(title, partyStateLabel(party.State), position).joinToString(" · ")
    val people = party.Participants.joinToString(", ") { it.Name }
    val host = party.HostName?.let { com.jellio.tv.ui.i18n.Strings.format("party_hosted_by", it) }
    val flags = listOfNotNull(
        com.jellio.tv.ui.i18n.Strings.get("party_private").takeIf { party.Private },
        com.jellio.tv.ui.i18n.Strings.get("party_host_only_short").takeIf { party.HostOnly },
    ).joinToString(" · ")
    return listOfNotNull(status, people, listOfNotNull(host, flags.ifEmpty { null }).joinToString(" · "))
}

private fun liveProgress(party: com.jellio.tv.data.model.LivePartyDto): Float? {
    val runtime = party.Item?.RunTimeTicks ?: return null
    val position = party.PositionTicks ?: return null
    if (runtime <= 0) return null
    return position.toFloat() / runtime.toFloat()
}

private fun onOff(on: Boolean) = com.jellio.tv.ui.i18n.Strings.get(if (on) "party_on" else "party_off")

// Settings' Group Watch section: the party this TV is in (its title,
// people, code and, for the host, its settings), or the parties to join
// and the ones planned. Typing, scheduling and the full chat stay on the
// web; the TV answers with the emoji roster in the player.
@Composable
fun GroupWatchPanelContent(userName: String, session: Session? = null, viewModel: GroupWatchViewModel = hiltViewModel()) {
    val group by viewModel.syncPlay.group.collectAsState()
    val invites by viewModel.invites.collectAsState()
    val partyState by viewModel.party.state.collectAsState()
    var groups by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.jellio.tv.data.model.SyncPlayGroupDto>>(emptyList()) }
    var parties by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.jellio.tv.data.model.PartiesDto?>(null) }
    var refreshKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(group?.groupId, refreshKey) {
        while (isActive) {
            val fresh = viewModel.loadParties()
            parties = fresh
            if (fresh == null) groups = viewModel.listGroups()
            delay(5_000)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val current = group
        if (current == null) {
            invites.forEach { invite ->
                InviteRow(invite = invite, onJoin = { viewModel.joinInvite(invite) }, onDismiss = { viewModel.dismissInvite(invite) })
            }
        }
        if (current != null) {
            val mine = parties?.Live?.firstOrNull { SyncPlayManager.sameId(it.GroupId, current.groupId) }
            Text(text = com.jellio.tv.ui.i18n.Strings.format("groupwatch_in_group", current.groupName), color = JellioText, style = MaterialTheme.typography.titleSmall)
            if (mine != null) {
                PartyCard(title = partyItemTitle(mine.Item) ?: mine.GroupName.orEmpty(), item = mine.Item, lines = liveLines(mine), session = session, progress = liveProgress(mine))
            } else {
                Text(text = current.participants.joinToString(", ").ifEmpty { "Just you" }, color = JellioTextSecondary)
            }
            val state = partyState
            state?.Code?.let {
                Text(text = com.jellio.tv.ui.i18n.Strings.format("party_code", it), color = JellioText, style = MaterialTheme.typography.titleMedium)
                Text(text = com.jellio.tv.ui.i18n.Strings.get("party_code_hint"), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (state?.IsHost == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GroupButton(
                        label = com.jellio.tv.ui.i18n.Strings.format("party_private_toggle", onOff(state.Private)),
                        onClick = { viewModel.setupParty(private = !state.Private) },
                    )
                    GroupButton(
                        label = com.jellio.tv.ui.i18n.Strings.format("party_host_only_toggle", onOff(state.HostOnly)),
                        onClick = { viewModel.setupParty(hostOnly = !state.HostOnly) },
                    )
                }
                val others = mine?.Participants.orEmpty().filter { it.UserId != null && !SyncPlayManager.sameId(it.UserId, state.HostUserId) }
                if (others.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        others.distinctBy { it.UserId }.take(4).forEach { person ->
                            GroupButton(
                                label = com.jellio.tv.ui.i18n.Strings.format("party_make_host_name", person.Name),
                                onClick = { viewModel.makeHost(person.UserId!!) },
                            )
                        }
                    }
                }
            } else if (state?.HostName != null) {
                Text(
                    text = com.jellio.tv.ui.i18n.Strings.format("party_hosted_by", state.HostName) +
                        if (state.HostOnly) " · " + com.jellio.tv.ui.i18n.Strings.get("party_host_only_short") else "",
                    color = JellioTextSecondary,
                )
            }
            Text(text = com.jellio.tv.ui.i18n.Strings.get("party_tv_hint"), color = JellioTextSecondary)
            GroupButton(label = com.jellio.tv.ui.i18n.string("gw_leave_group"), onClick = { viewModel.leave() })
        } else {
            Text(
                text = "Watch in sync with others on Jellio, Jellio TV or Jellyfin. Invites from other users pop up here too.",
                color = JellioTextSecondary,
            )
            val list = parties
            if (list != null) {
                if (list.Live.isNotEmpty()) {
                    Text(text = com.jellio.tv.ui.i18n.Strings.get("party_live_now"), color = JellioTextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                list.Live.forEach { live ->
                    PartyCard(
                        title = live.GroupName ?: "Group Watch",
                        item = live.Item,
                        lines = liveLines(live),
                        session = session,
                        progress = liveProgress(live),
                    ) {
                        GroupButton(label = com.jellio.tv.ui.i18n.string("gw_join"), primary = true, onClick = { viewModel.join(live.GroupId) })
                    }
                }
                if (list.Scheduled.isNotEmpty()) {
                    Text(text = com.jellio.tv.ui.i18n.Strings.get("party_upcoming"), color = JellioTextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                list.Scheduled.forEach { planned ->
                    val going = if (planned.Going.isEmpty()) "" else com.jellio.tv.ui.i18n.Strings.format("party_going_count", planned.Going.size)
                    PartyCard(
                        title = planned.Name ?: "Watch party",
                        item = planned.Item,
                        lines = listOf(
                            partyWhen(planned.StartsAt),
                            listOfNotNull(partyItemTitle(planned.Item), planned.HostName?.let { com.jellio.tv.ui.i18n.Strings.format("party_hosted_by", it) }).joinToString(" · "),
                            going,
                        ),
                        session = session,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (planned.GroupId != null) {
                                GroupButton(label = com.jellio.tv.ui.i18n.string("gw_join"), primary = true, onClick = { viewModel.join(planned.GroupId) })
                            } else if (planned.CanStart) {
                                GroupButton(
                                    label = com.jellio.tv.ui.i18n.Strings.get("party_start_now"),
                                    primary = true,
                                    onClick = { viewModel.startScheduled(planned, "$userName's group") },
                                )
                            }
                            if (!planned.IsHost) {
                                GroupButton(
                                    label = com.jellio.tv.ui.i18n.Strings.get(if (planned.IsGoing) "party_going" else "party_im_in"),
                                    onClick = {
                                        scope.launch {
                                            viewModel.rsvp(planned.Id, !planned.IsGoing)
                                            refreshKey++
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                groups.forEach { candidate ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(text = candidate.GroupName ?: "Group Watch", color = JellioText)
                            Text(text = candidate.Participants.joinToString(", "), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        GroupButton(label = com.jellio.tv.ui.i18n.string("gw_join"), primary = true, onClick = { viewModel.join(candidate.GroupId) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GroupButton(
                    label = com.jellio.tv.ui.i18n.Strings.get("gw_start_group"),
                    onClick = {
                        viewModel.create("$userName's group")
                        refreshKey++
                    },
                )
                GroupButton(label = com.jellio.tv.ui.i18n.string("gw_refresh"), onClick = { refreshKey++ })
            }
        }
    }
}

@Composable
fun InviteRow(invite: com.jellio.tv.data.model.GroupWatchInviteDto, onJoin: () -> Unit, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(text = (invite.FromUserName ?: "Someone") + " invited you", color = JellioText)
            Text(text = invite.GroupName ?: "Group Watch", color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
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

@Composable
fun GroupWatchOverlay(userName: String, onDismiss: () -> Unit, session: Session? = null) {
    SidePanelOverlay(title = com.jellio.tv.ui.i18n.string("nav_group_watch"), onDismiss = onDismiss) {
        GroupWatchPanelContent(userName = userName, session = session)
    }
}
