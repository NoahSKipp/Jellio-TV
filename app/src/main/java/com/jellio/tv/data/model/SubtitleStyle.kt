package com.jellio.tv.data.model

// key names the label in the string tables; the UI resolves it.
data class SubtitleSizeOption(val value: String, val rem: Float) {
    val key: String get() = "subtitle_size_$value"
}

data class SubtitleBackgroundOption(val value: String) {
    val key: String get() = "subtitle_bg_$value"
}

val SUBTITLE_SIZES = listOf(
    SubtitleSizeOption("small", 1f),
    SubtitleSizeOption("medium", 1.3f),
    SubtitleSizeOption("large", 1.7f),
    SubtitleSizeOption("xlarge", 2.1f),
)

val SUBTITLE_BACKGROUNDS = listOf(
    SubtitleBackgroundOption("none"),
    SubtitleBackgroundOption("semi"),
    SubtitleBackgroundOption("solid"),
)

data class SubtitleStyle(
    val size: String = "medium",
    val background: String = "semi",
    val color: String = "white",
    val edge: String = "outline",
    val position: String = "default",
)

// Text colours, as ARGB.
val SUBTITLE_COLORS = listOf(
    "white" to 0xFFFFFFFF.toInt(),
    "yellow" to 0xFFFFE14D.toInt(),
    "cyan" to 0xFF7FE8FF.toInt(),
    "green" to 0xFF8CF58C.toInt(),
)

// Outline styles, mapped onto CaptionStyleCompat's edge types in the player.
val SUBTITLE_EDGES = listOf("none", "outline", "shadow", "raised")

// Distance from the bottom of the picture, as a fraction of its height.
val SUBTITLE_POSITIONS = listOf(
    "low" to 0.03f,
    "default" to 0.08f,
    "raised" to 0.16f,
    "high" to 0.26f,
)

fun subtitleSizeOption(value: String): SubtitleSizeOption =
    SUBTITLE_SIZES.firstOrNull { it.value == value } ?: SUBTITLE_SIZES[1]

fun subtitleBackgroundOption(value: String): SubtitleBackgroundOption =
    SUBTITLE_BACKGROUNDS.firstOrNull { it.value == value } ?: SUBTITLE_BACKGROUNDS[1]
