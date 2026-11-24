package com.jellio.tv.ui.player

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.SuggestionRowDto

// One title in the end screen's "More like this" row.
data class EndRec(
    val itemId: String,
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String?,
)

internal const val END_RECS_LIMIT = 12

// How long a card stays focused before its backdrop and overview show.
internal const val END_RECS_PREVIEW_DELAY_MS = 5000L

// An episode is recommended from its series, anything else from itself.
internal fun recommendationSourceId(itemId: String, type: String?, seriesId: String?): String =
    if (type == "Episode" && !seriesId.isNullOrBlank()) seriesId else itemId

// The search suggestions endpoint wants tv for series and episodes.
internal fun recommendationMediaType(type: String?): String =
    if (type == "Episode" || type == "Series") "tv" else "movie"

internal fun tmdbIdOf(item: BaseItemDto?): String? =
    (item?.ProviderIds?.get("Tmdb") ?: item?.ProviderIds?.get("tmdb"))?.takeIf { it.isNotBlank() }

// The "similar" row of a suggestions answer (TMDB recommendations and
// similar, library titles first, then virtual ones).
internal fun similarRowItems(rows: List<SuggestionRowDto>): List<BaseItemDto> =
    rows.firstOrNull { it.Kind.equals("similar", ignoreCase = true) }?.Items.orEmpty()

// Drops the title just watched (by id or TMDB id), repeats and library
// titles already fully watched. Virtual titles carry no watch state.
internal fun keepRecommendations(
    items: List<BaseItemDto>,
    excludeIds: Set<String>,
    limit: Int = END_RECS_LIMIT,
    excludeTmdbId: String? = null,
): List<BaseItemDto> =
    items
        .filter {
            it.Id.isNotBlank() && it.Id !in excludeIds && it.UserData?.Played != true &&
                (excludeTmdbId == null || tmdbIdOf(it) != excludeTmdbId)
        }
        .distinctBy { it.Id }
        .take(limit)

// Merge order: the TMDB "similar" row is primary; the Jellyfin similar list
// is only used when the TMDB row has nothing left after filtering.
internal fun chooseRecommendations(
    tmdbRow: List<BaseItemDto>,
    jellyfinSimilar: List<BaseItemDto>,
    excludeIds: Set<String>,
    excludeTmdbId: String? = null,
    limit: Int = END_RECS_LIMIT,
): List<BaseItemDto> {
    val primary = keepRecommendations(tmdbRow, excludeIds, limit, excludeTmdbId)
    return primary.ifEmpty { keepRecommendations(jellyfinSimilar, excludeIds, limit, excludeTmdbId) }
}

// The title to preview once the focused card has dwelled, or null when
// nothing is settled or there is nothing to show for it.
internal fun previewRec(recs: List<EndRec>, settledIndex: Int?): EndRec? {
    val rec = settledIndex?.let { recs.getOrNull(it) } ?: return null
    return if (rec.backdropUrl != null || !rec.overview.isNullOrBlank()) rec else null
}
