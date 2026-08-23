package com.jellio.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.CircularProgressIndicator
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.PersonDto
import com.jellio.tv.data.model.TrailerDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.launch

private val HeroHeight = 620.dp

// Mirrors screens/detail.js's own real section order (hero, overview,
// seasons/episodes for a series, cast, trailers) and its own real
// backdrop fallback chain, not an independently designed TV layout: an
// Episode never carries its own BackdropImageTags (Jellyfin only ever
// stores backdrop art against a Movie/Series/Season), so its own
// series/season backdrop is next, then, only for an Episode with
// neither, its own Primary still.
private fun heroBackdropUrl(
    session: Session,
    item: BaseItemDto,
    rawImageUrl: (itemId: String, tag: String?, imageType: String) -> String,
): String? {
    item.BackdropImageTags?.firstOrNull()?.let { return rawImageUrl(item.Id, it, "Backdrop") }
    val parentTag = item.ParentBackdropImageTags?.firstOrNull()
    if (item.ParentBackdropItemId != null && parentTag != null) {
        return rawImageUrl(item.ParentBackdropItemId, parentTag, "Backdrop")
    }
    if (item.Type == "Episode") {
        item.ImageTags?.get("Primary")?.let { return rawImageUrl(item.Id, it, "Primary") }
    }
    return null
}

private fun formatRuntime(ticks: Long?): String {
    if (ticks == null || ticks <= 0) return ""
    val minutes = (ticks / 600000000L).toInt()
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

@Composable
fun DetailScreen(
    session: Session,
    itemId: String,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onOpenStreamPicker: (BaseItemDto) -> Unit,
    onPlayDirect: (itemId: String, mediaSourceId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(itemId) { viewModel.load(session, itemId) }

    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "Could not load this title", color = JellioTextSecondary)
            }
            uiState.item != null -> {
                val item = uiState.item!!
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        DetailHero(
                            session = session,
                            item = item,
                            state = uiState,
                            imageUrl = imageUrl,
                            onSeriesClick = onNavigateToDetail,
                            onPlay = {
                                val target = viewModel.resolvePlayTarget()
                                if (target != null) {
                                    scope.launch {
                                        // screens/detail.js's own real one-source fast
                                        // path (components/streamPicker.js's own
                                        // openStreamPicker): a picker with nothing to
                                        // pick between is not worth showing at all.
                                        val sources = viewModel.getMediaSources(session, target.Id)
                                        if (sources.size <= 1) {
                                            onPlayDirect(target.Id, sources.firstOrNull()?.Id)
                                        } else {
                                            onOpenStreamPicker(target)
                                        }
                                    }
                                }
                            },
                            onToggleWatchlist = { viewModel.toggleWatchlist(session) },
                            onToggleWatched = { viewModel.toggleWatched(session) },
                            onLike = { viewModel.setLike(session, true) },
                            onDislike = { viewModel.setLike(session, false) },
                        )
                    }
                    if (item.Type == "Series") {
                        item {
                            SeasonsSection(
                                session = session,
                                seriesId = item.Id,
                                state = uiState,
                                imageUrl = imageUrl,
                                onSelectSeason = { seasonId -> viewModel.selectSeason(session, item.Id, seasonId) },
                                onEpisodeClick = onNavigateToDetail,
                            )
                        }
                    }
                    if (uiState.cast.isNotEmpty()) {
                        item { CastRow(cast = uiState.cast, imageUrl = imageUrl) }
                    }
                    if (uiState.trailers.isNotEmpty()) {
                        item {
                            TrailersRow(trailers = uiState.trailers, onOpen = { url ->
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                                    )
                                }
                            })
                        }
                    }
                }
            }
        }

        Surface(
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.4f)),
            modifier = Modifier.padding(top = 32.dp, start = 32.dp).size(56.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = JellioText)
            }
        }
    }
}

