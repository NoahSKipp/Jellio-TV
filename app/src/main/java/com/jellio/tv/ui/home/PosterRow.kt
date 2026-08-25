package com.jellio.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.scaled

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
        RowTitle(text = section.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Real feedback live: a focusable heading blocked ordinary
            // Left/Right between cards in the same row, the nearest
            // focusable candidate above a row's own first card being
            // the title itself rather than nothing. Real feedback also
            // asked for the header back to plain, unfocusable text. A
            // real button at the row's own start, same shape and
            // height a real card here has, replaces it instead of
            // dropping "view all" entirely.
            if (onTitleClick != null) {
                item {
                    RowExpandButton(onClick = onTitleClick, height = PosterWidth.scaled() * 1.5f)
                }
            }
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

// Mirrors css/app.css's own .jellio-row-title/::after, shared by every
// real row heading (PosterRow, StudioHubRow, ComingSoonRow,
// LandscapeRow): a short accent bar under the title, not a full width
// rule, real feedback found on an earlier attempt that a border-bottom
// always spans an element's own full width, no way to make it read as
// short without this. Plain, unfocusable text now: RowExpandButton
// below carries whatever real "view all" action this used to.
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

// Real port of components/rowListModal.js's own makeRowTitleClickable():
// the same "view all" action this used to hang off the row's own
// title (real feedback asked for that back to plain, unfocusable
// text), moved to a real focusable button of its own instead, first
// in the row rather than fighting the title for the same bounds. A
// list icon over a bare chevron: this button is the only real
// indicator left that a "view everything in this row" screen exists
// at all, not just a decoration next to one.
@Composable
fun RowExpandButton(onClick: () -> Unit, height: Dp, modifier: Modifier = Modifier) {
    val width = RowExpandButtonWidth.scaled()
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        modifier = modifier.width(width).height(height),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "View all",
                tint = JellioText,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private val RowExpandButtonWidth = 64.dp
