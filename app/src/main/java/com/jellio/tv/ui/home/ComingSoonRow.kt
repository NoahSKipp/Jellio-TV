package com.jellio.tv.ui.home

import com.jellio.tv.ui.common.rowEntry
import com.jellio.tv.ui.common.RowEntryTarget
import com.jellio.tv.ui.common.rememberTvRowState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone


private val ISO_FORMATS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd",
)

private fun parseEntryDate(raw: String?): Date? {
    if (raw == null) return null
    for (pattern in ISO_FORMATS) {
        val format = SimpleDateFormat(pattern, Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val parsed = runCatching { format.parse(raw) }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

// Mirrors screens/home.js's own comingSoonDateLabel() exactly:
// Today/Tomorrow/In N days for anything close, a short real date past
// that.
private fun comingSoonDateLabel(entry: CalendarEntryDto): String {
    val date = parseEntryDate(entry.Date) ?: return ""
    val diffDays = ((date.time - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()
    return when {
        diffDays <= 0 -> "Today"
        diffDays == 1 -> "Tomorrow"
        diffDays < 7 -> "In $diffDays days"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}

private fun comingSoonKindLabel(entry: CalendarEntryDto): String {
    val strings = com.jellio.tv.ui.i18n.Strings
    return if (entry.Kind == "episode") {
        val label = strings.get("calendar_new_episode")
        if (!entry.Detail.isNullOrEmpty()) "$label \u00B7 ${entry.Detail}" else label
    } else {
        strings.get("calendar_digital_release")
    }
}

// Mirrors screens/home.js's own buildComingSoonRow()/buildComingSoonCard():
// real GET Jellio/calendar answer, a handful of cards wide, right on
// the front page rather than behind Calendar's own full screen only.
// A dedicated card rather than PosterCard: CalendarController's own
// real response carries no playable MediaSource, progress or
// watchlist state to show actions for, only Id/Name/Kind/Detail/Date,
// every one of them wrong to fake off a real BaseItemDto shape this
// never needed anyway.
@Composable
fun ComingSoonRow(
    entries: List<CalendarEntryDto>,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    // With the entries' library items these are the regular cards: focus
    // frame, widening, and the header following them.
    items: Map<String, com.jellio.tv.data.model.BaseItemDto> = emptyMap(),
    itemImageUrl: ((com.jellio.tv.data.model.BaseItemDto, String, Int) -> String)? = null,
    onItemFocus: ((com.jellio.tv.data.model.BaseItemDto) -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    Column(modifier = modifier.padding(bottom = RowSpacing)) {
        RowTitle(text = "Coming Soon")
        val firstCard = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides com.jellio.tv.ui.common.rememberTvHorizontalBringIntoViewSpec()) {
            LazyRow(
                state = rememberTvRowState(),
                contentPadding = PaddingValues(horizontal = RowStartPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.rowEntry(firstCard),
            ) {
                items(entries, key = { it.ItemId + (it.Date ?: "") }) { entry ->
                    RowEntryTarget(if (entry == entries.first()) firstCard else null) {
                        val item = items[entry.ItemId.replace("-", "").lowercase()]
                        if (item != null && itemImageUrl != null) {
                            PosterCard(
                                item = item,
                                imageUrl = itemImageUrl,
                                onClick = { onItemClick(entry.ItemId) },
                                onFocus = onItemFocus,
                                badge = comingSoonDateLabel(entry),
                            )
                        } else {
                            ComingSoonCard(entry = entry, imageUrl = imageUrl, onClick = { onItemClick(entry.ItemId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComingSoonCard(
    entry: CalendarEntryDto,
    imageUrl: (String, String?, String, Int) -> String,
    onClick: () -> Unit,
) {
    // Same size as the poster rows so the row lines up with the others.
    val cardWidth = PosterWidth.scaled()
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.width(cardWidth),
    ) {
        Box {
            AsyncImage(
                model = imageUrl(entry.ItemId, null, "Primary", 400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(cardWidth).aspectRatio(2f / 3f),
            )
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(JellioBg.copy(alpha = 0.85f), RoundedCornerShape(999.dp)),
            ) {
                Text(
                    text = comingSoonDateLabel(entry),
                    color = com.jellio.tv.ui.theme.JellioRarityLegendary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(cardWidth)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, JellioBg.copy(alpha = 0.92f))))
                    .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
            ) {
                Text(text = entry.Name ?: "", color = JellioText, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = comingSoonKindLabel(entry),
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
