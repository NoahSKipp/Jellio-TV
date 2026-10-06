package com.jellio.tv.ui.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.jellio.tv.ui.groupwatch.GroupWatchViewModel
import com.jellio.tv.ui.groupwatch.InviteRow
import com.jellio.tv.ui.notify.NotificationsViewModel
import com.jellio.tv.ui.notify.messageFor
import com.jellio.tv.ui.notify.subtitleFor
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

private val DrawerCyan = Color(0xFF17A7DD)
private val DrawerMagenta = Color(0xFFC657E3)
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
    notificationsViewModel: NotificationsViewModel = hiltViewModel(),
    groupWatchViewModel: GroupWatchViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onDismiss)

    LaunchedEffect(Unit) { notificationsViewModel.load() }
    val notifications by notificationsViewModel.items.collectAsState()
    val invites by groupWatchViewModel.invites.collectAsState()

    var selectedTab by remember { mutableStateOf(initialTab) }

    val nowPlayingCount = sessions.size + (if (localPaused != null) 1 else 0)
    val notificationsCount = invites.size + (notifications?.count { !it.Read } ?: 0)

    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(selectedTab) {
        kotlinx.coroutines.delay(100)
        runCatching { firstItemFocus.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .background(Color.Black.copy(alpha = 0.65f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .focusProperties {
                onExit = {
                    if (requestedFocusDirection == FocusDirection.Left) {
                        onDismiss()
                        FocusRequester.Cancel
                    } else {
                        FocusRequester.Default
                    }
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
                    .background(Color(0xF50D101C))
                    .padding(vertical = 24.dp, horizontal = 24.dp)
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(DrawerCyan, DrawerMagenta))),
                        ) {
                            Icon(
                                imageVector = if (selectedTab == QuickHubTab.NOW_PLAYING) Icons.Filled.GraphicEq else Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
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
                                color = DrawerCyan,
                            )
                        }
                    }

                    // Close Button
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.25f),
                            contentColor = JellioTextSecondary,
                            focusedContentColor = JellioText,
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
                            containerColor = if (isNowPlayingSelected) DrawerCyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                            contentColor = if (isNowPlayingSelected) Color.White else JellioTextSecondary,
                            focusedContainerColor = DrawerCyan.copy(alpha = 0.35f),
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = if (isNowPlayingSelected) Border(BorderStroke(1.5.dp, DrawerCyan.copy(alpha = 0.6f))) else Border.None,
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                        modifier = Modifier.weight(1f),
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
                                        .background(if (isNowPlayingSelected) DrawerCyan else Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = nowPlayingCount.toString(),
                                        color = if (isNowPlayingSelected) Color.Black else Color.White,
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
                            containerColor = if (isNotificationsSelected) DrawerCyan.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                            contentColor = if (isNotificationsSelected) Color.White else JellioTextSecondary,
                            focusedContainerColor = DrawerCyan.copy(alpha = 0.35f),
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = if (isNotificationsSelected) Border(BorderStroke(1.5.dp, DrawerCyan.copy(alpha = 0.6f))) else Border.None,
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                        modifier = Modifier.weight(1f),
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
                                        .background(if (isNotificationsSelected) DrawerCyan else Color(0xFFE50914)),
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
                                    tint = JellioTextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = Strings.get("now_playing_empty"),
                                    color = JellioTextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
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
                                    tint = JellioTextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = Strings.get("notifications_nothing_new"),
                                    color = JellioTextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
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
                                items(notifList, key = { it.Id ?: it.hashCode().toString() }) { n ->
                                    val isFirst = invites.isEmpty() && notifList.firstOrNull() == n
                                    val itemId = n.ItemId?.takeIf { n.Kind != "announcement" && it.replace("-", "").trim('0').isNotEmpty() }
                                    Surface(
                                        onClick = {
                                            if (n.Kind == "streamlink" || n.Kind == "streamlink_removed") {
                                                onDismiss()
                                                onOpenLiveTv()
                                            } else if (n.Kind == "party_scheduled" || n.Kind == "party_starting") {
                                                onDismiss()
                                                onOpenGroupWatch()
                                            } else if (itemId != null) {
                                                onDismiss()
                                                onNavigateToDetail(itemId)
                                            }
                                        },
                                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color.White.copy(alpha = if (n.Read) 0.04f else 0.1f),
                                            contentColor = JellioText,
                                            focusedContainerColor = Color.White.copy(alpha = 0.22f),
                                            focusedContentColor = JellioText,
                                        ),
                                        border = ClickableSurfaceDefaults.border(
                                            border = Border.None,
                                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (isFirst) Modifier.focusRequester(firstItemFocus) else Modifier),
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                            Text(
                                                text = messageFor(n),
                                                color = JellioText,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = subtitleFor(n),
                                                color = JellioTextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
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
            .background(Color(0xFF161A2B))
            .border(1.dp, Color(0xFF282F4E), RoundedCornerShape(16.dp))
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
                            .background(DrawerMagenta.copy(alpha = 0.2f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = null,
                            tint = DrawerMagenta,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Strings.get("now_playing_this_tv"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = DrawerMagenta,
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
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: NowPlayingSessionDto,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141828))
            .border(1.dp, Color(0xFF242C47), RoundedCornerShape(16.dp))
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
                            .background(DrawerCyan.copy(alpha = 0.2f)),
                    ) {
                        Text(
                            text = session.UserName?.take(1)?.uppercase() ?: "U",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = DrawerCyan,
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
                        .background(if (session.IsPaused) Color.White.copy(alpha = 0.08f) else DrawerCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (session.IsPaused) Strings.get("action_pause") else if (isLive) "LIVE" else Strings.get("action_play"),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (session.IsPaused) JellioTextSecondary else DrawerCyan,
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
            val pos = session.PositionTicks ?: 0L
            val runTime = item.RunTimeTicks ?: 0L
            if (runTime > 0L) {
                Spacer(modifier = Modifier.height(10.dp))
                val fraction = (pos.toFloat() / runTime.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.horizontalGradient(listOf(DrawerCyan, DrawerMagenta))),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = formatTicks(pos),
                        style = MaterialTheme.typography.labelSmall,
                        color = JellioTextSecondary,
                        fontSize = 10.sp,
                    )
                    Text(
                        text = "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = DrawerCyan,
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
                if (isLive && !item.Id.isNullOrBlank()) {
                    DrawerActionButton(
                        label = Strings.get("now_playing_tune_in"),
                        icon = Icons.Filled.LiveTv,
                        primary = true,
                        onClick = { onTuneIn(item.Id) },
                        modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                } else if (!item.Id.isNullOrBlank() && !isReading) {
                    DrawerActionButton(
                        label = Strings.get("now_playing_handoff"),
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        onClick = onHandoff,
                        modifier = Modifier.weight(1f).then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                }

                if (!item.Id.isNullOrBlank()) {
                    DrawerActionButton(
                        label = Strings.get("details_title"),
                        primary = false,
                        onClick = { onDetails(item.SeriesId ?: item.Id) },
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
            containerColor = if (primary) DrawerCyan else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = if (primary) DrawerCyan.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.22f),
            contentColor = if (primary) Color.Black else JellioText,
            focusedContentColor = if (primary) Color.Black else JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, if (primary) Color.White else DrawerCyan),
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
