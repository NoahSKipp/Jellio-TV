package com.jellio.tv.ui.library

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.home.PosterRow
import com.jellio.tv.ui.home.RowListModal
import com.jellio.tv.ui.nav.rememberNavCompact
import com.jellio.tv.ui.theme.JellioTextSecondary

@Composable
fun LibraryScreen(
    session: Session,
    library: BaseItemDto,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    contentFocusRequester: FocusRequester,
    onCompactChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val compact = rememberNavCompact(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
    var rowListTarget by remember { mutableStateOf<HomeSection?>(null) }

    // Keyed on both real Id and Name: getLibraryNavEntries()'s own
    // synthetic Anime stand-in shares the plain Shows library's exact
    // real Id (Anime has no library of its own), Id alone would never
    // notice a picker tap swapping between the two.
    LaunchedEffect(library.Id, library.Name) { viewModel.load(session, library) }
    LaunchedEffect(compact) { onCompactChange(compact) }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.emptyMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.emptyMessage ?: "", color = JellioTextSecondary)
            }
            uiState.sections.all { it.items.isEmpty() } && uiState.coverflowItems.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing here yet.", color = JellioTextSecondary)
            }
            // Real bug found live: D-pad Down from TopNavPill only ever
            // worked on Home, same real fix TopNavPill's own comment
            // documents.
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup()
                    .focusRequester(contentFocusRequester),
            ) {
                if (uiState.coverflowItems.size >= COVERFLOW_MIN_SLIDES) {
                    item {
                        LibraryCoverflow(items = uiState.coverflowItems, imageUrl = imageUrl, onViewDetails = onItemClick, badgeText = uiState.coverflowBadge, editorial = uiState.editorial)
                    }
                }
                item {
                    Text(
                        text = uiState.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(
                            top = if (uiState.coverflowItems.size >= COVERFLOW_MIN_SLIDES) 32.dp else 140.dp,
                            start = 48.dp,
                            bottom = 12.dp,
                        ),
                    )
                }
                items(uiState.sections, key = { it.title }) { section ->
                    PosterRow(
                        section = section,
                        imageUrl = imageUrl,
                        onItemClick = onItemClick,
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
            )
        }
    }
}
