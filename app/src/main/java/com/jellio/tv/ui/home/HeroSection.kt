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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
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

// Ported from Jellio Web's components/heroCarousel.js:
// Full action row featuring Play (direct playback), Watchlist (favorite toggle),
// and View Details, alongside tech badges and smooth TV D-pad navigation.
@OptIn(ExperimentalComposeUiApi::class)
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

    val leftArrowFocusRequester = remember { FocusRequester() }
    val rightArrowFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val watchlistFocusRequester = remember { FocusRequester() }
    val viewDetailsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(items) {
        if (items.size < 2) return@LaunchedEffect
        while (true) {
            delay(ADVANCE_MS)
            index = (index + 1) % items.size
        }
    }

    val item = items[index]
    val heroHeight = HeroHeight.scaled()

    Box(modifier = modifier.fillMaxWidth().height(heroHeight)) {
        Crossfade(
            targetState = item,
            animationSpec = tween(800),
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
                .fillMaxWidth()
                .height(heroHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, JellioBg),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
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
                .padding(start = 48.dp, bottom = 135.dp, end = 24.dp),
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
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, bottom = 52.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Play Button
                Surface(
                    onClick = { onPlay(item) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = JellioText,
                        contentColor = JellioBg,
                        focusedContainerColor = Color.White,
                        focusedContentColor = JellioBg,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.5.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        ),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier
                        .focusRequester(playFocusRequester)
                        .focusProperties {
                            left = leftArrowFocusRequester
                            right = watchlistFocusRequester
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                        Text(text = "Play", modifier = Modifier.padding(start = 8.dp))
                    }
                }

                // Watchlist Button
                val isWatchlisted = item.UserData?.IsFavorite == true
                Surface(
                    onClick = { onToggleWatchlist(item) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isWatchlisted) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.12f),
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.35f),
                        focusedContentColor = Color.White,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.5.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        ),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier
                        .focusRequester(watchlistFocusRequester)
                        .focusProperties {
                            left = playFocusRequester
                            right = viewDetailsFocusRequester
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isWatchlisted) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                        )
                        Text(
                            text = if (isWatchlisted) "Saved" else "Watchlist",
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                // View Details Button
                Surface(
                    onClick = { onViewDetails(item) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.35f),
                        focusedContentColor = Color.White,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.5.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        ),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                    modifier = Modifier
                        .focusRequester(viewDetailsFocusRequester)
                        .focusProperties {
                            left = watchlistFocusRequester
                            right = rightArrowFocusRequester
                        },
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

            if (items.size > 1) {
                Row(modifier = Modifier.padding(top = 16.dp)) {
                    items.forEachIndexed { i, _ ->
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (i == index) JellioText else Color.White.copy(alpha = 0.3f)),
                        )
                    }
                }
            }
        }

        if (items.size > 1) {
            Surface(
                onClick = { index = (index - 1 + items.size) % items.size },
                shape = ClickableSurfaceDefaults.shape(shape = androidx.compose.ui.graphics.RectangleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.3f),
                    focusedContentColor = JellioText,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, Color.White),
                        shape = androidx.compose.ui.graphics.RectangleShape,
                    ),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(32.dp)
                    .focusRequester(leftArrowFocusRequester)
                    .focusProperties { right = playFocusRequester },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "Previous")
                }
            }
            Surface(
                onClick = { index = (index + 1) % items.size },
                shape = ClickableSurfaceDefaults.shape(shape = androidx.compose.ui.graphics.RectangleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.3f),
                    focusedContentColor = JellioText,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, Color.White),
                        shape = androidx.compose.ui.graphics.RectangleShape,
                    ),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(32.dp)
                    .focusRequester(rightArrowFocusRequester)
                    .focusProperties { left = viewDetailsFocusRequester },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "Next")
                }
            }
        }
    }
}
