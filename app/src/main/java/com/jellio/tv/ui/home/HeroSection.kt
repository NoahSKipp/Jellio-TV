package com.jellio.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.jellio.tv.ui.theme.scaled
import kotlinx.coroutines.delay

// Real feedback live, matching a real screenshot comparison against
// the web build's own hero: at the old 460.dp this app's own hero
// pushed "Still up, Noah?" and the Continue Watching row entirely
// below the fold on a real TV viewport, needing a real scroll just to
// confirm either even existed. Web's own real hero (css/hero-carousel.css)
// runs comfortably shorter than that already.
private val HeroHeight = 380.dp
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
    val heroHeight = HeroHeight.scaled()

    Box(modifier = modifier.fillMaxWidth().height(heroHeight)) {
        // Real port of css/hero-carousel.css's own real backdrop-layer
        // crossfade (heroCarousel.js's own crossfadeBackdrop(), 800ms):
        // a plain AsyncImage model swap here read as an instant hard
        // cut on every real rotation, this app's own most prominent
        // real screen. Crossfade keyed on the item's own Id gives the
        // outgoing/incoming backdrop the same real overlapping fade.
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
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(JellioBg.copy(alpha = 0.8f), Color.Transparent),
                        endX = 900f,
                    ),
                ),
        )
        // Real bug found live: title/meta/overview/button/dots used to
        // be one real bottom-aligned Column, so every rotation to a
        // different real hero item (a different real overview length,
        // sometimes no overview at all) reflowed the whole real stack -
        // View Details and the dots moved with it, landing at a
        // different real height every time, read live as "jitters up
        // and down". Split into two real independently bottom-aligned
        // columns instead: this one (title/meta/overview) is free to
        // grow upward with whatever real content a given item has, the
        // action column below (button+dots) stays pinned to a fixed
        // real bottom position regardless, the arrows' own real bottom
        // padding below already matches that fixed real spot.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 640.dp)
                .padding(start = 48.dp, bottom = 140.dp, end = 24.dp),
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
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, bottom = 56.dp),
        ) {
            Surface(
                onClick = { onViewDetails(item) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = JellioText,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Filled.Info, contentDescription = null)
                    Text(text = "View Details", modifier = Modifier.padding(start = 8.dp))
                }
            }
            // Real components/heroCarousel.js's own dots: purely a
            // position indicator here rather than a real second D-pad
            // target beside it, real feedback's own explicit ask. A
            // real Surface per dot (this used to be) is still
            // individually focusable regardless of any onClick at all,
            // a whole real row of tiny targets a D-pad has no reason to
            // stop at; a plain Box carries no such real focus node.
            if (items.size > 1) {
                Row(modifier = Modifier.padding(top = 20.dp)) {
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
        // Real components/heroCarousel.js's own prevButton/nextButton:
        // this app's own real D-pad equivalent of that file's own
        // mouse-click chevrons, that file's own CSS centers these
        // against the whole real backdrop's own height, fine for a
        // pointer since a mouse can just move to wherever it needs to.
        //
        // Real bug found live: centering against this box's own full
        // real heroHeight put these level with roughly its own real
        // vertical middle, while View Details sits inside this file's
        // own real bottom-aligned info column instead, well below that
        // middle once a title/meta/overview stack ahead of it - real
        // feedback found the two landing at visibly different real
        // heights. Bottom-aligned instead, with a real padding
        // estimated against that same column's own real title+meta+
        // overview+button stack (TvScale.kt's own scaled() still
        // applies on top): not pixel exact for every real title (a
        // short Overview leaves real slack below the button), but far
        // closer than a full real center ever was.
        if (items.size > 1) {
            Surface(
                onClick = { index = (index - 1 + items.size) % items.size },
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.25f),
                    focusedContentColor = JellioText,
                ),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 86.dp).size(44.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "Previous")
                }
            }
            Surface(
                onClick = { index = (index + 1) % items.size },
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.25f),
                    focusedContentColor = JellioText,
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 86.dp).size(44.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "Next")
                }
            }
        }
    }
}
