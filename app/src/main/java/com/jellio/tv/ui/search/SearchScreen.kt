package com.jellio.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.PosterCard
import com.jellio.tv.ui.home.rememberCardOptionsHost
import com.jellio.tv.ui.common.JellioTextField
import com.jellio.tv.ui.home.SkeletonPoster
import com.jellio.tv.ui.theme.JellioTextSecondary

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.compose.ui.graphics.Color
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText

// Mirrors screens/search.js's own real live-search shape: a query box
// over a results grid, Movie/Series only, the same real endpoint
// runtime/api.js's own searchItems() calls.
@Composable
fun SearchScreen(
    session: Session,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    LaunchedEffect(session.userId) { viewModel.loadPermissions(session) }
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onQueryChange(session, spoken)
            }
        }
    }
    val openItemOptions = rememberCardOptionsHost(
        canDeleteItems = uiState.canDeleteItems,
        onToggleWatchlist = { viewModel.toggleWatchlist(session, it) },
        onToggleWatched = { viewModel.toggleWatched(session, it) },
        onDeleteItem = { viewModel.deleteItem(it) },
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 32.dp, start = 48.dp, end = 48.dp),
    ) {
        Text(text = "Search", style = MaterialTheme.typography.titleLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        ) {
            JellioTextField(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChange(session, it) },
                label = "Search movies and shows",
                modifier = Modifier.width(480.dp),
            )
            Surface(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a title to search...")
                    }
                    speechLauncher.launch(intent)
                },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = JellioBgElevated,
                    focusedContainerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = JellioText,
                ),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice search",
                        tint = JellioText,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        when {
            // Results show as each search answers (SearchViewModel), with
            // placeholder cards after them while more are on the way, and
            // a grid of placeholders before the first ones arrive.
            uiState.results.isNotEmpty() || uiState.isSearching -> {
                val placeholders = when {
                    !uiState.isSearching -> 0
                    uiState.results.isEmpty() -> 12
                    else -> 4
                }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                ) {
                    items(uiState.results, key = { it.Id }, contentType = { "poster_card" }) { item ->
                        PosterCard(item = item, imageUrl = imageUrl, onClick = { onItemClick(item) }, onOptionsClick = { openItemOptions(item) })
                    }
                    items(placeholders, key = { "placeholder-$it" }, contentType = { "placeholder" }) {
                        SkeletonPoster()
                    }
                }
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "", color = JellioTextSecondary)
            }
            uiState.hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No results for \"${uiState.query}\"", color = JellioTextSecondary)
            }
        }
    }
}
