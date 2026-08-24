package com.jellio.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

private val LandscapeCardWidth = 300.dp
private const val TICKS_PER_SECOND = 10_000_000L

// Mirrors PosterRow, real Continue Watching/Up Next shape instead of
// the plain poster one: components/card.js's own buildCard() dispatch
// (options.continueWatching || options.upNext) picks buildLandscapeCard
// over its own plain buildCard the exact same way.
@Composable
fun LandscapeRow(
    section: HomeSection,
    rawImageUrl: (String, String?, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
) {
    if (section.items.isEmpty()) return
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        RowTitle(text = section.title, onClick = onTitleClick)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(section.items, key = { it.Id }) { item ->
                LandscapeCard(item = item, imageUrl = rawImageUrl, onClick = { onItemClick(item) })
            }
        }
    }
}

// Real port of components/card.js's own landscapeImageUrl(): Backdrop
// art fits a 16:9 box far better than a poster does (portrait key art
// cropped into a wide box loses most of it), so a real Backdrop tag
// wins first when there is one; an Episode never carries one at all
// (confirmed against a real server, same real constraint
// screens/detail.js's own heroBackdropUrl() documents), so its own
// real still (Primary) is next, then the season/series' own real
// Thumb, then its own real Backdrop, the same real
// ParentThumbItemId/ParentThumbImageTag and ParentBackdropItemId/
// ParentBackdropImageTags fallback that file's own buildEpisodeCard()
// already uses.
private fun landscapeImageUrl(item: BaseItemDto, imageUrl: (String, String?, String, Int) -> String): String? {
    item.BackdropImageTags?.firstOrNull()?.let { return imageUrl(item.Id, it, "Backdrop", 500) }
    item.ImageTags?.get("Primary")?.let { return imageUrl(item.Id, it, "Primary", 500) }
    val parentThumbId = item.ParentThumbItemId
    val parentThumbTag = item.ParentThumbImageTag
    if (parentThumbId != null && parentThumbTag != null) return imageUrl(parentThumbId, parentThumbTag, "Thumb", 500)
    val parentBackdropId = item.ParentBackdropItemId
    val parentBackdropTag = item.ParentBackdropImageTags?.firstOrNull()
    if (parentBackdropId != null && parentBackdropTag != null) return imageUrl(parentBackdropId, parentBackdropTag, "Backdrop", 500)
    return null
}

// Real port of components/card.js's own remainingLabel(): blank
// rather than "0m left" for an Up Next item, which never carries a
// real PlaybackPositionTicks at all, matching real Harbor/Nuvio
// reference, only a title actually mid playback shows a real
// remaining time at all.
private fun remainingLabel(item: BaseItemDto): String {
    val runTicks = item.RunTimeTicks ?: return ""
    val posTicks = item.UserData?.PlaybackPositionTicks ?: return ""
    if (runTicks <= 0 || posTicks <= 0) return ""
    val remainingTicks = runTicks - posTicks
    if (remainingTicks <= 0) return ""
    val minutes = Math.round(remainingTicks / TICKS_PER_SECOND.toDouble() / 60.0)
    return if (minutes > 0) "${minutes}m left" else ""
}

// Real Harbor/Nuvio reference (screenshots checked before writing
// this on the web side): Up Next and Continue Watching read as a
// landscape strip, title and episode burned directly onto the bottom
// of a 16:9 still with a remaining-time badge and a real progress bar
// under it, not the plain 2:3 poster shape every other row uses.
// Real port of components/card.js's own buildLandscapeCard().
@Composable
fun LandscapeCard(
    item: BaseItemDto,
    imageUrl: (String, String?, String, Int) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item.Type == "Episode" && !item.SeriesName.isNullOrEmpty()
    val hasSeason = item.ParentIndexNumber != null
    val hasEpisode = item.IndexNumber != null
    val eyebrow = if (isEpisode && hasSeason && hasEpisode) "S${item.ParentIndexNumber} E${item.IndexNumber}" else ""
    val remaining = remainingLabel(item)
    val percentage = item.UserData?.PlayedPercentage

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        modifier = modifier.width(LandscapeCardWidth),
    ) {
        Box(modifier = Modifier.width(LandscapeCardWidth).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            val url = landscapeImageUrl(item, imageUrl)
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(JellioBg))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            )
            if (remaining.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp)),
                ) {
                    Text(
                        text = remaining,
                        color = JellioText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                if (eyebrow.isNotEmpty()) {
                    Text(
                        text = eyebrow,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
                Text(
                    text = if (isEpisode) item.SeriesName ?: "" else item.Name ?: "",
                    color = JellioText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isEpisode && !item.Name.isNullOrEmpty()) {
                    Text(
                        text = item.Name ?: "",
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (percentage != null && percentage > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(minOf(100.0, percentage).toFloat() / 100f)
                            .height(3.dp)
                            .background(JellioSecondary),
                    )
                }
            }
        }
    }
}
