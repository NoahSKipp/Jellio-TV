package com.jellio.tv.ui.common

import com.jellio.tv.data.playback.OutlookReason
import com.jellio.tv.data.playback.OutlookTone
import com.jellio.tv.data.playback.PlaybackOutlook
import com.jellio.tv.ui.i18n.Strings

fun OutlookReason.displayText(): String = Strings.get(
    when (this) {
        OutlookReason.CODEC -> "outlook_reason_codec"
        OutlookReason.HDR_ON_SDR -> "outlook_reason_hdr_on_sdr"
        OutlookReason.DOLBY_VISION -> "outlook_reason_dolby_vision"
        OutlookReason.INTERLACED -> "outlook_reason_interlaced"
        OutlookReason.IMAGE_SUBTITLES -> "outlook_reason_image_subtitles"
        OutlookReason.OTHER -> "outlook_reason_other"
    },
)

fun PlaybackOutlook.displayText(): String {
    val reasonText = (reason ?: OutlookReason.OTHER).displayText()
    return when (tone) {
        OutlookTone.Good -> Strings.get(if (guessed) "outlook_likely_smooth" else "outlook_smooth")
        OutlookTone.Warn -> Strings.format(if (guessed) "outlook_may_stutter" else "outlook_likely_stutter", reasonText)
        OutlookTone.Block -> Strings.format(if (guessed) "outlook_probably_wont_play" else "outlook_wont_play", reasonText)
    }
}
