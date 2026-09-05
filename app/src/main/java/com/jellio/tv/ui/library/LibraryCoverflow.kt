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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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

// Real feedback live: on initial open (or on returning to this item's
// own real top via View Details/an arrow), a reader should see this
// stage, the library's own title+filter chips, AND its first content
// row all at once, not just this stage alone with the row cut off
// below the fold. This app's own real reference viewport (TvScale.kt's
// own header) is only ~540dp tall, so this stage's own previous real
// 340dp (plus the editorial/badge header above it) left no real room
// for anything else.
//
// Real feedback live, round two: shrinking SlideHeight alone while
// leaving SlideWidth fixed (560dp) stretched every slide's own real
// backdrop art from a normal ~2.2:1 crop out to a real 3.4:1 sliver,
// reading as "incredibly small" even though this stage's own real
// footprint had barely shrunk - a distorted crop, not actually a
// smaller one. Both dimensions scaled down together this round
// instead, same real ~2.2:1 shape a real backdrop crop already has.
// CoverflowStageHeight now matches SlideHeight exactly too (real
// feedback's own "View Details has a different height than the
// arrows" report): the previous 220dp stage around a shorter 165dp
// slide centered both of them in the same real box, but the arrows
// anchor to this stage's own full real height while View Details sits
// bottom-aligned inside the slide's own shorter box, so the two read
// as sitting at different real heights whenever that gap existed. No
// gap left to read differently now.
private val CoverflowStageHeight = 210.dp
private val SlideWidth = 460.dp
private val SlideHeight = 210.dp

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
@OptIn(ExperimentalComposeUiApi::class)
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
    // Real bug found live: shrinking this stage's own real vertical
    // gap to its own content below (LibraryScreen.kt's own header on
    // that exact round) made this rail's own filter fields spatially
    // closer to View Details than either arrow now sits, so Compose's
    // own default Left/Right search started landing there instead of
    // this stage's own real chevrons - and since that field lives
    // outside this composable's own NoOpBringIntoViewSpec suppression,
    // landing on it fired Compose's own default per-child bring-into-
    // view request for real, scrolling this list down and cutting off
    // whatever real editorial/badge text sits above this stage. Explicit
    // real focus targets on View Details below answer Left/Right
    // itself rather than leaving it to that same real distance
    // heuristic, closing both off at once.
    val leftArrowFocusRequester = remember { FocusRequester() }
    val rightArrowFocusRequester = remember { FocusRequester() }

    // Real bug found live, on a real screenshot: entering this screen
    // with a real Right press off the sidebar's own Library row left
    // Compose's own default spatial search to pick whichever real
    // focusable was the closest vertical match, and this stage's own
    // real arrows/View Details sit far above that row while
    // LibraryScreen.kt's own real filter fields sit much closer to it
    // - landing there instead, with the exact same real scroll-down
    // consequence this file's own header above already covers.
    // Claiming this stage's own real initial focus outright, the same
    // real fix SidebarNav.kt's own header already uses for its own
    // cold-start focus gap, means this stage answers every real entry
    // itself rather than leaving it to that same real distance
    // heuristic.
    LaunchedEffect(Unit) { leftArrowFocusRequester.requestFocus() }

    LaunchedEffect(items) {
        while (true) {
            delay(ADVANCE_MS)
            index = (index + 1) % items.size
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (editorial != null) {
            Column(modifier = Modifier.widthIn(max = 520.dp).padding(start = 48.dp, top = 16.dp, end = 24.dp, bottom = 8.dp)) {
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
                modifier = Modifier.padding(start = 48.dp, top = 16.dp, bottom = 8.dp),
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
                        leftArrowFocusRequester = leftArrowFocusRequester,
                        rightArrowFocusRequester = rightArrowFocusRequester,
                    )
                }
            }
            // Real HeroSection.kt's own header on this exact fix: real
            // feedback found these two chevrons missing here too, same
            // real D-pad equivalent of components/libraryCoverflow.js's
            // own mouse-click prevButton/nextButton, docked against
            // this stage's own edges rather than the hero's.
            //
            // Real bug found live, on a real screenshot: both chevrons
            // really were in this real tree the whole time, just
            // invisible, and worse, invisibly focusable. CoverflowSlide's
            // own real zIndex (5f, 6f for whichever slide is current)
            // outranks a plain Surface's own real default (0f)
            // regardless of composition order, so the current slide's
            // own real full-bleed image painted over both buttons
            // completely. A real zIndex higher than either of those
            // wins this back.
            //
            // Real bug found live, on a real screenshot ("Movies" vs
            // "Shows" side by side): centering these against this
            // stage's own full real height put them level with its own
            // middle, while View Details sits bottom-aligned inside
            // CoverflowSlide's own info column instead (20dp from this
            // same stage's own real bottom edge, since stage height now
            // matches slide height exactly) - "Movies" only happened to
            // read close enough not to notice, every other real library
            // did not. Bottom-aligned here instead, with a real padding
            // matching that same 20dp plus half this real 44dp circle,
            // so both land at the exact same real height regardless of
            // library.
            Surface(
                onClick = { index = (index - 1 + items.size) % items.size },
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.25f),
                    focusedContentColor = JellioText,
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 20.dp)
                    .size(44.dp)
                    .zIndex(10f)
                    .focusRequester(leftArrowFocusRequester),
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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 20.dp)
                    .size(44.dp)
                    .zIndex(10f)
                    .focusRequester(rightArrowFocusRequester),
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CoverflowSlide(
    item: BaseItemDto,
    offset: Int,
    slideWidthPx: Float,
    slideWidth: Dp,
    slideHeight: Dp,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onViewDetails: (BaseItemDto) -> Unit,
    leftArrowFocusRequester: FocusRequester,
    rightArrowFocusRequester: FocusRequester,
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
                    // Real header above on why this doesn't just leave
                    // Left/Right to Compose's own default spatial
                    // search: explicit real targets here always answer
                    // to this stage's own chevrons, regardless of
                    // whatever sits below this list's own item now.
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .focusProperties {
                            left = leftArrowFocusRequester
                            right = rightArrowFocusRequester
                        },
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
