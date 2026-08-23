package com.jellio.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto

// Mirrors components/row.js: a title (the real library/Continue
// Watching name, not an invented category) over a horizontally
// scrolling real-content track.
@Composable
fun PosterRow(section: HomeSection, imageUrl: (BaseItemDto, String, Int) -> String, modifier: Modifier = Modifier) {
    if (section.items.isEmpty()) return
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(section.items, key = { it.Id }) { item ->
                PosterCard(item = item, imageUrl = imageUrl)
            }
        }
    }
}
