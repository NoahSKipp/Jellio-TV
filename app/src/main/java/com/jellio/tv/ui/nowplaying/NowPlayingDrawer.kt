package com.jellio.tv.ui.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.GroupWatchInviteDto
import com.jellio.tv.data.model.JellioNotificationDto
import com.jellio.tv.data.model.LivePartyDto
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.data.model.PartiesDto
import com.jellio.tv.data.model.PartyItemDto
import com.jellio.tv.data.model.ScheduledPartyDto
import com.jellio.tv.data.model.SyncPlayGroupDto
import com.jellio.tv.data.nowplaying.NowPlayingManager
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.syncplay.PartyManager
import com.jellio.tv.data.syncplay.SyncGroup
import com.jellio.tv.data.syncplay.SyncPlayManager
import com.jellio.tv.di.normalizeServerAddress
import com.jellio.tv.ui.common.JellioTextField
import com.jellio.tv.ui.common.dismissOnBack
import com.jellio.tv.ui.common.formatPlaybackTime
import com.jellio.tv.ui.common.formatRelativeTime
import com.jellio.tv.ui.groupwatch.GroupWatchViewModel
import com.jellio.tv.ui.i18n.Strings
import com.jellio.tv.ui.i18n.displayLocale
import com.jellio.tv.ui.notify.NotificationsViewModel
import com.jellio.tv.ui.notify.messageFor
import com.jellio.tv.ui.notify.subtitleFor
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val DrawerWidth = 500.dp

