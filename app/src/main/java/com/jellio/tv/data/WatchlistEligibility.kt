package com.jellio.tv.data

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.common.isUnreleasedItem

fun isSeriesWatchlistRemovalEligible(
    series: BaseItemDto,
    episodes: List<BaseItemDto>,
    watchedItemId: String? = null,
    isFavorite: Boolean = series.UserData?.IsFavorite == true,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    if (!isFavorite) return false
    if (!series.Status.equals("Ended", ignoreCase = true)) return false
    val regularEpisodes = episodes.filter { (it.ParentIndexNumber ?: 1) > 0 }
    if (regularEpisodes.isEmpty()) return false
    if (regularEpisodes.any { isUnreleasedItem(it, nowMs) }) return false
    return regularEpisodes.all { it.Id == watchedItemId || it.UserData?.Played == true }
}

fun isMovieWatchlistRemovalEligible(
    movie: BaseItemDto,
    isFavorite: Boolean = movie.UserData?.IsFavorite == true,
): Boolean {
    if (movie.Type != "Movie") return false
    return isFavorite
}
