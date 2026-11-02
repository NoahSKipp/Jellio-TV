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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
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
    // Focus target for the whole row (enters at its last focused card).
    rowFocusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    // Coming back to Home: this card takes focus once, then onFocusRestored.
    restoreItemId: String? = null,
    onFocusRestored: () -> Unit = {},
) {
    val rowItems = remember(section.items) { section.items.distinctBy { it.Id }.take(20) }
    if (rowItems.isEmpty()) return
    Column(modifier = modifier.padding(bottom = RowSpacing)) {
        RowTitle(text = section.title)
        val firstCard = remember { androidx.compose.ui.focus.FocusRequester() }
        val rowState = rememberTvRowState()
        ReportRowScrolling(rowState)
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvHorizontalBringIntoViewSpec()) {
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = RowStartPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = (if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
                    .rowEntry(firstCard),
            ) {
                items(rowItems, key = { it.Id }, contentType = { "poster_card" }) { item ->
                    val onCardClick = remember(item, onItemClick) { { onItemClick(item) } }
                    val onCardOptions = remember(item, onItemOptions) { onItemOptions?.let { opts -> { opts(item) } } }
                    val restoreHere = item.Id == restoreItemId
                    val restoreRequester = if (restoreHere) remember { androidx.compose.ui.focus.FocusRequester() } else null
                    if (restoreRequester != null) {
                        LaunchedEffect(Unit) {
                            runCatching { restoreRequester.requestFocus() }
                            onFocusRestored()
                        }
                    }
                    RowEntryTarget(if (item.Id == rowItems.first().Id) firstCard else null) {
                        PosterCard(
                            modifier = if (restoreRequester != null) Modifier.focusRequester(restoreRequester) else Modifier,
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

@Composable
fun RowTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = RowTitleFontSize,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = JellioText,
        maxLines = 1,
        modifier = modifier.padding(start = RowStartPadding, bottom = 14.dp),
    )
}

internal val RowStartPadding = 52.dp
internal val RowSpacing = 24.dp
internal val RowTitleFontSize = 16.sp

// Space kept above a focused row's cards so its title stays in view.
internal val RowHeaderHeight = 40.dp

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
                contentDescription = com.jellio.tv.ui.i18n.Strings.get("action_see_all"),
                tint = JellioText,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = com.jellio.tv.ui.i18n.string("action_see_all"),
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
