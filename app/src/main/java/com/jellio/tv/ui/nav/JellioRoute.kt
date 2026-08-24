package com.jellio.tv.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

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
//
// Detail/Player are real screens too but never appear in JellioNavItems
// below: reached only by pushing onto MainActivity's own real back
// stack (a card, a hero, Play), the same real distinction
// screens/detail.js's own #/item route and screens/player.js's own
// #/play route draw against the sidebar's fixed link set.
sealed interface JellioRoute {
    data object Profile : JellioRoute
    data object Home : JellioRoute
    data object Search : JellioRoute
    data object Watchlist : JellioRoute
    data object Calendar : JellioRoute
    data object Library : JellioRoute
    data object Settings : JellioRoute
    data class Detail(val itemId: String) : JellioRoute
    data class Person(val personId: String) : JellioRoute
    data class Service(val name: String) : JellioRoute
    data class Player(val itemId: String, val mediaSourceId: String?) : JellioRoute
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

fun JellioRoute.icon(): ImageVector = when (this) {
    JellioRoute.Profile -> Icons.Filled.AccountCircle
    JellioRoute.Home -> Icons.Filled.Home
    JellioRoute.Search -> Icons.Filled.Search
    JellioRoute.Watchlist -> Icons.Filled.BookmarkAdded
    JellioRoute.Calendar -> Icons.Filled.CalendarMonth
    // Real components/navShared.js's own SVG_ICONS.library: a generic
    // media-box shape, not a play-button-on-a-shelf the way Material's
    // own VideoLibrary reads, real feedback live called that one out
    // as a mismatch from the web build's own icon. PermMedia's own
    // folder-plus-media glyph is the closest real Material equivalent
    // to that generic real shape.
    JellioRoute.Library -> Icons.Filled.PermMedia
    JellioRoute.Settings -> Icons.Filled.Settings
    is JellioRoute.Detail, is JellioRoute.Person, is JellioRoute.Service, is JellioRoute.Player -> Icons.Filled.Home
}

fun JellioRoute.label(): String = when (this) {
    JellioRoute.Profile -> "Profile"
    JellioRoute.Home -> "Home"
    JellioRoute.Search -> "Search"
    JellioRoute.Watchlist -> "Watchlist"
    JellioRoute.Calendar -> "Calendar"
    JellioRoute.Library -> "Library"
    JellioRoute.Settings -> "Settings"
    is JellioRoute.Detail, is JellioRoute.Person, is JellioRoute.Service, is JellioRoute.Player -> ""
}

// Detail/Player are full screen immersive views (real backdrop art,
// real playback surface): the floating pill and its library picker
// stay reserved for the fixed tab set above them, same real
// distinction the web build draws between the sidebar/mobile nav and
// screens/detail.js's own hero, which renders no nav chrome of its own.
// Service carries the same real distinction, screens/service.js's own
// hero just as immersive as screens/detail.js's own.
fun JellioRoute.isImmersive(): Boolean =
    this is JellioRoute.Detail || this is JellioRoute.Person || this is JellioRoute.Service || this is JellioRoute.Player
