package com.jellio.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

private val HeroHeight = 460.dp

// Mirrors screens/home.js's own hero: a real backdrop, title and
// overview for whatever leads the reader's own real content (the
// first Continue Watching title, or the newest item on the first
// non-empty library row when nothing is in progress yet). No
// carousel/rotation yet, one real item until the rest of the row
// content around it exists to justify one. View Details only, no
// Play here: components/heroCarousel.js's own real feedback already
// settled that a hero's own Play skipped straight into playback with
// no chance to see anything about the title first, real Nuvio
// reference only ever offers View Details from its own hero either way.
@Composable
fun HeroSection(
    item: BaseItemDto?,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onViewDetails: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item == null) return

    Box(modifier = modifier.fillMaxWidth().height(HeroHeight)) {
        AsyncImage(
            model = imageUrl(item, "Backdrop", 1280),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(HeroHeight),
        )
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeroHeight)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(JellioBg.copy(alpha = 0.8f), Color.Transparent),
                        endX = 900f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 640.dp)
                .padding(start = 48.dp, bottom = 56.dp, end = 24.dp),
        ) {
            Text(text = item.Name ?: "", style = MaterialTheme.typography.titleLarge)
            item.Overview?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = JellioTextSecondary,
                    maxLines = 3,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Surface(
                onClick = { onViewDetails(item) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = JellioText,
                ),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = null)
                    Text(text = "View Details", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
