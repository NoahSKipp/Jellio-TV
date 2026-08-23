package com.jellio.tv.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
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

// Mirrors components/navShared.js's own real getPrimaryNavLinks()
// order: Profile leads, Settings trails, everything the reader's own
// real server actually has (libraries) sits between the fixed links.
// Library carries real id/name/collectionType rather than an index,
// so equality (used for the selected highlight) tracks the real
// library, not just a position that could point at a different one
// after a library list refresh.
sealed interface JellioRoute {
    data object Profile : JellioRoute
    data object Home : JellioRoute
    data object Search : JellioRoute
    data object Watchlist : JellioRoute
    data object Calendar : JellioRoute
    data class Library(val id: String, val name: String, val collectionType: String?) : JellioRoute
    data object Settings : JellioRoute
}

private fun JellioRoute.icon(): ImageVector = when (this) {
    JellioRoute.Profile -> Icons.Filled.AccountCircle
    JellioRoute.Home -> Icons.Filled.Home
    JellioRoute.Search -> Icons.Filled.Search
    JellioRoute.Watchlist -> Icons.Filled.BookmarkAdded
    JellioRoute.Calendar -> Icons.Filled.CalendarMonth
    is JellioRoute.Library -> when (collectionType) {
        "movies" -> Icons.Filled.Movie
        "tvshows" -> Icons.Filled.Tv
        else -> Icons.Filled.VideoLibrary
    }
    JellioRoute.Settings -> Icons.Filled.Settings
}

private fun JellioRoute.label(): String = when (this) {
    JellioRoute.Profile -> "Profile"
    JellioRoute.Home -> "Home"
    JellioRoute.Search -> "Search"
    JellioRoute.Watchlist -> "Watchlist"
    JellioRoute.Calendar -> "Calendar"
    is JellioRoute.Library -> name
    JellioRoute.Settings -> "Settings"
}

// The same real floating pill css/app.css's own .jellio-mobile-nav
// defines for a phone (rounded, solid elevated background at 0.96
// alpha, a faint white border, no live backdrop blur so it never
// re-samples scrolling content underneath it every frame), anchored
// to the top of the screen instead of the bottom: a D-pad's own "up"
// out of any row lands here on a TV the same way it lands on a phone
// pulling the pill up from below, and a top bar is where every real
// TV app (this app's own row content included) already expects its
// primary nav to live.
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
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(JellioBgElevated.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = route.icon(),
                contentDescription = null,
                tint = LocalContentColor.current,
            )
            Text(
                text = route.label(),
                color = LocalContentColor.current,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
