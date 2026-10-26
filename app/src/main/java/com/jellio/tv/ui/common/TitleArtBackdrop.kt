package com.jellio.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

// Draws the first candidate that loads; nothing when none do.
@Composable
fun TitleArtBackdrop(art: List<ArtCandidate>, modifier: Modifier = Modifier) {
    var index by remember(art) { mutableIntStateOf(0) }
    val current = art.getOrNull(index) ?: return
    Box(modifier = modifier) {
        AsyncImage(
            model = current.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { index++ },
            modifier = Modifier.fillMaxSize().then(if (current.blurred) Modifier.blur(24.dp) else Modifier),
        )
        if (current.blurred) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
    }
}
