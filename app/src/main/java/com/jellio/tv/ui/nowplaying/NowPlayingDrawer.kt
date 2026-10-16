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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.jellio.tv.ui.common.formatRelativeTime
import com.jellio.tv.ui.groupwatch.GroupWatchViewModel
import com.jellio.tv.ui.groupwatch.InviteRow
import com.jellio.tv.ui.notify.NotificationsViewModel
import com.jellio.tv.ui.notify.messageFor
import com.jellio.tv.ui.notify.subtitleFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.data.nowplaying.NowPlayingManager
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.dismissOnBack
import com.jellio.tv.ui.i18n.Strings
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled

private val DrawerWidth = 430.dp

enum class QuickHubTab {
    NOW_PLAYING,
    NOTIFICATIONS,
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

    var selectedTab by remember { mutableStateOf(initialTab) }

    val nowPlayingCount = sessions.size + (if (localPaused != null) 1 else 0)
    val notificationsCount = invites.size + (notifications?.count { !it.Read } ?: 0)

    val tabNowPlayingFocus = remember { FocusRequester() }
    val tabNotificationsFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    val emptyActionFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(25) {
            val focused = runCatching {
                if (initialTab == QuickHubTab.NOW_PLAYING) {
                    tabNowPlayingFocus.requestFocus()
                } else {
                    tabNotificationsFocus.requestFocus()
                }
            }.isSuccess
            if (focused) return@LaunchedEffect
            kotlinx.coroutines.delay(40)
        }
    }

    LaunchedEffect(selectedTab) {
        kotlinx.coroutines.delay(50)
        runCatching {
            if (selectedTab == QuickHubTab.NOW_PLAYING) {
                if (nowPlayingCount > 0) firstItemFocus.requestFocus()
                else emptyActionFocus.requestFocus()
            } else {
                if (notificationsCount > 0) firstItemFocus.requestFocus()
                else emptyActionFocus.requestFocus()
            }
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
                    .padding(vertical = 24.dp, horizontal = 24.dp)
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
                                imageVector = Icons.Filled.Tune,
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
                                        1 -> "1 unread"
                                        else -> "$notificationsCount unread"
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = JellioTextSecondary,
                            )
                        }
                    }

                    // Close Button
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
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

                // Tab Switcher Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val isNowPlayingSelected = selectedTab == QuickHubTab.NOW_PLAYING
                    Surface(
                        onClick = { selectedTab = QuickHubTab.NOW_PLAYING },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isNowPlayingSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                            contentColor = if (isNowPlayingSelected) Color.White else JellioTextSecondary,
                            focusedContainerColor = Color.White.copy(alpha = 0.28f),
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = if (isNowPlayingSelected) Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))) else Border.None,
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tabNowPlayingFocus),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Strings.get("quick_hub_tab_now_playing"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isNowPlayingSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (nowPlayingCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.22f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = nowPlayingCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    val isNotificationsSelected = selectedTab == QuickHubTab.NOTIFICATIONS
                    Surface(
                        onClick = { selectedTab = QuickHubTab.NOTIFICATIONS },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isNotificationsSelected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                            contentColor = if (isNotificationsSelected) Color.White else JellioTextSecondary,
                            focusedContainerColor = Color.White.copy(alpha = 0.28f),
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = if (isNotificationsSelected) Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))) else Border.None,
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(tabNotificationsFocus),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Strings.get("quick_hub_tab_notifications"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isNotificationsSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (notificationsCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF3B30)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = notificationsCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }

                // Drawer Body
                if (selectedTab == QuickHubTab.NOW_PLAYING) {
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
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        focusedContainerColor = Color.White.copy(alpha = 0.22f),
                                        contentColor = JellioText,
                                        focusedContentColor = Color.White,
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                                    ),
                                    modifier = Modifier.focusRequester(emptyActionFocus),
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
                            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            // 1. Local Paused Media Card (if any)
                            if (localPaused != null) {
                                item(key = "local_paused") {
                                    LocalPausedCard(
                                        media = localPaused,
                                        onResume = onResumeLocal,
                                        onDismiss = onDismissLocal,
                                        focusRequester = firstItemFocus,
                                    )
                                }
                            }

                            // 2. Remote Sessions
                            items(sessions, key = { it.Id ?: it.hashCode().toString() }) { sess ->
                                val isFirst = (localPaused == null && sessions.firstOrNull() == sess)
                                SessionCard(
                                    session = sess,
                                    currentSession = session,
                                    onHandoff = { onHandoff(sess) },
                                    onTuneIn = { channelId -> onTuneIn(channelId) },
                                    onJoinParty = { onJoinWatchParty(sess) },
                                    onDetails = { itemId -> onNavigateToDetail(itemId) },
                                    rawImageUrl = rawImageUrl,
                                    focusRequester = if (isFirst) firstItemFocus else null,
                                )
                            }
                        }
                    }
                } else {
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
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.08f),
                                        focusedContainerColor = Color.White.copy(alpha = 0.22f),
                                        contentColor = JellioText,
                                        focusedContentColor = Color.White,
                                    ),
                                    border = ClickableSurfaceDefaults.border(
                                        focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                                    ),
                                    modifier = Modifier.focusRequester(emptyActionFocus),
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
                            contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
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
                                            text = "${notifList.size} ${if (notifList.size == 1) Strings.get("quick_hub_tab_notifications").trimEnd('s') else Strings.get("quick_hub_tab_notifications")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = JellioTextSecondary,
                                        )
                                        Surface(
                                            onClick = { notificationsViewModel.clearAll() },
                                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
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
                                val isFirst = invites.firstOrNull() == invite
                                Box(modifier = if (isFirst) Modifier.focusRequester(firstItemFocus) else Modifier) {
                                    InviteRow(
                                        invite = invite,
                                        onJoin = {
                                            groupWatchViewModel.joinInvite(invite)
                                            onDismiss()
                                        },
                                        onDismiss = { groupWatchViewModel.dismissInvite(invite) },
                                    )
                                }
                            }

                            // Notification cards
                            if (notifList != null) {
                                items(notifList, key = { it.Id }) { n ->
                                    val isFirst = invites.isEmpty() && notifList.firstOrNull() == n
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
                                        isParty -> Icons.Filled.Tv
                                        else -> Icons.Filled.Info
                                    }

                                    NotificationCard(
                                        notification = n,
                                        onOpen = {
                                            onDismiss()
                                            if (isStreamLink) {
                                                onOpenLiveTv()
                                            } else if (isParty) {
                                                onOpenGroupWatch()
                                            } else if (itemId != null) {
                                                onNavigateToDetail(itemId)
                                            }
                                        },
                                        onDismiss = {
                                            notificationsViewModel.delete(n.Id)
                                        },
                                        hasAction = hasAction,
                                        actionLabel = actionLabel,
                                        actionIcon = actionIcon,
                                        focusRequester = if (isFirst) firstItemFocus else null,
                                    )
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
private fun LocalPausedCard(
    media: NowPlayingManager.LocalPausedMedia,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    focusRequester: FocusRequester? = null,
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
                    modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
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
    focusRequester: FocusRequester? = null,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Text(
                            text = session.UserName?.take(1)?.uppercase() ?: "U",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = session.UserName ?: Strings.get("tv_someone"),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = JellioText,
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

                // Play / Pause status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (session.IsPaused) Strings.get("action_pause") else if (isLive) "LIVE" else Strings.get("action_play"),
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
                } else null

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
                        modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                } else if (canHandoff) {
                    DrawerActionButton(
                        label = Strings.get("now_playing_handoff"),
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        onClick = onHandoff,
                        modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                }

                if (canShowDetails) {
                    val isOnlyAction = !canTuneIn && !canHandoff
                    DrawerActionButton(
                        label = Strings.get("details_title"),
                        icon = Icons.Filled.Info,
                        primary = isOnlyAction,
                        onClick = { onDetails(item.SeriesId ?: item.Id) },
                        modifier = Modifier.weight(1f).then(
                            if (focusRequester != null && isOnlyAction) Modifier.focusRequester(focusRequester) else Modifier
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: com.jellio.tv.data.model.JellioNotificationDto,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    hasAction: Boolean,
    actionLabel: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    focusRequester: FocusRequester? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (notification.Read) 0.04f else 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column {
            // Header Row: Subtitle/category on left, timestamp on right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    )
                }

                val timeStr = remember(notification.CreatedUtc, notification.Date) {
                    formatRelativeTime(notification.CreatedUtc ?: notification.Date)
                }
                if (timeStr.isNotEmpty()) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = JellioTextSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Message text
            Text(
                text = messageFor(notification),
                color = JellioText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (hasAction) {
                    DrawerActionButton(
                        label = actionLabel,
                        icon = actionIcon,
                        primary = true,
                        onClick = onOpen,
                        modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
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
                        modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
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
            focusedScale = 1.03f,
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
