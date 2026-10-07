package com.jellio.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaStreamDto
import com.jellio.tv.ui.theme.JellioTextSecondary

// Real port of screens/detail.js's own buildMediaTechBadges():
// extracts video resolution (4K UHD, 1080p, 720p), HDR profile (Dolby Vision,
// HDR10+, HDR10), codec (HEVC, AV1), and audio capabilities (Dolby Atmos,
// DTS:X, TrueHD, DTS-HD, 7.1, 5.1, Stereo) from MediaStreams.
enum class BadgeCategory {
    RES, HDR, CODEC, AUDIO
}

data class TechBadge(
    val label: String,
    val category: BadgeCategory,
)

fun extractMediaTechBadges(item: BaseItemDto?): List<TechBadge> {
    if (item == null) return emptyList()
    val streams: List<MediaStreamDto> = item.MediaStreams
        ?: item.MediaSources?.firstOrNull()?.MediaStreams
        ?: emptyList()
    if (streams.isEmpty()) return emptyList()

    val videoStream = streams.firstOrNull { it.Type.equals("Video", ignoreCase = true) }
    val audioStream = streams.firstOrNull { it.Type.equals("Audio", ignoreCase = true) && it.IsDefault == true }
        ?: streams.firstOrNull { it.Type.equals("Audio", ignoreCase = true) }

    val badges = mutableListOf<TechBadge>()

    if (videoStream != null) {
        val width = videoStream.Width ?: 0
        val height = videoStream.Height ?: 0
        if (width >= 3600 || height >= 2000) {
            badges.add(TechBadge("4K UHD", BadgeCategory.RES))
        } else if (width >= 1800 || height >= 900) {
            badges.add(TechBadge("1080p", BadgeCategory.RES))
        } else if (width >= 1200 || height >= 700) {
            badges.add(TechBadge("720p", BadgeCategory.RES))
        }

        val range = (videoStream.VideoRange ?: "").uppercase()
        val rangeType = (videoStream.VideoRangeType ?: "").uppercase()
        val dispTitle = (videoStream.DisplayTitle ?: "").uppercase()
        val title = (videoStream.Title ?: "").uppercase()

        if (rangeType.contains("DOVI") || range.contains("DOVI") || dispTitle.contains("VISION") || title.contains("VISION")) {
            badges.add(TechBadge("Dolby Vision", BadgeCategory.HDR))
        } else if (rangeType.contains("HDR10+") || range.contains("HDR10+") || dispTitle.contains("HDR10+")) {
            badges.add(TechBadge("HDR10+", BadgeCategory.HDR))
        } else if (range.contains("HDR") || rangeType.contains("HDR") || dispTitle.contains("HDR")) {
            badges.add(TechBadge("HDR10", BadgeCategory.HDR))
        }

        val codec = (videoStream.Codec ?: "").uppercase()
        if (codec == "HEVC" || codec == "H265") {
            badges.add(TechBadge("HEVC", BadgeCategory.CODEC))
        } else if (codec == "AV1") {
            badges.add(TechBadge("AV1", BadgeCategory.CODEC))
        }
    }

    if (audioStream != null) {
        val titleCombined = ((audioStream.Title ?: "") + " " + (audioStream.DisplayTitle ?: "") + " " + (audioStream.Profile ?: "")).uppercase()
        val audioCodec = (audioStream.Codec ?: "").uppercase()
        val channels = audioStream.Channels ?: 0
        val channelLayout = (audioStream.ChannelLayout ?: "").uppercase()

        if (titleCombined.contains("ATMOS")) {
            badges.add(TechBadge("Dolby Atmos", BadgeCategory.AUDIO))
        } else if (titleCombined.contains("DTS:X") || titleCombined.contains("DTS-X")) {
            badges.add(TechBadge("DTS:X", BadgeCategory.AUDIO))
        } else if (titleCombined.contains("TRUEHD") || audioCodec == "TRUEHD") {
            badges.add(TechBadge("TrueHD", BadgeCategory.AUDIO))
        } else if (titleCombined.contains("DTS-HD") || (audioCodec.contains("DTS") && titleCombined.contains("HD"))) {
            badges.add(TechBadge("DTS-HD", BadgeCategory.AUDIO))
        } else if (channels >= 8 || channelLayout.contains("7.1")) {
            badges.add(TechBadge("7.1", BadgeCategory.AUDIO))
        } else if (channels >= 6 || channelLayout.contains("5.1")) {
            badges.add(TechBadge("5.1", BadgeCategory.AUDIO))
        } else if (channels == 2 || channelLayout.contains("STEREO")) {
            badges.add(TechBadge("Stereo", BadgeCategory.AUDIO))
        }
    }

    return badges
}

@Composable
fun MediaTechBadgesRow(
    item: BaseItemDto?,
    modifier: Modifier = Modifier,
) {
    val badges = extractMediaTechBadges(item)
    if (badges.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badges.forEach { badge ->
            TechBadgePill(badge)
        }
    }
}

@Composable
fun TechBadgePill(
    badge: TechBadge,
    modifier: Modifier = Modifier,
) {
    val borderColor = when (badge.category) {
        BadgeCategory.HDR -> Color(0xFFD4AF37).copy(alpha = 0.5f)
        BadgeCategory.RES -> Color.White.copy(alpha = 0.25f)
        BadgeCategory.AUDIO -> Color.White.copy(alpha = 0.25f)
        BadgeCategory.CODEC -> Color.White.copy(alpha = 0.2f)
    }

    val textColor = when (badge.category) {
        BadgeCategory.HDR -> Color(0xFFF1D779)
        else -> JellioTextSecondary
    }

    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}
