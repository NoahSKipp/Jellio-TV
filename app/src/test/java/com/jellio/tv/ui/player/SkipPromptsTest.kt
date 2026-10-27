package com.jellio.tv.ui.player

import com.jellio.tv.data.playback.SkipRange
import com.jellio.tv.data.playback.SkipSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipPromptsTest {
    private val withCredits = SkipSegments(intro = SkipRange(10.0, 70.0), credits = SkipRange(1_700.0, 1_790.0))
    private val introOnly = SkipSegments(intro = SkipRange(10.0, 70.0))

    @Test
    fun activeSegmentFollowsThePosition() {
        assertEquals(SkipKind.INTRO, activeSkipSegment(withCredits, 30.0)?.kind)
        assertEquals(70.0, activeSkipSegment(withCredits, 30.0)?.targetSeconds ?: 0.0, 0.0)
        assertNull(activeSkipSegment(withCredits, 70.0))
        assertEquals(SkipKind.CREDITS, activeSkipSegment(withCredits, 1_750.0)?.kind)
        assertNull(activeSkipSegment(null, 30.0))
    }

    @Test
    fun nearEndPromptOnlyWithoutCreditsInTheLastThirtySeconds() {
        assertTrue(shouldShowNearEndUpNext(introOnly, true, 1_775.0, 1_800.0))
        assertTrue(shouldShowNearEndUpNext(null, true, 1_770.0, 1_800.0))
        assertFalse(shouldShowNearEndUpNext(introOnly, true, 1_760.0, 1_800.0))
        assertFalse(shouldShowNearEndUpNext(introOnly, true, 1_800.0, 1_800.0))
        assertFalse(shouldShowNearEndUpNext(withCredits, true, 1_775.0, 1_800.0))
    }

    @Test
    fun nearEndPromptWaitsForAllSourcesAndSkipsShortClips() {
        assertFalse(shouldShowNearEndUpNext(introOnly, false, 1_775.0, 1_800.0))
        assertFalse(shouldShowNearEndUpNext(null, true, 50.0, 60.0))
        assertFalse(shouldShowNearEndUpNext(null, true, 0.0, 0.0))
    }

    @Test
    fun creditsCardStartsAtTheCredits() {
        assertTrue(shouldShowUpNextNow(withCredits, 1_700.0, 1_800.0))
        assertFalse(shouldShowUpNextNow(withCredits, 1_699.0, 1_800.0))
        assertFalse(shouldShowUpNextNow(introOnly, 1_790.0, 1_800.0))
    }
}
