package com.jellio.tv.ui.notify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.JellioNotificationDto
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.ui.groupwatch.GroupWatchViewModel
import com.jellio.tv.ui.groupwatch.InviteRow
import com.jellio.tv.ui.groupwatch.SidePanelOverlay
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val api: JellyfinApi) : ViewModel() {
    private val _items = MutableStateFlow<List<JellioNotificationDto>?>(null)
    val items: StateFlow<List<JellioNotificationDto>?> = _items.asStateFlow()

    // Manga lives on the web and desktop only, so its alerts stay there.
    fun load() {
        viewModelScope.launch {
            _items.value = runCatching { api.getJellioNotifications() }
                .getOrDefault(emptyList())
                .filter { it.Kind != "manga" && !it.Id.orEmpty().startsWith("changelog:") }
            runCatching { api.markJellioNotificationsRead() }
        }
    }
}

// The same lines components/notifications.js's messageFor/subtitleFor show.
private fun messageFor(n: JellioNotificationDto): String = when (n.Kind) {
    "streamlink" -> com.jellio.tv.ui.i18n.Strings.format("live_link_dead_notice", n.Name.orEmpty())
    "streamlink_removed" -> com.jellio.tv.ui.i18n.Strings.format("live_link_removed_notice", n.Name.orEmpty(), n.Detail.orEmpty())
    "announcement" -> n.Name.orEmpty()
    "party_scheduled" -> com.jellio.tv.ui.i18n.Strings.format("party_notice_scheduled", n.Detail ?: com.jellio.tv.ui.i18n.Strings.get("tv_someone"), n.Name.orEmpty(), partyNoticeWhen(n.Date))
    "party_starting" -> com.jellio.tv.ui.i18n.Strings.format("party_notice_starting", n.Name.orEmpty())
    "episode" -> com.jellio.tv.ui.i18n.Strings.format("notify_out_now", (n.Name ?: com.jellio.tv.ui.i18n.Strings.get("notify_new_item")) + (n.Detail?.let { " $it" } ?: ""))
    else -> com.jellio.tv.ui.i18n.Strings.format("notify_available_to_watch", n.Name ?: com.jellio.tv.ui.i18n.Strings.get("notify_new_item"))
}

private fun partyNoticeWhen(date: String?): String {
    val at = com.jellio.tv.data.syncplay.PartyManager.parseTime(date) ?: return ""
    return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, com.jellio.tv.ui.i18n.displayLocale()).format(java.util.Date(at))
}

private fun subtitleFor(n: JellioNotificationDto): String = when (n.Kind) {
    "streamlink" -> com.jellio.tv.ui.i18n.Strings.get("live_link_dead_sub")
    "streamlink_removed" -> com.jellio.tv.ui.i18n.Strings.get("live_link_section")
    "announcement" -> com.jellio.tv.ui.i18n.Strings.get("notify_from_server")
    "party_scheduled", "party_starting" -> com.jellio.tv.ui.i18n.Strings.get("party_notice_sub")
    "episode" -> com.jellio.tv.ui.i18n.Strings.get("calendar_new_episode")
    else -> com.jellio.tv.ui.i18n.Strings.get("notify_now_streaming")
}

// The notification drawer from Jellio's sidebar, for the TV: Group Watch
// invites first, then releases from the watchlist and server announcements.
@Composable
fun NotificationsOverlay(
    onDismiss: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenLiveTv: () -> Unit = {},
    onOpenGroupWatch: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
    groupWatch: GroupWatchViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val items by viewModel.items.collectAsState()
    val invites by groupWatch.invites.collectAsState()
    SidePanelOverlay(title = com.jellio.tv.ui.i18n.string("notifications_title"), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            invites.forEach { invite ->
                InviteRow(
                    invite = invite,
                    onJoin = {
                        groupWatch.joinInvite(invite)
                        onDismiss()
                    },
                    onDismiss = { groupWatch.dismissInvite(invite) },
                )
            }
            val list = items
            when {
                list == null -> Text(text = com.jellio.tv.ui.i18n.string("status_loading"), color = JellioTextSecondary)
                list.isEmpty() && invites.isEmpty() -> Text(text = com.jellio.tv.ui.i18n.string("notifications_nothing_new"), color = JellioTextSecondary)
                else -> list.forEach { n ->
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
                                onOpenItem(itemId)
                            }
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = if (n.Read) 0.04f else 0.1f),
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.25f),
                            focusedContentColor = JellioText,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text(text = messageFor(n), color = JellioText)
                            Text(text = subtitleFor(n), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
