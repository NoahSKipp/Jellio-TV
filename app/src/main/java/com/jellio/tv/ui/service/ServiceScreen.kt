package com.jellio.tv.ui.service

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.serviceColor
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.ScreenSpinner
import com.jellio.tv.ui.common.TvVerticalRowBringIntoViewSpec
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.home.PosterRow
import com.jellio.tv.ui.home.RowHeaderHeight
import com.jellio.tv.ui.home.RowListModal
import com.jellio.tv.ui.home.ShowcaseHost
import com.jellio.tv.ui.home.rememberCardOptionsHost
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

private const val ALL_FILTER = "all"

// A Studio Hub: the same layout as Home and Libraries, the focused card's
// backdrop and details on top and one row per matched collection below,
// filtered by the All/Movies/Shows/genre chips.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceScreen(
    session: Session,
    serviceName: String,
    logoUrl: String?,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServiceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(ALL_FILTER) }
    var rowListTarget by remember { mutableStateOf<ServiceRow?>(null) }
    // No sidebar here to move focus in from, so the screen asks for it.
    val contentFocusRequester = remember { FocusRequester() }
    val listState = com.jellio.tv.ui.common.rememberTvColumnState()
    // Read only inside the showcase, so focus moves don't recompose the rows.
    val highlightedItem = remember { mutableStateOf<BaseItemDto?>(null) }
    val onCardFocus = remember { { item: BaseItemDto -> highlightedItem.value = item } }

    LaunchedEffect(serviceName) { viewModel.load(session, serviceName) }
    // Once, when the first row arrives: rows keep arriving after that.
    LaunchedEffect(uiState.rows.isNotEmpty()) {
        if (uiState.rows.isNotEmpty()) runCatching { contentFocusRequester.requestFocus() }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { com.jellio.tv.ui.perf.ScrollActivity.scrolling = it }
    }
    DisposableEffect(Unit) {
        onDispose { com.jellio.tv.ui.perf.ScrollActivity.scrolling = false }
    }
    val openItemOptions = rememberCardOptionsHost(
        onToggleWatchlist = { viewModel.toggleWatchlist(session, it) },
        onToggleWatched = { viewModel.toggleWatched(session, it) },
    )

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                    ScreenSpinner()
                    Text(text = com.jellio.tv.ui.i18n.string("status_loading"), color = JellioTextSecondary, modifier = Modifier.padding(top = 16.dp))
                }
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: com.jellio.tv.ui.i18n.formatString("service_load_error", serviceName), color = JellioTextSecondary)
            }
            uiState.rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = com.jellio.tv.ui.i18n.formatString("service_nothing_imported", serviceName), color = JellioTextSecondary)
            }
            else -> {
                // A row title opens the row's full collection, not the subset
                // a genre chip narrowed it to, so the ServiceRow travels along.
                val visibleRows = uiState.rows.mapNotNull { row ->
                    val filtered = filteredItems(row, filter)
                    if (filtered.isEmpty()) null else row to filtered
                }
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                val density = LocalDensity.current
                val rowsViewportHeight = screenHeight * 0.54f

                ShowcaseHost(
                    highlighted = highlightedItem,
                    fallback = uiState.heroItem ?: visibleRows.firstOrNull()?.second?.firstOrNull(),
                    loadInfo = { item -> viewModel.showcaseInfo(session, item) },
                    imageUrl = imageUrl,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(screenHeight - rowsViewportHeight),
                )

                // The service's colour washes in from the top, over the backdrop.
                serviceColor(serviceName)?.let { brand ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color(brand).copy(alpha = 0.24f),
                                    0.7f to Color.Transparent,
                                ),
                            ),
                    )
                }

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
                            .focusRequester(contentFocusRequester)
                            .focusRestorer(),
                    ) {
                        item(key = "header") {
                            ServiceHeader(
                                name = uiState.serviceName,
                                logoUrl = logoUrl,
                                genres = uiState.topGenres,
                                selected = filter,
                                onSelect = { filter = it },
                            )
                        }
                        items(visibleRows, key = { "row:" + it.first.title }) { (row, filteredRowItems) ->
                            PosterRow(
                                section = HomeSection(title = row.title, items = filteredRowItems),
                                imageUrl = imageUrl,
                                onItemClick = onItemClick,
                                onItemOptions = openItemOptions,
                                onTitleClick = { rowListTarget = row },
                                onItemFocus = onCardFocus,
                            )
                        }
                        item(key = "bottom_space") { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }

        rowListTarget?.let { row ->
            RowListModal(
                title = row.title,
                items = row.items,
                imageUrl = imageUrl,
                onItemClick = onItemClick,
                onDismiss = { rowListTarget = null },
                fetchAll = { viewModel.fetchAllForRow(session, row) },
            )
        }
    }
}

// Mirrors screens/service.js's own applyFilter(): a row's own real
// kind decides whether the Movies/TV Shows chip includes it at all
// (a genre chip never filters by kind), a card inside a kind-matched
// row still needs the real chosen genre among its own real Genres to
// stay visible under a genre chip, "all" real cards visible under
// either the All chip or a chip whose own kind already matched.
private fun filteredItems(row: ServiceRow, filter: String): List<BaseItemDto> {
    val isGenreFilter = filter.startsWith("genre:")
    if (row.kind == MIXED_KIND && (filter == "movies" || filter == "tvshows")) {
        val type = if (filter == "movies") "Movie" else "Series"
        return row.items.filter { it.Type.equals(type, ignoreCase = true) }
    }
    val typeMatch = filter == ALL_FILTER || isGenreFilter || row.kind == filter
    if (!typeMatch) return emptyList()
    if (!isGenreFilter) return row.items
    val genre = filter.removePrefix("genre:")
    return row.items.filter { it.Genres?.contains(genre) == true }
}

// The hub's name with the All/Movies/Shows/genre chips beside it.
@Composable
private fun ServiceHeader(name: String, logoUrl: String?, genres: List<String>, selected: String, onSelect: (String) -> Unit) {
    val chips = buildList {
        add(ALL_FILTER to "All")
        add("movies" to "Movies")
        add("tvshows" to "Shows")
        genres.forEach { genre -> add("genre:$genre" to genre) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp, bottom = 8.dp),
    ) {
        var logoFailed by remember(logoUrl) { mutableStateOf(logoUrl == null) }
        if (logoFailed) {
            Text(text = name, color = JellioText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        } else {
            AsyncImage(
                model = logoUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn),
                onState = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
                modifier = Modifier.height(30.dp).width(110.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(start = 24.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(chips, key = { it.first }) { (key, label) ->
                val isSelected = key == selected
                Surface(
                    onClick = { onSelect(key) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) JellioSecondary else JellioBgElevated,
                        contentColor = if (isSelected) JellioBg else JellioText,
                    ),
                ) {
                    Text(text = label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}
