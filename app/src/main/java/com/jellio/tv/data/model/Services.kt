package com.jellio.tv.data.model

// Service name matching, grouping and logo slugs, ported from
// components/services.js: a service only shows up when its real
// catalog collections exist, matched on the collection's own name
// since Gelato writes no field an item could carry instead.

val SERVICES = listOf(
    "Netflix",
    "HBO Max",
    "Max",
    "Disney+",
    "Prime Video",
    "Apple TV+",
    "Hulu",
    "Paramount+",
    "Peacock",
    "Crunchyroll",
    "AMC+",
    "Starz",
    "Shudder",
    "Discovery+",
    "Sky Go",
)

// Longest first, so "HBO Max" is not claimed by "Max".
private val SERVICES_LONGEST_FIRST = SERVICES.sortedByDescending { it.length }

fun serviceOf(name: String?): String? {
    val lower = (name ?: "").lowercase()
    return SERVICES_LONGEST_FIRST.firstOrNull { lower.startsWith(it.lowercase()) }
}

fun logoSlug(name: String): String =
    name.lowercase()
        .replace("+", "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

// FrontendController.cs's own real static asset route, the same real
// SVGs the web build's own logoUrl() already serves from the Jellyfin
// server itself, not a Coil image proxy call.
fun logoUrl(serverAddress: String, name: String): String =
    "$serverAddress/Jellio/frontend/img/services/${logoSlug(name)}.svg"

// Each service's brand colour, for the tint behind its hub.
private val SERVICE_COLORS = mapOf(
    "Netflix" to 0xFFE50914,
    "HBO Max" to 0xFF5822B4,
    "Max" to 0xFF002BE7,
    "Disney+" to 0xFF113CCF,
    "Prime Video" to 0xFF00A8E1,
    "Apple TV+" to 0xFF8E8E93,
    "Hulu" to 0xFF1CE783,
    "Paramount+" to 0xFF0064FF,
    "Peacock" to 0xFFFFB800,
    "Crunchyroll" to 0xFFF47521,
    "AMC+" to 0xFFF5C400,
    "Starz" to 0xFF6E6E73,
    "Shudder" to 0xFFC8102E,
    "Discovery+" to 0xFF2175D9,
    "Sky Go" to 0xFF0072C9,
)

fun serviceColor(name: String): Long? = SERVICE_COLORS[name]

fun groupByService(items: List<BaseItemDto>): Map<String, List<BaseItemDto>> {
    val groups = linkedMapOf<String, MutableList<BaseItemDto>>()
    items.forEach { item ->
        val service = serviceOf(item.Name) ?: return@forEach
        groups.getOrPut(service) { mutableListOf() }.add(item)
    }
    return groups
}

// A row's own name. The bare service catalogs are called just
// "Netflix" twice over, which says nothing on a page already titled
// Netflix, so those two get named for what they hold. Anything else
// already carries a real name ("Netflix Top 10 Movies (Global)") and
// keeps it.
fun rowTitle(collection: BaseItemDto, service: String, kind: String): String {
    if (!(collection.Name ?: "").equals(service, ignoreCase = true)) {
        return collection.Name ?: ""
    }
    return if (kind == "tvshows") "Shows on $service" else "Movies on $service"
}
