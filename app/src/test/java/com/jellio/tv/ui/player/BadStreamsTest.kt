package com.jellio.tv.ui.player

import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.streams.BadKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadStreamsTest {
    @Test
    fun keysAddTheReleaseNameLikeWeb() {
        val release = "🚀 FHD\nShow.S01E01.1080p.WEB-DL-GroupA.mkv\n⚡ RealDebrid | 1.8 GB"
        val streamA = MediaSourceDto(Id = "src-a", Name = release)
        val streamB = MediaSourceDto(Id = "src-b", Name = release)

        assertEquals(listOf("ep-1|id:src-a", "ep-1|release:$release"), BadStreams.keys("ep-1", streamA))
        assertEquals(listOf("ep-1|id:src-b", "ep-1|release:$release"), BadStreams.keys("ep-1", streamB))
    }

    @Test
    fun singleLineNameOnlyKeysById() {
        val singleLine = MediaSourceDto(Id = "src-1", Name = "🚀 4K")
        assertEquals(listOf("ep-1|id:src-1"), BadStreams.keys("ep-1", singleLine))
    }

    @Test
    fun softMarksOnlyKeyBySourceId() {
        val source = MediaSourceDto(Id = "src-a", Name = "FHD\nShow.S01E01.mkv")
        assertEquals(listOf("ep-1|id:src-a"), BadStreams.markKeys("ep-1", source, soft = true))
        assertEquals(BadStreams.keys("ep-1", source), BadStreams.markKeys("ep-1", source, soft = false))
    }

    @Test
    fun slowStartsAndStallsAreSoftFailures() {
        assertTrue(BadStreams.isSoftFailure("no picture after 25s"))
        assertTrue(BadStreams.isSoftFailure(PlaceholderSign.STALLED_EARLY.reason))
        assertFalse(BadStreams.isSoftFailure("error before the first frame"))
        assertFalse(BadStreams.isSoftFailure(PlaceholderSign.SHORT_DURATION.reason))
        assertFalse(BadStreams.isSoftFailure("could not start: boom"))
    }

    @Test
    fun kindTellsHardFromSoftByRemainingLifetime() {
        val now = 1_000_000L
        assertNull(BadStreams.kindOf(emptyList(), now))
        assertNull(BadStreams.kindOf(listOf(0L, now), now))
        assertEquals(BadKind.SOFT, BadStreams.kindOf(listOf(now + BadStreams.SOFT_LIFETIME_MS), now))
        assertEquals(BadKind.HARD, BadStreams.kindOf(listOf(now + BadStreams.LIFETIME_MS), now))
        assertEquals(
            BadKind.HARD,
            BadStreams.kindOf(listOf(now + BadStreams.SOFT_LIFETIME_MS, now + BadStreams.LIFETIME_MS), now),
        )
    }

    @Test
    fun looksLikePlaceholderCapsAtThreeMinutes() {
        // 2-minute placeholder against 45-minute episode
        assertTrue(looksLikePlaceholder(120_000L, 45L * 60 * 1000))
        // 24-minute episode against inflated 120-minute runtime is NOT a placeholder
        assertFalse(looksLikePlaceholder(24L * 60 * 1000, 120L * 60 * 1000))
        // 5-minute clip exceeds the 180s cap
        assertFalse(looksLikePlaceholder(300_000L, 60L * 60 * 1000))
        // Short expected runtime (< 10m) is never flagged
        assertFalse(looksLikePlaceholder(60_000L, 5L * 60 * 1000))
    }

    @Test
    fun normalizeStreamContainerMapsProbedContainerStrings() {
        assertEquals("mkv", com.jellio.tv.data.normalizeStreamContainer("matroska,webm"))
        assertEquals("mkv", com.jellio.tv.data.normalizeStreamContainer("mkv"))
        assertEquals("mp4", com.jellio.tv.data.normalizeStreamContainer("mov,mp4,m4a,3gp,3g2,mj2"))
        assertEquals("mp4", com.jellio.tv.data.normalizeStreamContainer("mp4"))
        assertEquals("mp4", com.jellio.tv.data.normalizeStreamContainer(null))
    }
}
