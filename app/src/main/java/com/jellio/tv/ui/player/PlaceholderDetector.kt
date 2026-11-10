package com.jellio.tv.ui.player

// AIOStreams/nzbdav serve a 2 minute, 1 fps, silent 720p clip in place of a
// release they could not resolve. These are the signs of it on a title that
// should run for ten minutes or more.
enum class PlaceholderSign(val reason: String) {
    SHORT_DURATION("placeholder clip: runs under 3 minutes"),
    SILENT_SLIDESHOW("placeholder clip: 2 fps or less and no audio"),
    ENDED_EARLY("placeholder clip: ended in the first quarter"),
    STALLED_EARLY("placeholder clip: stalled in the first quarter"),
}

data class PlaceholderSignals(
    val expectedRuntimeMs: Long,
    // 0 when the stream's own length is unknown or not trustworthy.
    val durationMs: Long = 0,
    val firstFrameRendered: Boolean = false,
    // Format.frameRate, or null when the stream does not say.
    val formatFrameRate: Float? = null,
    // Frames drawn over that many seconds of playback, 0 seconds when not sampled.
    val measuredFrames: Int = 0,
    val measuredSeconds: Double = 0.0,
    // null until the stream's tracks are known.
    val hasAudioTrack: Boolean? = null,
    val ended: Boolean = false,
    val stalledMs: Long = 0,
    val positionMs: Long = 0,
)

// What the player has seen of the current stream so far.
class PlaceholderWatch {
    var firstFrameAt = 0L
    var hasAudio: Boolean? = null
    var stallStartedAt = 0L
    var stallAfterSeek = false
    var measuredFrames = 0
    var measuredSeconds = 0.0
    var reported = false
    private var sampleAt = 0L
    private var sampleFrames = 0

    fun sampleFrames(rendered: Int?, playing: Boolean, now: Long) {
        if (rendered == null || !playing || firstFrameAt == 0L) {
            sampleAt = 0L
            return
        }
        if (sampleAt == 0L) {
            sampleAt = now
            sampleFrames = rendered
        } else if (now - sampleAt >= 4_000) {
            measuredFrames = rendered - sampleFrames
            measuredSeconds = (now - sampleAt) / 1000.0
            sampleAt = now
            sampleFrames = rendered
        }
    }
}

const val PLACEHOLDER_MIN_RUNTIME_MS = 10L * 60 * 1000
const val PLACEHOLDER_MAX_DURATION_MS = 3L * 60 * 1000
const val PLACEHOLDER_MAX_FPS = 2f
const val PLACEHOLDER_STALL_MS = 8_000L
const val PLACEHOLDER_MIN_SAMPLE_SECONDS = 3.0

// AIOStreams' "failed to download" clip plays at 1 fps with no audio,
// whatever length the player was told.
fun looksLikeSilentSlideshow(frames: Int, seconds: Double, noAudio: Boolean?, expectedRuntimeMs: Long): Boolean {
    if (expectedRuntimeMs < PLACEHOLDER_MIN_RUNTIME_MS) return false
    if (noAudio != true) return false
    if (frames < 0 || !seconds.isFinite() || seconds < PLACEHOLDER_MIN_SAMPLE_SECONDS) return false
    return frames / seconds <= PLACEHOLDER_MAX_FPS
}

// Ending or stalling in the first quarter of a long title.
fun stoppedTooEarly(positionMs: Long, expectedRuntimeMs: Long): Boolean {
    if (expectedRuntimeMs < PLACEHOLDER_MIN_RUNTIME_MS) return false
    if (positionMs < 0) return false
    return positionMs < expectedRuntimeMs * 0.25
}

fun detectPlaceholder(signals: PlaceholderSignals): PlaceholderSign? {
    val expected = signals.expectedRuntimeMs
    if (expected < PLACEHOLDER_MIN_RUNTIME_MS) return null
    if (signals.durationMs in 1 until PLACEHOLDER_MAX_DURATION_MS) return PlaceholderSign.SHORT_DURATION
    val noAudio = signals.hasAudioTrack?.not()
    val declaredSlow = signals.formatFrameRate?.let { it > 0f && it <= PLACEHOLDER_MAX_FPS } == true && noAudio == true
    val slideshow = declaredSlow || signals.measuredFrames > 0 &&
        looksLikeSilentSlideshow(signals.measuredFrames, signals.measuredSeconds, noAudio, expected)
    if (signals.firstFrameRendered && slideshow) return PlaceholderSign.SILENT_SLIDESHOW
    val early = stoppedTooEarly(signals.positionMs, expected)
    if (early && signals.ended) return PlaceholderSign.ENDED_EARLY
    if (early && signals.firstFrameRendered && signals.stalledMs > PLACEHOLDER_STALL_MS) return PlaceholderSign.STALLED_EARLY
    return null
}
