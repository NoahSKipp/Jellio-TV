package com.jellio.tv.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.jellio.tv.ui.home.HomeSection
import com.jellio.tv.ui.home.PosterRow
import com.jellio.tv.ui.home.rememberCardOptionsHost
import com.jellio.tv.ui.nav.rememberNavCompact
import com.jellio.tv.ui.theme.JellioTextSecondary

// Mirrors the real Watchlist split into Movies/Series sections, same
// real content Jellyfin's own favorites already back.
@Composable
fun WatchlistScreen(
    session: Session,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    onCompactChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val compact = rememberNavCompact(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)

    LaunchedEffect(session.userId) { viewModel.load(session) }
    LaunchedEffect(compact) { onCompactChange(compact) }
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
            uiState.movies.isEmpty() && uiState.series.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing on your Watchlist yet.", color = JellioTextSecondary)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 140.dp),
            ) {
                item {
                    Text(
                        text = "Watchlist",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
                    )
                }
                if (uiState.movies.isNotEmpty()) {
                    item { PosterRow(section = HomeSection("Movies", uiState.movies), imageUrl = imageUrl, onItemClick = onItemClick, onItemOptions = openItemOptions) }
                }
                if (uiState.series.isNotEmpty()) {
                    item { PosterRow(section = HomeSection("Series", uiState.series), imageUrl = imageUrl, onItemClick = onItemClick, onItemOptions = openItemOptions) }
                }
            }
        }
    }
}
