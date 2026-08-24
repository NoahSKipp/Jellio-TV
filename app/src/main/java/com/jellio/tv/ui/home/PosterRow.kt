package com.jellio.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioSecondary

// Mirrors components/row.js: a title (the real library/Continue
// Watching name, not an invented category) over a horizontally
// scrolling real-content track.
@Composable
fun PosterRow(
    section: HomeSection,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    onItemOptions: ((BaseItemDto) -> Unit)? = null,
) {
    if (section.items.isEmpty()) return
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        RowTitle(text = section.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(section.items, key = { it.Id }) { item ->
                PosterCard(
                    item = item,
                    imageUrl = imageUrl,
                    onClick = { onItemClick(item) },
                    onOptionsClick = onItemOptions?.let { { it(item) } },
                )
            }
        }
    }
}

// Mirrors css/app.css's own .jellio-row-title/::after, shared by
// every real row heading (PosterRow, StudioHubRow, ComingSoonRow):
// a short accent bar under the title, not a full width rule, real
// feedback found on an earlier attempt that a border-bottom always
// spans an element's own full width, no way to make it read as short
// without this.
@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 48.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 48.dp, top = 8.dp, bottom = 12.dp)
                .width(38.dp)
                .height(3.dp)
                .background(JellioSecondary, RoundedCornerShape(999.dp)),
        )
    }
}
