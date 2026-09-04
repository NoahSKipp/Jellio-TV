package com.jellio.tv.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Home
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
    // userId null means the signed in reader's own profile
    // (switchTab() below always uses that default); Feed rows push a
    // real other user's own id instead (Workstream 2's own real
    // #/profile?id=X port), same real reason Detail/Person/Player
    // below already carry their own real identity rather than reading
    // it back out of some other piece of screen state.
    data class Profile(val userId: String? = null) : JellioRoute
    data object Home : JellioRoute
    data object Search : JellioRoute
    data object Watchlist : JellioRoute
    data object Calendar : JellioRoute
    data object Library : JellioRoute
    data object Feed : JellioRoute
    data object Settings : JellioRoute
    data class Detail(val itemId: String) : JellioRoute
    data class Person(val personId: String) : JellioRoute
    data class Service(val name: String) : JellioRoute
    data class Player(val itemId: String, val mediaSourceId: String?) : JellioRoute
}

// Real css/persistent-sidebar.css's own mobile breakpoint (its own
// comment: "Real screenshot order: Home, Search, Libraries, Profile,
// matching Nuvio's own four-item mobile nav exactly") sets that exact
// order via CSS `order`, not DOM order, on those four survivors of the
// pill's own real Settings/Favorites/back/bottom-group cull. This
// pill has no cull of its own (Watchlist/Calendar/Settings all stay
// reachable, no other affordance reaches them the way the phone's own
// profile button and home tabs do), so those three sit appended after
// the same shared four, in that same shared order.
// Real components/sidebar.js's own real order: Profile leads the rail
// (real feedback's own explicit ask, matching web pixel for pixel),
// then Home/Search/Watchlist/Feed/Calendar exactly as that file's own
// FIXED_NAV_LINKS + Calendar append it, Library folded in right after
// (the one real library link a reader's own libraries would occupy
// there on web, consolidated to this app's own single picker button
// instead, JellioRoute's own header already explains why), Settings
// still last.
val JellioNavItems: List<JellioRoute> = listOf(
    JellioRoute.Profile(),
    JellioRoute.Home,
    JellioRoute.Search,
    JellioRoute.Watchlist,
    JellioRoute.Feed,
    JellioRoute.Calendar,
    JellioRoute.Library,
    JellioRoute.Settings,
)

fun JellioRoute.icon(): ImageVector = when (this) {
    is JellioRoute.Profile -> Icons.Filled.AccountCircle
    JellioRoute.Home -> Icons.Filled.Home
    // js/persistentSidebar.js's own SVG_ICONS.search, ported path data
    // the same real way Library's own icon already is: the Material
    // glyph was close in silhouette but not the real shape drawn here.
    JellioRoute.Search -> SearchIconVector
    JellioRoute.Watchlist -> Icons.Filled.BookmarkAdded
    JellioRoute.Calendar -> Icons.Filled.CalendarMonth
    // js/persistentSidebar.js's own SVG_ICONS.library, ported path
    // data rather than a Material approximation: real feedback live
    // called out VideoLibrary and then PermMedia in turn as visible
    // mismatches against this exact icon.
    JellioRoute.Library -> LibraryIconVector
    JellioRoute.Feed -> Icons.Filled.DynamicFeed
    JellioRoute.Settings -> Icons.Filled.Settings
    is JellioRoute.Detail, is JellioRoute.Person, is JellioRoute.Service, is JellioRoute.Player -> Icons.Filled.Home
}

// Material's own filled Settings glyph carries noticeably more
// built-in padding around the gear than the rest of this rail's icons
// (the ported search/library path data especially), so it reads
// visibly smaller than its siblings at the same box size even though
// every SidebarItem box is identical. Real feedback live: nudge just
// this one glyph up rather than resizing the whole rail to compensate
// for one icon's own viewBox padding.
fun JellioRoute.iconScale(): Float = when (this) {
    JellioRoute.Settings -> 1.35f
    else -> 1f
}

fun JellioRoute.label(): String = when (this) {
    is JellioRoute.Profile -> "Profile"
    JellioRoute.Home -> "Home"
    JellioRoute.Search -> "Search"
    JellioRoute.Watchlist -> "Watchlist"
    JellioRoute.Calendar -> "Calendar"
    JellioRoute.Library -> "Library"
    JellioRoute.Feed -> "Feed"
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

// A pushed real other-user Profile (Feed row click) still reserves
// this pill's own real space and stays reachable, same as any other
// non-immersive real screen: only Detail/Person/Service/Player above
// go full bleed.
