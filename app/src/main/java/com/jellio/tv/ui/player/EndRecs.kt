package com.jellio.tv.ui.player

import com.jellio.tv.data.model.BaseItemDto

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

// Drops the title just watched, repeats and titles already fully watched.
internal fun keepRecommendations(
    items: List<BaseItemDto>,
    excludeIds: Set<String>,
    limit: Int = END_RECS_LIMIT,
): List<BaseItemDto> =
    items
        .filter { it.Id.isNotBlank() && it.Id !in excludeIds && it.UserData?.Played != true }
        .distinctBy { it.Id }
        .take(limit)

// The title to preview once the focused card has dwelled, or null when
// nothing is settled or there is nothing to show for it.
internal fun previewRec(recs: List<EndRec>, settledIndex: Int?): EndRec? {
    val rec = settledIndex?.let { recs.getOrNull(it) } ?: return null
    return if (rec.backdropUrl != null || !rec.overview.isNullOrBlank()) rec else null
}
