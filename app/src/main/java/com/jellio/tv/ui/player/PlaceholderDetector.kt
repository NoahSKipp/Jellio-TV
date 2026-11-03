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
    val measuredFrameRate: Float? = null,
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
    var measuredFps: Float? = null
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
            measuredFps = (rendered - sampleFrames) * 1000f / (now - sampleAt)
            sampleAt = now
            sampleFrames = rendered
        }
    }
}

const val PLACEHOLDER_MIN_RUNTIME_MS = 10L * 60 * 1000
const val PLACEHOLDER_MAX_DURATION_MS = 3L * 60 * 1000
const val PLACEHOLDER_MAX_FPS = 2f
const val PLACEHOLDER_STALL_MS = 8_000L

fun detectPlaceholder(signals: PlaceholderSignals): PlaceholderSign? {
    val expected = signals.expectedRuntimeMs
    if (expected < PLACEHOLDER_MIN_RUNTIME_MS) return null
    if (signals.durationMs in 1 until PLACEHOLDER_MAX_DURATION_MS) return PlaceholderSign.SHORT_DURATION
    val slowFrames = listOfNotNull(signals.formatFrameRate, signals.measuredFrameRate)
        .any { it > 0f && it <= PLACEHOLDER_MAX_FPS }
    if (signals.firstFrameRendered && slowFrames && signals.hasAudioTrack == false) return PlaceholderSign.SILENT_SLIDESHOW
    val early = signals.positionMs < expected / 4
    if (early && signals.ended) return PlaceholderSign.ENDED_EARLY
    if (early && signals.firstFrameRendered && signals.stalledMs > PLACEHOLDER_STALL_MS) return PlaceholderSign.STALLED_EARLY
    return null
}
