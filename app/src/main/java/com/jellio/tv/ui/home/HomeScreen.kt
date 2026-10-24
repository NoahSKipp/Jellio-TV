package com.jellio.tv.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.launch

// Which row a card's own options menu was opened from, real
// components/cardOptionsMenu.js's own real continueWatching/upNext
// options carried alongside the item itself: CardOptionsMenu's own
// real content (and which of the callbacks below even apply) depends
// on it.
private data class CardMenuTarget(val item: BaseItemDto, val row: PosterHomeRow)


// Mirrors screens/home.js's own buildHomeSections(): a real hero over
// real rows, fetched from the real Jellio-Plugin backend rather than
// placeholder content.
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
    // Real feedback live: SidebarNav's own items stayed reachable while
    // Customize mode was active, so a reader mid-reorder could jump
    // straight off this screen (and off the reorder state with it)
    // with no equivalent to the modal's own scrim BackHandler standing
    // in the way. MainActivity threads this straight to the rail's own
    // enabled state, same real reasoning a modal overlay would trap
    // focus if this screen mounted one instead of toggling in place.
    onEditModeChange: (Boolean) -> Unit = {},
    onPlay: (BaseItemDto) -> Unit,
    // Real port of components/cardOptionsMenu.js's own "Play manually"
    // (openStreamPicker(item, { forceChoice: true })): built at
    // MainActivity's own root the same way DetailScreen's own Change
    // Stream button already reaches AppViewModel.resolvePlayAction and
    // the app-wide StreamPickerOverlay, not something this screen (or
    // HomeViewModel) reaches into on its own.
    onPlayManually: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
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
    // Real ui/library/LibraryScreen.kt's own header on this exact fix:
    // switching focus between an arrow and View Details still nudged
    // this list a real few pixels for exactly one real frame before the
    // real snapshotFlow correction below caught it back - reactive can
    // only ever answer a real drift a frame after it already rendered.
    // Blocking the real scroll from ever reaching this list in the
    // first place, while this item holds real focus, closes that last
    // Read only inside ShowcaseHost: a focus move then recomposes the
    // showcase alone, not this whole screen.
    val highlightedItem = remember { mutableStateOf<BaseItemDto?>(null) }
    var cardMenuTarget by remember { mutableStateOf<CardMenuTarget?>(null) }
    var rowListTarget by remember { mutableStateOf<HomeSection?>(null) }
    // Real port of components/cardOptionsMenu.js's own animateCardRemoval():
    // "Remove" only ever starts the real shatter below, the real
    // HomeViewModel.removeFromRow()/hideSeriesFromRow() call deferred to
    // LandscapeRow's own onShatterFinished, the same real reason that
    // file's own card stays in the DOM until its own overlay's
    // setTimeout finishes. isShowRemoval picks which of those two real
    // calls fires once the shatter actually finishes: "Remove from Up
    // Next" (mark this one episode played) vs "Remove Show from Up
    // Next" (hide the whole series server side).
    var removingTarget by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val userImageUrl = remember(session) {
        { userId: String, tag: String? ->
            val cleanServer = com.jellio.tv.di.normalizeServerAddress(session.serverAddress)
            val base = "$cleanServer/Users/$userId/Images/Primary?maxWidth=64"
            val withTag = if (!tag.isNullOrEmpty()) "$base&tag=$tag" else base
            if (session.accessToken.isNotEmpty()) "$withTag&ApiKey=${session.accessToken}&api_key=${session.accessToken}" else withTag
        }
    }
    // Real components/homeCustomizer.js's own header: editMode lives
    // only in this real local closure, reset to off on every fresh
    // visit to this screen the same way that file's own real editMode
    // local variable already resets on every real remount, not
    // something worth persisting across navigations. Only order/hidden
    // itself is real persisted state, through HomeViewModel's own
    // HomeCustomizationStore.
    var editMode by remember { mutableStateOf(false) }

    LaunchedEffect(session.userId) {
        viewModel.load(session)
    }
    LaunchedEffect(editMode) { onEditModeChange(editMode) }
    DisposableEffect(Unit) { onDispose { onEditModeChange(false) } }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> HomeSkeleton()
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: com.jellio.tv.ui.i18n.string("status_error"), color = JellioTextSecondary)
            }
            uiState.rows.isEmpty() && !uiState.loadingMore -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = com.jellio.tv.ui.i18n.string("status_empty"), color = JellioTextSecondary)
                }
            }
            // Real gap real feedback caught live across several rounds:
            // plain Compose Foundation LazyColumn/LazyRow, unlike
            // tv-foundation's own TvLazyColumn/TvLazyRow, advertise no
            // default D-pad entry point of their own for a system that
            // has never yet focused anything inside them. focusRestorer()
            // below is Compose's own real fix for that: finds a sensible
            // default child to enter on first arrival, then remembers
            // and restores the last focused child on every return trip.
            else -> {
                // Real port of components/homeCustomizer.js's own
                // applyHomeCustomization(): recomputed fresh off
                // whatever this session's own real rows and saved
                // customization currently are, same real "no DOM
                // mutation to go stale" reasoning that file's own real
                // bug-fix header documents, for free here since this is
                // a plain derived value off uiState rather than
                // anything mutated in place.
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

                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
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
                        .height(topFixtureHeight + 36.dp),
                )

                // Scrollable rows in the bottom viewport
                val verticalRowBringIntoViewSpec = remember(density) {
                    val topInsetPx = with(density) { RowHeaderHeight.toPx() }
                    com.jellio.tv.ui.common.TvVerticalRowBringIntoViewSpec(topInsetPx = topInsetPx) {
                        listState.canScrollBackward
                    }
                }

                CompositionLocalProvider(LocalBringIntoViewSpec provides verticalRowBringIntoViewSpec) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(rowsViewportHeight)
                            .clipToBounds()
                            .focusRestorer(),
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
                                        val onCardFocus = remember { { item: BaseItemDto -> highlightedItem.value = item } }
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
                                        )
                                    }
                                    is ComingSoonHomeRow -> ComingSoonRow(entries = row.entries, imageUrl = rawImageUrl, onItemClick = onComingSoonClick)
                                    is StudioHubsHomeRow -> StudioHubRow(services = row.services, logoUrl = serviceLogoUrl, onServiceClick = onServiceClick)
                                }
                            }
                        }
                        if (!editMode) {
                            item(key = "customize_bar_bottom") {
                                HomeCustomizeBar(
                                    editMode = false,
                                    onToggleEdit = { editMode = true },
                                    onReset = { viewModel.resetCustomization() },
                                    modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 32.dp),
                                )
                            }
                        }
                        // Rows still loading below the ones already shown.
                        if (uiState.loadingMore) {
                            item(key = "skeleton") { SkeletonRow() }
                        }
                    }
                }
            }
        }

        // Rendered at this screen's own root Box, not inside the
        // LazyColumn/PosterRow/PosterCard chain above: see PosterCard's
        // own header comment for why a full-screen menu has to live
        // here rather than inside a LazyRow item.
        cardMenuTarget?.let { target ->
            val item = target.item
            val continueWatching = target.row.section.key == "continue-watching"
            val upNext = target.row.section.key == "up-next"
            CardOptionsMenu(
                item = item,
                continueWatching = continueWatching,
                upNext = upNext,
                onGoToDetails = if (continueWatching || upNext) { { onItemClick(item) } } else null,
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
                onToggleWatchlist = if (!continueWatching && !upNext) { { viewModel.toggleWatchlist(session, item) } } else null,
                onToggleWatched = if (!continueWatching && !upNext) { { viewModel.toggleWatched(session, item) } } else null,
                onDismiss = { cardMenuTarget = null },
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
            )
        }
    }
}

