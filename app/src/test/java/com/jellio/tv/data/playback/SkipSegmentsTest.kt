package com.jellio.tv.data.playback

import com.jellio.tv.data.model.ChapterInfoDto
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.MediaSegmentDto
import com.jellio.tv.data.model.SkipSegmentDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentsTest {
    private val runtime = 1_800.0
    private fun ticks(seconds: Long) = seconds * 10_000_000L

    @Test
    fun nativeSegmentsMapIntroAndOutroFromTicks() {
        val segments = nativeSkipSegments(
            listOf(
                MediaSegmentDto(Type = "Recap", StartTicks = 0, EndTicks = ticks(20)),
                MediaSegmentDto(Type = "Intro", StartTicks = ticks(30), EndTicks = ticks(90)),
                MediaSegmentDto(Type = "Outro", StartTicks = ticks(1_700), EndTicks = ticks(1_790)),
            ),
        )
        assertEquals(SkipRange(30.0, 90.0), segments?.intro)
        assertEquals(SkipRange(1_700.0, 1_790.0), segments?.credits)
    }

    @Test
    fun nativeSegmentsWithoutIntroOrOutroAreNothing() {
        assertNull(nativeSkipSegments(emptyList()))
        assertNull(nativeSkipSegments(listOf(MediaSegmentDto(Type = "Commercial", StartTicks = 0, EndTicks = ticks(30)))))
    }

    @Test
    fun pluginShapeMapsAndEmptyResponseIsNothing() {
        assertNull(IntroSkipperSegmentsDto().toSkipSegments())
        val mapped = IntroSkipperSegmentsDto(Introduction = SkipSegmentDto(10.0, 70.0)).toSkipSegments()
        assertEquals(SkipRange(10.0, 70.0), mapped?.intro)
        assertNull(mapped?.credits)
    }

    @Test
    fun creditsInTheFirstHalfAreDropped() {
        val merged = mergeSkipSegments(null, SkipSegments(credits = SkipRange(300.0, 400.0)), runtime)
        assertNull(merged?.credits)
        assertTrue(isValidCredits(SkipRange(300.0, 400.0), 0.0))
    }

    @Test
    fun earlierSourcesWinAndLaterOnesOnlyFillGaps() {
        val community = SkipSegments(intro = SkipRange(5.0, 60.0))
        val native = SkipSegments(intro = SkipRange(10.0, 70.0), credits = SkipRange(1_700.0, 1_790.0))
        val merged = mergeSkipSegments(mergeSkipSegments(null, community, runtime), native, runtime)
        assertEquals(SkipRange(5.0, 60.0), merged?.intro)
        assertEquals(SkipRange(1_700.0, 1_790.0), merged?.credits)
        assertTrue(skipSegmentsComplete(merged, runtime))
    }

    @Test
    fun invalidRangesNeverWin() {
        val current = SkipSegments(intro = SkipRange(0.0, 0.0))
        val merged = mergeSkipSegments(current, SkipSegments(intro = SkipRange(12.0, 80.0)), runtime)
        assertEquals(SkipRange(12.0, 80.0), merged?.intro)
        assertFalse(skipSegmentsComplete(merged, runtime))
        assertEquals(current, mergeSkipSegments(current, null, runtime))
    }

    @Test
    fun chaptersNamedLikeIntroAndCreditsBecomeRanges() {
        val chapters = listOf(
            ChapterInfoDto("Opening Credits", 0),
            ChapterInfoDto("Episode", ticks(90)),
            ChapterInfoDto("End Credits", ticks(1_700)),
        )
        val segments = chapterSkipSegments(chapters, ticks(1_800))
        assertEquals(SkipRange(0.0, 90.0), segments?.intro)
        assertEquals(SkipRange(1_700.0, 1_800.0), segments?.credits)
        assertNull(chapterSkipSegments(listOf(ChapterInfoDto("Chapter 1", 0)), ticks(1_800)))
        assertNull(chapterSkipSegments(null, ticks(1_800)))
    }
}
