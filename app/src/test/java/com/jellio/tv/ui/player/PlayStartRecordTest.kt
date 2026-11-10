package com.jellio.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayStartRecordTest {
    private fun run(from: String, vararg marks: PlayStartMark) = PlayStartRun("item", from, 1_000, marks.toList())

    @Test
    fun stagesCountFromPlayerOpenAndTotalFromRunStart() {
        val record = playStartRecord(
            run(
                "title page",
                PlayStartMark("play target", 1_200, "remembered stream"),
                PlayStartMark("player open", 1_500, "source=abc"),
                PlayStartMark("details", 1_700, ""),
                PlayStartMark("playback info", 2_100, "from=prefetched direct=true"),
                PlayStartMark("stream url ready", 2_150),
                PlayStartMark("first byte", 2_400),
                PlayStartMark("player ready", 2_900),
                PlayStartMark("first frame", 3_000),
            ),
            mediaSourceId = "abc",
            directPlay = true,
            warmAtPlay = true,
            startPositionTicks = 0,
        )!!
        assertEquals("item", record.ItemId)
        assertEquals("abc", record.MediaSourceId)
        assertEquals("tv", record.Client)
        assertEquals("detail", record.Trigger)
        assertEquals("prefetched", record.From)
        assertEquals(true, record.WarmAtPlay)
        assertEquals(true, record.DirectPlay)
        assertEquals(200, record.Stages.DetailsMs)
        assertEquals(600, record.Stages.PlaybackInfoMs)
        assertEquals(1_400, record.Stages.PlayerReadyMs)
        assertEquals(900, record.Stages.FirstByteMs)
        assertEquals(1_500, record.Stages.FirstFrameMs)
        assertEquals(1_500, record.TotalMs)
    }

    @Test
    fun missingStagesStayNullAndFromDefaultsToFresh() {
        val record = playStartRecord(
            run("player", PlayStartMark("player open", 1_000, "source=default"), PlayStartMark("first frame", 4_000)),
            mediaSourceId = null,
            directPlay = null,
            warmAtPlay = null,
            startPositionTicks = 0,
        )!!
        assertEquals("other", record.Trigger)
        assertEquals("fresh", record.From)
        assertNull(record.Stages.DetailsMs)
        assertNull(record.Stages.PlaybackInfoMs)
        assertNull(record.Stages.FirstByteMs)
        assertEquals(3_000, record.Stages.FirstFrameMs)
        assertEquals(3_000, record.TotalMs)
    }

    @Test
    fun triggers() {
        fun trigger(from: String, openDetail: String, startTicks: Long) = playStartRecord(
            run(from, PlayStartMark("player open", 1_000, openDetail), PlayStartMark("first frame", 2_000)),
            null, null, null, startTicks,
        )!!.Trigger
        assertEquals("next-episode", trigger("next episode", "source=default", 0))
        assertEquals("resume", trigger("player", "source=default", 5_000_000))
        assertEquals("detail", trigger("title page", "source=default", 5_000_000))
        assertEquals("failover", trigger("title page", "failover", 0))
    }

    @Test
    fun failoverIsMeasuredFromItsOwnPlayerOpen() {
        val record = playStartRecord(
            run(
                "title page",
                PlayStartMark("player open", 1_100, "source=a"),
                PlayStartMark("details", 1_300),
                PlayStartMark("playback info", 1_500, "from=fresh direct=true"),
                PlayStartMark("first byte", 1_700),
                PlayStartMark("player open", 5_000, "failover"),
                PlayStartMark("details", 5_100),
                PlayStartMark("playback info", 5_400, "from=early direct=false"),
                PlayStartMark("first frame", 6_000),
            ),
            "b", false, null, 0,
        )!!
        assertEquals("failover", record.Trigger)
        assertEquals("early", record.From)
        assertEquals(100, record.Stages.DetailsMs)
        assertEquals(400, record.Stages.PlaybackInfoMs)
        assertNull(record.Stages.FirstByteMs)
        assertEquals(1_000, record.Stages.FirstFrameMs)
        assertEquals(1_000, record.TotalMs)
    }

    @Test
    fun noRecordWithoutPlayerOpenOrFirstFrame() {
        assertNull(playStartRecord(run("title page", PlayStartMark("first frame", 2_000)), null, null, null, 0))
        assertNull(playStartRecord(run("player", PlayStartMark("player open", 2_000)), null, null, null, 0))
    }

    @Test
    fun traceKeepsMarksAndClosesOnce() {
        PlayStartTrace.begin("title page")
        PlayStartTrace.markOnce("first byte")
        PlayStartTrace.mark("x", "player open", "source=default")
        PlayStartTrace.markOnce("first byte")
        PlayStartTrace.markOnce("first byte")
        val run = PlayStartTrace.end("first frame")!!
        assertEquals("x", run.itemId)
        assertEquals("title page", run.begunFrom)
        assertEquals(listOf("player open", "first byte", "first frame"), run.marks.map { it.step })
        assertNull(PlayStartTrace.end("first frame"))
    }
}
