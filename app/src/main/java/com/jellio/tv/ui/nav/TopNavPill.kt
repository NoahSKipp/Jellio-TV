package com.jellio.tv.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

enum class JellioRoute(val icon: ImageVector, val label: String) {
    Home(Icons.Filled.Home, "Home"),
    Search(Icons.Filled.Search, "Search"),
    Watchlist(Icons.Filled.BookmarkAdded, "Watchlist"),
    Calendar(Icons.Filled.CalendarMonth, "Calendar"),
    Settings(Icons.Filled.Settings, "Settings"),
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
            JellioRoute.entries.forEach { route ->
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
                imageVector = route.icon,
                contentDescription = null,
                tint = LocalContentColor.current,
            )
            Text(
                text = route.label,
                color = LocalContentColor.current,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}
