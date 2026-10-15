package com.jellio.tv.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator

private const val MB = 1024 * 1024

// Settings > Playback > Pre-buffer. Standard is Media3's own default.
// More and Maximum hold off starting until a few seconds are in, load
// further ahead while playing, wait for a proper stretch after running
// dry instead of stuttering, and keep loading several minutes ahead while
// paused. Byte budgets are capped by this app's heap so a Fire TV Stick
// can't run out of memory on a 4K remux.
@OptIn(UnstableApi::class)
private class PrebufferLoadControl(
    minBufferMs: Int,
    maxBufferMs: Int,
    startMs: Int,
    afterRebufferMs: Int,
    playingBytes: Int,
    private val pausedMaxUs: Long,
    private val pausedBytes: Long,
) : DefaultLoadControl(
    DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
    minBufferMs,
    maxBufferMs,
    startMs,
    afterRebufferMs,
    playingBytes,
    false,
    0,
    false,
) {
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (!parameters.playWhenReady) {
            return parameters.bufferedDurationUs < pausedMaxUs &&
                allocator.totalBytesAllocated < pausedBytes
        }
        return super.shouldContinueLoading(parameters)
    }
}

@OptIn(UnstableApi::class)
fun buildLoadControl(level: String): LoadControl {
    val heap = Runtime.getRuntime().maxMemory()
    fun capped(wantedMb: Int, heapShare: Double): Long = minOf(wantedMb.toLong() * MB, (heap * heapShare).toLong())
    return when (level) {
        "more" -> PrebufferLoadControl(
            minBufferMs = 30_000,
            maxBufferMs = 90_000,
            startMs = 3_000,
            afterRebufferMs = 8_000,
            playingBytes = capped(256, 0.35).toInt(),
            pausedMaxUs = 5L * 60 * 1_000_000,
            pausedBytes = capped(384, 0.45),
        )
        "max" -> PrebufferLoadControl(
            minBufferMs = 60_000,
            maxBufferMs = 180_000,
            startMs = 6_000,
            afterRebufferMs = 15_000,
            playingBytes = capped(384, 0.4).toInt(),
            pausedMaxUs = 10L * 60 * 1_000_000,
            pausedBytes = capped(640, 0.5),
        )
        else -> DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500,
            )
            .build()
    }
}
