package com.jellio.tv.ui.player

import com.jellio.tv.data.playback.SkipSegments

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
