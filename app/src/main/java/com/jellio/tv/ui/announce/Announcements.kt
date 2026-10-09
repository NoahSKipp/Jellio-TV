package com.jellio.tv.ui.announce

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val POLL_MS = 60_000L
private const val SHOW_MS = 10_000L
// An announcement sent while the TV was off still shows if it's this fresh.
private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

// Announcements from the Jellio plugin settings (Send to every user), the
// same ones components/notifications.js toasts on the web: polled while
// signed in and shown once each, over whatever screen is open.
@HiltViewModel
class AnnouncementViewModel @Inject constructor(
    private val repository: JellioRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("jellio_announcements", Context.MODE_PRIVATE)
    private val _current = MutableStateFlow<Announcement?>(null)
    val current: StateFlow<Announcement?> = _current.asStateFlow()
    private val queue = ArrayDeque<Announcement>()
    private var polling = false

    fun start() {
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (isActive) {
                runCatching { repository.getJellioNotifications() }.onSuccess { list ->
                    val seen = prefs.getStringSet("seen", emptySet()).orEmpty().toMutableSet()
                    val now = System.currentTimeMillis()
                    list.filter { it.Kind == "announcement" && it.Id !in seen }
                        .sortedBy { it.CreatedUtc.orEmpty() }
                        .forEach { entry ->
                            seen.add(entry.Id)
                            val created = runCatching { Instant.parse(entry.CreatedUtc.orEmpty().let { if (it.endsWith("Z")) it else it + "Z" }).toEpochMilli() }.getOrNull()
                            val text = entry.Name?.trim().orEmpty()
                            val image = entry.ImageId?.let { repository.announcementImageUrl(it) }
                            if ((text.isNotEmpty() || image != null) && (created == null || now - created < MAX_AGE_MS)) {
                                queue.addLast(Announcement(text, image))
                            }
                        }
                    prefs.edit().putStringSet("seen", seen.toList().takeLast(200).toSet()).apply()
                    if (_current.value == null) showNext()
                }
                delay(POLL_MS)
            }
        }
    }

    private fun showNext() {
        val next = queue.removeFirstOrNull() ?: return
        _current.value = next
        viewModelScope.launch {
            delay(if (next.imageUrl != null) SHOW_MS + 4_000 else SHOW_MS)
            _current.value = null
            delay(400)
            showNext()
        }
    }
}

data class Announcement(val text: String, val imageUrl: String?)

// The toast itself: top of the screen, above Home, the player and every
// other screen, gone after a few seconds without needing the remote.
@Composable
fun AnnouncementToast(modifier: Modifier = Modifier, viewModel: AnnouncementViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.start() }
    val message by viewModel.current.collectAsState()
    val announcement = message ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(top = 28.dp)
            .widthIn(max = 720.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(JellioBgElevated)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Icon(imageVector = Icons.Filled.Campaign, contentDescription = null, tint = JellioSecondary, modifier = Modifier.size(28.dp))
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(text = "From the server", color = JellioTextSecondary, style = MaterialTheme.typography.labelMedium)
            if (announcement.imageUrl != null) {
                // Shown shrunk to fit, never full size.
                coil3.compose.AsyncImage(
                    model = announcement.imageUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 220.dp)
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            if (announcement.text.isNotEmpty()) {
                Text(
                    text = announcement.text,
                    color = JellioText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = if (announcement.imageUrl != null) 8.dp else 0.dp),
                )
            }
        }
    }
}
