package com.jellio.tv.ui.nav

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

private val PillIconSize = 26.dp
private val PillItemMinWidth = 76.dp

// Explicit and fixed rather than left to wrap-content: real feedback
// live was a hugely oversized, oval-shaped pill, some interaction
// between Surface's own default focus scale/minimum touch target and
// this row's own intrinsic sizing that no amount of retuning the
// padding numbers alone fixed. Clamping the real outer bound directly
// is the one change guaranteed to hold regardless of which of those
// was actually responsible.
private val PillHeight = 88.dp

// Real css/app.css's own real comment on .jellio-mobile-nav-label:
// collapsing the label's own height is what actually reshapes the
// real pill, no separate rule on the pill itself needed there since
// each real link lays out icon over label in a column and the pill is
// only ever as tall as its own tallest child. This app's own pill
// height is a real fixed clamp instead (PillHeight's own header
// explains the real bug that forced that), so the compact height
// below is a second real fixed value animated to rather than left to
// reflow on its own.
private val PillHeightCompact = 68.dp
private val PillLabelHeight = 20.dp

// css/app.css's own real max-width: calc(100vw - 2 * safe gutter) on
// .jellio-mobile-nav, ported as a real fixed clamp: a TV screen is
// always wide enough that this is the binding constraint, not the
// viewport itself.
private val PillMaxWidth = 640.dp

// The same real floating pill css/app.css's own .jellio-mobile-nav
// defines for a phone (rounded, solid elevated background at 0.96
// alpha, a faint white border, no live backdrop blur so it never
// re-samples scrolling content underneath it every frame), anchored
// to the top of the screen instead of the bottom, and sized for a
// real 10-foot living room viewing distance rather than a phone held
// in hand: real feedback live was that the first pass read as native
// Jellyfin's own default, cramped TV chrome, default/small Compose
// component sizing exactly why.
//
// Real bug found live testing on device: Settings, the very last real
// item, rendered entirely off the right edge with nothing to scroll
// it into view, this Row never having had any width cap or scroll
// behaviour of its own at all. Real web's own .jellio-mobile-nav-scroll
// is a real horizontally scrolling strip for exactly this reason (a
// phone's own pill can run out of room just as easily with enough
// real libraries); ported the same way, a LazyRow rather than a
// scrollable Row so D-pad focus moving onto an off-screen item still
// brings it into view the way TV navigation actually needs.
@Composable
fun TopNavPill(
    items: List<JellioRoute>,
    selected: JellioRoute,
    onSelect: (JellioRoute) -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Real components/mobileNav.js's own scroll-driven compact state,
    // ported the same real 140/160ms pairing that file's own header
    // documents real feedback settling on (a longer duration read as
    // the whole pill feeling slow again).
    val pillHeight by animateDpAsState(targetValue = if (isCompact) PillHeightCompact else PillHeight, animationSpec = tween(160), label = "pillHeight")
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier
                .padding(top = 32.dp)
                .height(pillHeight)
                .widthIn(max = PillMaxWidth)
                .clip(RoundedCornerShape(999.dp))
                .background(JellioBgElevated.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(999.dp)),
        ) {
            lazyItems(items, key = { it::class.simpleName ?: it.toString() }) { route ->
                NavPillItem(
                    route = route,
                    isSelected = route == selected,
                    isCompact = isCompact,
                    onClick = { onSelect(route) },
                )
            }
        }
    }
}

// Mirrors css/app.css's own .jellio-mobile-nav-link exactly: icon over
// label in a column, not side by side, the real reason its own real
// selection/focus background reads as a proper rounded pill around
// both rather than a thin strip barely taller than the text, real
// feedback live's own complaint about the first pass here. min-width
// mirrors that file's own real 3.4em floor, so a short label (Home)
// gets the same real pill footprint a long one (Watchlist) does.
@Composable
private fun NavPillItem(route: JellioRoute, isSelected: Boolean, isCompact: Boolean, onClick: () -> Unit) {
    val labelHeight by animateDpAsState(targetValue = if (isCompact) 0.dp else PillLabelHeight, animationSpec = tween(160), label = "navLabelHeight")
    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = SelectableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        // Focus scale off: real feedback live was a hugely oversized
        // pill, and this is one of the few tv-material3 defaults that
        // grows a component past its own laid out bounds on focus.
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f, focusedSelectedScale = 1f),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = JellioTextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
            selectedContainerColor = Color.White.copy(alpha = 0.12f),
            selectedContentColor = JellioText,
            focusedSelectedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedSelectedContentColor = JellioText,
        ),
        modifier = Modifier.fillMaxHeight(),
    ) {
        // Real bug found live testing on device, and the first fix
        // attempted here (Column fillMaxSize instead of fillMaxHeight)
        // did not actually hold: a LazyRow measures each item with an
        // unbounded max width, and Compose's own fillMaxSize degrades
        // to a no-op under infinite constraints rather than forcing
        // any real size, so the Column stayed exactly as
        // intrinsically narrow as before. The real Surface above
        // wraps to whatever width this Column reports, so the actual
        // fix is putting the min-width floor on the Column itself
        // rather than on the Surface: this Column is now always at
        // least PillItemMinWidth wide regardless of tv-material3's
        // own Surface sizing behaviour, so horizontalAlignment always
        // has a real, guaranteed floor to center icon/label within,
        // and the Surface (which just wraps to match) stays in sync.
        Column(
            modifier = Modifier.fillMaxHeight().widthIn(min = PillItemMinWidth).padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = route.icon(),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(PillIconSize),
            )
            Box(modifier = Modifier.height(labelHeight).clipToBounds()) {
                Text(
                    text = route.label(),
                    color = LocalContentColor.current,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
