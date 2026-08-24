package com.jellio.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

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
// every real row heading (PosterRow, StudioHubRow, ComingSoonRow,
// LandscapeRow): a short accent bar under the title, not a full width
// rule, real feedback found on an earlier attempt that a border-bottom
// always spans an element's own full width, no way to make it read as
// short without this.
//
// onClick mirrors components/rowListModal.js's own
// makeRowTitleClickable(): a real chevron and a real Surface around
// the whole heading, opening RowListModal at the calling screen's own
// root, same real reason DetailScreen's own EpisodeOptionsMenu is not
// rendered by EpisodeCard either. null (the default) keeps the exact
// same plain, non-clickable heading this had before that modal existed.
@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    if (onClick == null) {
        RowTitleBody(text = text, showChevron = false, modifier = modifier)
    } else {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = JellioText),
            modifier = modifier,
        ) {
            RowTitleBody(text = text, showChevron = true)
        }
    }
}

@Composable
private fun RowTitleBody(text: String, showChevron: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 48.dp)) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
            if (showChevron) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = JellioTextSecondary,
                    modifier = Modifier.padding(start = 2.dp).size(18.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 48.dp, top = 8.dp, bottom = 12.dp)
                .width(38.dp)
                .height(3.dp)
                .background(JellioSecondary, RoundedCornerShape(999.dp)),
        )
    }
}
