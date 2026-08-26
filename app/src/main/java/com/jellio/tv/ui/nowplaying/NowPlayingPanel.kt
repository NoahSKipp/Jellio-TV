package com.jellio.tv.ui.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.NowPlayingItemDto
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Real port of components/nowPlaying.js's own trigger button: same
// real place a reader can always reach it from (rendered alongside
// SidebarNav on every non-immersive screen, this app's own real
// equivalent of that file's own sidebar-anchored button), the real
// active-session count as a badge rather than a bare icon.
@Composable
fun NowPlayingButton(sessionCount: Int, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated.copy(alpha = 0.96f),
            contentColor = JellioText,
        ),
        modifier = modifier.size(52.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Filled.LiveTv, contentDescription = "Now playing", modifier = Modifier.size(22.dp))
            if (sessionCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                        .size(18.dp)
                        .background(JellioSecondary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = sessionCount.toString(), color = JellioText, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

// Real port of components/nowPlaying.js's own render(): a plain
// vertical list, one row per real active session, "Nothing playing
// right now" the same real empty state that file's own panel shows
// rather than an empty list with no explanation.
@Composable
fun NowPlayingPanel(
    sessions: List<NowPlayingSessionDto>,
    imageUrl: (String, String?, String, Int) -> String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(top = 92.dp, end = 24.dp)
                .widthIn(min = 320.dp, max = 380.dp)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Now Playing",
                    color = JellioText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = JellioText),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (sessions.isEmpty()) {
                Text(
                    text = "Nothing playing right now",
                    color = JellioTextSecondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(horizontal = 12.dp)) {
                    items(sessions, key = { it.Id ?: it.hashCode().toString() }) { session ->
                        NowPlayingRow(session = session, imageUrl = imageUrl)
                    }
                }
            }
        }
    }
}

// Real port of components/nowPlaying.js's own displayTitle(): an
// Episode's own real SeriesName, not its own episode-only Name, same
// real distinction every other row in this app already draws.
private fun displayTitle(item: NowPlayingItemDto): String =
    if (item.Type == "Episode" && !item.SeriesName.isNullOrEmpty()) item.SeriesName else item.Name.orEmpty()

// Real port of that file's own subtitle(): a real S# E# code for an
// Episode, its own real ProductionYear for anything else.
private fun subtitle(item: NowPlayingItemDto): String {
    if (item.Type == "Episode") {
        return if (item.ParentIndexNumber != null && item.IndexNumber != null) {
            "S${item.ParentIndexNumber} E${item.IndexNumber}"
        } else {
            ""
        }
    }
    return item.ProductionYear?.toString() ?: ""
}

@Composable
private fun NowPlayingRow(
    session: NowPlayingSessionDto,
    imageUrl: (String, String?, String, Int) -> String,
) {
    val item = session.Item
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (item != null) {
            // Real components/nowPlaying.js's own imageId choice: an
            // Episode's own real series poster reads better in a
            // narrow row than its own real episode thumbnail does,
            // same real series-aware fallback this app's own player
            // pause overlay already uses.
            val seriesId = item.SeriesId
            val imageId = if (item.Type == "Episode" && seriesId != null) seriesId else item.Id
            AsyncImage(
                model = imageUrl(imageId, null, "Primary", 200),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(48.dp).height(72.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Box(modifier = Modifier.width(48.dp).height(72.dp).background(JellioBg, RoundedCornerShape(6.dp)))
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = item?.let { displayTitle(it) } ?: "",
                color = JellioText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val metaBits = listOfNotNull(
                session.UserName?.takeIf { it.isNotEmpty() },
                item?.let { subtitle(it) }?.takeIf { it.isNotEmpty() },
                if (session.IsPaused) "Paused" else "Playing",
            )
            Text(
                text = metaBits.joinToString(" • "),
                color = JellioTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
