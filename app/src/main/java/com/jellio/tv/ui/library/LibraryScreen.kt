package com.jellio.tv.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.PosterRow
import com.jellio.tv.ui.theme.JellioTextSecondary

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

    // Keyed on both real Id and Name: getLibraryNavEntries()'s own
    // synthetic Anime stand-in shares the plain Shows library's exact
    // real Id (Anime has no library of its own), Id alone would never
    // notice a picker tap swapping between the two.
    LaunchedEffect(library.Id, library.Name) { viewModel.load(session, library) }

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
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (uiState.coverflowItems.size >= COVERFLOW_MIN_SLIDES) {
                    item {
                        LibraryCoverflow(items = uiState.coverflowItems, imageUrl = imageUrl, onViewDetails = onItemClick, badgeText = uiState.coverflowBadge)
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
                    PosterRow(section = section, imageUrl = imageUrl, onItemClick = onItemClick)
                }
            }
        }
    }
}
