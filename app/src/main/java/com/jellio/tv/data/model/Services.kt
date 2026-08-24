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
    return if (kind == "tvshows") "Series on $service" else "Movies on $service"
}
