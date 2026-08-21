package com.jellio.tv.ui.home

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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val CardWidth = 170.dp

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

private fun comingSoonKindLabel(entry: CalendarEntryDto): String =
    if (entry.Kind == "episode") {
        if (!entry.Detail.isNullOrEmpty()) entry.Detail else "New episode"
    } else {
        "Digital release"
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
) {
    if (entries.isEmpty()) return
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        RowTitle(text = "Coming Soon")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(entries, key = { it.ItemId + (it.Date ?: "") }) { entry ->
                ComingSoonCard(entry = entry, imageUrl = imageUrl, onClick = { onItemClick(entry.ItemId) })
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
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        modifier = Modifier.width(CardWidth),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = imageUrl(entry.ItemId, null, "Primary", 400),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(CardWidth).aspectRatio(2f / 3f),
                )
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.BottomStart)
                        .background(JellioBg.copy(alpha = 0.85f), RoundedCornerShape(999.dp)),
                ) {
                    Text(
                        text = comingSoonDateLabel(entry),
                        color = JellioText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = entry.Name ?: "", color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = comingSoonKindLabel(entry),
                    color = JellioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
