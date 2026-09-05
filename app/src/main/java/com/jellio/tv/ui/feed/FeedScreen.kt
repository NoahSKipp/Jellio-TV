package com.jellio.tv.ui.feed

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.FeedEntryDto
import com.jellio.tv.ui.common.badgeActivityText
import com.jellio.tv.ui.common.formatRelativeTime
import com.jellio.tv.ui.common.rarityColor
import com.jellio.tv.ui.common.watchActivityText
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Real port of screens/feed.js's own renderFeed(): server wide "what
// has everyone been up to" (watch activity + badge unlocks, merged and
// privacy filtered server side already, Controllers/FeedController.cs's
// own header explains why), same real row shapes that file's own
// buildFeedRow() builds, one LazyColumn here instead of a plain scroll
// list of buttons.
@Composable
fun FeedScreen(
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    userImageUrl: (userId: String, tag: String?, maxWidth: Int) -> String,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = uiState.error ?: "Could not load the activity feed", color = JellioTextSecondary)
                    Surface(
                        onClick = { viewModel.retry() },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text(text = "Retry", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
                    }
                }
            }
            uiState.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing here yet.", color = JellioTextSecondary)
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(top = 32.dp).focusRestorer(),
                ) {
                    item {
                        Text(
                            text = "Feed",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 48.dp, bottom = 24.dp),
                        )
                    }
                    items(uiState.entries) { entry ->
                        FeedRow(
                            entry = entry,
                            imageUrl = imageUrl,
                            userImageUrl = userImageUrl,
                            onClick = { onUserClick(entry.UserId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(
    entry: FeedEntryDto,
    imageUrl: (String, String?, String, Int) -> String,
    userImageUrl: (String, String?, Int) -> String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 6.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.Kind == "Badge") {
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 96.dp)
                        .padding(end = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(rarityColor(entry.BadgeRarity).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = rarityColor(entry.BadgeRarity))
                }
            } else {
                var showFallback by remember(entry.ItemId) { mutableStateOf(false) }
                val posterId = entry.SeriesId ?: entry.ItemId
                if (showFallback || posterId == null) {
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 96.dp)
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(JellioBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Movie, contentDescription = null, tint = JellioTextSecondary)
                    }
                } else {
                    AsyncImage(
                        model = imageUrl(posterId, null, "Primary", 200),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { showFallback = true },
                        modifier = Modifier.size(width = 64.dp, height = 96.dp).padding(end = 16.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }

            Column {
                var avatarFallback by remember(entry.UserId) { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (avatarFallback) {
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape).background(JellioBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        AsyncImage(
                            model = userImageUrl(entry.UserId, null, 60),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onError = { avatarFallback = true },
                            modifier = Modifier.size(24.dp).clip(CircleShape),
                        )
                    }
                    Text(
                        text = entry.UserName ?: "",
                        color = JellioText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    Text(
                        text = " · " + formatRelativeTime(entry.OccurredAtUtc),
                        color = JellioTextSecondary,
                    )
                }
                val description = if (entry.Kind == "Badge") {
                    badgeActivityText(entry.BadgeName, entry.BadgeRarity)
                } else {
                    watchActivityText(
                        itemType = entry.ItemType,
                        seriesName = entry.SeriesName,
                        episodeCount = entry.EpisodeCount,
                        seasonNumber = entry.SeasonNumber,
                        firstEpisodeNumber = entry.FirstEpisodeNumber,
                        lastEpisodeNumber = entry.LastEpisodeNumber,
                        itemName = entry.ItemName,
                    )
                }
                Text(text = description, color = JellioText, modifier = Modifier.padding(top = 4.dp))
                if (entry.Kind == "Badge" && !entry.BadgeDescription.isNullOrEmpty()) {
                    Text(text = entry.BadgeDescription, color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
