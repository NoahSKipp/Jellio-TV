package com.jellio.tv.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
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
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Everyone's activity (screens/feed.js on the web): a list on the left,
// grouped by day, and the focused entry shown large on the right. The
// type chips along the top switch kinds of activity on and off.
@Composable
fun FeedScreen(
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    userImageUrl: (userId: String, tag: String?, maxWidth: Int) -> String,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var hiddenTypes by remember { mutableStateOf(FeedFilterPrefs.load(context)) }
    // Read only by the spotlight, so moving through the list redraws it alone.
    val focusedEntry = remember { mutableStateOf<FeedEntryDto?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.jellio.tv.ui.common.ScreenSpinner()
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = uiState.error ?: com.jellio.tv.ui.i18n.string("feed_load_error"), color = JellioTextSecondary)
                    Surface(
                        onClick = { viewModel.retry() },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text(text = com.jellio.tv.ui.i18n.string("action_retry"), modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
                    }
                }
            }
            uiState.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = com.jellio.tv.ui.i18n.string("status_empty"), color = JellioTextSecondary)
            }
            else -> {
                val counts = remember(uiState.entries) { uiState.entries.groupingBy(::feedType).eachCount() }
                val visible = remember(uiState.entries, hiddenTypes) { uiState.entries.filter { feedType(it) !in hiddenTypes } }
                val groups = remember(visible) { groupByDay(visible) }
                Column(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
                    Text(
                        text = "Feed",
                        color = JellioText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 48.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp),
                        modifier = Modifier.padding(top = 14.dp, bottom = 18.dp),
                    ) {
                        items(FEED_TYPES, key = { it.key }) { type ->
                            val off = type.key in hiddenTypes
                            FeedFilterChip(label = type.label, count = counts[type.key] ?: 0, off = off, onClick = {
                                hiddenTypes = if (off) hiddenTypes - type.key else hiddenTypes + type.key
                                FeedFilterPrefs.save(context, hiddenTypes)
                            })
                        }
                    }
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.width(420.dp).fillMaxHeight()) {
                            if (visible.isEmpty()) {
                                Text(
                                    text = "Nothing to show with these filters.",
                                    color = JellioTextSecondary,
                                    modifier = Modifier.padding(start = 48.dp, top = 8.dp),
                                )
                            } else {
                                FeedList(
                                    groups = groups,
                                    imageUrl = imageUrl,
                                    userImageUrl = userImageUrl,
                                    focusedEntry = focusedEntry,
                                    onUserClick = onUserClick,
                                )
                            }
                        }
                        FeedSpotlight(
                            entry = focusedEntry,
                            fallback = visible.firstOrNull(),
                            imageUrl = imageUrl,
                            userImageUrl = userImageUrl,
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 32.dp, bottom = 28.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class FeedGroup(val label: String, val entries: List<FeedEntryDto>)

private fun groupByDay(entries: List<FeedEntryDto>): List<FeedGroup> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    fun label(entry: FeedEntryDto): String {
        val day = runCatching { Instant.parse(entry.OccurredAtUtc).atZone(zone).toLocalDate() }.getOrNull() ?: return "Earlier"
        val daysAgo = ChronoUnit.DAYS.between(day, today)
        return when {
            daysAgo <= 0L -> com.jellio.tv.ui.i18n.Strings.get("calendar_today")
            daysAgo == 1L -> com.jellio.tv.ui.i18n.Strings.get("time_yesterday")
            daysAgo < 7L -> "This week"
            else -> com.jellio.tv.ui.i18n.Strings.get("time_earlier")
        }
    }
    return entries.groupBy(::label).map { (label, list) -> FeedGroup(label, list) }
}

