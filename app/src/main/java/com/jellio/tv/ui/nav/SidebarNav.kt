package com.jellio.tv.ui.nav

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
import com.jellio.tv.ui.theme.scaled

// Real port of Jellio-Plugin's own desktop css/app.css: .jellio-sidebar
// (--jellio-sidebar-width-collapsed: 5em, --jellio-sidebar-width: 15em,
// :hover/:focus-within expansion, position: fixed so widening it
// overlays real content instead of reflowing every card grid
// underneath). TopNavPill's own floating top pill is gone: this rail
// replaces it as this app's own only nav chrome, real desktop parity
// rather than the mobile pill shape this app shipped with first.
private val SidebarCollapsedWidth = 88.dp
private val SidebarExpandedWidth = 260.dp
private val SidebarItemHeight = 64.dp
private val SidebarIconSize = 30.dp
private val SidebarLabelMaxWidth = 160.dp

// Real MainActivity.kt's own contentPadding(start = ...) always
// reserves exactly this much real space, collapsed or not: the same
// real reason the web version's own .jellio-sidebar-mount only ever
// flexes a fixed collapsed basis, the rail's own real position: fixed
// self is what actually overlays past that on focus, not a reflow of
// whatever real space content itself was given.
val SidebarReservedWidth get() = SidebarCollapsedWidth

// Real port of components/sidebar.js/persistentSidebar.js: icon-only
// by default, real label text revealed once a reader's own D-pad
// focus (real desktop's own :focus-within, no pointer on a real TV to
// :hover with) actually lands somewhere inside this rail, collapsing
// straight back the moment it leaves, same real reasoning that CSS
// rule's own header gives.
//
// Always the real leftmost focusable thing on screen: this rail sits
// at the screen's own real x = 0 with real reserved layout space
// (never an overlay while collapsed, only while it already has real
// focus, see SidebarReservedWidth's own header), so Compose's own
// default spatial search finds it on a real Left press from any real
// screen content the same reliable way it already finds Down out of
// Search/Watchlist/Calendar/Settings, no explicit requestFocus()
// bridge to fight the same way that mechanism kept breaking for
// Home/Library's own Down navigation.
@Composable
fun SidebarNav(
    items: List<JellioRoute>,
    selected: JellioRoute,
    onSelect: (JellioRoute) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) SidebarExpandedWidth.scaled() else SidebarCollapsedWidth.scaled(),
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "sidebarWidth",
    )
    val shadowAlpha by animateFloatAsState(
        targetValue = if (expanded) 0.5f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "sidebarShadowAlpha",
    )

    // Real bug found live testing on device, on TopNavPill's own
    // predecessor: cold app start left focus nowhere at all, the very
    // first D-pad press falling through to Compose's own generic
    // "pick something" default instead of the already-selected entry.
    // Same real fix, ported: a plain LaunchedEffect(Unit) here fires
    // exactly once for this rail's own real lifetime (MainActivity's
    // own persistent Box, same as the pill it replaces), claiming
    // initial focus onto whichever entry is already selected.
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocusRequester.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            // Real port of css/app.css's own real box-shadow: 12px 0
            // 32px rgb(0 0 0/0.5), :hover/:focus-within only: the same
            // real visual separation from content this rail now
            // overlays once expanded, absent while collapsed and
            // sitting flush against real reserved layout space instead.
            .shadow(elevation = if (shadowAlpha > 0f) 24.dp else 0.dp, shape = RoundedCornerShape(0.dp))
            .background(JellioBgElevated)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(0.dp),
            )
            .padding(vertical = 20.dp, horizontal = 10.dp)
            // Real port of .jellio-sidebar's own :focus-within: Compose's
            // own FocusState.hasFocus already means exactly that (this
            // node or any real descendant), so a plain onFocusChanged
            // here is the whole real mechanism, nothing to hand-roll
            // per item.
            .onFocusChanged { state -> expanded = state.hasFocus },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { route ->
            SidebarItem(
                route = route,
                isSelected = route == selected,
                expanded = expanded,
                enabled = enabled,
                onClick = { onSelect(route) },
                focusRequester = if (route == selected) initialFocusRequester else null,
            )
        }
    }
}

// Mirrors css/app.css's own .jellio-sidebar-link: icon beside the
// label (not stacked, that file's own header explains the truncated-label
// bug a stacked layout forced), a fixed icon slot so the icon itself
// never drifts off-center between collapsed and expanded, the label
// fading in beside it rather than the row reflowing under it.
@Composable
private fun SidebarItem(
    route: JellioRoute,
    isSelected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val labelWidth by animateDpAsState(
        targetValue = if (expanded) SidebarLabelMaxWidth.scaled() else 0.dp,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "sidebarLabelWidth",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(if (expanded) 220 else 90, easing = FastOutSlowInEasing),
        label = "sidebarLabelAlpha",
    )
    Surface(
        selected = isSelected,
        onClick = onClick,
        enabled = enabled,
        shape = SelectableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        scale = SelectableSurfaceDefaults.scale(
            focusedScale = 1f,
            focusedSelectedScale = 1f,
            pressedScale = 0.96f,
            pressedSelectedScale = 0.96f,
        ),
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
        modifier = Modifier.fillMaxWidth().height(SidebarItemHeight.scaled()).let {
            if (focusRequester != null) it.focusRequester(focusRequester) else it
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.width(SidebarCollapsedWidth.scaled() - 20.dp.scaled()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = route.icon(),
                    contentDescription = if (!expanded) route.label() else null,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(SidebarIconSize.scaled()),
                )
            }
            Box(modifier = Modifier.width(labelWidth).clipToBounds()) {
                Text(
                    text = route.label(),
                    color = LocalContentColor.current,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(labelAlpha).padding(end = 16.dp),
                )
            }
        }
    }
}
