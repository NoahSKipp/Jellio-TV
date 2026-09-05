package com.jellio.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.jellio.tv.ui.common.ScreenSpinner
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.home.PosterRow
import com.jellio.tv.ui.home.RowListModal
import com.jellio.tv.ui.home.rememberCardOptionsHost
import com.jellio.tv.ui.theme.JellioTextSecondary

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
    // Real bug found live: MainActivity's own call site for this screen
    // is one fixed real position in a when branch, its own library
    // argument changing (a picker tap between Shows/Anime/Movies) is
    // never a fresh real composable instance on its own, only a
    // recomposition of this same one - so listState's own scroll
    // offset and the coverflow's own coverflowWasFocused guard further
    // below both survived a real library switch untouched. If that
    // guard already read true from whichever real library this reader
    // was on before, its own false-to-true transition (the one real
    // thing that fires scrollToItem(0) at all) never re-fired for this
    // new one, leaving this list scrolled wherever the last real
    // library left it - real feedback's own "view is too low, cuts off
    // the text above the carousel" is exactly that. key() below forces
    // every real remember in this whole function to start over fresh
    // on a real library switch, this list's own scroll position and
    // that guard included, rather than tracking each one down by hand.
    key(library.Id, library.Name) {
        val uiState by viewModel.uiState.collectAsState()
        val listState = rememberLazyListState()
        // Real bug found live, on a real screen recording: switching
        // focus between an arrow and View Details still nudged this
        // list a real few pixels for exactly one real frame before the
        // real snapshotFlow correction below caught it back - "tiny
        // flickering", real feedback's own words, every real time on
        // every real library with anything sitting above this stage to
        // reveal it (Movies has nothing there, so the identical real
        // nudge is invisible on it specifically). A real reactive
        // correction can only ever answer a real drift a frame after
        // it already rendered; blocking the real scroll from ever
        // reaching this list in the first place, while this item holds
        // real focus, is the only way to close that last real frame -
        // NestedScrollConnection's own real onPreScroll sits above this
        // list's own real internal scrollable in this same real
        // modifier chain, first real refusal on any real delta trying
        // to reach it. scrollToItem(0) itself is a direct real jump on
        // this list's own real state, not a delta dispatched through
        // this same real chain, so this real block never fights that.
        var coverflowHasFocus by remember { mutableStateOf(false) }
        val blockScrollWhileCoverflowFocused = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                    if (coverflowHasFocus) available else Offset.Zero
            }
        }
        var rowListTarget by remember { mutableStateOf<HomeSection?>(null) }
        var openFilterField by remember { mutableStateOf<LibraryFilterFieldTarget?>(null) }
        // Real bug found live: selecting a real option (or dismissing
        // via Back) removed this whole real overlay, focused option
        // included, with nothing requesting focus anywhere else first -
        // Compose had no real fallback target, and the real default it
        // picked landed on this rail's own sidebar instead, expanding it
        // ("selecting a genre opens the navbar", real feedback's own
        // words). Reclaiming focus onto whichever real field opened it,
        // in the same real callback that closes it, answers this the
        // same real way MainActivity's own sidebar popovers just did.
        val sortFieldFocusRequester = remember { FocusRequester() }
        val genreFieldFocusRequester = remember { FocusRequester() }
        val openItemOptions = rememberCardOptionsHost(
            canDeleteItems = uiState.canDeleteItems,
            onToggleWatchlist = { viewModel.toggleWatchlist(session, it) },
            onToggleWatched = { viewModel.toggleWatched(session, it) },
            onDeleteItem = { viewModel.deleteItem(it) },
        )

        LaunchedEffect(Unit) { viewModel.load(session, library) }

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
                    .nestedScroll(blockScrollWhileCoverflowFocused)
                    // Real feedback live: this rail's own reference
                    // viewport (TvScale.kt's own header) is only ~540dp
                    // tall, so on initial open (or back at this list's
                    // own real top via View Details/an arrow) a reader
                    // should see this library's own carousel, title and
                    // filter chips, AND its first content row, not just
                    // the first two with the row cut off below the
                    // fold. Trimmed from 32dp: every dp of top gutter
                    // here is a dp this list's own first row loses.
                    .padding(top = 16.dp)
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
                        // Real bug found live, on a real screen
                        // recording: NoOpBringIntoViewSpec's own real
                        // suppression stops the default request from
                        // ever nudging this list while focus already
                        // lives inside this item, confirmed stable
                        // across many real switches between the arrows
                        // and View Details in that same real recording
                        // - but the one real transition into this item
                        // (false-to-true, this stage's own real
                        // editorial/badge header still cut off well
                        // after that point in that same recording, "
                        // occasionally correct but usually not") still
                        // raced something outside this real callback's
                        // own control, a single real scrollToItem(0)
                        // call only sometimes winning. Reasserting it a
                        // few real times over the next 250ms, rather
                        // than trusting the first call alone, closes
                        // that race regardless of whatever it is
                        // racing against: scrollToItem(0) is a real
                        // instant jump, not an animation, so reasserting
                        // it after something else already moved this
                        // list is still a real correction, not a
                        // visible fight.
                        // Real bug found live, on a real screen
                        // recording: a real guessed delay before a
                        // second reassertion (first 250ms, then 220ms)
                        // lost anyway, every real time, to a later real
                        // default scroll landing at a real different
                        // moment each time (confirmed live: once right
                        // after this rail's own sidebar finished
                        // collapsing, once past 500ms with no sidebar
                        // involved at all) - not a fixed real delay at
                        // all, so no real guessed delay ever answers it
                        // for good. Watching this list's own real scroll
                        // position directly instead, for as long as
                        // focus actually lives anywhere inside this
                        // item, and correcting the real instant it ever
                        // actually drifts off item 0, answers every real
                        // one of them regardless of timing - reactive to
                        // a real state change rather than a blind real
                        // guess at when one might happen. Kept as a real
                        // fallback alongside the block above: this list's
                        // own coverflowHasFocus now lives one real scope
                        // up (the nestedScroll connection above needs it
                        // too), this item's own onFocusChanged below is
                        // the one real place that still sets it.
                        LaunchedEffect(coverflowHasFocus) {
                            if (coverflowHasFocus) {
                                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                                    .collect { (index, offset) ->
                                        if (index != 0 || offset != 0) listState.scrollToItem(0)
                                    }
                            }
                        }
                        Box(
                            modifier = Modifier.onFocusChanged { state ->
                                coverflowHasFocus = state.hasFocus
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
                            top = 8.dp,
                            start = 48.dp,
                            bottom = 8.dp,
                        ),
                    )
                }
                if (uiState.genreOptions.isNotEmpty() || uiState.mainRow != null) {
                    item {
                        LibraryFilterFields(
                            genres = uiState.genreOptions,
                            selectedSort = uiState.selectedSort,
                            selectedGenre = uiState.selectedGenre,
                            onOpenSort = { openFilterField = LibraryFilterFieldTarget.Sort },
                            onOpenGenre = { openFilterField = LibraryFilterFieldTarget.Genre },
                            sortFieldFocusRequester = sortFieldFocusRequester,
                            genreFieldFocusRequester = genreFieldFocusRequester,
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
        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
    ) {
        LibraryFilterField(label = sortLabel, onClick = onOpenSort, modifier = Modifier.focusRequester(sortFieldFocusRequester))
        if (genres.isNotEmpty()) {
            LibraryFilterField(label = genreLabel, onClick = onOpenGenre, modifier = Modifier.focusRequester(genreFieldFocusRequester))
        }
    }
}
