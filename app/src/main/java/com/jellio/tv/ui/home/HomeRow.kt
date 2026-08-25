package com.jellio.tv.ui.home

import com.jellio.tv.data.model.CalendarEntryDto

// A tagged union of every real row kind screens/home.js's own
// wrapRowForCustomization() call sites wrap (continue-watching/
// up-next/coming-soon/studio-hubs/catalog/genre/rec), so the
// customization bar in HomeScreen can reorder and hide across all of
// them the same way that file's own single flat #rows container does,
// not just the HomeSection-backed ones.
sealed interface HomeRow {
    val key: String
    val displayName: String
}

data class PosterHomeRow(val section: HomeSection, val landscape: Boolean = false) : HomeRow {
    override val key get() = section.key
    override val displayName get() = section.title
}

data class ComingSoonHomeRow(val entries: List<CalendarEntryDto>) : HomeRow {
    override val key = "coming-soon"
    override val displayName = "Coming Soon"
}

data class StudioHubsHomeRow(val services: List<String>) : HomeRow {
    override val key = "studio-hubs"
    // Matches StudioHubRow's own hardcoded RowTitle text exactly.
    override val displayName = "Studio Hubs"
}
