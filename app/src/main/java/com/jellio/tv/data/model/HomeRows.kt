package com.jellio.tv.data.model

import androidx.compose.runtime.Immutable
import com.jellio.tv.data.i18n.Strings

@Immutable
data class HomeSection(
    val title: String,
    val items: List<BaseItemDto>,
    val fetchAll: (suspend () -> List<BaseItemDto>)? = null,
    val key: String = title,
    // One page (start, size) of the full row, so View all keeps loading
    // as it is scrolled instead of fetching everything up front.
    val fetchPage: (suspend (Int, Int) -> List<BaseItemDto>)? = null,
)

@Immutable
sealed interface HomeRow {
    val key: String
    val displayName: String
}

@Immutable
data class PosterHomeRow(val section: HomeSection, val landscape: Boolean = false) : HomeRow {
    override val key get() = section.key
    override val displayName get() = section.title
}

@Immutable
// items: each entry's library item (by normalized id), so the cards can be
// the same focusable, widening cards as every other row.
data class ComingSoonHomeRow(val entries: List<CalendarEntryDto>, val items: Map<String, BaseItemDto> = emptyMap()) : HomeRow {
    override val key = "coming-soon"
    override val displayName: String get() = Strings.get("home_coming_soon")
}

@Immutable
data class StudioHubsHomeRow(val services: List<String>) : HomeRow {
    override val key = "studio-hubs"
    // Matches StudioHubRow's own hardcoded RowTitle text exactly.
    override val displayName: String get() = Strings.get("home_studio_hubs")
}
