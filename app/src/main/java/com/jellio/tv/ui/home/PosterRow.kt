package com.jellio.tv.ui.home

import com.jellio.tv.ui.common.rowEntry
import com.jellio.tv.ui.common.RowEntryTarget
import com.jellio.tv.ui.common.rememberTvRowState
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import com.jellio.tv.ui.common.rememberTvHorizontalBringIntoViewSpec

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
    userImageUrl: ((String, String?) -> String)? = null,
    onItemFocus: ((BaseItemDto) -> Unit)? = null,
    // Continue Watching / Up Next look, and the card mid removal (see
    // HomeScreen's removingTarget).
    progress: Boolean = false,
    removingItemId: String? = null,
    onShatterFinished: (BaseItemDto) -> Unit = {},
) {
    val rowItems = remember(section.items) { section.items.distinctBy { it.Id }.take(20) }
    if (rowItems.isEmpty()) return
    Column(modifier = modifier.padding(top = RowTopPadding, bottom = 8.dp)) {
        RowTitle(text = section.title)
        val firstCard = remember { androidx.compose.ui.focus.FocusRequester() }
        val rowState = rememberTvRowState()
        ReportRowScrolling(rowState)
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvHorizontalBringIntoViewSpec()) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.rowEntry(firstCard),
            ) {
                // Real feedback live: a focusable heading blocked ordinary
                // Left/Right between cards in the same row, the nearest
                // focusable candidate above a row's own first card being
                // the title itself rather than nothing. Real feedback also
                // asked for the header back to plain, unfocusable text. A
                // real button at the row's own start, same shape and
                // height a real card here has, replaces it instead of
                // dropping "view all" entirely.
                items(rowItems, key = { it.Id }, contentType = { "poster_card" }) { item ->
                    val onCardClick = remember(item, onItemClick) { { onItemClick(item) } }
                    val onCardOptions = remember(item, onItemOptions) { onItemOptions?.let { opts -> { opts(item) } } }
                    RowEntryTarget(if (item.Id == rowItems.first().Id) firstCard else null) {
                        PosterCard(
                            item = item,
                            imageUrl = imageUrl,
                            onClick = onCardClick,
                            onOptionsClick = onCardOptions,
                            userImageUrl = userImageUrl,
                            canExpand = true,
                            onFocus = onItemFocus,
                            progress = progress,
                            isRemoving = removingItemId == item.Id,
                            onShatterFinished = { onShatterFinished(item) },
                        )
                    }
                }
                if (onTitleClick != null && (section.items.size > 20 || section.fetchAll != null)) {
                    item {
                        RowExpandButton(onClick = onTitleClick, height = (PosterWidth * 1.5f).scaled())
                    }
                }
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
            fontSize = RowTitleFontSize,
            fontWeight = FontWeight.SemiBold,
            color = JellioText,
            maxLines = 1,
            modifier = Modifier.padding(start = 48.dp),
        )
        Box(
            modifier = Modifier
                .padding(start = 48.dp, top = 6.dp, bottom = 12.dp)
                .width(32.dp)
                .height(3.dp)
                .background(JellioSecondary, RoundedCornerShape(999.dp)),
        )
    }
}

// Space above a focused row's cards on Home (row top padding, title line,
// accent bar): Home keeps the focused cards this far below the showcase so
// the title stays visible.
internal val RowTopPadding = 8.dp
internal val RowTitleFontSize = 20.sp
internal val RowHeaderHeight = 60.dp

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
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.width(width).height(height),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ViewList,
                contentDescription = "View all",
                tint = JellioText,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelLarge,
                color = JellioText,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private val RowExpandButtonWidth = 120.dp

@Composable
internal fun ReportRowScrolling(state: androidx.compose.foundation.lazy.LazyListState) {
    androidx.compose.runtime.LaunchedEffect(state) {
        var counted = false
        try {
            androidx.compose.runtime.snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
                if (scrolling != counted) {
                    com.jellio.tv.ui.perf.ScrollActivity.rowsScrolling += if (scrolling) 1 else -1
                    counted = scrolling
                }
            }
        } finally {
            if (counted) com.jellio.tv.ui.perf.ScrollActivity.rowsScrolling -= 1
        }
    }
}