@Composable
private fun DetailHero(
    session: Session,
    item: BaseItemDto,
    state: DetailUiState,
    imageUrl: (String, String?, String, Int) -> String,
    onSeriesClick: (String) -> Unit,
    onPlay: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleWatched: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
) {
    val backdropUrl = heroBackdropUrl(session, item) { id, tag, type -> imageUrl(id, tag, type, 1920) }
    Box(modifier = Modifier.fillMaxWidth().height(HeroHeight)) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(HeroHeight),
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(HeroHeight).background(
                Brush.verticalGradient(listOf(Color.Transparent, JellioBg), startY = 0f, endY = Float.POSITIVE_INFINITY),
            ),
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(HeroHeight).background(
                Brush.horizontalGradient(listOf(JellioBg.copy(alpha = 0.85f), Color.Transparent), endX = 1100f),
            ),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomStart).widthIn(max = 820.dp).padding(start = 56.dp, bottom = 56.dp, end = 32.dp),
        ) {
            if (item.Type == "Episode" && item.SeriesId != null && item.SeriesName != null) {
                Surface(
                    onClick = { onSeriesClick(item.SeriesId) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
                ) {
                    Text(text = item.SeriesName, color = JellioTextSecondary, style = MaterialTheme.typography.bodyLarge)
                }
            }

            val hasEpisodeCode = item.ParentIndexNumber != null && item.IndexNumber != null
            val titleText = if (item.Type == "Episode" && hasEpisodeCode) {
                "S${item.ParentIndexNumber} E${item.IndexNumber} · ${item.Name.orEmpty()}"
            } else {
                item.Name.orEmpty()
            }
            Text(text = titleText, style = MaterialTheme.typography.titleLarge, color = JellioText, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val year = if (item.Type == "Episode") null else item.ProductionYear?.toString()
                year?.let { Text(text = it, color = JellioTextSecondary) }
                formatRuntime(item.RunTimeTicks).takeIf { it.isNotEmpty() }?.let { Text(text = it, color = JellioTextSecondary) }
                item.OfficialRating?.let { Text(text = it, color = JellioTextSecondary) }
                item.CommunityRating?.let { Text(text = "%.1f ★".format(it), color = JellioTextSecondary) }
            }

            item.Genres?.takeIf { it.isNotEmpty() }?.let {
                Text(text = it.joinToString(", "), color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
            }

            item.Overview?.let {
                Text(
                    text = it,
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onPlay,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioText, contentColor = JellioBg),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Text(text = state.playLabel, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                IconActionButton(
                    icon = if (state.isWatchlisted) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkAdd,
                    active = state.isWatchlisted,
                    contentDescription = "Watchlist",
                    onClick = onToggleWatchlist,
                )
                IconActionButton(
                    icon = Icons.Filled.Check,
                    active = state.isWatched,
                    contentDescription = "Mark watched",
                    onClick = onToggleWatched,
                )
                IconActionButton(
                    icon = Icons.Filled.ThumbUp,
                    active = state.likes == true,
                    contentDescription = "Like",
                    onClick = onLike,
                )
                IconActionButton(
                    icon = Icons.Filled.ThumbDown,
                    active = state.likes == false,
                    contentDescription = "Dislike",
                    onClick = onDislike,
                )
            }
        }
    }
}

@Composable
private fun IconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) JellioText.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.1f),
            contentColor = JellioText,
        ),
        modifier = Modifier.size(56.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun SeasonsSection(
    session: Session,
    seriesId: String,
    state: DetailUiState,
    imageUrl: (String, String?, String, Int) -> String,
    onSelectSeason: (String) -> Unit,
    onEpisodeClick: (String) -> Unit,
) {
    if (state.seasons.isEmpty()) return
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(text = "Episodes", style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.seasons, key = { it.Id }) { season ->
                val selected = season.Id == state.selectedSeasonId
                Surface(
                    onClick = { onSelectSeason(season.Id) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) JellioText.copy(alpha = 0.18f) else Color.Transparent,
                        contentColor = if (selected) JellioText else JellioTextSecondary,
                    ),
                ) {
                    Text(text = season.Name.orEmpty(), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.selectedSeasonEpisodes, key = { it.Id }) { episode ->
                EpisodeCard(episode = episode, imageUrl = imageUrl, onClick = { onEpisodeClick(episode.Id) })
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: BaseItemDto, imageUrl: (String, String?, String, Int) -> String, onClick: () -> Unit) {
    val thumbUrl = episode.ImageTags?.get("Primary")?.let { imageUrl(episode.Id, it, "Primary", 500) }
        ?: episode.ParentThumbImageTag?.let { tag -> episode.ParentThumbItemId?.let { id -> imageUrl(id, tag, "Thumb", 500) } }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        modifier = Modifier.width(320.dp),
    ) {
        Column {
            Box(modifier = Modifier.width(320.dp).aspectRatio(16f / 9f)) {
                if (thumbUrl != null) {
                    AsyncImage(model = thumbUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                episode.IndexNumber?.let {
                    Text(
                        text = "E$it",
                        color = JellioText,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (episode.UserData?.Played == true) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = JellioText,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = episode.Name.orEmpty(), color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                episode.Overview?.let {
                    Text(text = it, color = JellioTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun CastRow(cast: List<PersonDto>, imageUrl: (String, String?, String, Int) -> String) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(text = "Cast", style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cast, key = { it.Id }) { person ->
                Column(modifier = Modifier.width(140.dp)) {
                    Box(modifier = Modifier.width(140.dp).aspectRatio(1f).clip(CircleShape).background(JellioBgElevated)) {
                        val tag = person.PrimaryImageTag
                        if (tag != null) {
                            AsyncImage(
                                model = imageUrl(person.Id, tag, "Primary", 300),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        text = person.Name.orEmpty(),
                        color = JellioText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    person.Role?.let {
                        Text(text = it, color = JellioTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailersRow(trailers: List<TrailerDto>, onOpen: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 48.dp)) {
        Text(text = "Trailers", style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(trailers) { trailer ->
                val url = trailer.Url ?: return@items
                Surface(
                    onClick = { onOpen(url) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
                    modifier = Modifier.width(280.dp),
                ) {
                    Column {
                        Box(modifier = Modifier.width(280.dp).aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null, tint = JellioText)
                        }
                        Text(
                            text = trailer.Name ?: "Trailer",
                            color = JellioText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