@Composable
private fun FeedList(
    groups: List<FeedGroup>,
    imageUrl: (String, String?, String, Int) -> String,
    userImageUrl: (String, String?, Int) -> String,
    focusedEntry: MutableState<FeedEntryDto?>,
    onUserClick: (String) -> Unit,
) {
    // Entering the list from the filters lands on the newest entry, not the one nearest the focused chip.
    val firstEntry = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    val first = groups.firstOrNull()?.entries?.firstOrNull()
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 48.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().focusRestorer(firstEntry),
    ) {
        groups.forEach { group ->
            item(key = "header:" + group.label) {
                Text(
                    text = group.label.uppercase(),
                    color = JellioTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            items(group.entries, key = { it.UserId + it.Kind + it.OccurredAtUtc + (it.ItemId ?: it.BadgeId ?: "") }) { entry ->
                FeedListItem(
                    entry = entry,
                    imageUrl = imageUrl,
                    userImageUrl = userImageUrl,
                    onFocus = { focusedEntry.value = entry },
                    onClick = { onUserClick(entry.UserId) },
                    modifier = if (entry === first) Modifier.focusRequester(firstEntry) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun FeedListItem(
    entry: FeedEntryDto,
    imageUrl: (String, String?, String, Int) -> String,
    userImageUrl: (String, String?, Int) -> String,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated.copy(alpha = 0.6f),
            contentColor = JellioText,
            focusedContainerColor = JellioBgElevated,
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(14.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                FeedArtwork(entry = entry, imageUrl = imageUrl, width = 48, height = 72)
                UserAvatar(
                    userId = entry.UserId,
                    userImageUrl = userImageUrl,
                    size = 24,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 6.dp)
                        .border(2.dp, JellioBg, CircleShape),
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.UserName.orEmpty(),
                        color = JellioText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = "  " + formatRelativeTime(entry.OccurredAtUtc),
                        color = JellioTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
                Text(
                    text = activityText(entry),
                    color = JellioText.copy(alpha = 0.88f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun activityText(entry: FeedEntryDto) = if (entry.Kind == "Badge") {
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

@Composable
private fun FeedArtwork(entry: FeedEntryDto, imageUrl: (String, String?, String, Int) -> String, width: Int, height: Int) {
    val shape = RoundedCornerShape(if (width > 80) 12.dp else 8.dp)
    if (entry.Kind == "Badge") {
        val color = rarityColor(entry.BadgeRarity)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = width.dp, height = height.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.12f)))),
        ) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = color, modifier = Modifier.size((width * 0.55f).dp))
        }
        return
    }
    val posterId = entry.SeriesId ?: entry.ItemId
    var failed by remember(posterId) { mutableStateOf(false) }
    if (posterId == null || failed) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = width.dp, height = height.dp).clip(shape).background(JellioBgElevated),
        ) {
            Icon(Icons.Filled.Movie, contentDescription = null, tint = JellioTextSecondary)
        }
    } else {
        AsyncImage(
            model = imageUrl(posterId, null, "Primary", width * 4),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = Modifier.size(width = width.dp, height = height.dp).clip(shape),
        )
    }
}

@Composable
private fun UserAvatar(userId: String, userImageUrl: (String, String?, Int) -> String, size: Int, modifier: Modifier = Modifier) {
    var failed by remember(userId) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size.dp).clip(CircleShape).background(JellioBgElevated),
    ) {
        if (failed) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size((size * 0.6f).dp))
        } else {
            AsyncImage(
                model = userImageUrl(userId, null, size * 3),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// The focused entry, large: its artwork behind, who did what and when.
@Composable
private fun FeedSpotlight(
    entry: State<FeedEntryDto?>,
    fallback: FeedEntryDto?,
    imageUrl: (String, String?, String, Int) -> String,
    userImageUrl: (String, String?, Int) -> String,
    modifier: Modifier = Modifier,
) {
    val shown = entry.value ?: fallback ?: return
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(JellioBgElevated.copy(alpha = 0.5f))) {
        if (shown.Kind == "Badge") {
            val color = rarityColor(shown.BadgeRarity)
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)),
                ),
            )
        } else {
            val artId = shown.SeriesId ?: shown.ItemId
            if (artId != null) {
                val candidates = remember(artId) { listOf(imageUrl(artId, null, "Backdrop", 1280), imageUrl(artId, null, "Primary", 900)) }
                var index by remember(artId) { mutableIntStateOf(0) }
                candidates.getOrNull(index)?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        onError = { index += 1 },
                        modifier = Modifier.fillMaxSize().drawWithCache {
                            val fade = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to JellioBg.copy(alpha = 0.25f),
                                0.75f to JellioBg.copy(alpha = 0.85f),
                                1f to JellioBg,
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(fade)
                            }
                        },
                    )
                }
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(28.dp).widthIn(max = 560.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                FeedArtwork(entry = shown, imageUrl = imageUrl, width = 104, height = 156)
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(userId = shown.UserId, userImageUrl = userImageUrl, size = 36)
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(text = shown.UserName.orEmpty(), color = JellioText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(text = formatRelativeTime(shown.OccurredAtUtc), color = JellioTextSecondary, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = activityText(shown),
                        color = JellioText,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (shown.Kind == "Badge" && !shown.BadgeDescription.isNullOrEmpty()) {
                        Text(
                            text = shown.BadgeDescription,
                            color = JellioTextSecondary,
                            fontSize = 15.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class FeedType(val key: String, val label: String)

// Every type is always offered; the reading and listening ones start
// switched off on the TV (FeedFilterPrefs).
private val FEED_TYPES = listOf(
    FeedType("movies", "Movies"),
    FeedType("shows", "Shows"),
    FeedType("books", "Books"),
    FeedType("manga", "Manga"),
    FeedType("audiobooks", "Audiobooks"),
    FeedType("badges", "Badges"),
)

private fun feedType(entry: FeedEntryDto): String = when {
    entry.Kind == "Badge" -> "badges"
    entry.ItemType == "Episode" || entry.ItemType == "Series" || entry.ItemType == "Season" -> "shows"
    entry.ItemType == "Book" -> "books"
    entry.ItemType == "Manga" -> "manga"
    entry.ItemType == "AudioBook" -> "audiobooks"
    else -> "movies"
}

// Which feed types are switched off. On the TV books, manga and
// audiobooks start off, so the feed opens on movies and shows.
private object FeedFilterPrefs {
    private const val PREFS = "jellio_feed"
    private const val KEY = "hidden_types_v2"
    private val DEFAULT_HIDDEN = setOf("books", "manga", "audiobooks")

    fun load(context: android.content.Context): Set<String> =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getStringSet(KEY, null)?.toSet() ?: DEFAULT_HIDDEN

    fun save(context: android.content.Context, hidden: Set<String>) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putStringSet(KEY, hidden).apply()
    }
}

// A switched-off type stays on screen, dimmed and struck through.
@Composable
private fun FeedFilterChip(label: String, count: Int, off: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (off) Color.Transparent else JellioSecondary,
            contentColor = if (off) JellioTextSecondary else JellioBg,
            focusedContainerColor = if (off) Color.White.copy(alpha = 0.16f) else Color.White,
            focusedContentColor = if (off) JellioText else JellioBg,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = if (off) 0.25f else 0f)),
                shape = RoundedCornerShape(999.dp),
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(999.dp),
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (!off) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
            }
            Text(
                text = if (count > 0) "$label  $count" else label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (off) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
            )
        }
    }
}
