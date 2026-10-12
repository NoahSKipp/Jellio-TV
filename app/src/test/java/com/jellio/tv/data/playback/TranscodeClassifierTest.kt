package com.jellio.tv.data.playback

import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.MediaStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscodeClassifierTest {
    private class Caps(
        private val codecs: Set<String>,
        private val tenBit: Set<String> = emptySet(),
        override val dolbyVision: Boolean = false,
        override val hdrDisplay: Boolean = false,
    ) : DecoderCaps {
        override fun canDecode(codec: String, tenBit: Boolean): Boolean =
            codec in codecs && (!tenBit || codec in this.tenBit)
    }

    // A typical 4K Google TV box: HEVC/VP9/AV1 incl. 10-bit, no DV decoder.
    private val box = Caps(setOf("h264", "hevc", "vp9", "av1"), setOf("hevc", "vp9", "av1"))
    private val oldBox = Caps(setOf("h264"))

    private fun source(video: MediaStreamDto?, name: String? = null) =
        MediaSourceDto(Name = name, MediaStreams = listOfNotNull(video, MediaStreamDto(Type = "Audio", Codec = "eac3")))

    private fun video(codec: String, depth: Int = 8, range: String = "SDR", dvProfile: Int? = null, interlaced: Boolean = false) =
        MediaStreamDto(Type = "Video", Codec = codec, BitDepth = depth, VideoRangeType = range, DvProfile = dvProfile, IsInterlaced = interlaced)

    private data class Case(val label: String, val video: MediaStreamDto, val caps: DecoderCaps, val reasons: List<String>)

    private val cases = listOf(
        Case("h264 SDR", video("h264"), oldBox, emptyList()),
        Case("h264 10-bit", video("h264", 10), box, listOf("codec")),
        Case("hevc without a decoder", video("hevc"), oldBox, listOf("codec")),
        Case("hevc SDR", video("hevc"), box, emptyList()),
        Case("hevc HDR10 on SDR screen", video("hevc", 10, "HDR10"), box, listOf("hdr-on-sdr")),
        Case("hevc HDR10 on HDR screen", video("hevc", 10, "HDR10"), Caps(setOf("hevc"), setOf("hevc"), hdrDisplay = true), emptyList()),
        Case("HLG on SDR screen", video("hevc", 10, "HLG"), box, listOf("hdr-on-sdr")),
        Case("DV with HDR10 on HDR screen", video("hevc", 10, "DOVIWithHDR10"), Caps(setOf("hevc"), setOf("hevc"), hdrDisplay = true), emptyList()),
        Case("DV profile 5 without DV decoder", video("hevc", 10, "DOVI"), Caps(setOf("hevc"), setOf("hevc"), hdrDisplay = true), listOf("dolby-vision-5")),
        Case("DV profile 5 with DV decoder", video("hevc", 10, "DOVI"), Caps(setOf("hevc"), setOf("hevc"), dolbyVision = true, hdrDisplay = true), emptyList()),
        Case("DV profile 5 by DvProfile", video("hevc", 10, "DOVIWithHDR10", dvProfile = 5), Caps(setOf("hevc"), setOf("hevc"), hdrDisplay = true), listOf("dolby-vision-5")),
        Case("av1 10-bit", video("av1", 10), box, emptyList()),
        Case("vp9 10-bit on a box without profile 2", video("vp9", 10), Caps(setOf("h264", "vp9")), listOf("codec")),
        Case("interlaced h264", video("h264", interlaced = true), box, listOf("interlaced")),
        Case("mpeg2", video("mpeg2video"), box, listOf("codec")),
        Case("hevc HDR10 with neither", video("hevc", 10, "HDR10"), oldBox, listOf("codec", "hdr-on-sdr")),
    )

    @Test
    fun classifiesTable() {
        for (case in cases) {
            val result = classifyVideo(source(case.video), case.caps)
            assertEquals(case.label, case.reasons, result.reasons)
            assertEquals(case.label, case.reasons.isNotEmpty(), result.needsVideoTranscode)
        }
    }

    @Test
    fun burnInNeedsTranscode() {
        assertEquals(listOf("burn-in"), classifyVideo(source(video("h264")), box, burnInSubtitle = true).reasons)
    }

    @Test
    fun readsHintsFromStreamNames() {
        assertEquals(VideoInfo("hevc", 10, "DOVIWithHDR10", guessed = true), videoHintFromName("⚡ RD 2160p\nHEVC | DV | HDR10"))
        assertEquals(VideoInfo("hevc", 10, "DOVI", guessed = true), videoHintFromName("4K WEB-DL DV x265"))
        assertEquals(VideoInfo("h264", 8, "SDR", guessed = true), videoHintFromName("1080p BluRay x264"))
        assertEquals(VideoInfo("hevc", 10, "HDR10Plus", guessed = true), videoHintFromName("2160p HDR10+ H.265"))
        assertEquals(VideoInfo("av1", 10, "SDR", guessed = true), videoHintFromName("1080p AV1 10bit"))
        assertNull(videoHintFromName("Torrentio 1080p"))
        assertNull(videoHintFromName(null))
    }

    @Test
    fun unprobedSourceUsesItsName() {
        val dv5 = classifyVideo(MediaSourceDto(Name = "WEB-DL DV HEVC"), box)
        assertEquals(listOf("dolby-vision-5"), dv5.reasons)
        assertEquals(true, dv5.guessed)
        val hdrNoCodec = classifyVideo(MediaSourceDto(Name = "HDR"), oldBox)
        assertEquals(listOf("hdr-on-sdr"), hdrNoCodec.reasons)
        val unknown = classifyVideo(MediaSourceDto(Name = "Torrentio 1080p"), oldBox)
        assertEquals(false, unknown.needsVideoTranscode)
    }

    @Test
    fun probedStreamWinsOverName() {
        val result = classifyVideo(source(video("h264"), name = "HEVC DV"), oldBox)
        assertEquals(false, result.needsVideoTranscode)
        assertEquals(false, result.guessed)
    }

    @Test
    fun playMethodAndCopyableCodecs() {
        assertEquals(PlayMethod.DirectPlay, playMethodFor(Classification(false, emptyList(), false), true))
        assertEquals(PlayMethod.Remux, playMethodFor(Classification(false, emptyList(), false), false))
        assertEquals(PlayMethod.Transcode, playMethodFor(Classification(true, listOf("codec"), false), true))
        assertEquals(listOf("h264", "hevc", "av1", "vp9"), copyableVideoCodecs(box))
        assertEquals(listOf("h264"), copyableVideoCodecs(oldBox))
    }
}
