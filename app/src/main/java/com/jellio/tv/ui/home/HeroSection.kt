package com.jellio.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

private val HeroHeight = 460.dp
private const val ADVANCE_MS = 7000L

private fun metaLine(item: BaseItemDto): String {
    val parts = mutableListOf<String>()
    item.ProductionYear?.let { parts.add(it.toString()) }
    item.Genres?.take(2)?.let { parts.addAll(it) }
    return parts.joinToString(" · ")
}

// Mirrors screens/home.js's own hero: a real backdrop, title and
// overview for whatever leads the reader's own real content.
// Real components/heroCarousel.js's own real rotation, ported the
// same reduced way ui/library/LibraryCoverflow.kt already ports that
// file's own real carousel shape (auto-advance on the same real
// ADVANCE_MS interval, a real dot row beneath it, no separate
// crossfade layer since nothing else in this app configures Coil for
// one either): every real Movie/Series getHeroCandidates() returned,
// not just the first, real feedback asked for the same "rotates
// through several titles" reader-facing behaviour Continue Watching's
// own row leading title already implied but this hero never actually
// had. View Details only, no Play here: components/heroCarousel.js's
// own real feedback already settled that a hero's own Play skipped
// straight into playback with no chance to see anything about the
// title first, real Nuvio reference only ever offers View Details
// from its own hero either way.
@Composable
fun HeroSection(
    items: List<BaseItemDto>,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onViewDetails: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var index by remember(items) { mutableIntStateOf(0) }

    LaunchedEffect(items) {
        if (items.size < 2) return@LaunchedEffect
        while (true) {
            delay(ADVANCE_MS)
            index = (index + 1) % items.size
        }
    }

    val item = items[index]

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
            val meta = metaLine(item)
            if (meta.isNotEmpty()) {
                Text(text = meta, color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
            }
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
            if (items.size > 1) {
                Row(modifier = Modifier.padding(top = 20.dp)) {
                    items.forEachIndexed { i, _ ->
                        Surface(
                            onClick = { index = i },
                            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (i == index) JellioText else Color.White.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.padding(end = 8.dp).size(8.dp),
                        ) {}
                    }
                }
            }
        }
    }
}
