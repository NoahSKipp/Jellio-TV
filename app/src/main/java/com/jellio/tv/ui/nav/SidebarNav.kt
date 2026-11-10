package com.jellio.tv.ui.nav

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.tv.material3.Border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled

private val SidebarCollapsedWidth = 88.dp
private val SidebarExpandedWidth = 260.dp
private val SidebarItemHeight = 64.dp
private val SidebarMinItemHeight = 44.dp
private val SidebarIconSize = 26.dp
private val SidebarLabelMaxWidth = 160.dp

val SidebarReservedWidth get() = SidebarCollapsedWidth

@Composable
fun SidebarNav(
    items: List<JellioRoute>,
    selected: JellioRoute,
    onSelect: (JellioRoute) -> Unit,
    enabled: Boolean = true,
    profileAvatarUrl: String? = null,
    profileName: String? = null,
    restoreFocusRequester: FocusRequester? = null,
    forceCollapsed: Boolean = false,
    // False while a panel is open over the screen, so Left from inside
    // it can't land on the rail behind it.
    focusable: Boolean = true,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val visuallyExpanded = expanded && !forceCollapsed
    val homeFocusRequester = remember { FocusRequester() }
    val entryRoute = if (items.contains(selected)) selected else JellioRoute.Home()

    val width by animateDpAsState(
        targetValue = if (visuallyExpanded) SidebarExpandedWidth.scaled() else SidebarCollapsedWidth.scaled(),
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "sidebarWidth",
    )
    val shadowAlpha by animateFloatAsState(
        targetValue = if (visuallyExpanded) 0.5f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "sidebarShadowAlpha",
    )

    LaunchedEffect(Unit) { restoreFocusRequester?.requestFocus() }

    // A closing panel hands focus back here before the rail is focusable
    // again; take it once it is.
    var wasBlocked by remember { mutableStateOf(!focusable) }
    LaunchedEffect(focusable) {
        if (focusable && wasBlocked) runCatching { restoreFocusRequester?.requestFocus() }
        wasBlocked = !focusable
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .shadow(elevation = if (shadowAlpha > 0f) 24.dp else 0.dp, shape = RoundedCornerShape(0.dp))
            // Solid: library heroes run under it. Same colour as every
            // screen's background, so it still reads as one surface.
            .background(JellioBg)
            .padding(vertical = 20.dp, horizontal = 10.dp)
            .onFocusChanged { state ->
                expanded = state.hasFocus
                onFocusChange(state.hasFocus)
            }
            .focusProperties {
                onEnter = {
                    if (focusable && enabled) {
                        if (requestedFocusDirection == FocusDirection.Left) {
                            (restoreFocusRequester ?: homeFocusRequester).requestFocus()
                        }
                    } else {
                        FocusRequester.Cancel
                    }
                }
            }
            .focusGroup(),
    ) {
        // Rows shrink so every entry fits the screen; past a readable
        // minimum the rail scrolls instead of hiding entries off screen.
        val gap = 4.dp
        val count = items.size.coerceAtLeast(1)
        val fit = (maxHeight - gap * (count - 1)) / count
        val itemHeight = minOf(SidebarItemHeight.scaled(), fit).coerceAtLeast(SidebarMinItemHeight)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        items.forEach { route ->
            val isSelfProfile = route is JellioRoute.Profile && route.userId == null
            val matchesSelected = (route == selected) || (route is JellioRoute.Home && selected is JellioRoute.Home)

            SidebarItem(
                icon = route.icon(),
                iconScale = route.iconScale(),
                label = if (isSelfProfile && !profileName.isNullOrBlank()) profileName else route.label(),
                avatarUrl = if (isSelfProfile) profileAvatarUrl else null,
                isSelected = matchesSelected,
                expanded = visuallyExpanded,
                enabled = enabled,
                onClick = { onSelect(route) },
                focusRequester = if (matchesSelected) restoreFocusRequester else null,
                homeFocusRequester = if (route == entryRoute) homeFocusRequester else null,
                height = itemHeight,
                focusable = focusable && enabled,
            )

        }
    }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    homeFocusRequester: FocusRequester? = null,
    iconScale: Float = 1f,
    avatarUrl: String? = null,
    height: androidx.compose.ui.unit.Dp = SidebarItemHeight,
    focusable: Boolean = true,
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
            focusedContainerColor = Color.White.copy(alpha = 0.25f),
            focusedContentColor = JellioText,
            selectedContainerColor = Color.White.copy(alpha = 0.12f),
            selectedContentColor = JellioText,
            focusedSelectedContainerColor = Color.White.copy(alpha = 0.25f),
            focusedSelectedContentColor = JellioText,
        ),
        border = SelectableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(14.dp),
            ),
            focusedSelectedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(14.dp),
            ),
        ),
        modifier = Modifier.focusProperties { canFocus = focusable && enabled }.fillMaxWidth().height(height).let {
            if (focusRequester != null) it.focusRequester(focusRequester) else it
        }.let {
            if (homeFocusRequester != null) it.focusRequester(homeFocusRequester) else it
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.width(SidebarCollapsedWidth.scaled() - 20.dp.scaled()),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = if (!expanded) label else null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(minOf(SidebarIconSize.scaled() * 1.35f, height - 8.dp)).clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (!expanded) label else null,
                        tint = LocalContentColor.current,
                        modifier = Modifier.size(SidebarIconSize.scaled() * iconScale),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(labelWidth)
                    .clipToBounds()
                    .alpha(labelAlpha)
                    .padding(end = 16.dp),
            ) {
                Text(
                    text = label,
                    color = LocalContentColor.current,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}
