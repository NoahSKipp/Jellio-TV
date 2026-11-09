package com.jellio.tv.ui.home

import androidx.activity.compose.BackHandler

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jellio.tv.ui.common.dpadFastScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.NoOpBringIntoViewSpec
import com.jellio.tv.ui.common.TvBringIntoViewSpec
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.launch

private data class CardMenuTarget(val item: BaseItemDto, val row: PosterHomeRow)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    session: Session,
    imageUrl: (BaseItemDto, String, Int) -> String,
    rawImageUrl: (String, String?, String, Int) -> String,
    serviceLogoUrl: (String) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    onComingSoonClick: (String) -> Unit,
    onServiceClick: (String) -> Unit,
    onEditModeChange: (Boolean) -> Unit = {},
    onPlay: (BaseItemDto) -> Unit,
    onPlayManually: (BaseItemDto) -> Unit,
    onOpenNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialEditMode: Boolean = false,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val nowPlayingSessions by viewModel.nowPlayingSessions.collectAsState()
    val nowPlayingActiveCount by viewModel.nowPlayingActiveCount.collectAsState()
    val nowPlayingLocalPaused by viewModel.nowPlayingLocalPaused.collectAsState()
    val listState = com.jellio.tv.ui.common.rememberTvColumnState()
    // The seasonal scenes hold their frame while the rows scroll.
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }
            .collect { com.jellio.tv.ui.perf.ScrollActivity.scrolling = it }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { com.jellio.tv.ui.perf.ScrollActivity.scrolling = false }
    }
    val scope = rememberCoroutineScope()
    val highlightedItem = remember { mutableStateOf<BaseItemDto?>(null) }
    val toastMessage by viewModel.toastMessage.collectAsState()
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(3000L)
            viewModel.clearToast()
        }
    }
    LaunchedEffect(uiState.rows) {
        val current = highlightedItem.value ?: return@LaunchedEffect
        val stillExists = uiState.rows.any { row ->
            when (row) {
                is PosterHomeRow -> row.section.items.any { it.Id == current.Id }
                is ComingSoonHomeRow -> row.items.containsKey(current.Id) || row.entries.any { it.ItemId == current.Id }
                else -> false
            }
        }
        if (!stillExists) {
            highlightedItem.value = uiState.rows.firstNotNullOfOrNull { (it as? PosterHomeRow)?.section?.items?.firstOrNull() }
        }
    }
    // The last focused card (row key and item id), kept across visits to a
    // title's page so coming back puts focus right where it was.
    var lastFocus by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<List<String>?>(null) }
    var restorePending by remember { mutableStateOf(true) }
    val rowFocus = remember { mutableMapOf<String, androidx.compose.ui.focus.FocusRequester>() }
    var cardMenuTarget by remember { mutableStateOf<CardMenuTarget?>(null) }
    var comingSoonMenuTarget by remember { mutableStateOf<com.jellio.tv.data.model.CalendarEntryDto?>(null) }
    var rowListTarget by remember { mutableStateOf<HomeSection?>(null) }
    var removingTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val userImageUrl = remember(session) {
        { userId: String, tag: String? ->
            val cleanServer = com.jellio.tv.di.normalizeServerAddress(session.serverAddress)
            val base = "$cleanServer/Users/$userId/Images/Primary?maxWidth=64"
            val withTag = if (!tag.isNullOrEmpty()) "$base&tag=$tag" else base
            if (session.accessToken.isNotEmpty()) "$withTag&ApiKey=${session.accessToken}&api_key=${session.accessToken}" else withTag
        }
    }
    var editMode by remember(initialEditMode) { mutableStateOf(initialEditMode) }

    BackHandler(enabled = editMode) {
        editMode = false
    }

    LaunchedEffect(session.userId, com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage) {
        viewModel.load(session)
    }
    LaunchedEffect(editMode) { onEditModeChange(editMode) }
    DisposableEffect(Unit) { onDispose { onEditModeChange(false) } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> HomeSkeleton()
            uiState.error != null -> com.jellio.tv.ui.common.ErrorState(
                message = com.jellio.tv.ui.i18n.string("home_load_error"),
                failure = uiState.error,
                onRetry = { viewModel.retry(session) },
                modifier = Modifier.fillMaxSize(),
            )
            uiState.rows.isEmpty() && !uiState.loadingMore -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = com.jellio.tv.ui.i18n.string("status_empty"), color = JellioTextSecondary)
                }
            }
            else -> {
                val (order, visibleRows) = remember(uiState.rows, uiState.customization, editMode) {
                    val liveKeys = uiState.rows.map { it.key }
                    val ord = HomeCustomization.effectiveOrder(liveKeys, uiState.customization)
                    val byKey = uiState.rows.associateBy { it.key }
                    val orderedRows = ord.mapNotNull { byKey[it] }
                    val visible = if (editMode) {
                        orderedRows
                    } else {
                        orderedRows.filter { !uiState.customization.hidden.contains(it.key) }
                    }
                    ord to visible
                }

                // The space this screen really gets, which can be less than the display.
                val screenHeight = maxHeight
                val density = LocalDensity.current
                val rowsViewportHeight = screenHeight * 0.52f
                val topFixtureHeight = screenHeight - rowsViewportHeight
                val firstRowItem = uiState.rows.firstNotNullOfOrNull { (it as? PosterHomeRow)?.section?.items?.firstOrNull() }
                ShowcaseHost(
                    highlighted = highlightedItem,
                    fallback = firstRowItem,
                    loadInfo = { item -> viewModel.showcaseInfo(session, item) },
                    imageUrl = imageUrl,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(topFixtureHeight + HeaderOverlap),
                )

                if (com.jellio.tv.data.prefs.DisplayPreferences.showNowPlayingPill) {
                    com.jellio.tv.ui.nowplaying.NowPlayingPill(
                        activeCount = nowPlayingActiveCount,
                        topSession = nowPlayingSessions.firstOrNull(),
                        localPaused = nowPlayingLocalPaused,
                        onClick = onOpenNowPlaying,
                        sessions = nowPlayingSessions,
                        session = session,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 28.dp, end = 40.dp),
                    )
                }

                // Scrollable rows in the bottom viewport
                val defaultScrollSpec = LocalBringIntoViewSpec.current.scrollAnimationSpec
                val verticalRowBringIntoViewSpec = remember(density) {
                    val topInsetPx = with(density) { RowHeaderHeight.toPx() }
                    com.jellio.tv.ui.common.TvVerticalRowBringIntoViewSpec(topInsetPx = topInsetPx, scrollAnimationSpec = defaultScrollSpec) { listState.canScrollBackward }
                }

                CompositionLocalProvider(LocalBringIntoViewSpec provides verticalRowBringIntoViewSpec) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(rowsViewportHeight)
                            .clipToBounds()
                            .focusRestorer()
                            .dpadFastScroll(listState) {
                                if (!editMode) {
                                    // Focus lands on the first row that is fully in view.
                                    val visible = listState.layoutInfo.visibleItemsInfo
                                    val target = visible.firstOrNull { it.offset >= 0 && rowFocus.containsKey(it.key) }
                                        ?: visible.firstOrNull { rowFocus.containsKey(it.key) }
                                    target?.let { runCatching { rowFocus[it.key]?.requestFocus() } }
                                }
                            },
                        // Room for the last rows to scroll up to the top, as in Nuvio.
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = rowsViewportHeight),
                    ) {
                        if (editMode) {
                            item(key = "customize_bar_top") {
                                HomeCustomizeBar(
                                    editMode = true,
                                    onToggleEdit = { editMode = !editMode },
                                    onReset = { viewModel.resetCustomization() },
                                    modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 8.dp),
                                )
                            }
                        }
                        items(visibleRows, key = { it.key }, contentType = { it::class.java.name }) { row ->
                            val hidden = uiState.customization.hidden.contains(row.key)
                            val index = order.indexOf(row.key)
                            val onMoveUp = remember(row.key) { { viewModel.moveRow(row.key, -1) } }
                            val onMoveDown = remember(row.key) { { viewModel.moveRow(row.key, 1) } }
                            val onToggleHidden = remember(row.key) { { viewModel.toggleRowHidden(row.key) } }
                            HomeRowEditor(
                                editMode = editMode,
                                displayName = row.displayName,
                                hidden = hidden,
                                canMoveUp = index > 0,
                                canMoveDown = index < order.size - 1,
                                onMoveUp = onMoveUp,
                                onMoveDown = onMoveDown,
                                onToggleHidden = onToggleHidden,
                            ) {
                                when (row) {
                                    is PosterHomeRow -> {
                                        val onRowTitle = remember(row.section) { { rowListTarget = row.section } }
                                        val onRowOptions = remember(row) { { item: BaseItemDto -> cardMenuTarget = CardMenuTarget(item, row) } }
                                        val onCardFocus = remember(row.section.key) {
                                            { item: BaseItemDto ->
                                                highlightedItem.value = item
                                                lastFocus = listOf(row.section.key, item.Id)
                                            }
                                        }
                                        val onShatterFinished = remember(row, session) {
                                            { item: BaseItemDto ->
                                                val isShowRemoval = removingTarget?.second == true
                                                removingTarget = null
                                                when {
                                                    isShowRemoval -> viewModel.hideShowFromRow(item)
                                                    row.section.key == "continue-watching" -> viewModel.removeFromContinueWatching(session, item)
                                                    else -> viewModel.removeFromRow(session, item)
                                                }
                                            }
                                        }
                                        PosterRow(
                                            section = row.section,
                                            imageUrl = imageUrl,
                                            onItemClick = onItemClick,
                                            onItemOptions = onRowOptions,
                                            onTitleClick = onRowTitle,
                                            userImageUrl = userImageUrl,
                                            onItemFocus = onCardFocus,
                                            progress = row.landscape,
                                            removingItemId = if (row.landscape) removingTarget?.first else null,
                                            onShatterFinished = onShatterFinished,
                                            rowFocusRequester = rowFocus.getOrPut(row.section.key) { androidx.compose.ui.focus.FocusRequester() },
                                            restoreItemId = lastFocus?.takeIf { restorePending && it[0] == row.section.key }?.get(1),
                                            onFocusRestored = { restorePending = false },
                                        )
                                    }
                                    is ComingSoonHomeRow -> ComingSoonRow(
                                        entries = row.entries,
                                        imageUrl = rawImageUrl,
                                        onItemClick = onComingSoonClick,
                                        items = row.items,
                                        itemImageUrl = imageUrl,
                                        onItemFocus = { item -> highlightedItem.value = item },
                                        onItemOptions = { entry -> comingSoonMenuTarget = entry },
                                    )
                                    is StudioHubsHomeRow -> StudioHubRow(services = row.services, logoUrl = serviceLogoUrl, onServiceClick = onServiceClick)
                                }
                            }
                        }
                        if (uiState.rowsError != null && !editMode) {
                            item(key = "rows_error") {
                                com.jellio.tv.ui.common.InlineErrorState(
                                    message = com.jellio.tv.ui.i18n.string("rows_load_error"),
                                    onRetry = { viewModel.retry(session) },
                                    modifier = Modifier.padding(start = 48.dp, top = 8.dp, bottom = 8.dp),
                                )
                            }
                        }
                        // Rows still loading below the ones already shown.
                        if (uiState.loadingMore) {
                            item(key = "skeleton") { SkeletonRow() }
                            item(key = "skeleton-2") { SkeletonRow() }
                        }
                    }
                }
            }
        }

        cardMenuTarget?.let { target ->
            val item = target.item
            val continueWatching = target.row.section.key == "continue-watching"
            val upNext = target.row.section.key == "up-next"
            val isWatchlistRow = target.row.section.key.startsWith("watchlist")
            val itemWithFavorite = if (isWatchlistRow && item.UserData?.IsFavorite != true) {
                item.copy(UserData = (item.UserData ?: com.jellio.tv.data.model.UserItemDataDto()).copy(IsFavorite = true))
            } else {
                item
            }
            CardOptionsMenu(
                item = itemWithFavorite,
                continueWatching = continueWatching,
                upNext = upNext,
                onGoToDetails = if (continueWatching || upNext) { { onItemClick(item) } } else null,
                onGoToSeries = if ((continueWatching || upNext) && item.SeriesId != null) {
                    { onItemClick(BaseItemDto(Id = item.SeriesId, Name = item.SeriesName, Type = "Series")) }
                } else null,
                onPlayManually = if (continueWatching) { { onPlayManually(item) } } else null,
                onStartOver = if (continueWatching) {
                    {
                        scope.launch {
                            if (viewModel.restartFromBeginning(session, item)) onPlay(item)
                        }
                    }
                } else {
                    null
                },
                onRemoveFromRow = if (continueWatching || upNext) { { removingTarget = item.Id to false } } else null,
                onRemoveShowFromRow = if (upNext && item.SeriesId != null) { { removingTarget = item.Id to true } } else null,
                onToggleWatchlist = if (!continueWatching && !upNext) {
                    {
                        viewModel.toggleWatchlist(session, itemWithFavorite)
                        if (highlightedItem.value?.Id == item.Id && isWatchlistRow) {
                            val remainingInRow = target.row.section.items.filterNot { it.Id == item.Id }
                            val nextItem = remainingInRow.firstOrNull() ?: uiState.rows.firstNotNullOfOrNull {
                                (it as? PosterHomeRow)?.section?.items?.firstOrNull { candidate -> candidate.Id != item.Id }
                            }
                            highlightedItem.value = nextItem
                        }
                    }
                } else null,
                onToggleWatched = if (!continueWatching && !upNext) { { viewModel.toggleWatched(session, itemWithFavorite) } } else null,
                onDismiss = { cardMenuTarget = null },
            )
        }

        comingSoonMenuTarget?.let { entry ->
            CardOptionsMenu(
                item = BaseItemDto(Id = entry.ItemId, Name = entry.Name),
                comingSoon = true,
                onGoToDetails = { onComingSoonClick(entry.ItemId) },
                onRemoveFromComingSoon = { viewModel.removeFromComingSoon(entry.ItemId) },
                onDismiss = { comingSoonMenuTarget = null },
            )
        }

        rowListTarget?.let { section ->
            RowListModal(
                title = section.title,
                items = section.items,
                imageUrl = imageUrl,
                onItemClick = onItemClick,
                onDismiss = { rowListTarget = null },
                fetchAll = section.fetchAll,
                fetchPage = section.fetchPage,
            )
        }

        toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .background(JellioBgElevated, RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JellioText,
                )
            }
        }
    }
}

