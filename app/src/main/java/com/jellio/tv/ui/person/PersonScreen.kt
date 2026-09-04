package com.jellio.tv.ui.person

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

@Composable
fun PersonScreen(
    session: Session,
    personId: String,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // Same real immersive-screen gap DetailScreen's own header comment
    // documents: no SidebarNav mounts here for a D-pad press to search
    // Down from, so nothing ever requests focus into this screen
    // without this.
    val contentFocusRequester = remember { FocusRequester() }
    LaunchedEffect(personId) { viewModel.load(session, personId) }
    LaunchedEffect(uiState.person) {
        if (uiState.person != null) contentFocusRequester.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "Could not load this person", color = JellioTextSecondary)
            }
            uiState.person != null -> {
                val person = uiState.person!!
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 140.dp).focusRequester(contentFocusRequester).focusRestorer()) {
                    item {
                        Row(modifier = Modifier.padding(horizontal = 48.dp)) {
                            val tag = person.ImageTags?.get("Primary")
                            Box(
                                modifier = Modifier.width(220.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(JellioBgElevated),
                            ) {
                                if (tag != null) {
                                    AsyncImage(
                                        model = imageUrl(person.Id, tag, "Primary", 400),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(start = 32.dp).widthIn(max = 700.dp)) {
                                Text(text = person.Name.orEmpty(), style = MaterialTheme.typography.titleLarge, color = JellioText)
                                person.Overview?.let {
                                    Text(
                                        text = it,
                                        color = JellioTextSecondary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(top = 16.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (uiState.filmography.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 32.dp)) {
                                Text(
                                    text = "Filmography",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JellioText,
                                    modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
                                )
                                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(uiState.filmography, key = { it.Id }) { work -> FilmographyCard(work, imageUrl, onClick = { onItemClick(work.Id) }) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            onClick = onBack,
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
            modifier = Modifier.padding(top = 32.dp, start = 32.dp).size(56.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = JellioText)
            }
        }
    }
}

@Composable
private fun FilmographyCard(item: BaseItemDto, imageUrl: (String, String?, String, Int) -> String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
        modifier = Modifier.width(170.dp),
    ) {
        Column {
            val tag = item.ImageTags?.get("Primary")
            AsyncImage(
                model = imageUrl(item.Id, tag, "Primary", 400),
                contentDescription = item.Name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
            Text(
                text = item.Name.orEmpty(),
                color = JellioText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
