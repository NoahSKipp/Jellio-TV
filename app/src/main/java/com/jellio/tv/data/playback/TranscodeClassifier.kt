package com.jellio.tv.data.playback

import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.MediaStreamDto

// Whether playing a source here makes the server re-encode its video, the
// expensive part of a transcode on a server without hardware acceleration.
// Same rules as the web player's runtime/transcodeClassifier.js. Pure, so
// tests can pass any DecoderCaps.

interface DecoderCaps {
    fun canDecode(codec: String, tenBit: Boolean): Boolean
    val dolbyVision: Boolean
    val hdrDisplay: Boolean
}

data class VideoInfo(
    val codec: String?,
    val bitDepth: Int,
    val rangeType: String,
    val dvProfile: Int? = null,
    val interlaced: Boolean = false,
    // Read from the stream's name: Gelato hasn't probed it yet.
    val guessed: Boolean = false,
)

enum class PlayMethod(val label: String) { DirectPlay("Direct play"), Remux("Remux"), Transcode("Transcode") }

data class Classification(
    val needsVideoTranscode: Boolean,
    val reasons: List<String>,
    val guessed: Boolean,
)

object TranscodeReason {
    const val CODEC = "codec"
    const val HDR_ON_SDR = "hdr-on-sdr"
    const val DOLBY_VISION_5 = "dolby-vision-5"
    const val INTERLACED = "interlaced"
    const val BURN_IN = "burn-in"

    fun describe(reason: String): String = when (reason) {
        CODEC -> "this TV can't decode the video"
        HDR_ON_SDR -> "HDR on a non-HDR screen"
        DOLBY_VISION_5 -> "Dolby Vision without a fallback"
        INTERLACED -> "interlaced video"
        BURN_IN -> "an image subtitle"
        else -> reason
    }
}

private val HDR_RANGES = setOf(
    "HDR10", "HDR10Plus", "HLG", "DOVI", "DOVIWithHDR10", "DOVIWithHLG", "DOVIWithHDR10Plus",
    "DOVIWithEL", "DOVIWithELHDR10Plus", "DOVIInvalid",
)

fun normalizeVideoCodec(codec: String?): String? = when (val c = codec?.lowercase()?.trim()) {
    null, "" -> null
    "avc", "h264" -> "h264"
    "h265", "hevc" -> "hevc"
    else -> c
}

private fun MediaStreamDto.toVideoInfo(): VideoInfo {
    val profile = Profile.orEmpty().lowercase()
    val depth = BitDepth ?: if (profile.contains("10")) 10 else 8
    return VideoInfo(
        codec = normalizeVideoCodec(Codec),
        bitDepth = depth,
        rangeType = VideoRangeType ?: "SDR",
        dvProfile = DvProfile,
        interlaced = IsInterlaced == true,
    )
}

private val SEPARATORS = Regex("[|·•,()\\[\\]{}/_\\s-]+")

// AIOStreams names say what's inside ("4K HEVC DV HDR10"). Null when the
// name says nothing about the video.
fun videoHintFromName(name: String?): VideoInfo? {
    val text = " " + name.orEmpty().replace(SEPARATORS, " ") + " "
    fun has(pattern: String) = Regex("\\s(?:$pattern)\\s", RegexOption.IGNORE_CASE).containsMatchIn(text)
    val codec = when {
        has("hevc|[xh]\\.?265") -> "hevc"
        has("av1") -> "av1"
        has("vp9") -> "vp9"
        has("avc|[xh]\\.?264") -> "h264"
        else -> null
    }
    val dv = has("dv|dovi|dolby\\s?vision")
    var range = when {
        Regex("hdr10(\\+|plus)", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "HDR10Plus"
        has("hdr(?:10)?") -> "HDR10"
        has("hlg") -> "HLG"
        else -> null
    }
    if (dv) {
        range = when (range) {
            "HDR10Plus" -> "DOVIWithHDR10Plus"
            "HDR10" -> "DOVIWithHDR10"
            "HLG" -> "DOVIWithHLG"
            else -> "DOVI"
        }
    }
    if (codec == null && range == null) return null
    val tenBit = range != null || has("10\\s?bit")
    return VideoInfo(codec = codec, bitDepth = if (tenBit) 10 else 8, rangeType = range ?: "SDR", guessed = true)
}

fun videoInfoOf(source: MediaSourceDto): VideoInfo? =
    source.MediaStreams.orEmpty().firstOrNull { it.Type == "Video" }?.toVideoInfo()
        ?: videoHintFromName(source.Name)

fun classifyVideo(source: MediaSourceDto, caps: DecoderCaps, burnInSubtitle: Boolean = false): Classification {
    val reasons = mutableListOf<String>()
    if (burnInSubtitle) reasons += TranscodeReason.BURN_IN
    val video = videoInfoOf(source) ?: return Classification(reasons.isNotEmpty(), reasons, guessed = false)

    // A name without a codec says nothing about decoding it.
    val codec = video.codec
    if (codec != null || !video.guessed) {
        if (codec == null || !caps.canDecode(codec, video.bitDepth > 8)) reasons += TranscodeReason.CODEC
    }

    // Profile 5 has no HDR10 or SDR base layer to fall back to.
    val dolbyVisionOnly = video.rangeType == "DOVI" || video.rangeType == "DOVIInvalid" || video.dvProfile == 5
    if (dolbyVisionOnly) {
        if (!caps.dolbyVision) reasons += TranscodeReason.DOLBY_VISION_5
    } else if (video.rangeType in HDR_RANGES && !caps.hdrDisplay) {
        reasons += TranscodeReason.HDR_ON_SDR
    }

    if (video.interlaced) reasons += TranscodeReason.INTERLACED
    return Classification(reasons.isNotEmpty(), reasons, video.guessed)
}

fun playMethodFor(classification: Classification, directPlayable: Boolean): PlayMethod = when {
    classification.needsVideoTranscode -> PlayMethod.Transcode
    directPlayable -> PlayMethod.DirectPlay
    else -> PlayMethod.Remux
}

// Codecs a stream request can name so the server copies the video: h264
// first (what it encodes to when it has to), then what this device decodes.
fun copyableVideoCodecs(caps: DecoderCaps): List<String> = buildList {
    add("h264")
    if (caps.canDecode("hevc", false)) add("hevc")
    if (caps.canDecode("av1", false)) add("av1")
    if (caps.canDecode("vp9", false)) add("vp9")
}
