package com.jellio.tv.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.home.PosterRow
import com.jellio.tv.ui.home.RowListModal
import com.jellio.tv.ui.home.rememberCardOptionsHost
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled

private val HeroHeight = 360.dp
private const val ALL_FILTER = "all"

// The page a Studio Hub tile opens: real screens/service.js's own
// "Popular on {service}" header (no Play/View Details of its own,
// unlike HeroSection, this hero fronts a whole service rather than
// one real item) over one row per real matched collection, filtered
// by the same real All/Movies/TV Shows/genre chip bar that file's own
// buildChips()/applyFilter() render.
@Composable
fun ServiceScreen(
    session: Session,
    serviceName: String,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServiceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var filter by remember { mutableStateOf(ALL_FILTER) }
    var rowListTarget by remember { mutableStateOf<ServiceRow?>(null) }
    // Same real immersive-screen gap DetailScreen's own header comment
    // documents: no TopNavPill mounts here for a D-pad press to search
    // Down from, so nothing ever requests focus into this screen
    // without this.
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(serviceName) { viewModel.load(session, serviceName) }
    LaunchedEffect(uiState.rows) {
        if (uiState.rows.isNotEmpty()) contentFocusRequester.requestFocus()
    }
    val openItemOptions = rememberCardOptionsHost(
        canDeleteItems = uiState.canDeleteItems,
        onToggleWatchlist = { viewModel.toggleWatchlist(session, it) },
        onToggleWatched = { viewModel.toggleWatched(session, it) },
        onDeleteItem = { viewModel.deleteItem(it) },
    )

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "Could not load $serviceName", color = JellioTextSecondary)
            }
            uiState.rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing imported for $serviceName yet.", color = JellioTextSecondary)
            }
            else -> {
                // Real screens/service.js's own makeRowTitleClickable():
                // that file's own call passes the row's full real
                // items/fetchAll, never the currently filtered subset a
                // genre chip narrowed the visible track down to, so the
                // original ServiceRow (not the filtered HomeSection
                // below) travels alongside its own filtered items here.
                val visibleRows = uiState.rows.mapNotNull { row ->
                    val filtered = filteredItems(row, filter)
                    if (filtered.isEmpty()) null else row to filtered
                }
                LazyColumn(modifier = Modifier.fillMaxSize().focusRequester(contentFocusRequester).focusRestorer()) {
                    item { ServiceHero(name = uiState.serviceName, heroItem = uiState.heroItem, imageUrl = imageUrl) }
                    item {
                        ServiceFilterChips(
                            genres = uiState.topGenres,
                            selected = filter,
                            onSelect = { filter = it },
                        )
                    }
                    items(visibleRows, key = { it.first.title }) { (row, filteredRowItems) ->
                        PosterRow(
                            section = HomeSection(title = row.title, items = filteredRowItems),
                            imageUrl = imageUrl,
                            onItemClick = onItemClick,
                            onItemOptions = openItemOptions,
                            onTitleClick = { rowListTarget = row },
                        )
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
    val typeMatch = filter == ALL_FILTER || isGenreFilter || row.kind == filter
    if (!typeMatch) return emptyList()
    if (!isGenreFilter) return row.items
    val genre = filter.removePrefix("genre:")
    return row.items.filter { it.Genres?.contains(genre) == true }
}

@Composable
private fun ServiceFilterChips(genres: List<String>, selected: String, onSelect: (String) -> Unit) {
    val chips = buildList {
        add(ALL_FILTER to "All")
        add("movies" to "Movies")
        add("tvshows" to "TV Shows")
        genres.forEach { genre -> add("genre:$genre" to genre) }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp),
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
                Text(text = label, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun ServiceHero(
    name: String,
    heroItem: BaseItemDto?,
    imageUrl: (BaseItemDto, String, Int) -> String,
) {
    val heroHeight = HeroHeight.scaled()
    Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
        if (heroItem != null) {
            AsyncImage(
                model = imageUrl(heroItem, "Backdrop", 1280),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(heroHeight),
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(heroHeight).background(JellioBg))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, JellioBg),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 32.dp),
        ) {
            Text(text = "Popular on", color = JellioTextSecondary, style = MaterialTheme.typography.bodyLarge)
            Text(text = name, color = JellioText, style = MaterialTheme.typography.titleLarge)
        }
    }
}
