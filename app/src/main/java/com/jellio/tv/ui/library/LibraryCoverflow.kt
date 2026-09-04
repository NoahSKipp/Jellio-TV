package com.jellio.tv.ui.library

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Weekend
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.ShowsEditorial
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled
import kotlinx.coroutines.delay
import kotlin.math.abs

private val CoverflowStageHeight = 340.dp
private val SlideWidth = 560.dp
private val SlideHeight = 254.dp

// Below this a coverflow is not a coverflow (mirrors
// components/libraryCoverflow.js's own real MIN_SLIDES floor exactly):
// the caller checks this before ever mounting one, same real reasoning.
const val COVERFLOW_MIN_SLIDES = 3
private const val ADVANCE_MS = 7000L
private const val NEAR_TRANSLATE_FRACTION = 0.72f
private const val NEAR_SCALE = 0.88f
private val SLIDE_EASING = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

private fun metaLine(item: BaseItemDto): String {
    val parts = mutableListOf<String>()
    item.ProductionYear?.let { parts.add(it.toString()) }
    item.Genres?.take(2)?.let { parts.addAll(it) }
    return parts.joinToString(" · ")
}

private fun editorialIcon(icon: String): ImageVector = when (icon) {
    "wb_sunny" -> Icons.Filled.WbSunny
    "wb_cloudy" -> Icons.Filled.WbCloudy
    "weekend" -> Icons.Filled.Weekend
    "bedtime" -> Icons.Filled.Bedtime
    else -> Icons.Filled.Schedule
}

// Real port of components/libraryCoverflow.js's own offset calculation:
// the shortest signed wrap-around distance from the current index, so
// the slide right before index 0 reads as offset -1 rather than
// items.size - 1.
private fun shortestOffset(slideIndex: Int, currentIndex: Int, count: Int): Int {
    val raw = slideIndex - currentIndex
    val half = count / 2
    return when {
        raw > half -> raw - count
        raw < -half -> raw + count
        else -> raw
    }
}

// Real port of components/libraryCoverflow.js's own three-wide
// overlapping stage: offset-driven translateX/scale/opacity on up to
// three slides at once (current plus its immediate neighbours) rather
// than the single full-bleed slide the previous reduced port drew.
// Compose TV has no swipe gesture to drive it, so the stage still
// auto-advances on the same real ADVANCE_MS interval and the dot row
// still jumps directly to an index, same as the reduced port kept.
@Composable
fun LibraryCoverflow(
    items: List<BaseItemDto>,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onViewDetails: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    editorial: ShowsEditorial? = null,
) {
    if (items.size < COVERFLOW_MIN_SLIDES) return
    var index by remember(items) { mutableIntStateOf(0) }

    LaunchedEffect(items) {
        while (true) {
            delay(ADVANCE_MS)
            index = (index + 1) % items.size
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (editorial != null) {
            Column(modifier = Modifier.widthIn(max = 520.dp).padding(start = 48.dp, top = 32.dp, end = 24.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = editorialIcon(editorial.icon), contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(18.dp))
                    Text(text = editorial.label, color = JellioTextSecondary, modifier = Modifier.padding(start = 6.dp))
                }
                Text(text = editorial.tagline, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
                Text(text = editorial.description, color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
            }
        } else if (badgeText != null) {
            // Checked against NuvioWeb's own css/base.css: no accent
            // hue exists there at all, this badge's own icon tinting
            // orange was invented on this app's own side with no real
            // source on either end. JellioSecondary is the one real
            // bright tone that palette actually has.
            Row(
                modifier = Modifier.padding(start = 48.dp, top = 32.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = Icons.Filled.TrendingUp, contentDescription = null, tint = JellioSecondary, modifier = Modifier.size(18.dp))
                Text(text = badgeText, color = JellioText, modifier = Modifier.padding(start = 6.dp))
            }
        }

        val slideWidth = SlideWidth.scaled()
        val slideHeight = SlideHeight.scaled()
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(CoverflowStageHeight.scaled()), contentAlignment = Alignment.Center) {
            val slideWidthPx = with(LocalDensity.current) { slideWidth.toPx() }
            items.forEachIndexed { i, item ->
                val offset = shortestOffset(i, index, items.size)
                if (abs(offset) <= 1) {
                    CoverflowSlide(
                        item = item,
                        offset = offset,
                        slideWidthPx = slideWidthPx,
                        slideWidth = slideWidth,
                        slideHeight = slideHeight,
                        imageUrl = imageUrl,
                        onViewDetails = onViewDetails,
                    )
                }
            }
            // Real HeroSection.kt's own header on this exact fix: real
            // feedback found these two chevrons missing here too, same
            // real D-pad equivalent of components/libraryCoverflow.js's
            // own mouse-click prevButton/nextButton, docked against
            // this stage's own edges rather than the hero's.
            Surface(
                onClick = { index = (index - 1 + items.size) % items.size },
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.25f),
                    focusedContentColor = JellioText,
                ),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).size(44.dp),
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
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(44.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "Next")
                }
            }
        }

        // Real feedback: purely a position indicator here, same real
        // reason HeroSection.kt's own dot row is no longer a real
        // Surface either. A real Surface per dot (even with its own
        // onClick removed) is still individually focusable on its own,
        // a whole real row of tiny D-pad targets with nothing useful to
        // stop at; a plain Box carries no such real focus node at all.
        Row(modifier = Modifier.padding(start = 48.dp, top = 20.dp)) {
            items.forEachIndexed { i, _ ->
                val active = i == index
                val dotWidth by animateDpAsState(targetValue = if (active) 30.dp else 8.dp, animationSpec = tween(320), label = "dotWidth")
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(dotWidth)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) JellioText else Color.White.copy(alpha = 0.3f)),
                )
            }
        }
    }
}

