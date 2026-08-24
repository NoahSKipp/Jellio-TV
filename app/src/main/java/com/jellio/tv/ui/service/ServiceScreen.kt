package com.jellio.tv.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.home.PosterRow
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

private val HeroHeight = 360.dp

// The page a Studio Hub tile opens: real screens/service.js's own
// "Popular on {service}" header (no Play/View Details of its own,
// unlike HeroSection, this hero fronts a whole service rather than
// one real item) over one row per real matched collection.
@Composable
fun ServiceScreen(
    session: Session,
    serviceName: String,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServiceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(serviceName) { viewModel.load(session, serviceName) }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "Could not load $serviceName", color = JellioTextSecondary)
            }
            uiState.sections.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Nothing imported for $serviceName yet.", color = JellioTextSecondary)
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { ServiceHero(name = uiState.serviceName, heroItem = uiState.heroItem, imageUrl = imageUrl) }
                items(uiState.sections, key = { it.title }) { section ->
                    PosterRow(section = section, imageUrl = imageUrl, onItemClick = onItemClick)
                }
            }
        }
    }
}

@Composable
private fun ServiceHero(
    name: String,
    heroItem: BaseItemDto?,
    imageUrl: (BaseItemDto, String, Int) -> String,
) {
    Box(modifier = Modifier.fillMaxWidth().height(HeroHeight)) {
        if (heroItem != null) {
            AsyncImage(
                model = imageUrl(heroItem, "Backdrop", 1280),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(HeroHeight),
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(HeroHeight).background(JellioBg))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, JellioBg),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 32.dp),
        ) {
            Text(text = "Popular on", color = JellioTextSecondary, style = MaterialTheme.typography.bodyLarge)
            Text(text = name, color = JellioText, style = MaterialTheme.typography.titleLarge)
        }
    }
}
