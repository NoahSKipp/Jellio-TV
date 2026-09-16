package com.jellio.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.jellio.tv.ui.common.NoOpBringIntoViewSpec
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val HeroHeight = 480.dp

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

private val PREMIERE_DATE_FORMATS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd",
)

// Mirrors screens/detail.js's own meta row exactly: an Episode with a
// real PremiereDate shows that (new Date(...).toLocaleDateString()),
// everything else, including an Episode with none, falls back to
// ProductionYear.
private fun formatPremiereDate(raw: String?): String? {
    if (raw == null) return null
    for (pattern in PREMIERE_DATE_FORMATS) {
        val parser = SimpleDateFormat(pattern, Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = runCatching { parser.parse(raw) }.getOrNull() ?: continue
        return DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }
    return null
}

@Composable
@androidx.compose.foundation.ExperimentalFoundationApi
fun DetailScreen(
    session: Session,
    itemId: String,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToPerson: (String) -> Unit,
    onOpenStreamPicker: (BaseItemDto) -> Unit,
    onPlayDirect: (itemId: String, mediaSourceId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var episodeMenuTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    // Real bug found live testing on device: this screen is pushed as
    // new content to the right of SidebarNav (JellioRoute.isImmersive()
    // only ever excludes that rail for Player, not Detail), not
    // switched to the way a rail tap itself is, so nothing had ever
    // requested real focus into it - a reader pushing into a title from
    // a card had no possible interaction once here. MainActivity's own
    // central redirect now covers the generic case (any route push,
    // this one included) as a real fallback, but an explicit target
    // here is still worth keeping: precise (the real Play button, not
    // whatever a generic rightward spatial search happens to land on
    // first) and immediate, not waiting on that central mechanism's own
    // reactive retry loop to eventually find its own way in.
    // Real feedback live: cold focusRestorer() entry (the LazyColumn's
    // own default "first focusable descendant" pick, back when this
    // screen only ever requested focus onto the list itself) landed on
    // the series-name link above the hero's own Play button for an
    // Episode - the one earlier, purely incidental clickable Surface in
    // reading order, not a deliberate first stop. Targeted explicitly
    // now instead of left to that implicit default, same real lesson
    // ui/detail/StreamPickerOverlay.kt's own header on Nuvio's pre-warm
    // fix already covers: an explicit target beats trusting whichever
    // child a cold focus group happens to pick first.
    val playButtonFocusRequester = remember { FocusRequester() }
    // Hoisted so the hero-pin effect below can scrollToItem(0) on the
    // same real list this screen's own focusRestorer() still reads.
    val listState = rememberLazyListState()
    LaunchedEffect(itemId) { viewModel.load(session, itemId) }
    // Fires once the item actually loads and the LazyColumn below is
    // really composed, not in the load effect above: requesting focus
    // before that node exists would silently do nothing. Retried across
    // real frames (matching StreamPickerOverlay.kt's own real fix,
    // ported from Nuvio) rather than trusting a single call: this
    // screen only ever requests real focus once, nothing else competes
    // for it first the way Resume/chips did there, but the retry is
    // cheap insurance against that same class of timing gap regardless.
    LaunchedEffect(uiState.item) {
        if (uiState.item != null) {
            listState.scrollToItem(0)
            for (attempt in 0 until 30) {
                withFrameNanos {}
                runCatching { playButtonFocusRequester.requestFocus() }
            }
        }
    }
    // Real ui/home/HomeScreen.kt's own header on this exact mechanism
    // (an 8-round real fix there): Compose's own default per-child
    // bring-into-view request scrolls this list just far enough to
    // reveal whatever's newly focused inside the hero (the Play button,
    // sitting well down its own real bottom-aligned Column), cropping
    // the hero's own real backdrop/title above it rather than keeping
    // the full banner in view the way real feedback wants for every
    // real focus target inside it, Play included, not just the hero's
    // own real top edge. Ported wholesale rather than re-derived: same
    // real NestedScrollConnection blocking scroll from ever reaching
    // this list while the hero holds focus, same real
    // NoOpBringIntoViewSpec suppressing Compose's own default request
    // outright, same real reactive snapshotFlow correction as a
    // fallback for whatever either of those still misses.
    var heroHasFocus by remember { mutableStateOf(false) }
    val blockScrollWhileHeroFocused = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (heroHasFocus) available else Offset.Zero
        }
    }

    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = uiState.error ?: "Could not load this title", color = JellioTextSecondary)
                    Surface(
                        onClick = { viewModel.retry(session, itemId) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                        modifier = Modifier.padding(top = 20.dp),
                    ) {
                        Text(text = "Retry", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
                    }
                }
            }
            uiState.item != null -> {
                val item = uiState.item!!
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(blockScrollWhileHeroFocused).focusRestorer(),
                ) {
                    item {
                        // heroHasFocus (declared above, shared with the
                        // nestedScroll connection) is the one real place
                        // that sets it; the reactive correction below
                        // reads it back to know how long to keep
                        // watching this list's own scroll position.
                        LaunchedEffect(heroHasFocus) {
                            if (heroHasFocus) {
                                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                                    .collect { (index, offset) ->
                                        if (index != 0 || offset != 0) listState.scrollToItem(0)
                                    }
                            }
                        }
                        Box(modifier = Modifier.onFocusChanged { heroHasFocus = it.hasFocus }) {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides NoOpBringIntoViewSpec) {
                                DetailHero(
                                    session = session,
                                    item = item,
                                    state = uiState,
                                    imageUrl = imageUrl,
                                    onSeriesClick = onNavigateToDetail,
                                    playButtonFocusRequester = playButtonFocusRequester,
                                    onPlay = {
                                // screens/detail.js's own real playButton.disabled
                                // = true while its own targetPromise is still
                                // resolving, re-enabled once it settles either
                                // way (its own .finally()).
                                scope.launch {
                                    viewModel.setResolvingPlay(true)
                                    try {
                                        val target = viewModel.resolvePlayTarget()
                                        if (target != null) {
                                            when (val action = viewModel.resolvePlayAction(session, target)) {
                                                is PlayAction.Direct -> onPlayDirect(action.itemId, action.mediaSourceId)
                                                is PlayAction.ShowPicker -> onOpenStreamPicker(action.item)
                                            }
                                        }
                                    } finally {
                                        viewModel.setResolvingPlay(false)
                                    }
                                }
                            },
                            onChangeStream = if (item.Type != "Series") {
                                {
                                    scope.launch {
                                        val target = viewModel.resolvePlayTarget()
                                        if (target != null) {
                                            when (val action = viewModel.resolvePlayAction(session, target, forceChoice = true)) {
                                                is PlayAction.Direct -> onPlayDirect(action.itemId, action.mediaSourceId)
                                                is PlayAction.ShowPicker -> onOpenStreamPicker(action.item)
                                            }
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                            onToggleWatchlist = { viewModel.toggleWatchlist(session) },
                            onToggleWatched = { viewModel.toggleWatched(session) },
                            onLike = { viewModel.setLike(session, true) },
                            onDislike = { viewModel.setLike(session, false) },
                                )
                            }
                        }
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
                                onEpisodeOptions = { episode -> episodeMenuTarget = episode },
                            )
                        }
                    }
                    if (uiState.cast.isNotEmpty()) {
                        item { CastRow(cast = uiState.cast, imageUrl = imageUrl, onPersonClick = onNavigateToPerson) }
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

        val menuTarget = episodeMenuTarget
        if (menuTarget != null) {
            val index = uiState.selectedSeasonEpisodes.indexOfFirst { it.Id == menuTarget.Id }
            EpisodeOptionsMenu(
                episode = menuTarget,
                hasPrevious = index > 0,
                onToggleWatched = { viewModel.toggleEpisodeWatched(session, menuTarget) },
                onMarkPreviousWatched = { viewModel.markPreviousWatched(session, menuTarget) },
                onMarkSeasonWatched = { viewModel.markSeasonWatched(session) },
                onDismiss = { episodeMenuTarget = null },
            )
        }
    }
}

// Real port of screens/detail.js's own openEpisodeOptionsMenu(): the
// same real three actions (no Play Manually here, that only ever made
// sense for a Continue Watching card with a resume position to skip
// past), reached through this screen's own options button rather than
// that file's own hold/right-click (no direct D-pad equivalent for a
// hold gesture worth trusting untested).
@Composable
private fun EpisodeOptionsMenu(
    episode: BaseItemDto,
    hasPrevious: Boolean,
    onToggleWatched: () -> Unit,
    onMarkPreviousWatched: () -> Unit,
    onMarkSeasonWatched: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val isPlayed = episode.UserData?.Played == true
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp),
        ) {
            EpisodeMenuRow(label = if (isPlayed) "Mark as unwatched" else "Mark as watched", onClick = { onToggleWatched(); onDismiss() })
            if (hasPrevious) {
                EpisodeMenuRow(label = "Mark previous as watched", onClick = { onMarkPreviousWatched(); onDismiss() })
            }
            EpisodeMenuRow(label = "Mark season as watched", onClick = { onMarkSeasonWatched(); onDismiss() })
        }
    }
}

