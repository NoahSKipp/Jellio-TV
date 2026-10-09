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
            _items.value = runCatching { api.getJellioNotifications() }.getOrDefault(emptyList()).filter { it.Kind != "manga" }
            runCatching { api.markJellioNotificationsRead() }
        }
    }
}

// The same lines components/notifications.js's messageFor/subtitleFor show.
private fun messageFor(n: JellioNotificationDto): String = when (n.Kind) {
    "announcement" -> n.Name.orEmpty()
    "episode" -> n.Name.orEmpty() + (n.Detail?.let { " $it" } ?: "") + " is out now"
    else -> n.Name.orEmpty() + " is available to watch"
}

private fun subtitleFor(n: JellioNotificationDto): String = when (n.Kind) {
    "announcement" -> "From the server"
    "episode" -> "New episode"
    else -> "Now streaming"
}

// The notification drawer from Jellio's sidebar, for the TV: Group Watch
// invites first, then releases from the watchlist and server announcements.
@Composable
fun NotificationsOverlay(
    onDismiss: () -> Unit,
    onOpenItem: (String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
    groupWatch: GroupWatchViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val items by viewModel.items.collectAsState()
    val invites by groupWatch.invites.collectAsState()
    SidePanelOverlay(title = "Notifications", onDismiss = onDismiss) {
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
                list == null -> Text(text = "Loading...", color = JellioTextSecondary)
                list.isEmpty() && invites.isEmpty() -> Text(text = "Nothing new.", color = JellioTextSecondary)
                else -> list.forEach { n ->
                    val itemId = n.ItemId?.takeIf { n.Kind != "announcement" && it.replace("-", "").trim('0').isNotEmpty() }
                    Surface(
                        onClick = {
                            if (itemId != null) {
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
