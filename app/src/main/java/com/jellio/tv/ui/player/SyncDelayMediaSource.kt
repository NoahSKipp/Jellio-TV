@file:androidx.annotation.OptIn(UnstableApi::class)

package com.jellio.tv.ui.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

// Audio and subtitle sync, adapted from Nuvio's AudioDelayMediaSource: the
// timestamps of samples are shifted as the player reads them, so a change
// applies within moments without reloading the stream.
// - Audio delay: audio is the playback clock, so the video is shifted the
//   other way instead (positive = sound later than the picture).
// - Subtitle delay: subtitle cues are shifted (positive = subtitles later).
internal class SyncDelayMediaSource(
    mediaSource: MediaSource,
    private val audioDelayUs: () -> Long,
    private val subtitleDelayUs: () -> Long,
) : WrappingMediaSource(mediaSource) {
    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
        SyncDelayMediaPeriod(mediaSource.createPeriod(id, allocator, startPositionUs), audioDelayUs, subtitleDelayUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        mediaSource.releasePeriod((mediaPeriod as SyncDelayMediaPeriod).mediaPeriod)
    }

    override fun onChildSourceInfoRefreshed(timeline: Timeline) {
        refreshSourceInfo(timeline)
    }
}

private class SyncDelayMediaPeriod(
    val mediaPeriod: MediaPeriod,
    private val audioDelayUs: () -> Long,
    private val subtitleDelayUs: () -> Long,
) : MediaPeriod, MediaPeriod.Callback {
    private var callback: MediaPeriod.Callback? = null

    override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
        this.callback = callback
        mediaPeriod.prepare(this, positionUs)
    }

    override fun maybeThrowPrepareError() = mediaPeriod.maybeThrowPrepareError()
    override fun getTrackGroups(): TrackGroupArray = mediaPeriod.trackGroups
    override fun getStreamKeys(trackSelections: List<ExoTrackSelection>): List<StreamKey> = mediaPeriod.getStreamKeys(trackSelections)

    override fun selectTracks(
        selections: Array<ExoTrackSelection?>,
        mayRetainStreamFlags: BooleanArray,
        streams: Array<SampleStream?>,
        streamResetFlags: BooleanArray,
        positionUs: Long,
    ): Long {
        val childStreams = arrayOfNulls<SampleStream>(streams.size)
        for (i in streams.indices) childStreams[i] = (streams[i] as? ShiftedSampleStream)?.childStream ?: streams[i]
        val selectedPositionUs = mediaPeriod.selectTracks(selections, mayRetainStreamFlags, childStreams, streamResetFlags, positionUs)
        for (i in streams.indices) {
            val child = childStreams[i]
            val type = selections[i]?.selectedFormat?.sampleMimeType?.let(MimeTypes::getTrackType)
            // Video moves against the audio delay; subtitles move with their delay.
            val shift: (() -> Long)? = when (type) {
                C.TRACK_TYPE_VIDEO -> { { -audioDelayUs() } }
                C.TRACK_TYPE_TEXT -> subtitleDelayUs
                else -> null
            }
            streams[i] = when {
                child == null -> null
                shift == null -> child
                (streams[i] as? ShiftedSampleStream)?.childStream === child -> streams[i]
                else -> ShiftedSampleStream(child, shift)
            }
        }
        return selectedPositionUs
    }

    override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) = mediaPeriod.discardBuffer(positionUs, toKeyframe)
    override fun readDiscontinuity(): Long = mediaPeriod.readDiscontinuity()
    override fun seekToUs(positionUs: Long): Long = mediaPeriod.seekToUs(positionUs)
    override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters): Long =
        mediaPeriod.getAdjustedSeekPositionUs(positionUs, seekParameters)
    override fun getBufferedPositionUs(): Long = mediaPeriod.bufferedPositionUs
    override fun getNextLoadPositionUs(): Long = mediaPeriod.nextLoadPositionUs
    override fun continueLoading(loadingInfo: LoadingInfo): Boolean = mediaPeriod.continueLoading(loadingInfo)
    override fun isLoading(): Boolean = mediaPeriod.isLoading
    override fun reevaluateBuffer(positionUs: Long) = mediaPeriod.reevaluateBuffer(positionUs)
    override fun onPrepared(mediaPeriod: MediaPeriod) {
        callback?.onPrepared(this)
    }
    override fun onContinueLoadingRequested(source: MediaPeriod) {
        callback?.onContinueLoadingRequested(this)
    }
}

// Adds shiftUs() to every sample's timestamp.
private class ShiftedSampleStream(val childStream: SampleStream, private val shiftUs: () -> Long) : SampleStream {
    override fun isReady(): Boolean = childStream.isReady
    override fun maybeThrowError() = childStream.maybeThrowError()
    override fun readData(formatHolder: FormatHolder, buffer: DecoderInputBuffer, readFlags: Int): Int {
        val result = childStream.readData(formatHolder, buffer, readFlags)
        if (result == C.RESULT_BUFFER_READ) buffer.timeUs += shiftUs()
        return result
    }
    override fun skipData(positionUs: Long): Int = childStream.skipData((positionUs - shiftUs()).coerceAtLeast(0L))
}
