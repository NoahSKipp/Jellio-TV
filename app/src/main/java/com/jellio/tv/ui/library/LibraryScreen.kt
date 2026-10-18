package com.jellio.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jellio.tv.ui.common.TvVerticalRowBringIntoViewSpec
import com.jellio.tv.ui.home.RowHeaderHeight
import com.jellio.tv.ui.home.ShowcaseHost
import com.jellio.tv.ui.theme.JellioText
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.NoOpBringIntoViewSpec
import com.jellio.tv.ui.common.TvBringIntoViewSpec
import com.jellio.tv.ui.common.ScreenSpinner
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.home.PosterRow
import com.jellio.tv.ui.home.RowListModal
import com.jellio.tv.ui.home.rememberCardOptionsHost
import com.jellio.tv.ui.theme.JellioTextSecondary

// Same layout as Home: the focused card's backdrop and details fill the top
// of the screen and the rows scroll underneath. What the old carousel showed
// is now the first row (Featured, or Trending on AniList for Anime).
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    session: Session,
    library: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    // A library switch reuses this composable; key() starts every remember
    // (scroll position included) over for the new library.
    key(library.Id, library.Name) {
        val uiState by viewModel.uiState.collectAsState()
        val listState = com.jellio.tv.ui.common.rememberTvColumnState()
        var rowListTarget by remember { mutableStateOf<HomeSection?>(null) }
        var openFilterField by remember { mutableStateOf<LibraryFilterFieldTarget?>(null) }
        // Closing a filter list hands focus back to the field that opened it,
        // otherwise it fell to the sidebar.
        val sortFieldFocusRequester = remember { FocusRequester() }
        val genreFieldFocusRequester = remember { FocusRequester() }
        // Read only inside the showcase, so focus moves don't recompose the rows.
        val highlightedItem = remember { mutableStateOf<BaseItemDto?>(null) }
        val onCardFocus = remember { { item: BaseItemDto -> highlightedItem.value = item } }
        val openItemOptions = rememberCardOptionsHost(
            onToggleWatchlist = { viewModel.toggleWatchlist(session, it) },
            onToggleWatched = { viewModel.toggleWatched(session, it) },
            onItemChanged = { viewModel.replaceItem(it) },
        )

        LaunchedEffect(Unit) { viewModel.load(session, library) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { com.jellio.tv.ui.perf.ScrollActivity.scrolling = it }
        }
        androidx.compose.runtime.DisposableEffect(Unit) {
            onDispose { com.jellio.tv.ui.perf.ScrollActivity.scrolling = false }
        }

        val featuredRow = remember(uiState.coverflowItems, uiState.coverflowBadge, uiState.editorial) {
            uiState.coverflowItems.takeIf { it.isNotEmpty() }?.let { items ->
                HomeSection(uiState.coverflowBadge ?: uiState.editorial?.tagline ?: "Featured", items)
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                    ScreenSpinner()
                    Text(text = "Loading...", color = JellioTextSecondary, modifier = Modifier.padding(top = 16.dp))
                }
            }
            uiState.emptyMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.emptyMessage ?: "", color = JellioTextSecondary)
            }
            uiState.sections.all { it.items.isEmpty() } && (uiState.mainRow?.items?.isEmpty() != false) && featuredRow == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing here yet.", color = JellioTextSecondary)
            }
            else -> {
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                val density = LocalDensity.current
                val rowsViewportHeight = screenHeight * 0.54f
                val firstItem = featuredRow?.items?.firstOrNull()
                    ?: uiState.mainRow?.items?.firstOrNull()
                    ?: uiState.sections.firstNotNullOfOrNull { it.items.firstOrNull() }

                ShowcaseHost(
                    highlighted = highlightedItem,
                    fallback = firstItem,
                    loadInfo = { item -> viewModel.showcaseInfo(session, item) },
                    imageUrl = imageUrl,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(screenHeight - rowsViewportHeight + 36.dp),
                )

                val rowsSpec = remember(density) {
                    TvVerticalRowBringIntoViewSpec(topInsetPx = with(density) { RowHeaderHeight.toPx() }) { listState.canScrollBackward }
                }
                CompositionLocalProvider(LocalBringIntoViewSpec provides rowsSpec) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(rowsViewportHeight)
                            .focusRestorer(),
                    ) {
                        item(key = "header") {
                            LibraryHeader(
                                title = uiState.title,
                                showFilters = uiState.genreOptions.isNotEmpty() || uiState.mainRow != null,
                                genres = uiState.genreOptions,
                                selectedSort = uiState.selectedSort,
                                selectedGenre = uiState.selectedGenre,
                                onOpenSort = { openFilterField = LibraryFilterFieldTarget.Sort },
                                onOpenGenre = { openFilterField = LibraryFilterFieldTarget.Genre },
                                sortFieldFocusRequester = sortFieldFocusRequester,
                                genreFieldFocusRequester = genreFieldFocusRequester,
                            )
                        }
                        featuredRow?.let { row ->
                            item(key = "featured") {
                                PosterRow(
                                    section = row,
                                    imageUrl = imageUrl,
                                    onItemClick = onItemClick,
                                    onItemOptions = openItemOptions,
                                    onItemFocus = onCardFocus,
                                )
                            }
                        }
                        uiState.mainRow?.let { row ->
                            item(key = "main") {
                                val onMainRowTitleClick = remember(row) { { rowListTarget = row } }
                                PosterRow(
                                    section = row,
                                    imageUrl = imageUrl,
                                    onItemClick = onItemClick,
                                    onItemOptions = openItemOptions,
                                    onTitleClick = onMainRowTitleClick,
                                    onItemFocus = onCardFocus,
                                )
                            }
                        }
                        items(uiState.sections, key = { "section:" + it.title }, contentType = { "library_section" }) { section ->
                            val onSectionTitleClick = remember(section) { { rowListTarget = section } }
                            PosterRow(
                                section = section,
                                imageUrl = imageUrl,
                                onItemClick = onItemClick,
                                onItemOptions = openItemOptions,
                                onTitleClick = onSectionTitleClick,
                                onItemFocus = onCardFocus,
                            )
                        }
                        item(key = "bottom_space") { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
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

        openFilterField?.let { target ->
            when (target) {
                LibraryFilterFieldTarget.Sort -> LibraryFilterFieldOverlay(
                    title = "Sort by",
                    options = LIBRARY_SORT_OPTIONS.map { LibraryFilterOption(it.label, it.value) },
                    selectedValue = uiState.selectedSort,
                    onSelect = { value ->
                        sortFieldFocusRequester.requestFocus()
                        openFilterField = null
                        if (value != null) viewModel.changeSort(session, value)
                    },
                    onDismiss = {
                        sortFieldFocusRequester.requestFocus()
                        openFilterField = null
                    },
                )
                LibraryFilterFieldTarget.Genre -> LibraryFilterFieldOverlay(
                    title = "Genre",
                    options = listOf(LibraryFilterOption("All genres", null)) +
                        uiState.genreOptions.map { LibraryFilterOption(it, it) },
                    selectedValue = uiState.selectedGenre,
                    onSelect = { value ->
                        genreFieldFocusRequester.requestFocus()
                        openFilterField = null
                        viewModel.changeGenre(session, value)
                    },
                    onDismiss = {
                        genreFieldFocusRequester.requestFocus()
                        openFilterField = null
                    },
                )
            }
        }
    }
    }
}

