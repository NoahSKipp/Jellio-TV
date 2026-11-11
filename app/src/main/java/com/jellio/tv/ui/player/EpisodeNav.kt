package com.jellio.tv.ui.player

// Which of the previous / next episode buttons the transport row shows. A
// shown button without a target keeps its place but is hidden and skipped
// by focus, so nothing moves when the neighbour is resolved later.
data class EpisodeNav(val show: Boolean, val hasPrevious: Boolean, val hasNext: Boolean)

fun episodeNav(isEpisode: Boolean, blockedInGroup: Boolean, previousId: String?, nextId: String?): EpisodeNav {
    val show = isEpisode && !blockedInGroup
    return EpisodeNav(show = show, hasPrevious = show && previousId != null, hasNext = show && nextId != null)
}
