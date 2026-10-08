package com.jellio.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.common.MediaTechBadgesRow
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled
import kotlinx.coroutines.delay

// Real feedback live, matching a real screenshot comparison against
// the web build's own hero: at the old 460.dp this app's own hero
// pushed "Still up, Noah?" and the Continue Watching row entirely
// below the fold on a real TV viewport, needing a real scroll just to
// confirm either even existed. Web's own real hero (css/hero-carousel.css)
// runs comfortably shorter than that already.
private val HeroHeight = 400.dp
private const val ADVANCE_MS = 7000L

private fun metaLine(item: BaseItemDto): String {
    val parts = mutableListOf<String>()
    item.ProductionYear?.let { parts.add(it.toString()) }
    item.Genres?.take(2)?.let { parts.addAll(it) }
    return parts.joinToString(" · ")
}

// The hero is one selectable card: Left/Right step through its titles
// (and keep focus here, so they never fall through to the nav rail), OK
// opens the one showing. Title, year, genres, badges and overview stay;
// the action buttons and edge arrows are gone.
@Composable
fun HeroSection(
    items: List<BaseItemDto>,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onPlay: (BaseItemDto) -> Unit,
    onToggleWatchlist: (BaseItemDto) -> Unit,
    onViewDetails: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var index by remember(items) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }

    // Advances on its own, restarting the wait after every manual step.
    LaunchedEffect(items, index) {
        if (items.size < 2) return@LaunchedEffect
        delay(ADVANCE_MS)
        index = (index + 1) % items.size
    }

    val item = items[index.coerceIn(0, items.lastIndex)]
    val heroHeight = HeroHeight.scaled()
    val shape = RoundedCornerShape(0.dp)

    Surface(
        onClick = { onViewDetails(item) },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = JellioText,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, Color.White.copy(alpha = 0.85f)), inset = 0.dp, shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val step = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown && items.size > 1) {
                    index = (index + step + items.size) % items.size
                }
                true
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = item,
                animationSpec = tween(600),
                label = "heroBackdropCrossfade",
            ) { crossfadeItem ->
                AsyncImage(
                    model = imageUrl(crossfadeItem, "Backdrop", 1280),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(heroHeight),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, JellioBg))),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(JellioBg.copy(alpha = 0.85f), Color.Transparent),
                            endX = 900f,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = 680.dp)
                    .padding(start = 48.dp, bottom = 72.dp, end = 24.dp),
            ) {
                Text(text = item.Name ?: "", style = MaterialTheme.typography.titleLarge)
                val meta = metaLine(item)
                if (meta.isNotEmpty()) {
                    Text(text = meta, color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
                }
                MediaTechBadgesRow(item = item, modifier = Modifier.padding(top = 8.dp))
                item.Overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = JellioTextSecondary,
                        maxLines = 3,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (focused) {
                    Text(
                        text = if (items.size > 1) "◀ ▶ browse · OK for details" else "OK for details",
                        style = MaterialTheme.typography.labelSmall,
                        color = JellioTextSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            // Which of the titles is showing.
            if (items.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 44.dp),
                ) {
                    items.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(if (i == index) 22.dp else 8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i == index) JellioText else JellioText.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
    }
}