@Composable
private fun EpisodeMenuRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
    }
}

@Composable
private fun DetailHero(
    session: Session,
    item: BaseItemDto,
    state: DetailUiState,
    imageUrl: (String, String?, String, Int) -> String,
    onSeriesClick: (String) -> Unit,
    playButtonFocusRequester: FocusRequester,
    onPlay: () -> Unit,
    onChangeStream: (() -> Unit)?,
    onToggleWatchlist: () -> Unit,
    onToggleWatched: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
) {
    val backdropUrl = heroBackdropUrl(session, item) { id, tag, type -> imageUrl(id, tag, type, 1920) }
    val heroHeight = HeroHeight.scaled()
    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(heroHeight),
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(heroHeight).background(
                Brush.verticalGradient(listOf(Color.Transparent, JellioBg), startY = 0f, endY = Float.POSITIVE_INFINITY),
            ),
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(heroHeight).background(
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
                val year = if (item.Type == "Episode") {
                    formatPremiereDate(item.PremiereDate) ?: item.ProductionYear?.toString()
                } else {
                    item.ProductionYear?.toString()
                }
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

            // Real port of js/detailButtonsExpand.js's own real
            // collapse/expand: Play and the "..." trigger sit alone at
            // rest, the same real Watchlist/Mark watched/Like/Dislike/
            // Change Stream buttons that file's own real
            // jellio-buttons-expanded class reveals only render into
            // this composition (and so only ever become real focus
            // targets) once expanded here, no separate real
            // tabindex-sync concept needed the way that file's own
            // header explains plain CSS visibility forced there.
            var actionsExpanded by remember { mutableStateOf(false) }
            Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onPlay,
                    enabled = !state.resolvingPlay,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioText, contentColor = JellioBg),
                    modifier = Modifier.focusRequester(playButtonFocusRequester),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Text(text = state.playLabel, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                AnimatedVisibility(visible = actionsExpanded, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        if (onChangeStream != null) {
                            IconActionButton(
                                icon = Icons.Filled.SwapHoriz,
                                active = false,
                                contentDescription = "Change Stream",
                                onClick = onChangeStream,
                            )
                        }
                    }
                }
                IconActionButton(
                    icon = Icons.Filled.MoreVert,
                    active = actionsExpanded,
                    contentDescription = if (actionsExpanded) "Fewer actions" else "More actions",
                    onClick = { actionsExpanded = !actionsExpanded },
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
    onEpisodeOptions: (BaseItemDto) -> Unit,
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
                    // Real bug found live against a real screenshot: a
                    // translucent white fill (JellioText.copy(alpha =
                    // 0.18f)) behind the same real white JellioText it
                    // was tinting read as barely-there, near-illegible
                    // once this tab was both selected and focused (the
                    // real screenshot's own Season 01), since neither
                    // was set explicitly here and ClickableSurfaceDefaults.
                    // colors() fell back to its own real default focused
                    // pair rather than this tab's own real selected one
                    // the moment it also had real focus. This is a plain
                    // ClickableSurfaceDefaults surface, not a selectable
                    // one (selected here is this tab's own real boolean,
                    // not Surface's own selected param SelectableSurfaceDefaults
                    // alone accepts), so containerColor/contentColor
                    // already fold that state in; focusedContainerColor/
                    // focusedContentColor just needed to be a real solid,
                    // opposite-luminance pair too instead of defaulting,
                    // same real pairing ui/library/LibraryScreen.kt's own
                    // FilterChip already uses.
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) JellioSecondary else JellioBgElevated,
                        contentColor = if (selected) JellioBg else JellioText,
                        focusedContainerColor = JellioSecondary,
                        focusedContentColor = JellioBg,
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
                EpisodeCard(
                    episode = episode,
                    imageUrl = imageUrl,
                    onClick = { onEpisodeClick(episode.Id) },
                    onOptionsClick = { onEpisodeOptions(episode) },
                )
            }
        }
    }
}

