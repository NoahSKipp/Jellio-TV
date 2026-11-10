package com.jellio.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import com.jellio.tv.ui.common.dismissOnBack
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
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
import com.jellio.tv.ui.common.MediaTechBadgesRow
import com.jellio.tv.ui.common.NoOpBringIntoViewSpec
import com.jellio.tv.ui.common.episodeReleaseLabel
import com.jellio.tv.ui.common.seasonEpisodeReleaseLabels
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioRarityLegendary
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val HeroHeight = 480.dp

private fun formatRuntime(ticks: Long?): String {
    if (ticks == null || ticks <= 0) return ""
    val totalMinutes = (ticks / 600000000L).toInt()
    return com.jellio.tv.ui.i18n.formatDurationMinutes(totalMinutes)
}

private val PREMIERE_DATE_FORMATS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd",
)

private fun formatPremiereDate(raw: String?): String? {
    if (raw == null) return null
    for (pattern in PREMIERE_DATE_FORMATS) {
        val parser = SimpleDateFormat(pattern, Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = runCatching { parser.parse(raw) }.getOrNull() ?: continue
        return DateFormat.getDateInstance(DateFormat.SHORT, com.jellio.tv.ui.i18n.displayLocale()).format(date)
    }
    return null
}

@Composable
@androidx.compose.foundation.ExperimentalFoundationApi
fun DetailScreen(
    session: Session,
    itemId: String,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToDetailReplace: (String) -> Unit = onNavigateToDetail,
    onNavigateToPerson: (String) -> Unit,
    onOpenStreamPicker: (BaseItemDto) -> Unit,
    onPlayDirect: (itemId: String, mediaSourceId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val availableEditions by viewModel.availableEditions.collectAsState()
    val selectedEdition by viewModel.selectedEdition.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var episodeMenuTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    val playButtonFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(itemId) { viewModel.load(session, itemId) }
    androidx.compose.runtime.DisposableEffect(itemId) { onDispose { viewModel.onScreenHidden() } }
    LaunchedEffect(uiState.item?.Id) {
        if (uiState.item != null) {
            listState.scrollToItem(0)
            for (attempt in 0 until 30) {
                withFrameNanos {}
                if (runCatching { playButtonFocusRequester.requestFocus() }.isSuccess && attempt >= 1) {
                    break
                }
            }
        }
    }
    var heroHasFocus by remember { mutableStateOf(false) }
    val blockScrollWhileHeroFocused = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (heroHasFocus) available else Offset.Zero
        }
    }

    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> {
                val loadingFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) { runCatching { loadingFocus.requestFocus() } }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(loadingFocus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            event.key == androidx.compose.ui.input.key.Key.DirectionLeft ||
                                event.key == androidx.compose.ui.input.key.Key.DirectionRight ||
                                event.key == androidx.compose.ui.input.key.Key.DirectionUp ||
                                event.key == androidx.compose.ui.input.key.Key.DirectionDown
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    DetailLoading(uiState.loadingArt)
                }
            }
            uiState.error != null -> com.jellio.tv.ui.common.ErrorState(
                message = uiState.error ?: com.jellio.tv.ui.i18n.string("detail_load_error"),
                onRetry = { viewModel.retry(session, itemId) },
                modifier = Modifier.fillMaxSize(),
            )
            uiState.item != null -> {
                val item = uiState.item!!
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 48.dp),
                    modifier = Modifier.fillMaxSize().nestedScroll(blockScrollWhileHeroFocused).focusRestorer(),
                ) {
                    item {
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
                                    item = item,
                                    state = uiState,
                                    availableEditions = availableEditions,
                                    selectedEdition = selectedEdition,
                                    onSelectEdition = { viewModel.selectEdition(it) },
                                    onRetryDetails = { viewModel.retry(session, itemId) },
                                    onPlayFocusChanged = { viewModel.onPlayFocusChanged(session, it) },
                                    imageUrl = imageUrl,
                                    onSeriesClick = onNavigateToDetail,
                                    playButtonFocusRequester = playButtonFocusRequester,
                                    onPlay = {
                                com.jellio.tv.ui.player.PlayStartTrace.begin("title page")
                                scope.launch {
                                    viewModel.setResolvingPlay(true)
                                    try {
                                        val target = viewModel.resolvePlayTarget()
                                        if (target != null) {
                                            val action = viewModel.resolvePlayAction(session, target)
                                            com.jellio.tv.ui.player.PlayStartTrace.mark(target.Id, "play target", if (action is PlayAction.Direct) "remembered stream" else "picker")
                                            when (action) {
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
                    val showSeasons = (item.Type == "Series" || (item.Type == "Episode" && item.SeriesId != null)) && (uiState.seasons.isNotEmpty() || uiState.selectedSeasonEpisodes.isNotEmpty())
                    if (showSeasons) {
                        item {
                            SeasonsSection(
                                state = uiState,
                                imageUrl = imageUrl,
                                currentEpisodeId = if (item.Type == "Episode") item.Id else null,
                                onSelectSeason = { seasonId -> viewModel.selectSeason(session, item.SeriesId ?: item.Id, seasonId, byUser = true) },
                                onEpisodeClick = if (item.Type == "Episode") onNavigateToDetailReplace else onNavigateToDetail,
                                onEpisodeOptions = { episode -> episodeMenuTarget = episode },
                                onEpisodeFocused = { episodeId -> viewModel.preloadEpisode(episodeId) },
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

@OptIn(ExperimentalComposeUiApi::class)
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
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        for (attempt in 0 until 10) {
            withFrameNanos {}
            if (runCatching { firstFocusRequester.requestFocus() }.isSuccess && attempt >= 1) {
                break
            }
        }
    }
    val isPlayed = episode.UserData?.Played == true
    Box(
        modifier = Modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .background(Color.Black.copy(alpha = 0.5f))
            .focusProperties { onExit = { FocusRequester.Cancel } }
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
            EpisodeMenuRow(
                label = if (isPlayed) com.jellio.tv.ui.i18n.string("detail_mark_unwatched") else com.jellio.tv.ui.i18n.string("detail_mark_watched"),
                onClick = { onToggleWatched(); onDismiss() },
                modifier = Modifier.focusRequester(firstFocusRequester),
            )
            if (hasPrevious) {
                EpisodeMenuRow(label = com.jellio.tv.ui.i18n.string("detail_mark_previous_watched"), onClick = { onMarkPreviousWatched(); onDismiss() })
            }
            EpisodeMenuRow(label = com.jellio.tv.ui.i18n.string("detail_mark_season_watched"), onClick = { onMarkSeasonWatched(); onDismiss() })
        }
    }
}

@Composable
private fun EpisodeMenuRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
    }
}

@Composable
private fun DetailHero(
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
    onRetryDetails: (() -> Unit)? = null,
    onPlayFocusChanged: (Boolean) -> Unit = {},
    availableEditions: List<String> = emptyList(),
    selectedEdition: String? = null,
    onSelectEdition: (String) -> Unit = {},
) {
    val season = state.selectedSeason?.takeIf { item.Type == "Series" }
    // Backdrops, Thumb, then the poster blurred; a failed load moves on to the
    // next one, and when none load the gradients below still frame the title.
    val candidates = remember(item.Id, season?.Id, item.BackdropImageTags, item.ImageTags) {
        com.jellio.tv.ui.common.titleArtCandidates(item, { id, tag, type, w -> imageUrl(id, tag, type, w) }, season = season)
    }
    val logos = remember(item.Id, item.ImageTags) {
        if (item.Type == "Episode") emptyList() else com.jellio.tv.ui.common.titleLogoCandidates(item, { id, tag, type, w -> imageUrl(id, tag, type, w) })
    }
    var logoIndex by remember(logos) { mutableIntStateOf(0) }
    var artIndex by remember(candidates) { mutableIntStateOf(0) }
    val art = candidates.getOrNull(artIndex)
    val heroHeight = HeroHeight.scaled()
    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
        if (art != null) {
            AsyncImage(
                model = art.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { artIndex++ },
                modifier = Modifier.fillMaxWidth().height(heroHeight).then(if (art.blurred) Modifier.blur(28.dp) else Modifier),
            )
            if (art.blurred) {
                Box(modifier = Modifier.fillMaxWidth().height(heroHeight).background(JellioBg.copy(alpha = 0.35f)))
            }
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
            modifier = Modifier.align(Alignment.BottomStart).widthIn(max = 920.dp).padding(start = 56.dp, bottom = 56.dp, end = 32.dp),
        ) {
            if (item.Type == "Episode" && item.SeriesId != null && item.SeriesName != null) {
                Surface(
                    onClick = { onSeriesClick(item.SeriesId) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = JellioTextSecondary,
                        focusedContainerColor = Color.White.copy(alpha = 0.25f),
                        focusedContentColor = Color.White,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(8.dp),
                        ),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tv,
                            contentDescription = null,
                            tint = JellioSecondary,
                            modifier = Modifier.size(16.dp).padding(end = 6.dp),
                        )
                        Text(
                            text = item.SeriesName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            val hasEpisodeCode = item.ParentIndexNumber != null && item.IndexNumber != null
            val titleText = if (item.Type == "Episode" && hasEpisodeCode) {
                "${com.jellio.tv.ui.i18n.seasonEpisodeTag(item.ParentIndexNumber, item.IndexNumber)} \u00B7 ${com.jellio.tv.ui.i18n.episodeDisplayName(item.Name, item.IndexNumber)}"
            } else {
                item.Name.orEmpty()
            }
            val logo = logos.getOrNull(logoIndex)
            if (logo != null) {
                AsyncImage(
                    model = logo,
                    contentDescription = titleText,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onError = { logoIndex++ },
                    modifier = Modifier.widthIn(max = 520.dp).height(120.dp),
                )
            } else {
                Text(text = titleText, style = MaterialTheme.typography.titleLarge, color = JellioText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val epReleaseLabel = if (item.Type == "Episode") episodeReleaseLabel(item) else null
                if (epReleaseLabel != null) {
                    Text(text = epReleaseLabel, color = JellioRarityLegendary, fontWeight = FontWeight.SemiBold)
                }
                val year = if (item.Type == "Episode") {
                    formatPremiereDate(item.PremiereDate) ?: item.ProductionYear?.toString()
                } else {
                    item.ProductionYear?.toString()
                }
                if (year != null && (epReleaseLabel == null || year != epReleaseLabel)) {
                    Text(text = year, color = JellioTextSecondary)
                }
                formatRuntime(item.RunTimeTicks).takeIf { it.isNotEmpty() }?.let { Text(text = it, color = JellioTextSecondary) }
                item.OfficialRating?.let { Text(text = it, color = JellioTextSecondary) }
                // TMDB's score, labelled like the cards.
                item.CommunityRating?.let { com.jellio.tv.ui.home.RatingChip(it, fontSize = 15.sp, badgeWidth = 28.dp, badgeHeight = 12.dp) }
            }

            MediaTechBadgesRow(item = item, modifier = Modifier.padding(top = 10.dp))

            item.Genres?.takeIf { it.isNotEmpty() }?.let {
                Text(text = it.joinToString(", ") { g -> com.jellio.tv.ui.i18n.genreDisplayName(g) }, color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
            }

            (season?.Overview?.takeIf { it.isNotBlank() } ?: item.Overview)?.let {
                Text(
                    text = it,
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (availableEditions.size > 1) {
                Row(
                    modifier = Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    availableEditions.forEach { edition ->
                        val isSelected = edition == selectedEdition
                        Surface(
                            onClick = { onSelectEdition(edition) },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) JellioSecondary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                                contentColor = if (isSelected) Color.White else JellioTextSecondary,
                                focusedContainerColor = JellioSecondary,
                                focusedContentColor = Color.White,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = Border(
                                    border = BorderStroke(1.dp, if (isSelected) JellioSecondary.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(999.dp),
                                ),
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, Color.White),
                                    shape = RoundedCornerShape(999.dp),
                                ),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                        ) {
                            Text(
                                text = edition,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            var actionsExpanded by remember(item.Id) { mutableStateOf(false) }
            Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onPlay,
                    enabled = !state.resolvingPlay,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = JellioText,
                        contentColor = JellioBg,
                        focusedContainerColor = Color.White,
                        focusedContentColor = JellioBg,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.5.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        ),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier
                        .focusRequester(playButtonFocusRequester)
                        .onFocusChanged { onPlayFocusChanged(it.isFocused) },
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
                            contentDescription = com.jellio.tv.ui.i18n.string("nav_watchlist"),
                            onClick = onToggleWatchlist,
                        )
                        IconActionButton(
                            icon = Icons.Filled.Check,
                            active = state.isWatched,
                            contentDescription = com.jellio.tv.ui.i18n.string("detail_mark_watched"),
                            onClick = onToggleWatched,
                        )
                        IconActionButton(
                            icon = Icons.Filled.ThumbUp,
                            active = state.likes == true,
                            contentDescription = com.jellio.tv.ui.i18n.string("detail_like"),
                            onClick = onLike,
                        )
                        IconActionButton(
                            icon = Icons.Filled.ThumbDown,
                            active = state.likes == false,
                            contentDescription = com.jellio.tv.ui.i18n.string("detail_dislike"),
                            onClick = onDislike,
                        )
                        if (onChangeStream != null) {
                            IconActionButton(
                                icon = Icons.Filled.SwapHoriz,
                                active = false,
                                contentDescription = com.jellio.tv.ui.i18n.string("detail_change_stream"),
                                onClick = onChangeStream,
                            )
                        }
                    }
                }
                IconActionButton(
                    icon = Icons.Filled.MoreVert,
                    active = actionsExpanded,
                    contentDescription = if (actionsExpanded) com.jellio.tv.ui.i18n.string("detail_fewer_actions") else com.jellio.tv.ui.i18n.string("detail_more_actions"),
                    onClick = { actionsExpanded = !actionsExpanded },
                )
            }
            val status = when {
                state.findingStreams -> com.jellio.tv.ui.i18n.string("detail_finding_streams")
                else -> state.streamStatus
            }
            status?.let {
                Text(
                    text = it,
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (state.detailsRetrying) {
                Text(
                    text = com.jellio.tv.ui.i18n.string("detail_refreshing"),
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (state.detailsFailed && onRetryDetails != null) {
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = com.jellio.tv.ui.i18n.string("detail_refresh_failed"),
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Surface(
                        onClick = onRetryDetails,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        Text(text = com.jellio.tv.ui.i18n.string("action_retry"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

// The title's backdrop, dimmed, with a spinner and its name while the page
// loads.
@Composable
private fun DetailLoading(art: TitleLoadingArtInfo?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        art?.art?.takeIf { it.isNotEmpty() }?.let { candidates ->
            com.jellio.tv.ui.common.TitleArtBackdrop(candidates, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.jellio.tv.ui.common.ScreenSpinner(size = 44.dp)
            Text(
                text = art?.title?.takeIf { it.isNotBlank() }?.let { com.jellio.tv.ui.i18n.formatString("detail_loading_title", it) } ?: com.jellio.tv.ui.i18n.string("status_loading"),
                color = JellioText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
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
    val popScale = remember { androidx.compose.animation.core.Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        popScale.snapTo(1.22f)
        popScale.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
            ),
        )
    }
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioSecondary else Color.White.copy(alpha = 0.1f),
        label = "actionBtnContainer",
    )
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioBg else JellioText,
        label = "actionBtnContent",
    )
    val focusedContainerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioSecondary else Color.White.copy(alpha = 0.35f),
        label = "actionBtnFocusedContainer",
    )
    val focusedContentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (active) JellioBg else Color.White,
        label = "actionBtnFocusedContent",
    )
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            contentColor = contentColor,
            focusedContainerColor = focusedContainerColor,
            focusedContentColor = focusedContentColor,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, Color.White),
                shape = CircleShape,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = popScale.value
                scaleY = popScale.value
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

@Composable
private fun SeasonsSection(
    state: DetailUiState,
    imageUrl: (String, String?, String, Int) -> String,
    currentEpisodeId: String? = null,
    onSelectSeason: (String) -> Unit,
    onEpisodeClick: (String) -> Unit,
    onEpisodeOptions: (BaseItemDto) -> Unit,
    onEpisodeFocused: (String) -> Unit,
) {
    if (state.seasons.isEmpty() && state.selectedSeasonEpisodes.isEmpty()) return
    // The episode the row was brought to, or the user took over first; saveable
    // so the row does not jump again when this section scrolls back into view.
    var settledFor by rememberSaveable { mutableStateOf<String?>(null) }
    // Bumped on each season the user picks, so the episode row starts at its first card.
    var manualSeasonSwitches by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(text = com.jellio.tv.ui.i18n.string("detail_episodes"), style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        if (state.seasons.isNotEmpty()) {
            val seasonsListState = rememberLazyListState()
            LaunchedEffect(state.selectedSeasonId, state.seasons) {
                val idx = state.seasons.indexOfFirst { it.Id == state.selectedSeasonId }
                if (idx < 0) return@LaunchedEffect
                val laidOut = snapshotFlow { seasonsListState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
                val fullyVisible = laidOut.let { info ->
                    info.visibleItemsInfo.any { it.index == idx && it.offset >= info.viewportStartOffset && it.offset + it.size <= info.viewportEndOffset }
                }
                if (!fullyVisible) seasonsListState.scrollToItem(idx)
            }
            LazyRow(state = seasonsListState, contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.seasons, key = { it.Id }) { season ->
                    val selected = season.Id == state.selectedSeasonId
                    Surface(
                        onClick = {
                            settledFor = currentEpisodeId
                            onSelectSeason(season.Id)
                            manualSeasonSwitches++
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected) JellioSecondary else JellioBgElevated,
                            contentColor = if (selected) JellioBg else JellioText,
                            focusedContainerColor = JellioSecondary,
                            focusedContentColor = JellioBg,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(999.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    ) {
                        Text(text = com.jellio.tv.ui.i18n.seasonDisplayName(season), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                }
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.jellio.tv.ui.common.rememberTvHorizontalBringIntoViewSpec()) {
            val releaseLabels = remember(state.selectedSeasonEpisodes, state.streamVerifiedTick) { seasonEpisodeReleaseLabels(state.selectedSeasonEpisodes) }
            val episodesListState = rememberLazyListState()
            LaunchedEffect(currentEpisodeId, state.selectedSeasonEpisodes) {
                if (currentEpisodeId == null || settledFor == currentEpisodeId) return@LaunchedEffect
                val idx = state.selectedSeasonEpisodes.indexOfFirst { it.Id == currentEpisodeId }
                if (idx < 0) return@LaunchedEffect
                // Offset 0 lands the card on the start padding, where focus parks a card.
                episodesListState.scrollToItem(idx)
                settledFor = currentEpisodeId
            }
            LaunchedEffect(manualSeasonSwitches) {
                if (manualSeasonSwitches > 0) episodesListState.scrollToItem(0)
            }
            LazyRow(
                state = episodesListState,
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.selectedSeasonEpisodes, key = { it.Id }) { episode ->
                    EpisodeCard(
                        episode = episode,
                        imageUrl = imageUrl,
                        releaseLabel = releaseLabels[episode.Id],
                        isCurrent = currentEpisodeId != null && episode.Id == currentEpisodeId,
                        onClick = { onEpisodeClick(episode.Id) },
                        onOptionsClick = { onEpisodeOptions(episode) },
                        onFocused = {
                            settledFor = currentEpisodeId
                            if (releaseLabels[episode.Id] == null) {
                                onEpisodeFocused(episode.Id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: BaseItemDto,
    imageUrl: (String, String?, String, Int) -> String,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onFocused: () -> Unit = {},
    releaseLabel: String? = null,
    isCurrent: Boolean = false,
) {
    val thumbUrl = episode.ImageTags?.get("Primary")?.let { imageUrl(episode.Id, it, "Primary", 500) }
        ?: episode.ParentThumbImageTag?.let { tag -> episode.ParentThumbItemId?.let { id -> imageUrl(id, tag, "Thumb", 500) } }
    val cardWidth = 320.dp.scaled()
    var cardFocused by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(cardWidth)) {
        Surface(
            onClick = onClick,
            onLongClick = onOptionsClick,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBgElevated,
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.28f),
                focusedContentColor = JellioText,
            ),
            border = if (isCurrent) {
                ClickableSurfaceDefaults.border(
                    border = Border(
                        border = BorderStroke(1.5.dp, JellioSecondary.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp),
                    ),
                    focusedBorder = Border(
                        border = BorderStroke(2.5.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ),
                )
            } else {
                ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.5.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ),
                )
            },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = Modifier
                .width(cardWidth)
                .onFocusChanged {
                    cardFocused = it.isFocused
                    if (it.isFocused) onFocused()
                },
        ) {
            Column {
                Box(modifier = Modifier.width(cardWidth).aspectRatio(16f / 9f)) {
                    if (thumbUrl != null) {
                        AsyncImage(model = thumbUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    episode.IndexNumber?.let {
                        Text(
                            text = com.jellio.tv.ui.i18n.seasonEpisodeTag(null, it),
                            color = JellioText,
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    // The episode's rating, the same chip as on Home's cards.
                    episode.CommunityRating.let { rating ->
                        com.jellio.tv.ui.home.RatingChip(
                            rating = rating,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        )
                    }
                    if (!releaseLabel.isNullOrEmpty()) {
                        Text(
                            text = releaseLabel,
                            color = JellioRarityLegendary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(JellioBg.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    if (isCurrent) {
                        Text(
                            text = com.jellio.tv.ui.i18n.string("detail_current_episode"),
                            color = JellioSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(JellioBg.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
                                .border(1.dp, JellioSecondary.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    } else if (episode.UserData?.Played == true) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = JellioText,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        )
                    }
                }
                Column(modifier = Modifier.padding(12.dp)) {
                    com.jellio.tv.ui.common.FocusMarqueeText(text = com.jellio.tv.ui.i18n.episodeDisplayName(episode.Name, episode.IndexNumber), focused = cardFocused, color = JellioText)
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
        Text(text = com.jellio.tv.ui.i18n.string("detail_cast"), style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        val avatarWidth = 140.dp.scaled()
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.jellio.tv.ui.common.rememberTvHorizontalBringIntoViewSpec()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(cast, key = { it.Id }) { person ->
                    Surface(
                        onClick = { onPersonClick(person.Id) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = 0.25f),
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
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
}

@Composable
private fun TrailersRow(trailers: List<TrailerDto>, onOpen: (String) -> Unit) {
    val playable = remember(trailers) { trailers.mapNotNull { t -> t.Url?.takeIf { it.isNotEmpty() }?.let { t to it } } }
    if (playable.isEmpty()) return
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(text = com.jellio.tv.ui.i18n.string("detail_trailers"), style = MaterialTheme.typography.titleMedium, color = JellioText, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        val trailerCardWidth = 280.dp.scaled()
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.jellio.tv.ui.common.rememberTvHorizontalBringIntoViewSpec()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(playable) { (trailer, url) ->
                    Surface(
                        onClick = { onOpen(url) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = JellioBgElevated,
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.28f),
                            focusedContentColor = JellioText,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.5.dp, Color.White),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        modifier = Modifier.width(trailerCardWidth),
                    ) {
                        Column {
                            Box(modifier = Modifier.width(trailerCardWidth).aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                                // The icon stays underneath: it shows when there is no id or no thumbnail loads.
                                Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null, tint = JellioText)
                                val thumbs = remember(url) { youTubeId(url)?.let(::youTubeThumbnails).orEmpty() }
                                var thumbIndex by remember(thumbs) { mutableIntStateOf(0) }
                                thumbs.getOrNull(thumbIndex)?.let { thumb ->
                                    coil3.compose.AsyncImage(
                                        model = thumb,
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        onError = { thumbIndex++ },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Text(
                                text = trailer.Name ?: com.jellio.tv.ui.i18n.string("action_trailer"),
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
}
