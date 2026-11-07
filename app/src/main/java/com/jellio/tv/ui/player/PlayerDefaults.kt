package com.jellio.tv.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val SEEK_STEP_MS = 10_000L
internal const val PROGRESS_REPORT_INTERVAL_MS = 10_000L
internal const val CONTROLS_HIDE_DELAY_MS = 4_000L
// Paused this long with nothing open, the controls give way to the
// "You're watching" screen (NuvioTV does the same).
internal const val PAUSE_OVERLAY_DELAY_MS = 5_000L
internal const val TOAST_DURATION_MS = 4_000L
internal const val TICKS_PER_MS = 10_000L
// Further apart than this from the group, a play or pause also seeks.
internal const val SYNC_DRIFT_MS = 1_500L
internal const val START_TIMEOUT_MS = 25_000L
internal const val SIDELOADED_SUBTITLE_PREFIX = "jellio-sub-"
internal const val REENCODE_START_TIMEOUT_MS = 12_000L
// How long Play Next counts down on the Up Next card and end screen.
internal const val UPNEXT_COUNTDOWN_SECONDS = 15

internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

internal fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val target = (positionMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0) target.coerceAtMost(durationMs) else target
}

internal data class SeekFlash(val target: Long, val totalMs: Long, val stamp: Long)

// "+10s", "-1m 30s", "+2m".
internal fun formatSeekDelta(totalMs: Long): String {
    val totalSeconds = totalMs / 1000
    val sign = if (totalSeconds >= 0) "+" else "-"
    val absSec = kotlin.math.abs(totalSeconds)
    if (absSec < 60) return "$sign${absSec}s"
    val m = absSec / 60
    val s = absSec % 60
    return if (s > 0) "$sign${m}m ${s}s" else "$sign${m}m"
}

internal fun formatSpeed(speed: Float): String {
    val whole = speed.toInt()
    return if (speed == whole.toFloat()) "${whole}x" else "${speed}x"
}

internal val SLEEP_TIMER_OPTIONS = listOf(15, 30, 45, 60, 90)

// screens/player.js's EPISODE_SLEEP_TIMER_OPTIONS: stop after this many
// episodes instead of after some minutes.
internal val EPISODE_SLEEP_TIMER_OPTIONS = listOf(1, 2, 3, 5)

// Episodes left before the episode sleep timer stops playback. Lives
// outside the player screen because each next episode opens a fresh one.
internal object EpisodeSleepTimer {
    var remaining by mutableStateOf<Int?>(null)

    // Counts one finished episode; true when it was the last one.
    fun countEpisode(): Boolean {
        val left = remaining ?: return false
        remaining = if (left <= 1) null else left - 1
        return left <= 1
    }
}
