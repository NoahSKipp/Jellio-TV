package com.jellio.tv.ui.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.nav.rememberNavCompact
import com.jellio.tv.ui.theme.JellioTextSecondary

// Mirrors screens/home.js's own buildHomeSections(): a real hero over
// real rows, fetched from the real Jellio-Plugin backend rather than
// placeholder content.
@Composable
fun HomeScreen(
    session: Session,
    imageUrl: (BaseItemDto, String, Int) -> String,
    rawImageUrl: (String, String?, String, Int) -> String,
    serviceLogoUrl: (String) -> String,
    contentFocusRequester: FocusRequester,
    onItemClick: (BaseItemDto) -> Unit,
    onComingSoonClick: (String) -> Unit,
    onServiceClick: (String) -> Unit,
    onCompactChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val compact = rememberNavCompact(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)

    LaunchedEffect(session.userId) {
        viewModel.load(session)
    }
    LaunchedEffect(compact) { onCompactChange(compact) }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "Something went wrong", color = JellioTextSecondary)
            }
            uiState.sections.isEmpty() && uiState.leadingSections.isEmpty() && uiState.comingSoon.isEmpty() && uiState.studioHubs.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Nothing here yet.", color = JellioTextSecondary)
                }
            }
            // Explicit down escape from TopNavPill lands here
            // (TopNavPill's own focusProperties override), same real
            // gap real feedback caught live: plain Compose Foundation
            // LazyColumn/LazyRow, unlike tv-foundation's own
            // TvLazyColumn/TvLazyRow, advertise no default D-pad entry
            // point of their own for a system that has never yet
            // focused anything inside them.
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup()
                    .focusRequester(contentFocusRequester),
            ) {
                item { HeroSection(item = uiState.heroItem, imageUrl = imageUrl, onViewDetails = onItemClick) }
                items(uiState.leadingSections, key = { it.title }) { section ->
                    PosterRow(section = section, imageUrl = imageUrl, onItemClick = onItemClick)
                }
                item {
                    ComingSoonRow(entries = uiState.comingSoon, imageUrl = rawImageUrl, onItemClick = onComingSoonClick)
                }
                item {
                    StudioHubRow(services = uiState.studioHubs, logoUrl = serviceLogoUrl, onServiceClick = onServiceClick)
                }
                items(uiState.sections, key = { it.title }) { section ->
                    PosterRow(section = section, imageUrl = imageUrl, onItemClick = onItemClick)
                }
            }
        }
    }
}
