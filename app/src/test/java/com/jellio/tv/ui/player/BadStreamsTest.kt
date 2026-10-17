package com.jellio.tv.ui.player

import com.jellio.tv.data.model.MediaSourceDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadStreamsTest {
    @Test
    fun keysUseIdAndLines2PlusNeverLine1Header() {
        val streamA = MediaSourceDto(
            Id = "src-a",
            Name = "🚀 FHD\nShow.S01E01.1080p.WEB-DL-GroupA.mkv\n⚡ RealDebrid | 1.8 GB",
        )
        val streamB = MediaSourceDto(
            Id = "src-b",
            Name = "🚀 FHD\nShow.S01E01.1080p.WEB-DL-GroupB.mkv\n⚡ RealDebrid | 2.1 GB",
        )
        val keysA = BadStreams.keys("ep-1", streamA)
        val keysB = BadStreams.keys("ep-1", streamB)

        assertEquals(
            listOf(
                "ep-1|id:src-a",
                "ep-1|desc:Show.S01E01.1080p.WEB-DL-GroupA.mkv\n⚡ RealDebrid | 1.8 GB",
            ),
            keysA,
        )
        assertTrue("Distinct streams sharing line 1 must not share any BadStreams keys", keysA.intersect(keysB.toSet()).isEmpty())
    }

    @Test
    fun singleLineNameOnlyKeysById() {
        val singleLine = MediaSourceDto(Id = "src-1", Name = "🚀 4K")
        assertEquals(listOf("ep-1|id:src-1"), BadStreams.keys("ep-1", singleLine))
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
