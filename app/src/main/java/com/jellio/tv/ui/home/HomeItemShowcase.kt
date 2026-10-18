package com.jellio.tv.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled
import java.util.Locale

// The focused card's full details, and its show when it is an episode.
data class ShowcaseInfo(val item: BaseItemDto, val series: BaseItemDto?)

private const val TICKS_PER_MINUTE = 10_000_000L * 60L
private val TmdbDark = Color(0xFF0D253F)
private val TmdbGreen = Color(0xFF90CEA1)
private val TmdbBlue = Color(0xFF01B4E4)

// Top of Home: the focused card's backdrop on the right, fading into the
// background towards the left where its logo, details and overview sit.
@Composable
fun HomeItemShowcase(
    item: BaseItemDto?,
    info: ShowcaseInfo?,
    imageUrl: (BaseItemDto, String, Int) -> String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().background(JellioBg)) {
        if (item == null) return@BoxWithConstraints
        val details = info?.item?.takeIf { it.Id == item.Id } ?: item
        val series = info?.series?.takeIf { info.item.Id == item.Id }
        val isEpisode = details.Type.equals("Episode", ignoreCase = true) || details.Type.equals("Season", ignoreCase = true)

        ShowcaseBackdrop(
            item = details,
            series = series,
            imageUrl = imageUrl,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.72f),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = 24.dp, end = 24.dp)
                .width(maxWidth * 0.46f),
        ) {
            ShowcaseLogo(
                item = details,
                series = series,
                title = if (isEpisode) details.SeriesName ?: series?.Name ?: details.Name.orEmpty() else details.Name.orEmpty(),
                imageUrl = imageUrl,
            )
            ShowcaseMetadata(details = details, series = series, isEpisode = isEpisode)
            val status = (series ?: details).Status
            if (status.equals("Continuing", ignoreCase = true)) {
                Text(
                    text = "ONGOING",
                    color = JellioText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            val overview = details.Overview?.takeIf { it.isNotBlank() } ?: series?.Overview?.takeIf { it.isNotBlank() }
            if (overview != null) {
                Text(
                    text = overview,
                    color = JellioText.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ShowcaseBackdrop(
    item: BaseItemDto,
    series: BaseItemDto?,
    imageUrl: (BaseItemDto, String, Int) -> String,
    modifier: Modifier,
) {
    val candidates = remember(item.Id, series?.Id) { posterBackdropCandidates(item, imageUrl, 1280, series = series) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val url = candidates.getOrNull(index) ?: return
    val context = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(300).build() }
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            drawRect(
                Brush.horizontalGradient(
                    0f to JellioBg,
                    0.18f to JellioBg.copy(alpha = 0.9f),
                    0.42f to JellioBg.copy(alpha = 0.55f),
                    0.68f to JellioBg.copy(alpha = 0.15f),
                    1f to Color.Transparent,
                    endX = size.width * 0.7f,
                ),
            )
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.8f to JellioBg.copy(alpha = 0.7f),
                    1f to JellioBg,
                ),
            )
        },
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            onError = { index += 1 },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ShowcaseLogo(
    item: BaseItemDto,
    series: BaseItemDto?,
    title: String,
    imageUrl: (BaseItemDto, String, Int) -> String,
) {
    val candidates = remember(item.Id, series?.Id) { posterLogoCandidates(item, imageUrl, 600, series = series) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val url = candidates.getOrNull(index)
    Box(contentAlignment = Alignment.BottomStart, modifier = Modifier.height(72.dp.scaled())) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onError = { index += 1 },
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 340.dp.scaled()),
            )
        } else {
            Text(
                text = title,
                color = JellioText,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// "Movie • Action • 2h 25m • 2026 • TMDB 8.0" on one line; an episode shows
// "S1 E3 • Episode name" in place of the type.
@Composable
private fun ShowcaseMetadata(details: BaseItemDto, series: BaseItemDto?, isEpisode: Boolean) {
    val parts = remember(details, series) {
        buildList {
            if (isEpisode) {
                val label = episodeLabel(details)
                if (label.isNotEmpty()) add(label)
                details.Name?.takeIf { it.isNotBlank() && label.isNotEmpty() }?.let { add(it) }
            } else {
                when (details.Type?.lowercase(Locale.US)) {
                    "movie" -> add("Movie")
                    "series" -> add("Series")
                    "boxset" -> add("Collection")
                }
            }
            (details.Genres?.firstOrNull() ?: series?.Genres?.firstOrNull())?.takeIf { it.isNotBlank() }?.let { add(it) }
            details.RunTimeTicks?.let { ticks ->
                val minutes = ticks / TICKS_PER_MINUTE
                if (minutes > 0) add(if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m")
            }
            val year = details.ProductionYear?.toString() ?: details.PremiereDate?.take(4)
            if (!year.isNullOrBlank()) {
                val ongoing = !isEpisode && details.Status.equals("Continuing", ignoreCase = true)
                add(if (ongoing) "$year–" else year)
            }
        }
    }
    val rating = details.CommunityRating ?: series?.CommunityRating?.takeIf { isEpisode }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        parts.forEachIndexed { index, part ->
            if (index > 0) MetaDot()
            Text(
                text = part,
                color = JellioTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = if (index == 1 && isEpisode) Modifier.weight(1f, fill = false) else Modifier,
            )
        }
        if (rating != null && rating > 0) {
            if (parts.isNotEmpty()) MetaDot()
            TmdbBadge()
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                color = JellioTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun MetaDot() {
    Text(text = "•", color = JellioTextSecondary.copy(alpha = 0.6f), fontSize = 13.sp, softWrap = false)
}

// Ratings here are TMDB's (Gelato imports them), same badge Jellio Web shows.
@Composable
private fun TmdbBadge() {
    Text(
        text = "TMDB",
        color = TmdbDark,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .background(Brush.horizontalGradient(listOf(TmdbGreen, TmdbBlue)), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
