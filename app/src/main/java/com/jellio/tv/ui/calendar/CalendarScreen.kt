package com.jellio.tv.ui.calendar

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.ui.nav.rememberNavCompact
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
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

private fun dayKey(calendar: Calendar) = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)

// "Today"/"Tomorrow" read at a glance, the real weekday for anything
// within a week, a full date past that: mirrors screens/calendar.js's
// own dateHeading() exactly, same real reasoning ("a bare 'March 5'
// alone leaves a reader doing their own mental math").
private fun dateHeading(date: Date): String {
    val today = Calendar.getInstance()
    val target = Calendar.getInstance().apply { time = date }
    val diffDays = dayKey(target) - dayKey(today)
    return when {
        diffDays == 0 -> "Today"
        diffDays == 1 -> "Tomorrow"
        diffDays in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(date)
    }
}

private fun kindLabel(entry: CalendarEntryDto): String =
    if (entry.Kind == "episode") {
        if (!entry.Detail.isNullOrEmpty()) "New episode · ${entry.Detail}" else "New episode"
    } else {
        "Digital release"
    }

@Composable
fun CalendarScreen(
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onItemClick: (String) -> Unit,
    onCompactChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val compact = rememberNavCompact(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(compact) { onCompactChange(compact) }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading...", color = JellioTextSecondary)
            }
            uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = uiState.error ?: "Could not load the calendar", color = JellioTextSecondary)
            }
            uiState.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing upcoming yet. Anything on your Watchlist with a known air or digital release date shows up here.",
                    color = JellioTextSecondary,
                )
            }
            else -> {
                val groups = uiState.entries
                    .mapNotNull { entry -> parseEntryDate(entry.Date)?.let { it to entry } }
                    .sortedBy { it.first.time }
                    .groupBy { Calendar.getInstance().apply { time = it.first }.let(::dayKey) }
                    .toSortedMap()

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup()
                        .padding(top = 140.dp),
                ) {
                    item {
                        Text(
                            text = "Calendar",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 48.dp, bottom = 24.dp),
                        )
                    }
                    groups.values.forEach { entries ->
                        val date = entries.first().first
                        item {
                            Text(
                                text = dateHeading(date),
                                style = MaterialTheme.typography.titleMedium,
                                color = JellioText,
                                modifier = Modifier.padding(start = 48.dp, top = 16.dp, bottom = 8.dp),
                            )
                        }
                        items(entries) { (_, entry) ->
                            CalendarEntryRow(entry = entry, imageUrl = imageUrl, onClick = { onItemClick(entry.ItemId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEntryRow(
    entry: CalendarEntryDto,
    imageUrl: (String, String?, String, Int) -> String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 6.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = imageUrl(entry.ItemId, null, "Primary", 160),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 64.dp, height = 96.dp).padding(end = 16.dp),
            )
            Column {
                Text(text = entry.Name ?: "", color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = kindLabel(entry), color = JellioTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
