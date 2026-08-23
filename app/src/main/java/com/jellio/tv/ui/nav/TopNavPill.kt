package com.jellio.tv.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Mirrors components/mobileNav.js's own real link set and its own
// consolidated Library button (components/libraryPicker.js's own
// popover behind it), not components/sidebar.js's desktop rail: this
// pill is a floating bar the same shape as the phone's own, not a
// tall scrollable rail with room for one entry per real library.
// Library carries no per-library identity here on purpose, same
// reason mobileNav.js's own buildLibraryButton() stores its real
// hashes as a group rather than one hash per button: which library is
// currently open is real screen state (MainActivity's own
// selectedLibrary), not nav identity.
sealed interface JellioRoute {
    data object Profile : JellioRoute
    data object Home : JellioRoute
    data object Search : JellioRoute
    data object Watchlist : JellioRoute
    data object Calendar : JellioRoute
    data object Library : JellioRoute
    data object Settings : JellioRoute
}

val JellioNavItems: List<JellioRoute> = listOf(
    JellioRoute.Profile,
    JellioRoute.Home,
    JellioRoute.Search,
    JellioRoute.Watchlist,
    JellioRoute.Calendar,
    JellioRoute.Library,
    JellioRoute.Settings,
)

private fun JellioRoute.icon(): ImageVector = when (this) {
    JellioRoute.Profile -> Icons.Filled.AccountCircle
    JellioRoute.Home -> Icons.Filled.Home
    JellioRoute.Search -> Icons.Filled.Search
    JellioRoute.Watchlist -> Icons.Filled.BookmarkAdded
    JellioRoute.Calendar -> Icons.Filled.CalendarMonth
    JellioRoute.Library -> Icons.Filled.VideoLibrary
    JellioRoute.Settings -> Icons.Filled.Settings
}

private fun JellioRoute.label(): String = when (this) {
    JellioRoute.Profile -> "Profile"
    JellioRoute.Home -> "Home"
    JellioRoute.Search -> "Search"
    JellioRoute.Watchlist -> "Watchlist"
    JellioRoute.Calendar -> "Calendar"
    JellioRoute.Library -> "Library"
    JellioRoute.Settings -> "Settings"
}

private val PillIconSize = 30.dp

// The same real floating pill css/app.css's own .jellio-mobile-nav
// defines for a phone (rounded, solid elevated background at 0.96
// alpha, a faint white border, no live backdrop blur so it never
// re-samples scrolling content underneath it every frame), anchored
// to the top of the screen instead of the bottom, and sized for a
// real 10-foot living room viewing distance rather than a phone held
// in hand: real feedback live was that the first pass read as native
// Jellyfin's own default, cramped TV chrome, default/small Compose
// component sizing exactly why.
@Composable
fun TopNavPill(
    items: List<JellioRoute>,
    selected: JellioRoute,
    onSelect: (JellioRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Row(
            modifier = Modifier
                .padding(top = 32.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(JellioBgElevated.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            items.forEach { route ->
                NavPillItem(
                    route = route,
                    isSelected = route == selected,
                    onClick = { onSelect(route) },
                )
            }
        }
    }
}

@Composable
private fun NavPillItem(route: JellioRoute, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = SelectableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
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
        modifier = Modifier.padding(horizontal = 5.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = route.icon(),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(PillIconSize),
            )
            Text(
                text = route.label(),
                color = LocalContentColor.current,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
