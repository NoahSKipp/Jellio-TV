package com.jellio.tv.ui.common

import com.jellio.tv.ui.detail.youTubeId
import com.jellio.tv.ui.detail.youTubeThumbnails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeTest {
    @Test
    fun readsTheIdFromEveryLinkShape() {
        val id = "dQw4w9WgXcQ"
        for (url in listOf(
            "https://www.youtube.com/watch?v=$id",
            "http://youtube.com/watch?v=$id&t=10s",
            "https://m.youtube.com/watch?feature=share&v=$id",
            "https://www.youtube.com/embed/$id?autoplay=1",
            "https://www.youtube-nocookie.com/embed/$id",
            "https://youtu.be/$id",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "plugin://plugin.video.youtube/play/?videoid=$id",
        )) {
            assertEquals(url, id, youTubeId(url))
        }
    }

    @Test
    fun noIdForOtherLinks() {
        assertNull(youTubeId(null))
        assertNull(youTubeId(""))
        assertNull(youTubeId("https://vimeo.com/123456789"))
        assertNull(youTubeId("https://www.youtube.com/watch?v=short"))
        assertNull(youTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQextra"))
        assertNull(youTubeId("https://www.youtube.com/channel/UC1234567890"))
    }

    @Test
    fun thumbnailsTryHighThenMedium() {
        assertEquals(
            listOf("https://i.ytimg.com/vi/abc/hqdefault.jpg", "https://i.ytimg.com/vi/abc/mqdefault.jpg"),
            youTubeThumbnails("abc"),
        )
    }
}
