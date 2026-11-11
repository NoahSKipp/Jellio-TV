package com.jellio.tv.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.data.model.JellioNotificationDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.formatRelativeTime
import com.jellio.tv.ui.notify.messageFor
import com.jellio.tv.ui.notify.subtitleFor
import com.jellio.tv.ui.nowplaying.DrawerActionButton
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Quick Hub cards for the friend kinds: a request to answer in place, an
// accepted request, and titles a friend added to the watchlist.
@Composable
fun FriendNotificationCard(
    notification: JellioNotificationDto,
    session: Session,
    viewModel: FriendsViewModel,
    rawImageUrl: (String, String?, String, Int) -> String,
    onOpenItem: (String) -> Unit,
    onOpenWatchlist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val kind = notification.Kind
    val sender = notification.FromUserName ?: notification.Name.orEmpty()
    val senderAvatar = notification.FromUserId?.let { viewModel.avatarUrl(session, it, notification.FromImageTag) }
    val itemId = notification.ItemId?.takeIf { it.replace("-", "").trim('0').isNotEmpty() }
    val count = watchlistAddedCount(notification.Detail)
    val target = watchlistNoticeTarget(count, itemId != null)
    var posterFailed by remember(notification.Id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (notification.Read) 0.04f else 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (kind == KIND_WATCHLIST_ADDED && itemId != null && !posterFailed) {
                    Box {
                        AsyncImage(
                            model = rawImageUrl(itemId, null, "Primary", 200),
                            contentDescription = notification.Name,
                            contentScale = ContentScale.Crop,
                            onError = { posterFailed = true },
                            modifier = Modifier
                                .size(width = 48.dp, height = 68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                        )
                        FriendAvatar(
                            url = senderAvatar,
                            name = sender,
                            size = 24.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .border(1.5.dp, JellioBg, CircleShape),
                        )
                    }
                } else {
                    FriendAvatar(url = senderAvatar, name = sender, size = 48.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                            if (!notification.Read) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = subtitleFor(notification),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = JellioTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                        val time = remember(notification.CreatedUtc, notification.Date) {
                            formatRelativeTime(notification.CreatedUtc ?: notification.Date)
                        }
                        if (time.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                color = JellioTextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = messageFor(notification),
                        color = JellioText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when (kind) {
                    KIND_FRIEND_REQUEST -> {
                        // The request id rides in Detail; the notification id is friendreq:{id}.
                        val requestId = notification.Detail?.takeIf { it.isNotBlank() } ?: notification.Id.removePrefix("friendreq:")
                        DrawerActionButton(
                            label = Strings.get("friends_accept"),
                            icon = Icons.Filled.Info,
                            primary = true,
                            onClick = {
                                viewModel.accept(requestId)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        DrawerActionButton(
                            label = Strings.get("friends_decline"),
                            icon = Icons.Filled.Close,
                            primary = false,
                            onClick = {
                                viewModel.decline(requestId)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    KIND_WATCHLIST_ADDED -> {
                        if (target != NoticeTarget.None) {
                            DrawerActionButton(
                                label = Strings.get(if (target == NoticeTarget.Watchlist) "friends_open_watchlist" else "details_title"),
                                icon = Icons.Filled.Info,
                                primary = true,
                                onClick = {
                                    if (target == NoticeTarget.Watchlist) onOpenWatchlist() else itemId?.let(onOpenItem)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (count == 1 && itemId != null) {
                            DrawerActionButton(
                                label = Strings.get("friends_undo"),
                                icon = Icons.Filled.Close,
                                primary = false,
                                onClick = {
                                    viewModel.undoWatchlistAdd(session, itemId) { onDismiss() }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        DrawerActionButton(
                            label = Strings.get("notifications_dismiss"),
                            icon = Icons.Filled.Close,
                            primary = target == NoticeTarget.None,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    else -> DrawerActionButton(
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
