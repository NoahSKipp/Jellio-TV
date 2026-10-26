package com.jellio.tv.ui.common

import com.jellio.tv.data.model.BaseItemDto

data class ArtCandidate(val url: String, val blurred: Boolean)

// A poster blurred to fill a wide frame only needs a few pixels: upscaling
// them softens it on every Android version, Modifier.blur only on 12+.
const val BLURRED_POSTER_WIDTH = 64

// Wide art for a title header, best first: backdrops, the series' for an
// episode, an episode still, Thumb art, then the poster blurred. Callers
// move to the next entry when one fails to load and draw only the gradient
// and title once the list runs out.
fun titleArtCandidates(
    item: BaseItemDto,
    url: (itemId: String, tag: String, imageType: String, maxWidth: Int) -> String,
    season: BaseItemDto? = null,
    seriesFirst: Boolean = false,
    backdropWidth: Int = 1920,
): List<ArtCandidate> {
    val seen = HashSet<String>()
    val out = mutableListOf<ArtCandidate>()
    fun add(id: String?, tag: String?, type: String, blurred: Boolean = false) {
        if (id.isNullOrBlank() || tag.isNullOrBlank() || !seen.add("$id/$type")) return
        out += ArtCandidate(url(id, tag, type, if (blurred) BLURRED_POSTER_WIDTH else backdropWidth), blurred)
    }
    fun backdrops(id: String?, tags: List<String>?) =
        tags?.forEachIndexed { i, tag -> add(id, tag, if (i == 0) "Backdrop" else "Backdrop/$i") }

    val episode = item.Type == "Episode"
    val primary = item.ImageTags?.get("Primary") ?: item.PrimaryImageTag
    if (seriesFirst && episode) {
        backdrops(item.ParentBackdropItemId, item.ParentBackdropImageTags)
        add(item.ParentThumbItemId, item.ParentThumbImageTag, "Thumb")
    }
    season?.let { backdrops(it.Id, it.BackdropImageTags) }
    backdrops(item.Id, item.BackdropImageTags)
    backdrops(item.ParentBackdropItemId, item.ParentBackdropImageTags)
    if (episode) add(item.Id, primary, "Primary")
    add(item.Id, item.ImageTags?.get("Thumb") ?: item.ThumbImageTag, "Thumb")
    add(item.ParentThumbItemId, item.ParentThumbImageTag, "Thumb")
    if (episode) add(item.SeriesId, item.SeriesPrimaryImageTag, "Primary", blurred = true)
    add(item.Id, primary, "Primary", blurred = true)
    return out
}

// The item's clear logo, else its show's.
fun titleLogoCandidates(
    item: BaseItemDto,
    url: (itemId: String, tag: String, imageType: String, maxWidth: Int) -> String,
    maxWidth: Int = 800,
): List<String> = buildList {
    item.ImageTags?.get("Logo")?.let { add(url(item.Id, it, "Logo", maxWidth)) }
    if (item.ParentLogoItemId != null && item.ParentLogoImageTag != null) {
        add(url(item.ParentLogoItemId, item.ParentLogoImageTag, "Logo", maxWidth))
    }
}.distinct()
