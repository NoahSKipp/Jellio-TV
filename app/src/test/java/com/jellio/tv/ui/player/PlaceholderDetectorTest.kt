package com.jellio.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            detectPlaceholder(clip.copy(formatFrameRate = null, measuredFrameRate = 1.2f)),
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
}
