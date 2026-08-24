package com.jellio.tv.ui.library

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

private val CoverflowHeight = 460.dp

// Below this a coverflow is not a coverflow (mirrors
// components/libraryCoverflow.js's own real MIN_SLIDES floor exactly):
// the caller checks this before ever mounting one, same real reasoning.
const val COVERFLOW_MIN_SLIDES = 3
private const val ADVANCE_MS = 7000L

private fun metaLine(item: BaseItemDto): String {
    val parts = mutableListOf<String>()
    item.ProductionYear?.let { parts.add(it.toString()) }
    item.Genres?.take(2)?.let { parts.addAll(it) }
    return parts.joinToString(" · ")
}

// A reduced real port of components/libraryCoverflow.js: this app's
// own D-pad has no hover to pause on and Compose TV draws one slide
// at a time rather than that file's own three-wide overlapping
// stage, so this keeps its real behaviour instead of its exact
// choreography, same real full-bleed backdrop/gradient shape
// HeroSection already uses, auto-advancing on the same real
// ADVANCE_MS interval with the same real dot row beneath it.
@Composable
fun LibraryCoverflow(
    items: List<BaseItemDto>,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onViewDetails: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.size < COVERFLOW_MIN_SLIDES) return
    var index by remember(items) { mutableIntStateOf(0) }

    LaunchedEffect(items) {
        while (true) {
            delay(ADVANCE_MS)
            index = (index + 1) % items.size
        }
    }

    val item = items[index]

    Box(modifier = modifier.fillMaxWidth().height(CoverflowHeight)) {
        AsyncImage(
            model = imageUrl(item, "Backdrop", 1280),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(CoverflowHeight),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CoverflowHeight)
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
                .height(CoverflowHeight)
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
                .padding(start = 48.dp, bottom = 40.dp, end = 24.dp),
        ) {
            Text(text = item.Name ?: "", style = MaterialTheme.typography.titleLarge)
            val meta = metaLine(item)
            if (meta.isNotEmpty()) {
                Text(text = meta, color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
            }
            Surface(
                onClick = { onViewDetails(item) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = JellioText,
                ),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = null)
                    Text(text = "View Details", modifier = Modifier.padding(start = 8.dp))
                }
            }
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
