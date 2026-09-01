package com.jellio.tv.ui.live

import com.jellio.tv.data.model.StreamLinkDto
import com.jellio.tv.data.model.StreamLinkEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLinksTest {
    private val links = listOf(
        StreamLinkDto(
            Id = "a1",
            Name = "Red Bull TV",
            SourceUrl = "https://rb.example/live",
            Kind = "stream",
            Description = "Extreme sports",
            ImageUrl = "https://rb.example/i.png",
            SiteName = "Red Bull",
            Quality = "1080p",
            Health = "failing",
        ),
        StreamLinkDto(
            Id = "p1",
            Name = "Free channels",
            SourceUrl = "https://pl.example/list.m3u",
            Kind = "playlist",
            Entries = listOf(
                StreamLinkEntryDto(Name = "Sport1 Germany", Url = "https://a/1.m3u8", Group = "Sports", Logo = "https://a/1.png"),
                StreamLinkEntryDto(Name = "France 24", Url = "https://a/2.m3u8", Group = "News", Logo = "http://a/2.png"),
            ),
        ),
    )

    @Test
    fun channelIdsRoundTrip() {
        assertEquals("link:a1", linkChannelId("a1", null))
        assertEquals("link:p1:3", linkChannelId("p1", 3))
        assertEquals(LinkChannelRef("a1", null), parseLinkChannelId("link:a1"))
        assertEquals(LinkChannelRef("p1", 3), parseLinkChannelId("link:p1:3"))
        assertEquals(LinkChannelRef("p1", null), parseLinkChannelId("link:p1:x"))
        assertNull(parseLinkChannelId("dlhd.1"))
        assertNull(parseLinkChannelId("link:"))
        assertNull(parseLinkChannelId(null))
    }

    @Test
    fun flatteningExpandsPlaylistsAndClassifies() {
        val flat = flattenStreamLinks(links)
        assertEquals(listOf("link:a1", "link:p1:0", "link:p1:1"), flat.map { it.id })
        assertEquals("Red Bull", flat[0].group)
        assertEquals("Extreme sports", flat[0].description)
        assertEquals("1080p", flat[0].quality)
        assertEquals("https://rb.example/i.png", flat[0].logoUrl)
        assertTrue(flat[0].failing)
        assertNull(flat[0].entryIndex)
        assertEquals(1, flat[2].entryIndex)
        assertTrue(!flat[1].failing)
        assertEquals(LiveKind.SPORTS, flat[1].kind)
        assertEquals("German", flat[1].language)
        assertEquals("https://a/1.png", flat[1].logoUrl)
        // Plain http logos are dropped.
        assertNull(flat[2].logoUrl)
        assertTrue(flat.all { it.userStream && !it.favorite && it.now == null })
    }

    @Test
    fun searchMatchesGroups() {
        val flat = flattenStreamLinks(links)
        assertEquals(listOf("France 24"), filterChannels(flat, "news").map { it.name })
        assertEquals(listOf("Sport1 Germany"), filterChannels(flat, "", LiveFilters(kind = LiveKind.SPORTS)).map { it.name })
    }

    @Test
    fun errorsMapToStrings() {
        assertEquals("live_link_error_blocked", streamLinkErrorKey("blocked_address"))
        assertEquals("live_link_error_none", streamLinkErrorKey("no_stream_found"))
        assertEquals("live_link_error_failed", streamLinkErrorKey(null))
    }

    @Test
    fun qrEncodesPairingAddress() {
        val matrix = encodeQr("https://jellyfin.example.com/Jellio/add?c=ABC234")
        assertNotNull(matrix)
        assertTrue(matrix!!.width >= 21 && matrix.width == matrix.height)
    }
}
