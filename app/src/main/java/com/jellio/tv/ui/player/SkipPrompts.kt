package com.jellio.tv.ui.player

import com.jellio.tv.data.playback.SkipSegments
import com.jellio.tv.data.i18n.Strings

// A skip intro / credits prompt leaves by itself after this long on screen.
internal const val SKIP_PROMPT_VISIBLE_MS = 15_000L

// Prompts sit in the bottom right corner; while the transport controls are up
// they move above them.
internal const val PROMPT_BOTTOM_CORNER_DP = 40
internal const val PROMPT_BOTTOM_ABOVE_CONTROLS_DP = 148

// How long before the end an episode without credits data offers its next one.
internal const val NEAR_END_UP_NEXT_SECONDS = 30.0

internal fun shouldShowUpNextNow(segments: SkipSegments?, currentSeconds: Double, durationSeconds: Double): Boolean {
    if (durationSeconds <= 0) return false
    val credits = segments?.credits
    if (credits != null && credits.end > 0 && credits.start >= 0) {
        return currentSeconds >= credits.start
    }
    // Like the web player: no credits data, no countdown card. The near-end
    // prompt and then the end screen cover these episodes instead.
    return false
}

// The near-end Up Next prompt: only once every skip source has answered
// without credits, in the last 30 seconds, and not for a clip so short that
// the whole of it would count as the end.
internal fun shouldShowNearEndUpNext(
    segments: SkipSegments?,
    segmentsResolved: Boolean,
    currentSeconds: Double,
    durationSeconds: Double,
): Boolean {
    if (!segmentsResolved || segments?.credits != null) return false
    if (durationSeconds <= NEAR_END_UP_NEXT_SECONDS * 2) return false
    return currentSeconds >= durationSeconds - NEAR_END_UP_NEXT_SECONDS && currentSeconds < durationSeconds
}

enum class SkipKind { INTRO, CREDITS }

data class SkipSegment(
    val kind: SkipKind,
    val targetSeconds: Double,
) {
    val eyebrow: String get() = Strings.get(if (kind == SkipKind.INTRO) "player_skip_eyebrow_intro" else "player_skip_eyebrow_credits")
    val label: String get() = Strings.get(if (kind == SkipKind.INTRO) "player_skip_intro" else "player_skip_credits")
}

fun activeSkipSegment(segments: SkipSegments?, currentSeconds: Double): SkipSegment? {
    segments ?: return null
    val intro = segments.intro
    if (intro != null && intro.end > 0 && currentSeconds >= intro.start && currentSeconds < intro.end) {
        return SkipSegment(SkipKind.INTRO, intro.end)
    }
    val credits = segments.credits
    if (credits != null && credits.end > 0 && currentSeconds >= credits.start && currentSeconds < credits.end) {
        return SkipSegment(SkipKind.CREDITS, credits.end)
    }
    return null
}
