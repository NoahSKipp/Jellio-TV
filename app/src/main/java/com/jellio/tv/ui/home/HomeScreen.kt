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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.jellio.tv.ui.common.ProgressSweep
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
    onPlayDirect: (String, String?) -> Unit,
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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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
    // Real port of components/cardOptionsMenu.js's own window.confirm()
    // step before deleteItem() ever fires: the card options menu itself
    // only ever sets this, RemoveFromLibraryConfirm below owns the
    // actual real deletion call.
    var pendingDeleteItem by remember { mutableStateOf<BaseItemDto?>(null) }
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
            uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                // Real port of css/progress.css's own real sweep, that
                // file's own header calls out real work with nothing on
                // screen to say so as the exact case this covers.
                ProgressSweep(modifier = Modifier.align(Alignment.TopCenter))
                Text(text = "Loading...", color = JellioTextSecondary, modifier = Modifier.align(Alignment.Center))
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "Something went wrong", color = JellioTextSecondary)
            }
            uiState.rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Nothing here yet.", color = JellioTextSecondary)
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
                val liveKeys = uiState.rows.map { it.key }
                val order = HomeCustomization.effectiveOrder(liveKeys, uiState.customization)
                val byKey = uiState.rows.associateBy { it.key }
                val orderedRows = order.mapNotNull { byKey[it] }
                val visibleRows = if (editMode) {
                    orderedRows
                } else {
                    orderedRows.filter { !uiState.customization.hidden.contains(it.key) }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp)
                        .focusRestorer(),
                ) {
                    item {
                        // Real bug found live: pressing Up back to this
                        // real hero only ever scrolled this list the
                        // minimum real distance Compose's own default
                        // "bring the newly focused child into view"
                        // logic needed to make that child's own bounds
                        // visible, not necessarily this list's own real
                        // top, so the hero's own top edge (its own real
                        // title/logo row) stayed clipped behind
                        // whatever content padding sits above it.
                        // Forced back to a real hard scrollToItem(0) the
                        // moment focus lands anywhere inside this real
                        // item instead, same real fix a plain
                        // BringIntoView request cannot give on its own.
                        Box(
                            modifier = Modifier.onFocusChanged { state ->
                                if (state.hasFocus) {
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            },
                        ) {
                            HeroSection(items = uiState.heroItems, imageUrl = imageUrl, onViewDetails = onItemClick)
                        }
                    }
                    // Real screens/home.js's own jellio-home-greeting: real
                    // feedback found "Welcome back" read as a placeholder
                    // the moment it was ever anything else, a real
                    // time-of-day/name greeting instead.
                    if (uiState.greeting.isNotEmpty()) {
                        item {
                            Text(
                                text = uiState.greeting,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(
                                    start = 48.dp,
                                    top = 24.dp,
                                    bottom = 8.dp,
                                ),
                            )
                        }
                    }
                    item {
                        HomeCustomizeBar(
                            editMode = editMode,
                            onToggleEdit = { editMode = !editMode },
                            onReset = { viewModel.resetCustomization() },
                            modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                        )
                    }
                    items(visibleRows, key = { it.key }) { row ->
                        val hidden = uiState.customization.hidden.contains(row.key)
                        val index = order.indexOf(row.key)
                        HomeRowEditor(
                            editMode = editMode,
                            displayName = row.displayName,
                            hidden = hidden,
                            canMoveUp = index > 0,
                            canMoveDown = index < order.size - 1,
                            onMoveUp = { viewModel.moveRow(row.key, -1) },
                            onMoveDown = { viewModel.moveRow(row.key, 1) },
                            onToggleHidden = { viewModel.toggleRowHidden(row.key) },
                        ) {
                            when (row) {
                                is PosterHomeRow -> if (row.landscape) {
                                    LandscapeRow(
                                        section = row.section,
                                        rawImageUrl = rawImageUrl,
                                        onItemClick = onItemClick,
                                        onTitleClick = { rowListTarget = row.section },
                                        onItemOptions = { item -> cardMenuTarget = CardMenuTarget(item, row) },
                                        removingItemId = removingTarget?.first,
                                        onShatterFinished = { item ->
                                            val isShowRemoval = removingTarget?.second == true
                                            removingTarget = null
                                            if (isShowRemoval) {
                                                viewModel.hideShowFromRow(item)
                                            } else {
                                                viewModel.removeFromRow(session, item)
                                            }
                                        },
                                    )
                                } else {
                                    PosterRow(
                                        section = row.section,
                                        imageUrl = imageUrl,
                                        onItemClick = onItemClick,
                                        onItemOptions = { item -> cardMenuTarget = CardMenuTarget(item, row) },
                                        onTitleClick = { rowListTarget = row.section },
                                    )
                                }
                                is ComingSoonHomeRow -> ComingSoonRow(entries = row.entries, imageUrl = rawImageUrl, onItemClick = onComingSoonClick)
                                is StudioHubsHomeRow -> StudioHubRow(services = row.services, logoUrl = serviceLogoUrl, onServiceClick = onServiceClick)
                            }
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
                            if (viewModel.restartFromBeginning(session, item)) onPlayDirect(item.Id, null)
                        }
                    }
                } else {
                    null
                },
                onRemoveFromRow = if (continueWatching || upNext) { { removingTarget = item.Id to false } } else null,
                onRemoveShowFromRow = if (upNext && item.SeriesId != null) { { removingTarget = item.Id to true } } else null,
                onToggleWatchlist = if (!continueWatching && !upNext) { { viewModel.toggleWatchlist(session, item) } } else null,
                onToggleWatched = if (!continueWatching && !upNext) { { viewModel.toggleWatched(session, item) } } else null,
                canDelete = !continueWatching && !upNext && uiState.canDeleteItems,
                onDeleteItem = { pendingDeleteItem = item },
                onDismiss = { cardMenuTarget = null },
            )
        }

        pendingDeleteItem?.let { item ->
            RemoveFromLibraryConfirm(
                item = item,
                onConfirm = {
                    pendingDeleteItem = null
                    viewModel.deleteItem(item)
                },
                onCancel = { pendingDeleteItem = null },
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
                    Text(text = "Reset")
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
                Text(text = if (editMode) "Done" else "Customize")
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
        if (!hidden) RowEntrance(content = content)
    }
}

// Real port of css/library-browse.css's own real jellio-row-enter
// keyframe (opacity 0 to 1, translateY 12px to 0, 420ms ease-out),
// css/streaming-hub.css's own jellio-hub-enter reusing the identical
// animation for hub rows: this LazyColumn item's own real first
// composition (remember here is scoped to it, the same as every other
// row's own real key) is close enough to that file's own real
// scroll-into-view trigger for the same real fade-and-rise read.
@Composable
private fun RowEntrance(content: @Composable () -> Unit) {
    val startOffsetPx = with(LocalDensity.current) { 12.dp.toPx() }
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(startOffsetPx) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, animationSpec = tween(420, easing = LinearOutSlowInEasing)) }
        launch { offsetY.animateTo(0f, animationSpec = tween(420, easing = LinearOutSlowInEasing)) }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = offsetY.value
        },
    ) {
        content()
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
            HomeRowEditorButton(icon = Icons.Filled.ArrowUpward, enabled = canMoveUp, onClick = onMoveUp, contentDescription = "Move row up")
            HomeRowEditorButton(icon = Icons.Filled.ArrowDownward, enabled = canMoveDown, onClick = onMoveDown, contentDescription = "Move row down")
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
