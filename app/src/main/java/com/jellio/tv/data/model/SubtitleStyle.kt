package com.jellio.tv.data.model

// Real port of screens/player.js's own SUBTITLE_SIZES/
// SUBTITLE_BACKGROUNDS/DEFAULT_SUBTITLE_STYLE: that file drives ::cue
// custom properties a browser's own WebVTT renderer reads directly.
// This app has no such renderer, PlayerScreen.kt's own
// subtitleCaptionStyle()/subtitleFractionalTextSize() translate the
// same two real style axes onto Media3's own SubtitleView/
// CaptionStyleCompat instead. rem is kept here only for parity with
// the real web file's own values; PlayerScreen.kt derives a fraction
// of SubtitleView's own default text size fraction from it, relative
// to medium's own real 1.3rem baseline, rather than using rem itself.
data class SubtitleSizeOption(val value: String, val label: String, val rem: Float)

data class SubtitleBackgroundOption(val value: String, val label: String)

val SUBTITLE_SIZES = listOf(
    SubtitleSizeOption("small", "Small", 1f),
    SubtitleSizeOption("medium", "Medium", 1.3f),
    SubtitleSizeOption("large", "Large", 1.7f),
    SubtitleSizeOption("xlarge", "Extra large", 2.1f),
)

val SUBTITLE_BACKGROUNDS = listOf(
    SubtitleBackgroundOption("none", "None"),
    SubtitleBackgroundOption("semi", "Semi"),
    SubtitleBackgroundOption("solid", "Solid"),
)

data class SubtitleStyle(val size: String = "medium", val background: String = "semi")

fun subtitleSizeOption(value: String): SubtitleSizeOption =
    SUBTITLE_SIZES.firstOrNull { it.value == value } ?: SUBTITLE_SIZES[1]

fun subtitleBackgroundOption(value: String): SubtitleBackgroundOption =
    SUBTITLE_BACKGROUNDS.firstOrNull { it.value == value } ?: SUBTITLE_BACKGROUNDS[1]
