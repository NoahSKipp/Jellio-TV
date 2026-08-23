package com.jellio.tv.ui.watchlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.jellio.tv.ui.theme.JellioTextSecondary

// Mirrors the real Watchlist split into Movies/Series sections, same
// real content Jellyfin's own favorites already back.
@Composable
fun WatchlistScreen(
    session: Session,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(session.userId) { viewModel.load(session) }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.movies.isEmpty() && uiState.series.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing on your Watchlist yet.", color = JellioTextSecondary)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 140.dp)) {
                item {
                    Text(
                        text = "Watchlist",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
                    )
                }
                if (uiState.movies.isNotEmpty()) {
                    item { PosterRow(section = HomeSection("Movies", uiState.movies), imageUrl = imageUrl, onItemClick = onItemClick) }
                }
                if (uiState.series.isNotEmpty()) {
                    item { PosterRow(section = HomeSection("Series", uiState.series), imageUrl = imageUrl, onItemClick = onItemClick) }
                }
            }
        }
    }
}