enum class QuickHubTab {
    NOW_PLAYING,
    NOTIFICATIONS,
    GROUP_WATCH,
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NowPlayingDrawer(
    session: Session,
    sessions: List<NowPlayingSessionDto>,
    localPaused: NowPlayingManager.LocalPausedMedia?,
    onDismiss: () -> Unit,
    onHandoff: (NowPlayingSessionDto) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onTuneIn: (String) -> Unit,
    onJoinWatchParty: (NowPlayingSessionDto) -> Unit,
    onResumeLocal: () -> Unit,
    onDismissLocal: () -> Unit,
    rawImageUrl: (String, String?, String, Int) -> String,
    modifier: Modifier = Modifier,
    initialTab: QuickHubTab = QuickHubTab.NOW_PLAYING,
    onOpenLiveTv: () -> Unit = {},
    onOpenGroupWatch: () -> Unit = {},
    onRefreshNowPlaying: () -> Unit = {},
    notificationsViewModel: NotificationsViewModel = hiltViewModel(),
    groupWatchViewModel: GroupWatchViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) { notificationsViewModel.load() }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(4000L)
            onRefreshNowPlaying()
        }
    }

    val notifications by notificationsViewModel.items.collectAsState()
    val invites by groupWatchViewModel.invites.collectAsState()
    val activeGroup by groupWatchViewModel.syncPlay.group.collectAsState()
    val partyState by groupWatchViewModel.party.state.collectAsState()

    var parties by remember { mutableStateOf<PartiesDto?>(null) }
    var groups by remember { mutableStateOf<List<SyncPlayGroupDto>>(emptyList()) }
    var gwRefreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeGroup?.groupId, gwRefreshKey) {
        while (isActive) {
            val fresh = groupWatchViewModel.loadParties()
            parties = fresh
            if (fresh == null) {
                groups = groupWatchViewModel.listGroups()
            }
            delay(5000L)
        }
    }

    var selectedTab by remember { mutableStateOf(initialTab) }

    val nowPlayingCount = sessions.size + (if (localPaused != null) 1 else 0)
    val notificationsCount = invites.size + (notifications?.count { !it.Read } ?: 0)
    val groupWatchBadgeCount = if (activeGroup != null) {
        1
    } else {
        invites.size + (parties?.Live?.size ?: 0)
    }

    val tabNowPlayingFocus = remember { FocusRequester() }
    val tabNotificationsFocus = remember { FocusRequester() }
    val tabGroupWatchFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(25) {
            val focused = runCatching {
                when (initialTab) {
                    QuickHubTab.NOW_PLAYING -> tabNowPlayingFocus.requestFocus()
                    QuickHubTab.NOTIFICATIONS -> tabNotificationsFocus.requestFocus()
                    QuickHubTab.GROUP_WATCH -> tabGroupWatchFocus.requestFocus()
                }
            }.isSuccess
            if (focused) return@LaunchedEffect
            delay(40)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .background(Color.Black.copy(alpha = 0.70f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Left) {
                        onDismiss()
                    }
                    FocusRequester.Cancel
                }
            }
            .focusGroup(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(180)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(DrawerWidth.scaled())
                    .background(Color(0xF2141414))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)))
                    .padding(vertical = 24.dp, horizontal = 22.dp)
                    .focusGroup()
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                // Header Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Widgets,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = Strings.get("quick_hub_title"),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = JellioText,
                            )
                            Text(
                                text = when (selectedTab) {
                                    QuickHubTab.NOW_PLAYING -> when (nowPlayingCount) {
                                        0 -> Strings.get("now_playing_empty")
                                        1 -> Strings.get("now_playing_active_stream")
                                        else -> Strings.format("now_playing_active_streams", nowPlayingCount)
                                    }
                                    QuickHubTab.NOTIFICATIONS -> when (notificationsCount) {
                                        0 -> Strings.get("notifications_nothing_new")
                                        1 -> Strings.get("notifications_unread_one")
                                        else -> Strings.format("notifications_unread_many", notificationsCount)
                                    }
                                    QuickHubTab.GROUP_WATCH -> {
                                        val grp = activeGroup
                                        val liveCount = parties?.Live?.size ?: 0
                                        if (grp != null) {
                                            Strings.format("groupwatch_in_group", grp.groupName)
                                        } else if (liveCount > 0) {
                                            "$liveCount · ${Strings.get("party_live_now")}"
                                        } else {
                                            Strings.get("party_title")
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = JellioTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Close Button
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f, pressedScale = 0.95f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.22f),
                            contentColor = JellioTextSecondary,
                            focusedContentColor = JellioText,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = Strings.get("action_cancel"),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // 3-Tab Switcher Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickHubTabPill(
                        label = Strings.get("quick_hub_tab_now_playing"),
                        icon = Icons.Filled.GraphicEq,
                        selected = selectedTab == QuickHubTab.NOW_PLAYING,
                        badgeCount = nowPlayingCount,
                        badgeColor = Color.White.copy(alpha = 0.22f),
                        onSelect = { selectedTab = QuickHubTab.NOW_PLAYING },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tabNowPlayingFocus),
                    )

                    QuickHubTabPill(
                        label = Strings.get("quick_hub_tab_notifications"),
                        icon = Icons.Filled.Notifications,
                        selected = selectedTab == QuickHubTab.NOTIFICATIONS,
                        badgeCount = notificationsCount,
                        badgeColor = Color(0xFFFF3B30),
                        onSelect = { selectedTab = QuickHubTab.NOTIFICATIONS },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tabNotificationsFocus),
                    )

                    val gwTabLabel = Strings.get("quick_hub_tab_group_watch")
                        .takeIf { it != "quick_hub_tab_group_watch" }
                        ?: Strings.get("nav_group_watch")
                    QuickHubTabPill(
                        label = gwTabLabel,
                        icon = Icons.Filled.Groups,
                        selected = selectedTab == QuickHubTab.GROUP_WATCH,
                        badgeCount = groupWatchBadgeCount,
                        badgeColor = if (activeGroup != null) Color(0xFF22C55E) else Color.White.copy(alpha = 0.22f),
                        onSelect = { selectedTab = QuickHubTab.GROUP_WATCH },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tabGroupWatchFocus),
                    )
                }

                // Drawer Body
                when (selectedTab) {
                    QuickHubTab.NOW_PLAYING -> {
                        val totalCount = sessions.size + (if (localPaused != null) 1 else 0)
                        if (totalCount == 0) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Tv,
                                        contentDescription = null,
                                        tint = JellioTextSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = Strings.get("now_playing_empty"),
                                        color = JellioTextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Surface(
                                        onClick = onDismiss,
                                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f, pressedScale = 0.97f),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            focusedContainerColor = Color.White.copy(alpha = 0.22f),
                                            contentColor = JellioText,
                                            focusedContentColor = Color.White,
                                        ),
                                        border = ClickableSurfaceDefaults.border(
                                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                                        ),
                                    ) {
                                        Text(
                                            text = Strings.get("action_cancel"),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            ) {
                                if (localPaused != null) {
                                    item(key = "local_paused") {
                                        LocalPausedCard(
                                            media = localPaused,
                                            onResume = onResumeLocal,
                                            onDismiss = onDismissLocal,
                                        )
                                    }
                                }

                                items(sessions, key = { it.Id ?: it.hashCode().toString() }) { sess ->
                                    SessionCard(
                                        session = sess,
                                        currentSession = session,
                                        onHandoff = { onHandoff(sess) },
                                        onTuneIn = { channelId -> onTuneIn(channelId) },
                                        onJoinParty = { onJoinWatchParty(sess) },
                                        onDetails = { itemId -> onNavigateToDetail(itemId) },
                                        rawImageUrl = rawImageUrl,
                                    )
                                }
                            }
                        }
                    }

                    QuickHubTab.NOTIFICATIONS -> {
                        val notifList = notifications
                        val isEmpty = invites.isEmpty() && (notifList == null || notifList.isEmpty())
                        if (isEmpty) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Notifications,
                                        contentDescription = null,
                                        tint = JellioTextSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = Strings.get("notifications_nothing_new"),
                                        color = JellioTextSecondary,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Surface(
                                        onClick = onDismiss,
                                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f, pressedScale = 0.97f),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color.White.copy(alpha = 0.08f),
                                            focusedContainerColor = Color.White.copy(alpha = 0.22f),
                                            contentColor = JellioText,
                                            focusedContentColor = Color.White,
                                        ),
                                        border = ClickableSurfaceDefaults.border(
                                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                                        ),
                                    ) {
                                        Text(
                                            text = Strings.get("action_cancel"),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            ) {
                                // Clear all header
                                if (notifList != null && notifList.isNotEmpty()) {
                                    item(key = "clear_all_header") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = if (notifList.size == 1) {
                                                    Strings.get("notifications_count_one")
                                                } else {
                                                    Strings.format("notifications_count_many", notifList.size)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = JellioTextSecondary,
                                            )
                                            Surface(
                                                onClick = { notificationsViewModel.clearAll() },
                                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f, pressedScale = 0.97f),
                                                colors = ClickableSurfaceDefaults.colors(
                                                    containerColor = Color.White.copy(alpha = 0.08f),
                                                    focusedContainerColor = Color.White.copy(alpha = 0.22f),
                                                    contentColor = JellioTextSecondary,
                                                    focusedContentColor = Color.White,
                                                ),
                                                border = ClickableSurfaceDefaults.border(
                                                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))),
                                                    focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                                                ),
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.DeleteSweep,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = Strings.get("notifications_clear_all"),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                        fontSize = 11.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Group watch invites
                                items(invites, key = { "invite_${it.Id}" }) { invite ->
                                    DrawerInviteCard(
                                        invite = invite,
                                        onJoin = {
                                            groupWatchViewModel.joinInvite(invite)
                                            onDismiss()
                                        },
                                        onDismiss = { groupWatchViewModel.dismissInvite(invite) },
                                    )
                                }

                                // Notification cards
                                if (notifList != null) {
                                    items(notifList, key = { it.Id }) { n ->
                                        val itemId = n.ItemId?.takeIf { n.Kind != "announcement" && it.replace("-", "").trim('0').isNotEmpty() }
                                        val isStreamLink = n.Kind == "streamlink" || n.Kind == "streamlink_removed"
                                        val isParty = n.Kind == "party_scheduled" || n.Kind == "party_starting"
                                        val hasAction = isStreamLink || isParty || itemId != null

                                        val actionLabel = when {
                                            isStreamLink -> Strings.get("now_playing_tune_in")
                                            isParty -> Strings.get("gw_join")
                                            else -> Strings.get("details_title")
                                        }
                                        val actionIcon = when {
                                            isStreamLink -> Icons.Filled.LiveTv
                                            isParty -> Icons.Filled.Groups
                                            else -> Icons.Filled.Info
                                        }

                                        NotificationCard(
                                            notification = n,
                                            session = session,
                                            rawImageUrl = rawImageUrl,
                                            onOpen = {
                                                if (isStreamLink) {
                                                    onDismiss()
                                                    onOpenLiveTv()
                                                } else if (isParty) {
                                                    selectedTab = QuickHubTab.GROUP_WATCH
                                                } else if (itemId != null) {
                                                    onDismiss()
                                                    onNavigateToDetail(itemId)
                                                }
                                            },
                                            onDismiss = {
                                                notificationsViewModel.delete(n.Id)
                                            },
                                            hasAction = hasAction,
                                            actionLabel = actionLabel,
                                            actionIcon = actionIcon,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    QuickHubTab.GROUP_WATCH -> {
                        GroupWatchTabContent(
                            session = session,
                            groupWatchViewModel = groupWatchViewModel,
                            activeGroup = activeGroup,
                            partyState = partyState,
                            parties = parties,
                            groups = groups,
                            invites = invites,
                            onRefresh = { gwRefreshKey++ },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickHubTabPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int,
    badgeColor: Color,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSelect,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f, pressedScale = 0.97f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
            contentColor = if (selected) Color.White else JellioTextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
        ),
        modifier = modifier.onFocusChanged {
            if (it.isFocused) {
                onSelect()
            }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp, horizontal = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badgeCount > 0) {
                Spacer(modifier = Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerInviteCard(
    invite: GroupWatchInviteDto,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Strings.format(
                            "tv_invited_you",
                            invite.FromUserName ?: Strings.get("tv_someone"),
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = JellioText,
                    )
                    Text(
                        text = invite.GroupName ?: Strings.get("tv_group_watch"),
                        style = MaterialTheme.typography.bodySmall,
                        color = JellioTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DrawerActionButton(
                    label = Strings.get("gw_join"),
                    icon = Icons.Filled.PlayArrow,
                    primary = true,
                    onClick = onJoin,
                    modifier = Modifier.weight(1f),
                )
                DrawerActionButton(
                    label = Strings.get("action_dismiss"),
                    icon = Icons.Filled.Close,
                    primary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GroupWatchTabContent(
    session: Session,
    groupWatchViewModel: GroupWatchViewModel,
    activeGroup: SyncGroup?,
    partyState: com.jellio.tv.data.model.PartyStateDto?,
    parties: PartiesDto?,
    groups: List<SyncPlayGroupDto>,
    invites: List<GroupWatchInviteDto>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var codeBusy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
        modifier = modifier,
    ) {
        if (activeGroup != null) {
            item(key = "active_party") {
                val mine = parties?.Live?.firstOrNull { SyncPlayManager.sameId(it.GroupId, activeGroup.groupId) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Strings.format("groupwatch_in_group", activeGroup.groupName),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = JellioText,
                            )
                        }

                        if (mine != null) {
                            DrawerPartyMediaRow(
                                title = drawerPartyItemTitle(mine.Item) ?: mine.GroupName.orEmpty(),
                                item = mine.Item,
                                lines = drawerLiveLines(mine),
                                session = session,
                                progress = drawerLiveProgress(mine),
                            )
                        } else {
                            val participantsText = activeGroup.participants.joinToString(", ")
                            Text(
                                text = if (participantsText.isEmpty()) Strings.get("tv_just_you") else participantsText,
                                color = JellioTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        partyState?.Code?.let { partyCode ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Column {
                                    Text(
                                        text = Strings.format("party_code", partyCode),
                                        color = JellioText,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = Strings.get("party_code_hint"),
                                        color = JellioTextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }

                        if (partyState?.IsHost == true) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                DrawerActionButton(
                                    label = Strings.format(
                                        "party_private_toggle",
                                        Strings.get(if (partyState.Private) "party_on" else "party_off"),
                                    ),
                                    primary = false,
                                    onClick = { groupWatchViewModel.setupParty(private = !partyState.Private) },
                                    modifier = Modifier.weight(1f),
                                )
                                DrawerActionButton(
                                    label = Strings.format(
                                        "party_host_only_toggle",
                                        Strings.get(if (partyState.HostOnly) "party_on" else "party_off"),
                                    ),
                                    primary = false,
                                    onClick = { groupWatchViewModel.setupParty(hostOnly = !partyState.HostOnly) },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            val others = mine?.Participants.orEmpty().filter {
                                it.UserId != null && !SyncPlayManager.sameId(it.UserId, partyState.HostUserId)
                            }.distinctBy { it.UserId }
                            if (others.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    others.take(3).forEach { person ->
                                        DrawerActionButton(
                                            label = Strings.format("party_make_host_name", person.Name),
                                            primary = false,
                                            onClick = { groupWatchViewModel.makeHost(person.UserId!!) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        } else if (partyState?.HostName != null) {
                            Text(
                                text = Strings.format("party_hosted_by", partyState.HostName) +
                                    if (partyState.HostOnly) " · " + Strings.get("party_host_only_short") else "",
                                color = JellioTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Text(
                            text = Strings.get("party_tv_hint"),
                            color = JellioTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                        )

                        DrawerActionButton(
                            label = Strings.get("gw_leave_group"),
                            icon = Icons.Filled.Close,
                            primary = true,
                            onClick = { groupWatchViewModel.leave() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
            // Pending Invites
            items(invites, key = { "gw_invite_${it.Id}" }) { invite ->
                DrawerInviteCard(
                    invite = invite,
                    onJoin = { groupWatchViewModel.joinInvite(invite) },
                    onDismiss = { groupWatchViewModel.dismissInvite(invite) },
                )
            }

            // Start or Join by Code Card
            item(key = "gw_controls") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = Strings.get("tv_groupwatch_intro"),
                            color = JellioTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            DrawerActionButton(
                                label = Strings.get(if (creating) "party_creating" else "gw_start_group"),
                                icon = Icons.Filled.Groups,
                                primary = true,
                                onClick = {
                                    if (!creating) {
                                        creating = true
                                        createError = null
                                        scope.launch {
                                            createError = groupWatchViewModel.create(
                                                Strings.format("gw_users_group", session.userName),
                                            )
                                            creating = false
                                            onRefresh()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            DrawerActionButton(
                                label = Strings.get("gw_refresh"),
                                icon = Icons.Filled.Refresh,
                                primary = false,
                                onClick = onRefresh,
                                modifier = Modifier.weight(0.65f),
                            )
                        }

                        createError?.let { err ->
                            Text(
                                text = err,
                                color = Color(0xFFFF6B6B),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.06f)),
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            JellioTextField(
                                value = code,
                                onValueChange = { value ->
                                    code = value.filter { it.isLetterOrDigit() }.uppercase().take(6)
                                    codeError = null
                                },
                                label = Strings.get("party_code_placeholder"),
                                modifier = Modifier.weight(1f),
                            )
                            DrawerActionButton(
                                label = Strings.get("party_join_code"),
                                primary = false,
                                onClick = {
                                    if (!codeBusy) {
                                        codeBusy = true
                                        scope.launch {
                                            codeError = groupWatchViewModel.joinByCode(code)
                                            if (codeError == null) code = ""
                                            codeBusy = false
                                        }
                                    }
                                },
                            )
                        }

                        codeError?.let { err ->
                            Text(
                                text = err,
                                color = Color(0xFFFF6B6B),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // Live Parties
            val liveParties = parties?.Live.orEmpty()
            if (liveParties.isNotEmpty()) {
                item(key = "gw_live_header") {
                    Text(
                        text = Strings.get("party_live_now"),
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                items(liveParties, key = { "live_${it.GroupId}" }) { live ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DrawerPartyMediaRow(
                                title = live.GroupName ?: Strings.get("tv_group_watch"),
                                item = live.Item,
                                lines = drawerLiveLines(live),
                                session = session,
                                progress = drawerLiveProgress(live),
                            )
                            DrawerActionButton(
                                label = Strings.get("gw_join"),
                                icon = Icons.Filled.PlayArrow,
                                primary = true,
                                onClick = { groupWatchViewModel.join(live.GroupId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // Scheduled Parties
            val scheduledParties = parties?.Scheduled.orEmpty()
            if (scheduledParties.isNotEmpty()) {
                item(key = "gw_scheduled_header") {
                    Text(
                        text = Strings.get("party_upcoming"),
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                items(scheduledParties, key = { "sched_${it.Id}" }) { planned ->
                    val going = if (planned.Going.isEmpty()) {
                        ""
                    } else {
                        Strings.format("party_going_count", planned.Going.size)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DrawerPartyMediaRow(
                                title = planned.Name ?: Strings.get("party_title"),
                                item = planned.Item,
                                lines = listOf(
                                    drawerPartyWhen(planned.StartsAt),
                                    listOfNotNull(
                                        drawerPartyItemTitle(planned.Item),
                                        planned.HostName?.let { Strings.format("party_hosted_by", it) },
                                    ).joinToString(" · "),
                                    going,
                                ),
                                session = session,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (planned.GroupId != null) {
                                    DrawerActionButton(
                                        label = Strings.get("gw_join"),
                                        icon = Icons.Filled.PlayArrow,
                                        primary = true,
                                        onClick = { groupWatchViewModel.join(planned.GroupId) },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else if (planned.CanStart) {
                                    DrawerActionButton(
                                        label = Strings.get("party_start_now"),
                                        icon = Icons.Filled.PlayArrow,
                                        primary = true,
                                        onClick = {
                                            groupWatchViewModel.startScheduled(
                                                planned,
                                                Strings.format("gw_users_group", session.userName),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (!planned.IsHost) {
                                    DrawerActionButton(
                                        label = Strings.get(if (planned.IsGoing) "party_going" else "party_im_in"),
                                        primary = planned.GroupId == null && !planned.CanStart,
                                        onClick = {
                                            scope.launch {
                                                groupWatchViewModel.rsvp(planned.Id, !planned.IsGoing)
                                                onRefresh()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Fallback SyncPlay groups when parties endpoint is unavailable
            if (parties == null && groups.isNotEmpty()) {
                items(groups, key = { "group_${it.GroupId}" }) { candidate ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.GroupName ?: Strings.get("tv_group_watch"),
                                    color = JellioText,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = candidate.Participants.joinToString(", "),
                                    color = JellioTextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DrawerActionButton(
                                label = Strings.get("gw_join"),
                                primary = true,
                                onClick = { groupWatchViewModel.join(candidate.GroupId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerPartyMediaRow(
    title: String,
    item: PartyItemDto?,
    lines: List<String>,
    session: Session,
    progress: Float? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val art = item?.ImageItemId?.takeIf { it.isNotEmpty() }
        if (art != null) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                AsyncImage(
                    model = normalizeServerAddress(session.serverAddress) +
                        "/Items/$art/Images/Primary?maxWidth=160&quality=85&ApiKey=" +
                        session.accessToken,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = JellioText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            lines.filter { it.isNotBlank() }.forEach { line ->
                Text(
                    text = line,
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
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
    }
}

private fun drawerPartyStateLabel(state: String?) = Strings.get(
    when (state) {
        "Playing" -> "party_state_playing"
        "Waiting" -> "party_state_waiting"
        "Paused" -> "party_state_paused"
        else -> "party_state_idle"
    },
)

private fun drawerPartyItemTitle(item: PartyItemDto?): String? {
    item ?: return null
    if (item.Type == "Episode" && item.SeriesName != null) {
        val code = if (item.SeasonNumber != null && item.EpisodeNumber != null) {
            " S${item.SeasonNumber}E${item.EpisodeNumber}"
        } else {
            ""
        }
        return item.SeriesName + code
    }
    return item.Name
}

private fun drawerPartyWhen(startsAt: String?): String {
    val at = PartyManager.parseTime(startsAt) ?: return ""
    val clock = DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT,
        displayLocale(),
    ).format(Date(at))
    val minutes = (at - System.currentTimeMillis()) / 60_000L
    val relative = when {
        minutes <= 0 -> Strings.get("party_due_now")
        minutes < 60 -> Strings.format("party_in_minutes", minutes)
        else -> Strings.format("party_in_hours", (minutes + 30) / 60)
    }
    return "$clock · $relative"
}

private fun drawerLiveLines(party: LivePartyDto): List<String> {
    val title = drawerPartyItemTitle(party.Item)
    val position = party.PositionTicks?.let { formatPlaybackTime(it / 10_000L) }
    val status = listOfNotNull(title, drawerPartyStateLabel(party.State), position).joinToString(" · ")
    val people = party.Participants.joinToString(", ") { it.Name }
    val host = party.HostName?.let { Strings.format("party_hosted_by", it) }
    val flags = listOfNotNull(
        Strings.get("party_private").takeIf { party.Private },
        Strings.get("party_host_only_short").takeIf { party.HostOnly },
    ).joinToString(" · ")
    return listOfNotNull(status, people, listOfNotNull(host, flags.ifEmpty { null }).joinToString(" · "))
}

private fun drawerLiveProgress(party: LivePartyDto): Float? {
    val runtime = party.Item?.RunTimeTicks ?: return null
    val position = party.PositionTicks ?: return null
    if (runtime <= 0) return null
    return position.toFloat() / runtime.toFloat()
}

@Composable
private fun LocalPausedCard(
    media: NowPlayingManager.LocalPausedMedia,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.get("now_playing_this_tv"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = media.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = JellioText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!media.subtitle.isNullOrBlank()) {
                Text(
                    text = media.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = JellioTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DrawerActionButton(
                    label = Strings.get("now_playing_resume"),
                    icon = Icons.Filled.PlayArrow,
                    primary = true,
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                )
                DrawerActionButton(
                    label = Strings.get("action_dismiss"),
                    primary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: NowPlayingSessionDto,
    currentSession: Session,
    onHandoff: () -> Unit,
    onTuneIn: (String) -> Unit,
    onJoinParty: () -> Unit,
    onDetails: (String) -> Unit,
    rawImageUrl: (String, String?, String, Int) -> String,
) {
    val item = session.Item ?: return
    val isLive = session.Activity == "watching_live"
    val isReading = session.Activity == "reading"

    val isOwnSession = remember(session.UserId, session.UserName, currentSession.userId, currentSession.userName) {
        if (!session.UserId.isNullOrBlank() && currentSession.userId.isNotBlank()) {
            session.UserId.replace("-", "").equals(currentSession.userId.replace("-", ""), ignoreCase = true)
        } else if (!session.UserName.isNullOrBlank() && currentSession.userName.isNotBlank()) {
            session.UserName.equals(currentSession.userName, ignoreCase = true)
        } else {
            false
        }
    }

    val userAvatarUrl = remember(session.UserId, currentSession.serverAddress, currentSession.accessToken) {
        if (!session.UserId.isNullOrBlank()) {
            val cleanServer = normalizeServerAddress(currentSession.serverAddress)
            val base = "$cleanServer/Users/${session.UserId}/Images/Primary?maxWidth=96"
            if (currentSession.accessToken.isNotEmpty()) {
                "$base&ApiKey=${currentSession.accessToken}&api_key=${currentSession.accessToken}"
            } else {
                base
            }
        } else {
            null
        }
    }
    var avatarError by remember(session.UserId) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Column {
            // User and Device Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                    ) {
                        if (userAvatarUrl != null && !avatarError) {
                            AsyncImage(
                                model = userAvatarUrl,
                                contentDescription = session.UserName,
                                contentScale = ContentScale.Crop,
                                onError = { avatarError = true },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                            )
                        } else {
                            Text(
                                text = session.UserName?.take(1)?.uppercase() ?: "U",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = session.UserName ?: Strings.get("tv_someone"),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = JellioText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val deviceDesc = listOfNotNull(session.DeviceName, session.Client).joinToString(" · ")
                        if (deviceDesc.isNotEmpty()) {
                            Text(
                                text = deviceDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = JellioTextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause status indicator badge
                val statusDotColor = when {
                    session.IsPaused -> Color(0xFFF59E0B)
                    isLive -> Color(0xFFEF4444)
                    else -> Color(0xFF22C55E)
                }
                val statusText = when {
                    session.IsPaused -> Strings.get("party_state_paused")
                    isLive -> Strings.get("live_badge")
                    else -> Strings.get("party_state_playing")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusDotColor),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = JellioTextSecondary,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Media Info Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                val imageItemId = item.SeriesId ?: item.Id
                val thumbUrl = if (!imageItemId.isNullOrBlank()) {
                    rawImageUrl(imageItemId, null, "Primary", 200)
                } else {
                    null
                }

                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = item.Name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.DarkGray),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.Name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = JellioText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subTitle = item.SeriesName ?: if (isLive) item.Source else null
                    if (!subTitle.isNullOrBlank()) {
                        Text(
                            text = subTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = JellioTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Progress Bar (if playback ticks exist)
            val runTime = item.RunTimeTicks ?: 0L
            val baseTicks = session.PositionTicks ?: 0L
            var livePositionTicks by remember(session.Id, session.PositionTicks, session.IsPaused) {
                mutableStateOf(baseTicks)
            }

            LaunchedEffect(session.Id, session.PositionTicks, session.IsPaused) {
                if (!session.IsPaused && baseTicks > 0L) {
                    val startUptime = android.os.SystemClock.uptimeMillis()
                    while (isActive) {
                        delay(1000L)
                        val elapsedMs = android.os.SystemClock.uptimeMillis() - startUptime
                        val newTicks = baseTicks + (elapsedMs * 10_000L)
                        livePositionTicks = if (runTime > 0L) newTicks.coerceAtMost(runTime) else newTicks
                    }
                } else {
                    livePositionTicks = baseTicks
                }
            }

            val targetFraction = if (runTime > 0L) (livePositionTicks.toFloat() / runTime.toFloat()).coerceIn(0f, 1f) else 0f
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 950, easing = LinearEasing),
                label = "progressBarFraction",
            )

            if (runTime > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatTicks(livePositionTicks),
                        style = MaterialTheme.typography.labelSmall,
                        color = JellioTextSecondary,
                        fontSize = 10.sp,
                    )
                    Text(
                        text = "${(targetFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = JellioTextSecondary,
                        fontSize = 10.sp,
                    )
                    Text(
                        text = formatTicks(runTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = JellioTextSecondary,
                        fontSize = 10.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val canHandoff = !item.Id.isNullOrBlank() && !isReading && isOwnSession
                val canTuneIn = isLive && !item.Id.isNullOrBlank()
                val canShowDetails = !item.Id.isNullOrBlank()

                if (canTuneIn) {
                    DrawerActionButton(
                        label = Strings.get("now_playing_tune_in"),
                        icon = Icons.Filled.LiveTv,
                        primary = true,
                        onClick = { onTuneIn(item.Id) },
                        modifier = Modifier.weight(1f),
                    )
                } else if (canHandoff) {
                    DrawerActionButton(
                        label = Strings.get("now_playing_handoff"),
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        onClick = onHandoff,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (canShowDetails) {
                    val isOnlyAction = !canTuneIn && !canHandoff
                    DrawerActionButton(
                        label = Strings.get("details_title"),
                        icon = Icons.Filled.Info,
                        primary = isOnlyAction,
                        onClick = { onDetails(item.SeriesId ?: item.Id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: JellioNotificationDto,
    session: Session,
    rawImageUrl: (String, String?, String, Int) -> String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    hasAction: Boolean,
    actionLabel: String,
    actionIcon: ImageVector,
) {
    val notifImageUrl = remember(notification.Id, notification.Kind, notification.ImageId, notification.ItemId, session.serverAddress, session.accessToken) {
        val cleanServer = normalizeServerAddress(session.serverAddress)
        if (notification.Kind == "announcement") {
            val img = notification.ImageId?.takeIf { it.isNotBlank() }
            when {
                img == null -> null
                img.startsWith("http://") || img.startsWith("https://") -> img
                img.startsWith("/") -> "$cleanServer$img"
                else -> {
                    val encoded = URLEncoder.encode(img, "UTF-8")
                    if (session.accessToken.isNotEmpty()) {
                        "$cleanServer/Jellio/notifications/image/$encoded?ApiKey=${session.accessToken}&api_key=${session.accessToken}"
                    } else {
                        "$cleanServer/Jellio/notifications/image/$encoded"
                    }
                }
            }
        } else {
            val mediaImageId = notification.ImageId?.takeIf { it.replace("-", "").trim('0').isNotEmpty() }
                ?: notification.ItemId?.takeIf { it.replace("-", "").trim('0').isNotEmpty() }
            when {
                mediaImageId == null -> null
                mediaImageId.startsWith("http://") || mediaImageId.startsWith("https://") -> mediaImageId
                mediaImageId.startsWith("/") -> "$cleanServer$mediaImageId"
                else -> rawImageUrl(mediaImageId, null, "Primary", 200)
            }
        }
    }
    var imageError by remember(notifImageUrl) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (notification.Read) 0.04f else 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (notifImageUrl != null && !imageError) {
                    AsyncImage(
                        model = notifImageUrl,
                        contentDescription = notification.Name,
                        contentScale = ContentScale.Crop,
                        onError = { imageError = true },
                        modifier = Modifier
                            .size(width = 48.dp, height = 68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Header Row: Subtitle/category on left, timestamp on right
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            if (!notification.Read) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = subtitleFor(notification),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = JellioTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val timeStr = remember(notification.CreatedUtc, notification.Date) {
                            formatRelativeTime(notification.CreatedUtc ?: notification.Date)
                        }
                        if (timeStr.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = JellioTextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Message text
                    Text(
                        text = messageFor(notification),
                        color = JellioText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (hasAction) {
                    DrawerActionButton(
                        label = actionLabel,
                        icon = actionIcon,
                        primary = true,
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                    )
                    DrawerActionButton(
                        label = Strings.get("notifications_dismiss"),
                        icon = Icons.Filled.Close,
                        primary = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    DrawerActionButton(
                        label = Strings.get("notifications_dismiss"),
                        icon = Icons.Filled.Close,
                        primary = true,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(
            focusedScale = 1.02f,
            pressedScale = 0.96f,
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = if (primary) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.22f),
            contentColor = if (primary) Color.Black else JellioText,
            focusedContentColor = if (primary) Color.Black else Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, if (primary) Color.Transparent else Color.White.copy(alpha = 0.10f)),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = shape,
            ),
        ),
        modifier = modifier.height(38.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatTicks(ticks: Long): String {
    val totalSeconds = ticks / 10_000_000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