@Composable
private fun CoverflowSlide(
    item: BaseItemDto,
    offset: Int,
    slideWidthPx: Float,
    slideWidth: Dp,
    slideHeight: Dp,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onViewDetails: (BaseItemDto) -> Unit,
) {
    val isCurrent = offset == 0
    val translateX by animateFloatAsState(
        targetValue = offset * NEAR_TRANSLATE_FRACTION * slideWidthPx,
        animationSpec = tween(520, easing = SLIDE_EASING),
        label = "slideTranslate",
    )
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1f else NEAR_SCALE,
        animationSpec = tween(520, easing = SLIDE_EASING),
        label = "slideScale",
    )
    val infoAlpha by animateFloatAsState(
        targetValue = if (isCurrent) 1f else 0f,
        animationSpec = tween(320),
        label = "slideInfoAlpha",
    )

    Box(
        modifier = Modifier
            .width(slideWidth)
            .height(slideHeight)
            .graphicsLayer {
                translationX = translateX
                scaleX = scale
                scaleY = scale
            }
            .zIndex(if (isCurrent) 6f else 5f)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        AsyncImage(
            model = imageUrl(item, "Backdrop", 960),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                if (isCurrent) {
                    Brush.horizontalGradient(colors = listOf(JellioBg.copy(alpha = 0.85f), Color.Transparent))
                } else {
                    Brush.verticalGradient(colors = listOf(Color.Transparent, JellioBg.copy(alpha = 0.68f)))
                },
            ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .graphicsLayer { alpha = infoAlpha }
                .widthIn(max = 420.dp)
                .padding(start = 24.dp, bottom = 20.dp, end = 16.dp),
        ) {
            Text(text = item.Name ?: "", style = MaterialTheme.typography.titleMedium)
            val meta = metaLine(item)
            if (meta.isNotEmpty()) {
                Text(text = meta, color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            // Only the current slide's own button is ever mounted: the
            // neighbours already sit at infoAlpha 0, but a Surface stays
            // focusable even at zero alpha, so D-pad focus could still
            // land on an invisible button off to either side.
            if (isCurrent) {
                Surface(
                    onClick = { onViewDetails(item) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = JellioText,
                    ),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = "View Details", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
