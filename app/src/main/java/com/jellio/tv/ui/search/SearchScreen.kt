package com.jellio.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.jellio.tv.ui.home.PosterCard
import com.jellio.tv.ui.common.JellioTextField
import com.jellio.tv.ui.theme.JellioTextSecondary

// Mirrors screens/search.js's own real live-search shape: a query box
// over a results grid, Movie/Series only, the same real endpoint
// runtime/api.js's own searchItems() calls.
@Composable
fun SearchScreen(
    session: Session,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Real bug found live: D-pad Down from TopNavPill only ever worked
    // on Home, since only Home's own content attached itself to the
    // pill's shared real focus target, same real fix TopNavPill's own
    // comment documents.
    Column(
        modifier = modifier
            .fillMaxSize()
            .focusGroup()
            .focusRequester(contentFocusRequester)
            .padding(top = 140.dp, start = 48.dp, end = 48.dp),
    ) {
        Text(text = "Search", style = MaterialTheme.typography.titleLarge)
        JellioTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChange(session, it) },
            label = "Search movies and shows",
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp).width(480.dp),
        )

        when {
            uiState.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Searching...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "", color = JellioTextSecondary)
            }
            uiState.hasSearched && uiState.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No results for \"${uiState.query}\"", color = JellioTextSecondary)
            }
            uiState.results.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.results, key = { it.Id }) { item ->
                    PosterCard(item = item, imageUrl = imageUrl, onClick = { onItemClick(item) })
                }
            }
        }
    }
}
