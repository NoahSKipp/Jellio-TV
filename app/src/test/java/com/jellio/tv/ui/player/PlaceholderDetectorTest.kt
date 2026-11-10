package com.jellio.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderDetectorTest {
    private val movie = 110L * 60 * 1000

    @Test
    fun shortClipOnALongTitle() {
        assertEquals(PlaceholderSign.SHORT_DURATION, detectPlaceholder(PlaceholderSignals(movie, durationMs = 120_000)))
        assertNull(detectPlaceholder(PlaceholderSignals(movie, durationMs = movie)))
    }

    @Test
    fun skipsShortTitles() {
        val short = 8L * 60 * 1000
        assertNull(detectPlaceholder(PlaceholderSignals(short, durationMs = 120_000)))
        assertNull(detectPlaceholder(PlaceholderSignals(short, ended = true, positionMs = 1_000)))
        assertNull(detectPlaceholder(PlaceholderSignals(0, durationMs = 120_000)))
    }

    @Test
    fun silentSlideshowNeedsFirstFrameAndKnownMissingAudio() {
        val clip = PlaceholderSignals(movie, firstFrameRendered = true, formatFrameRate = 1f, hasAudioTrack = false)
        assertEquals(PlaceholderSign.SILENT_SLIDESHOW, detectPlaceholder(clip))
        assertNull(detectPlaceholder(clip.copy(firstFrameRendered = false)))
        assertNull(detectPlaceholder(clip.copy(hasAudioTrack = null)))
        assertNull(detectPlaceholder(clip.copy(hasAudioTrack = true)))
        assertNull(detectPlaceholder(clip.copy(formatFrameRate = 23.976f)))
        assertEquals(
            PlaceholderSign.SILENT_SLIDESHOW,
            detectPlaceholder(clip.copy(formatFrameRate = null, measuredFrames = 5, measuredSeconds = 4.2)),
        )
        assertNull(detectPlaceholder(clip.copy(formatFrameRate = -1f)))
    }

    @Test
    fun endedOrStalledEarly() {
        assertEquals(PlaceholderSign.ENDED_EARLY, detectPlaceholder(PlaceholderSignals(movie, ended = true, positionMs = 120_000)))
        assertNull(detectPlaceholder(PlaceholderSignals(movie, ended = true, positionMs = movie - 1_000)))
        val stall = PlaceholderSignals(movie, firstFrameRendered = true, stalledMs = 9_000, positionMs = 60_000)
        assertEquals(PlaceholderSign.STALLED_EARLY, detectPlaceholder(stall))
        assertNull(detectPlaceholder(stall.copy(stalledMs = 7_000)))
        assertNull(detectPlaceholder(stall.copy(positionMs = movie / 2)))
        assertNull(detectPlaceholder(stall.copy(firstFrameRendered = false)))
    }

    @Test
    fun silentSlideshowMatchesWeb() {
        assertTrue(looksLikeSilentSlideshow(5, 5.0, noAudio = true, expectedRuntimeMs = movie))
        assertTrue(looksLikeSilentSlideshow(10, 5.0, noAudio = true, expectedRuntimeMs = movie))
        assertFalse(looksLikeSilentSlideshow(11, 5.0, noAudio = true, expectedRuntimeMs = movie))
        assertFalse(looksLikeSilentSlideshow(120, 5.0, noAudio = true, expectedRuntimeMs = movie))
        assertFalse(looksLikeSilentSlideshow(5, 5.0, noAudio = false, expectedRuntimeMs = movie))
        assertFalse(looksLikeSilentSlideshow(5, 5.0, noAudio = null, expectedRuntimeMs = movie))
        assertFalse(looksLikeSilentSlideshow(2, 2.0, noAudio = true, expectedRuntimeMs = movie))
        assertFalse(looksLikeSilentSlideshow(5, Double.NaN, noAudio = true, expectedRuntimeMs = movie))
        assertFalse(looksLikeSilentSlideshow(5, 5.0, noAudio = true, expectedRuntimeMs = 9L * 60 * 1000))
    }

    @Test
    fun stoppedTooEarlyMatchesWeb() {
        assertTrue(stoppedTooEarly(60_000, movie))
        assertTrue(stoppedTooEarly(0, movie))
        assertFalse(stoppedTooEarly(movie / 4, movie))
        assertFalse(stoppedTooEarly(movie / 2, movie))
        assertFalse(stoppedTooEarly(-1, movie))
        assertFalse(stoppedTooEarly(1_000, 9L * 60 * 1000))
        assertFalse(stoppedTooEarly(1_000, 0))
    }

    @Test
    fun zeroMeasuredFramesIsNotASlideshow() {
        val clip = PlaceholderSignals(movie, firstFrameRendered = true, measuredFrames = 0, measuredSeconds = 5.0, hasAudioTrack = false)
        assertNull(detectPlaceholder(clip))
    }
}
