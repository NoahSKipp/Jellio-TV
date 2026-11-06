package com.jellio.tv.data.model

data class SubtitleSizeOption(val value: String, val label: String, val rem: Float, val key: String = "subtitle_size_$value") {
    val displayName: String get() = if (key.isNotEmpty()) com.jellio.tv.ui.i18n.Strings.get(key) else label
}

data class SubtitleBackgroundOption(val value: String, val label: String, val key: String = "subtitle_bg_$value") {
    val displayName: String get() = if (key.isNotEmpty()) com.jellio.tv.ui.i18n.Strings.get(key) else label
}

val SUBTITLE_SIZES = listOf(
    SubtitleSizeOption("small", "Small", 1f, "subtitle_size_small"),
    SubtitleSizeOption("medium", "Medium", 1.3f, "subtitle_size_medium"),
    SubtitleSizeOption("large", "Large", 1.7f, "subtitle_size_large"),
    SubtitleSizeOption("xlarge", "Extra large", 2.1f, "subtitle_size_xlarge"),
)

val SUBTITLE_BACKGROUNDS = listOf(
    SubtitleBackgroundOption("none", "None", "subtitle_bg_none"),
    SubtitleBackgroundOption("semi", "Semi", "subtitle_bg_semi"),
    SubtitleBackgroundOption("solid", "Solid", "subtitle_bg_solid"),
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
