package com.jellio.tv.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioTextSecondary

// Mirrors screens/home.js's own buildHomeSections(): a real hero over
// real rows, fetched from the real Jellio-Plugin backend rather than
// placeholder content.
@Composable
fun HomeScreen(
    session: Session,
    imageUrl: (BaseItemDto, String, Int) -> String,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(session.userId) {
        viewModel.load(session)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "Something went wrong", color = JellioTextSecondary)
            }
            uiState.sections.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing here yet.", color = JellioTextSecondary)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { HeroSection(item = uiState.heroItem, imageUrl = imageUrl) }
                items(uiState.sections, key = { it.title }) { section ->
                    PosterRow(section = section, imageUrl = imageUrl)
                }
            }
        }
    }
}
