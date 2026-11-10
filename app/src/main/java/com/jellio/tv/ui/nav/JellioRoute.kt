package com.jellio.tv.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

private val ANIME_NAME = Regex("anime", RegexOption.IGNORE_CASE)
sealed interface JellioRoute {
    data class Profile(val userId: String? = null) : JellioRoute
    data class Home(val editMode: Boolean = false) : JellioRoute
    data object Search : JellioRoute
    data object Feed : JellioRoute
    // One library as its own rail button (the Anime entry shares the
    // Shows library's id, so the name tells them apart).
    data class LibraryTab(val libraryId: String, val name: String, val collectionType: String?) : JellioRoute
    data object Settings : JellioRoute
    data class Detail(val itemId: String) : JellioRoute
    data class Person(val personId: String) : JellioRoute
    data class Service(val name: String) : JellioRoute
    data class Player(val itemId: String, val mediaSourceId: String? = null, val startPositionTicks: Long? = null) : JellioRoute
    data object LiveTv : JellioRoute
    data class LivePlayer(val channelId: String) : JellioRoute
}

fun jellioNavItems(libraries: List<com.jellio.tv.data.model.BaseItemDto>): List<JellioRoute> = buildList {
    add(JellioRoute.Profile())
    add(JellioRoute.Home())
    add(JellioRoute.Search)
    libraries.forEach { library ->
        if (library.CollectionType == "livetv") add(JellioRoute.LiveTv)
        else add(JellioRoute.LibraryTab(library.Id, library.Name.orEmpty(), library.CollectionType))
    }
    add(JellioRoute.Feed)
}

fun JellioRoute.icon(): ImageVector = when (this) {
    is JellioRoute.Profile -> Icons.Filled.AccountCircle
    is JellioRoute.Home -> Icons.Filled.Home
    JellioRoute.Search -> SearchIconVector
    JellioRoute.Feed -> Icons.Filled.DynamicFeed
    // The same icons Jellio web draws (components/navShared.js SVG_ICONS),
    // ported in JellioIcons.kt.
    is JellioRoute.LibraryTab -> when {
        ANIME_NAME.containsMatchIn(name) -> AnimeIconVector
        collectionType == "movies" -> MovieIconVector
        collectionType == "tvshows" -> TvShowsIconVector
        else -> LibraryIconVector
    }
    JellioRoute.Settings -> Icons.Filled.Settings
    JellioRoute.LiveTv -> Icons.Filled.LiveTv
    is JellioRoute.Detail, is JellioRoute.Person, is JellioRoute.Service, is JellioRoute.Player, is JellioRoute.LivePlayer -> Icons.Filled.Home
}

fun JellioRoute.iconScale(): Float = when (this) {
    JellioRoute.Settings -> 2.4f
    else -> 1f
}

fun JellioRoute.label(): String = when (this) {
    is JellioRoute.Profile -> com.jellio.tv.data.i18n.Strings.get("nav_profile")
    is JellioRoute.Home -> com.jellio.tv.data.i18n.Strings.get("nav_home")
    JellioRoute.Search -> com.jellio.tv.data.i18n.Strings.get("nav_search")
    JellioRoute.Feed -> com.jellio.tv.data.i18n.Strings.get("nav_feed")
    is JellioRoute.LibraryTab -> com.jellio.tv.data.i18n.libraryDisplayName(name, collectionType)
    JellioRoute.Settings -> com.jellio.tv.data.i18n.Strings.get("nav_settings")
    JellioRoute.LiveTv -> com.jellio.tv.data.i18n.Strings.get("nav_live_tv")
    is JellioRoute.Detail, is JellioRoute.Person, is JellioRoute.Service, is JellioRoute.Player, is JellioRoute.LivePlayer -> ""
}

fun JellioRoute.isImmersive(): Boolean = this is JellioRoute.Player || this is JellioRoute.LivePlayer

