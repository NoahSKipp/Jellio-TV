package com.jellio.tv.data.playback

import android.app.Activity
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import com.jellio.tv.data.model.MediaSourceDto

// The server's "Video transcode policy" (Jellio plugin settings): Allow
// keeps the old behaviour; Avoid and Block keep the server from
// re-encoding video.
object VideoTranscodePolicy {
    @Volatile
    var policy: String = "Allow"
        private set

    @Volatile
    private var loadedAt = 0L

    fun set(value: String?) {
        policy = if (value == "Avoid" || value == "Block") value else "Allow"
        loadedAt = System.currentTimeMillis()
    }

    fun isFresh(): Boolean = System.currentTimeMillis() - loadedAt < 5 * 60_000L

    val avoiding: Boolean get() = policy != "Allow"
    val blocking: Boolean get() = policy == "Block"

    fun classify(source: MediaSourceDto, burnInSubtitle: Boolean = false): Classification =
        classifyVideo(source, DeviceDecoderCaps, burnInSubtitle)

    fun needsReencode(source: MediaSourceDto): Boolean = classify(source).needsVideoTranscode
}

enum class OutlookTone { Good, Warn, Block }

data class PlaybackOutlook(val tone: OutlookTone, val text: String)

private fun shortReason(reason: String?): String = when (reason) {
    TranscodeReason.CODEC -> "a video format this TV can't play"
    TranscodeReason.HDR_ON_SDR -> "HDR on a non-HDR screen"
    TranscodeReason.DOLBY_VISION_5 -> "Dolby Vision only"
    TranscodeReason.INTERLACED -> "interlaced video"
    TranscodeReason.BURN_IN -> "image subtitles"
    else -> "needs converting"
}

// What a viewer cares about: will this play smoothly here? Guesses from a
// stream's name are worded less certainly than probed details.
fun playbackOutlook(classification: Classification, policy: String): PlaybackOutlook {
    val guessed = classification.guessed
    if (!classification.needsVideoTranscode) {
        return PlaybackOutlook(OutlookTone.Good, if (guessed) "Likely plays smoothly" else "Plays smoothly")
    }
    val reason = shortReason(classification.reasons.firstOrNull())
    return if (policy == "Block") {
        PlaybackOutlook(OutlookTone.Block, (if (guessed) "Probably won't play: " else "Won't play: ") + reason)
    } else {
        PlaybackOutlook(OutlookTone.Warn, (if (guessed) "May stutter: " else "Likely to stutter: ") + reason)
    }
}

// What this TV's decoders and screen handle, asked once.
object DeviceDecoderCaps : DecoderCaps {
    private const val AV1_PROFILE_MAIN_10 = 2

    private val mimeTypes = mapOf(
        "h264" to "video/avc",
        "hevc" to "video/hevc",
        "av1" to "video/av01",
        "vp9" to "video/x-vnd.on2.vp9",
        "vp8" to "video/x-vnd.on2.vp8",
        "mpeg4" to "video/mp4v-es",
        "mpeg2video" to "video/mpeg2",
    )

    private val decoders by lazy {
        runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder } }.getOrDefault(emptyList())
    }

    private val answers = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var hdr = false

    fun attach(activity: Activity) {
        hdr = runCatching {
            @Suppress("DEPRECATION")
            val display = if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display?.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        }.getOrDefault(false)
    }

    override val hdrDisplay: Boolean get() = hdr

    override val dolbyVision: Boolean by lazy { hasDecoder("video/dolby-vision") }

    private fun hasDecoder(mime: String, profile: Int? = null): Boolean = decoders.any { info ->
        info.supportedTypes.any { it.equals(mime, ignoreCase = true) } && (
            profile == null || runCatching {
                info.getCapabilitiesForType(mime).profileLevels.any { it.profile == profile }
            }.getOrDefault(false)
            )
    }

    override fun canDecode(codec: String, tenBit: Boolean): Boolean = answers.getOrPut("$codec/$tenBit") {
        val mime = mimeTypes[codec] ?: return@getOrPut false
        if (!tenBit) return@getOrPut hasDecoder(mime)
        when (codec) {
            "h264" -> hasDecoder(mime, CodecProfileLevel.AVCProfileHigh10)
            "hevc" -> hasDecoder(mime, CodecProfileLevel.HEVCProfileMain10)
            "vp9" -> hasDecoder(mime, CodecProfileLevel.VP9Profile2)
            "av1" -> hasDecoder(mime, AV1_PROFILE_MAIN_10)
            else -> false
        }
    }
}