// The options button is a separate, independently focusable Surface
// sibling to the main clickable card rather than nested inside it
// (Compose's own clickable-inside-clickable is ambiguous, real click
// dispatch there is not something to trust without a device to test
// on): a real D-pad-navigable stand in for screens/detail.js's own
// hold/right-click trigger on this same card.
@Composable
private fun EpisodeCard(
    episode: BaseItemDto,
    imageUrl: (String, String?, String, Int) -> String,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
) {
    val thumbUrl = episode.ImageTags?.get("Primary")?.let { imageUrl(episode.Id, it, "Primary", 500) }
        ?: episode.ParentThumbImageTag?.let { tag -> episode.ParentThumbItemId?.let { id -> imageUrl(id, tag, "Thumb", 500) } }
    val cardWidth = 320.dp.scaled()
    Box(modifier = Modifier.width(cardWidth)) {
        Surface(
            onClick = onClick,
            onLongClick = onOptionsClick,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
            modifier = Modifier.width(cardWidth),
        ) {
            Column {
                Box(modifier = Modifier.width(cardWidth).aspectRatio(16f / 9f)) {
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
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
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
}

@Composable
private fun CastRow(cast: List<PersonDto>, imageUrl: (String, String?, String, Int) -> String, onPersonClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(text = "Cast", style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        val avatarWidth = 140.dp.scaled()
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cast, key = { it.Id }) { person ->
                Surface(
                    onClick = { onPersonClick(person.Id) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.width(avatarWidth),
                ) {
                    Column {
                        Box(modifier = Modifier.width(avatarWidth).aspectRatio(1f).clip(CircleShape).background(JellioBgElevated)) {
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
}

@Composable
private fun TrailersRow(trailers: List<TrailerDto>, onOpen: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 48.dp)) {
        Text(text = "Trailers", style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        val trailerCardWidth = 280.dp.scaled()
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(trailers) { trailer ->
                val url = trailer.Url ?: return@items
                Surface(
                    onClick = { onOpen(url) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                    modifier = Modifier.width(trailerCardWidth),
                ) {
                    Column {
                        Box(modifier = Modifier.width(trailerCardWidth).aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
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
