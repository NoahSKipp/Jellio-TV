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
import androidx.compose.foundation.layout.Arrangement
import com.jellio.tv.ui.theme.JellioSecondary
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var hiddenTypes by remember { mutableStateOf(FeedFilterPrefs.load(context)) }

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
                val tvEntries = uiState.entries.filter { feedType(it) !in READING_TYPES }
                val presentTypes = tvEntries.map { feedType(it) }.toSet()
                val visible = tvEntries.filter { feedType(it) !in hiddenTypes }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(top = 32.dp).focusRestorer(),
                ) {
                    item {
                        Text(
                            text = "Feed",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 48.dp, bottom = 16.dp),
                        )
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 16.dp),
                        ) {
                            FEED_TYPES.filter { it.key in presentTypes }.forEach { type ->
                                val off = type.key in hiddenTypes
                                FeedFilterChip(label = type.label, off = off, onClick = {
                                    hiddenTypes = if (off) hiddenTypes - type.key else hiddenTypes + type.key
                                    FeedFilterPrefs.save(context, hiddenTypes)
                                })
                            }
                        }
                    }
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                text = "Nothing to show with these filters.",
                                color = JellioTextSecondary,
                                modifier = Modifier.padding(start = 48.dp, top = 8.dp),
                            )
                        }
                    }
                    items(visible) { entry ->
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

private data class FeedType(val key: String, val label: String)

// The TV has no books, manga or audiobooks, so their feed entries never
// show here (see READING_TYPES).
private val FEED_TYPES = listOf(
    FeedType("movies", "Movies"),
    FeedType("shows", "Shows"),
    FeedType("badges", "Badges"),
)

private val READING_TYPES = setOf("books", "manga", "audiobooks")

private fun feedType(entry: FeedEntryDto): String = when {
    entry.Kind == "Badge" -> "badges"
    entry.ItemType == "Episode" || entry.ItemType == "Series" || entry.ItemType == "Season" -> "shows"
    entry.ItemType == "Book" -> "books"
    entry.ItemType == "Manga" -> "manga"
    entry.ItemType == "AudioBook" -> "audiobooks"
    else -> "movies"
}

// Which feed types are switched off; all on to begin with.
private object FeedFilterPrefs {
    private const val PREFS = "jellio_feed"
    private const val KEY = "hidden_types"
    private val DEFAULT_HIDDEN = emptySet<String>()

    fun load(context: android.content.Context): Set<String> =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getStringSet(KEY, null)?.toSet() ?: DEFAULT_HIDDEN

    fun save(context: android.content.Context, hidden: Set<String>) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putStringSet(KEY, hidden).apply()
    }
}

// A switched-off type stays on screen, dimmed and struck through.
@Composable
private fun FeedFilterChip(label: String, off: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (off) Color.Transparent else JellioSecondary,
            contentColor = if (off) JellioTextSecondary else JellioBg,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (off) 0.25f else 0f)),
                shape = RoundedCornerShape(999.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(999.dp),
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(
            text = label,
            textDecoration = if (off) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
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