@Composable
private fun HomeCustomizeBar(editMode: Boolean, onToggleEdit: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        if (editMode) {
            Surface(
                onClick = onReset,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White,
                    focusedContentColor = JellioBg,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Icon(imageVector = Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(text = com.jellio.tv.ui.i18n.string("home_reset_order"))
                }
            }
        }
        Surface(
            onClick = onToggleEdit,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (editMode) JellioSecondary else Color.White.copy(alpha = 0.08f),
                contentColor = if (editMode) JellioBg else JellioText,
                focusedContainerColor = Color.White,
                focusedContentColor = JellioBg,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Icon(imageVector = Icons.Filled.Tune, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(text = if (editMode) com.jellio.tv.ui.i18n.string("action_done") else com.jellio.tv.ui.i18n.string("home_customize"))
            }
        }
    }
}

@Composable
private fun HomeRowEditor(
    editMode: Boolean,
    displayName: String,
    hidden: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleHidden: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        if (editMode) {
            HomeRowEditorBar(
                displayName = displayName,
                hidden = hidden,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onToggleHidden = onToggleHidden,
            )
        } else if (!hidden) {
            content()
        }
    }
}

@Composable
private fun HomeRowEditorBar(
    displayName: String,
    hidden: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    var hasChildFocus by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .padding(top = 8.dp, bottom = 4.dp)
            .background(
                if (hasChildFocus) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(12.dp),
            )
            .let {
                if (hasChildFocus) it.border(BorderStroke(2.dp, Color.White), RoundedCornerShape(12.dp)) else it
            }
            .onFocusChanged { hasChildFocus = it.hasFocus }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = displayName,
            color = if (hasChildFocus) JellioText else JellioTextSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (hasChildFocus) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeRowEditorButton(
                icon = Icons.Filled.ArrowUpward,
                enabled = canMoveUp,
                onClick = onMoveUp,
                contentDescription = com.jellio.tv.ui.i18n.Strings.get("home_move_row_up"),
            )
            HomeRowEditorButton(
                icon = Icons.Filled.ArrowDownward,
                enabled = canMoveDown,
                onClick = onMoveDown,
                contentDescription = com.jellio.tv.ui.i18n.Strings.get("home_move_row_down"),
            )
            HomeRowEditorButton(
                icon = if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                enabled = true,
                active = hidden,
                onClick = onToggleHidden,
                contentDescription = com.jellio.tv.ui.i18n.Strings.get(if (hidden) "row_show" else "row_hide"),
            )
        }
    }
}

@Composable
private fun HomeRowEditorButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    active: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (active) JellioSecondary else JellioTextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = JellioBg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        modifier = Modifier.padding(2.dp),
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
        }
    }
}