// Real port of components/homeCustomizer.js's own
// buildHomeCustomizeBar()/updateHomeCustomizeBar(): a Reset button only
// ever shown while editing, and the toggle itself relabelled Done/
// tinted active the same way that file's own real
// jellio-home-customize-toggle-active class is.
@Composable
private fun HomeCustomizeBar(editMode: Boolean, onToggleEdit: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        if (editMode) {
            Surface(
                onClick = onReset,
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = JellioText),
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
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Icon(imageVector = Icons.Filled.Tune, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(text = if (editMode) com.jellio.tv.ui.i18n.string("action_done") else com.jellio.tv.ui.i18n.string("home_customize"))
            }
        }
    }
}

// Real port of components/homeCustomizer.js's own wrapRowForCustomization()
// plus buildRowBar()/applyHomeCustomization()'s own real
// data-hidden/jellio-home-editing CSS rules (css/app.css's own
// .jellio-row-editor[data-hidden='true'] .jellio-row-editor-content):
// a hidden row's own content never renders at all, editing or not,
// only its own bar (name plus a Show toggle) does, and only while
// editing; a visible row's own bar only ever shows while editing too.
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
        }
        if (!hidden) content()
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
            .padding(top = 8.dp, bottom = 4.dp)
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = displayName,
            color = JellioTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HomeRowEditorButton(icon = Icons.Filled.ArrowUpward, enabled = canMoveUp, onClick = onMoveUp, contentDescription = com.jellio.tv.ui.i18n.Strings.get("home_move_row_up"))
            HomeRowEditorButton(icon = Icons.Filled.ArrowDownward, enabled = canMoveDown, onClick = onMoveDown, contentDescription = com.jellio.tv.ui.i18n.Strings.get("home_move_row_down"))
            HomeRowEditorButton(
                icon = if (hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                enabled = true,
                active = hidden,
                onClick = onToggleHidden,
                contentDescription = if (hidden) "Show row" else "Hide row",
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
        ),
        modifier = Modifier.padding(2.dp),
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = if (active) JellioSecondary else JellioTextSecondary)
        }
    }
}
