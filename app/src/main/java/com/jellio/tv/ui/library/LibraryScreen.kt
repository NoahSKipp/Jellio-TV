package com.jellio.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.NoOpBringIntoViewSpec
import com.jellio.tv.ui.common.ScreenSpinner
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.home.PosterRow
import com.jellio.tv.ui.home.RowListModal
import com.jellio.tv.ui.home.rememberCardOptionsHost
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    session: Session,
    library: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var rowListTarget by remember { mutableStateOf<HomeSection?>(null) }
    val openItemOptions = rememberCardOptionsHost(
        canDeleteItems = uiState.canDeleteItems,
        onToggleWatchlist = { viewModel.toggleWatchlist(session, it) },
        onToggleWatched = { viewModel.toggleWatched(session, it) },
        onDeleteItem = { viewModel.deleteItem(it) },
    )

    // Keyed on both real Id and Name: getLibraryNavEntries()'s own
    // synthetic Anime stand-in shares the plain Shows library's exact
    // real Id (Anime has no library of its own), Id alone would never
    // notice a picker tap swapping between the two.
    LaunchedEffect(library.Id, library.Name) { viewModel.load(session, library) }

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
            uiState.sections.all { it.items.isEmpty() } && (uiState.mainRow?.items?.isEmpty() != false) && uiState.coverflowItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing here yet.", color = JellioTextSecondary)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp)
                    .focusRestorer(),
            ) {
                if (uiState.coverflowItems.size >= COVERFLOW_MIN_SLIDES) {
                    item {
                        // Real HomeScreen.kt's own header on this exact
                        // fix: LibraryCoverflow's own real View Details
                        // button sits near its own bottom edge too, so
                        // without this, Compose's own default per-child
                        // bring-into-view request left this list
                        // scrolled to that button's own real bounds on
                        // a Down-then-Up round trip, never this item's
                        // own real top. A guarded, fire-once version of
                        // this (that same file's own header covers why
                        // it moved past that shape) still let this
                        // list drift away from this item's own real top
                        // on every later real focus move the guard no
                        // longer answered: cancelling whichever real job
                        // this callback's own last call started, every
                        // real time rather than only the first, keeps
                        // exactly one real scrollToItem(0) in flight at
                        // once, always the newest.
                        // Real HomeScreen.kt's own header on this exact
                        // round: a delay before scrolling let our own
                        // scrollToItem(0) win the race against Compose's
                        // own default per-child bring-into-view request,
                        // but that default request still visibly nudged
                        // this list for the delay's own real duration
                        // before snapping back, reading as "jumps up and
                        // down". NoOpBringIntoViewSpec (its own header
                        // covers why) suppresses that default request
                        // outright instead, so this is the only real
                        // thing that ever scrolls this list while focus
                        // lives anywhere inside this item.
                        var coverflowWasFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier.onFocusChanged { state ->
                                if (state.hasFocus && !coverflowWasFocused) {
                                    scope.launch { listState.scrollToItem(0) }
                                }
                                coverflowWasFocused = state.hasFocus
                            },
                        ) {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides NoOpBringIntoViewSpec) {
                                LibraryCoverflow(items = uiState.coverflowItems, imageUrl = imageUrl, onViewDetails = onItemClick, badgeText = uiState.coverflowBadge, editorial = uiState.editorial)
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = uiState.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(
                            top = 12.dp,
                            start = 48.dp,
                            bottom = 12.dp,
                        ),
                    )
                }
                if (uiState.genreOptions.isNotEmpty() || uiState.mainRow != null) {
                    item {
                        LibraryFilterChips(
                            genres = uiState.genreOptions,
                            selectedSort = uiState.selectedSort,
                            selectedGenre = uiState.selectedGenre,
                            onSelectSort = { viewModel.changeSort(session, it) },
                            onSelectGenre = { viewModel.changeGenre(session, it) },
                        )
                    }
                }
                uiState.mainRow?.let { row ->
                    item {
                        PosterRow(
                            section = row,
                            imageUrl = imageUrl,
                            onItemClick = onItemClick,
                            onItemOptions = openItemOptions,
                            onTitleClick = { rowListTarget = row },
                        )
                    }
                }
                items(uiState.sections, key = { it.title }) { section ->
                    PosterRow(
                        section = section,
                        imageUrl = imageUrl,
                        onItemClick = onItemClick,
                        onItemOptions = openItemOptions,
                        onTitleClick = { rowListTarget = section },
                    )
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
    }
}

// Real port of screens/library.js's own sort/genre <select> pair
// (SORT_OPTIONS, discoverGenres()): governs only the main row above
// this, same real scope that file's own header comment gives. Two
// real chip rows rather than a dropdown, same real reason
// ServiceScreen's own ServiceFilterChips already picks a chip row
// over one: a D-pad has no real hover/click affordance a
// dropdown needs, a focusable row of real chips does not.
@Composable
private fun LibraryFilterChips(
    genres: List<String>,
    selectedSort: String,
    selectedGenre: String?,
    onSelectSort: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
) {
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = if (genres.isNotEmpty()) 8.dp else 12.dp),
        ) {
            items(LIBRARY_SORT_OPTIONS, key = { it.value }) { option ->
                FilterChip(label = option.label, selected = option.value == selectedSort, onClick = { onSelectSort(option.value) })
            }
        }
        if (genres.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                item {
                    FilterChip(label = "All genres", selected = selectedGenre == null, onClick = { onSelectGenre(null) })
                }
                items(genres, key = { it }) { genre ->
                    FilterChip(label = genre, selected = genre == selectedGenre, onClick = { onSelectGenre(genre) })
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) JellioSecondary else JellioBgElevated,
            contentColor = if (selected) JellioBg else JellioText,
        ),
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}
