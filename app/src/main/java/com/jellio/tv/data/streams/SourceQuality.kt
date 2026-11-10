package com.jellio.tv.data.streams

import com.jellio.tv.data.model.MediaSourceDto

internal val QUALITY_ORDER = listOf("4K", "QHD", "FHD", "HD", "SD")
private val QUALITY_NAME_PATTERNS = listOf(
    // Names say "720p" or just "HD", "Full HD", "Ultra HD"; HDR and HDTV
    // aren't resolutions.
    "4K" to Regex("(^|[^a-z0-9])(2160p|4k|uhd|ultra[ .-]?hd)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
    "QHD" to Regex("(^|[^a-z0-9])(1440p|qhd|2k)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
    "FHD" to Regex("(^|[^a-z0-9])(1080[pi]|fhd|full[ .-]?hd)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
    "HD" to Regex("(^|[^a-z0-9])(720p|hd)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
    "SD" to Regex("(^|[^a-z0-9])(576p|540p|480p|360p|240p|sd|dvdrip)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
)

private fun matchQuality(text: String): String? =
    QUALITY_NAME_PATTERNS.firstOrNull { it.second.containsMatchIn(text) }?.first

// The tier the addon's own label (the name's first line) states, or null.
internal fun labelQuality(source: MediaSourceDto): String? =
    source.Name?.substringBefore('\n')?.let { matchQuality(it) }

internal fun sourceQuality(source: MediaSourceDto): String? {
    labelQuality(source)?.let { return it }
    val video = source.MediaStreams?.firstOrNull { it.Type == "Video" }
    val height = video?.Height ?: 0
    val width = video?.Width ?: 0
    if (height > 0 || width > 0) {
        return when {
            height >= 2000 || width >= 3200 -> "4K"
            height >= 1300 || width >= 2400 -> "QHD"
            height >= 900 || width >= 1800 -> "FHD"
            height >= 650 || width >= 1200 -> "HD"
            else -> "SD"
        }
    }
    return source.Name?.let { matchQuality(it) }
}
