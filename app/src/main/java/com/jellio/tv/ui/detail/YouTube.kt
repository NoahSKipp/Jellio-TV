package com.jellio.tv.ui.detail

// Same links screens/detail.js extractYouTubeId() reads, plus the other
// shapes TMDB and Kodi-style trailer urls use.
private val YOUTUBE_ID = Regex(
    "(?:youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^#]*&)?v=|embed/|shorts/|v/|live/)|youtu\\.be/|[?&]videoid=)([\\w-]{11})(?![\\w-])",
    RegexOption.IGNORE_CASE,
)

fun youTubeId(url: String?): String? = url?.let { YOUTUBE_ID.find(it)?.groupValues?.get(1) }

// hqdefault exists for every video; mqdefault is the smaller fallback.
fun youTubeThumbnails(id: String): List<String> =
    listOf("https://i.ytimg.com/vi/$id/hqdefault.jpg", "https://i.ytimg.com/vi/$id/mqdefault.jpg")
