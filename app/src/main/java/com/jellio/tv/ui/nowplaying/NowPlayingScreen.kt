package com.jellio.tv.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NowPlayingUiState(
    val loaded: Boolean = false,
    val sessions: List<NowPlayingSessionDto> = emptyList(),
    val error: Boolean = false,
)

// components/nowPlaying.js on the TV: who is watching, listening to or
// reading what, refreshed every few seconds while the screen is open.
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val repository: JellioRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()
    private var polling = false

    fun start() {
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (isActive) {
                runCatching { repository.getNowPlaying() }
                    .onSuccess { _uiState.value = NowPlayingUiState(loaded = true, sessions = it) }
                    .onFailure { _uiState.value = _uiState.value.copy(loaded = true, error = true) }
                delay(10_000)
            }
        }
    }
}

private data class DeviceKind(val icon: ImageVector, val label: String)

// Same rules as components/nowPlaying.js deviceIcon(): what the client and
// device names say about the hardware.
private fun deviceKind(session: NowPlayingSessionDto): DeviceKind? {
    val text = "${session.Client.orEmpty()} ${session.DeviceName.orEmpty()}".lowercase()
    if (text.isBlank()) return null
    return when {
        Regex("chromecast|crkey|\\bcast\\b").containsMatchIn(text) -> DeviceKind(Icons.Filled.Cast, "Chromecast")
        Regex("\\btv\\b|android tv|google tv|fire ?tv|aft\\w|shield|roku|kodi|webos|web0s|tizen|bravia|apple tv|tvos|xbox|playstation|smart-?tv").containsMatchIn(text) ->
            DeviceKind(Icons.Filled.Tv, "TV")
        Regex("ipad|tablet").containsMatchIn(text) -> DeviceKind(Icons.Filled.TabletMac, "Tablet")
        Regex("iphone|ipod|android|mobile|findroid|swiftfin|phone|pixel|galaxy").containsMatchIn(text) -> DeviceKind(Icons.Filled.Smartphone, "Phone")
        Regex("windows|mac|linux|chromeos|desktop|media player|jellyfin web|browser|chrome|firefox|edge|safari|opera|jellio").containsMatchIn(text) ->
            DeviceKind(Icons.Filled.Computer, "Computer")
        else -> DeviceKind(Icons.Filled.Devices, "Device")
    }
}

private fun titleOf(session: NowPlayingSessionDto): String {
    val item = session.Item
    return when {
        item.Type == "Episode" && !item.SeriesName.isNullOrBlank() -> item.SeriesName
        item.Type == "AudioBook" && !item.Album.isNullOrBlank() -> item.Album
        else -> item.Name.orEmpty()
    }
}

private fun subtitleOf(session: NowPlayingSessionDto): String? {
    val item = session.Item
    if (item.Type != "Episode") return null
    val code = if (item.ParentIndexNumber != null && item.IndexNumber != null) "S${item.ParentIndexNumber}E${item.IndexNumber}" else null
    return listOfNotNull(code, item.Name).joinToString(" · ").ifBlank { null }
}

private fun statusOf(session: NowPlayingSessionDto): String = when {
    session.Activity == "reading" -> "Reading"
    session.IsPaused -> "Paused"
    session.Activity == "listening" -> "Listening"
    else -> "Watching"
}

@Composable
fun NowPlayingScreen(
    imageUrl: (itemId: String, imageType: String, maxWidth: Int) -> String,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.start() }

    Column(modifier = modifier.padding(start = 48.dp, end = 48.dp, top = 32.dp)) {
        Text(text = "Watching now", style = MaterialTheme.typography.titleLarge, color = JellioText)
        val message = when {
            !state.loaded -> "Loading…"
            state.sessions.isEmpty() && state.error -> "Couldn't reach the server."
            state.sessions.isEmpty() -> "Nobody is watching anything right now."
            else -> null
        }
        if (message != null) {
            Text(text = message, color = JellioTextSecondary, modifier = Modifier.padding(top = 16.dp))
            return@Column
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 48.dp),
        ) {
            items(state.sessions, key = { it.Id }) { session ->
                SessionRow(session = session, imageUrl = imageUrl, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: NowPlayingSessionDto,
    imageUrl: (itemId: String, imageType: String, maxWidth: Int) -> String,
    onOpen: (String) -> Unit,
) {
    val item = session.Item
    val target = when {
        item.Type == "Episode" && item.SeriesId != null -> item.SeriesId
        item.Type == "AudioBook" && item.AlbumId != null -> item.AlbumId
        else -> item.Id
    }
    val device = deviceKind(session)
    Surface(
        onClick = { if (target != null && session.Activity != "reading") onOpen(target) },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.2f),
            focusedContentColor = JellioText,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                if (target != null) {
                    AsyncImage(
                        model = imageUrl(target, "Primary", 160),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 18.dp)) {
                Text(text = titleOf(session), style = MaterialTheme.typography.titleMedium, color = JellioText, maxLines = 1)
                subtitleOf(session)?.let { Text(text = it, color = JellioTextSecondary, maxLines = 1) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    if (device != null) {
                        Icon(
                            imageVector = device.icon,
                            contentDescription = device.label,
                            tint = JellioTextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    val where = listOfNotNull(session.DeviceName, session.Client).distinct().joinToString(" · ")
                    Text(
                        text = listOfNotNull(session.UserName, statusOf(session), where.ifBlank { null }).joinToString(" • "),
                        color = JellioTextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(start = if (device != null) 8.dp else 0.dp),
                    )
                }
            }
        }
    }
}