// The library's name with its sort and genre fields beside it.
@Composable
private fun LibraryHeader(
    title: String,
    showFilters: Boolean,
    genres: List<String>,
    selectedSort: String,
    selectedGenre: String?,
    onOpenSort: () -> Unit,
    onOpenGenre: () -> Unit,
    sortFieldFocusRequester: FocusRequester,
    genreFieldFocusRequester: FocusRequester,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            text = title,
            color = JellioText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(start = 48.dp, end = 20.dp, bottom = 12.dp),
        )
        if (showFilters) {
            LibraryFilterFields(
                genres = genres,
                selectedSort = selectedSort,
                selectedGenre = selectedGenre,
                onOpenSort = onOpenSort,
                onOpenGenre = onOpenGenre,
                sortFieldFocusRequester = sortFieldFocusRequester,
                genreFieldFocusRequester = genreFieldFocusRequester,
            )
        }
    }
}

private enum class LibraryFilterFieldTarget { Sort, Genre }

// Real port of screens/library.js's own sort/genre <select> pair
// (SORT_OPTIONS, discoverGenres()): governs only the main row above
// this, same real scope that file's own header comment gives. Real
// feedback live asked for this rail's own field-plus-popover shape
// (matching that <select> pair's own real screenshot) rather than the
// always visible chip row this used to be: LibraryFilterField opens
// LibraryFilterFieldOverlay.kt's own real modal list instead of laying
// every option out inline.
@Composable
private fun LibraryFilterFields(
    genres: List<String>,
    selectedSort: String,
    selectedGenre: String?,
    onOpenSort: () -> Unit,
    onOpenGenre: () -> Unit,
    sortFieldFocusRequester: FocusRequester,
    genreFieldFocusRequester: FocusRequester,
) {
    val sortLabel = LIBRARY_SORT_OPTIONS.firstOrNull { it.value == selectedSort }?.label ?: "Sort"
    val genreLabel = selectedGenre ?: "All genres"
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        LibraryFilterField(label = sortLabel, onClick = onOpenSort, modifier = Modifier.focusRequester(sortFieldFocusRequester))
        if (genres.isNotEmpty()) {
            LibraryFilterField(label = genreLabel, onClick = onOpenGenre, modifier = Modifier.focusRequester(genreFieldFocusRequester))
        }
    }
}
