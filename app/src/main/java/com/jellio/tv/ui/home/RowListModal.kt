package com.jellio.tv.ui.home

import com.jellio.tv.ui.common.dismissOnBack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.ui.common.RegisterGridFocusItem
import com.jellio.tv.ui.common.gridColumnFocus
import com.jellio.tv.ui.common.rememberGridFocusController
import com.jellio.tv.ui.common.rememberTvGridState
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled

// A row's "view all": every item the row loaded, as a full-screen poster
// grid (it used to be a vertical list), rendered by the calling screen
// at its own root the same way CardOptionsMenu/EpisodeOptionsMenu are.
//
// fetchAll mirrors that file's own options.fetchAll: a second,
// unbounded real request for this exact same row, swapped in only once
// it lands and only if it actually turned up more than the row's own
// already-loaded depth, same real guard that file's own
// `if (fullItems && fullItems.length > items.length) renderList(fullItems)`
// applies. Real web's own stale-reopen guard (a bumped openToken) has
// no Kotlin equivalent needed here: this composable's own
// LaunchedEffect is keyed on items, so Compose's own structured
// concurrency already cancels a still-resolving fetchAll from the
// previous row the instant a different row's own items replace it.
//
// Real bug found live testing on device: nothing here ever requested
// initial D-pad focus when this modal opened, and its own list had no
// default D-pad entry point of its own either (the same real
// lazy-layout gap the pill screens already hit), so a reader clicking
// a row's own title into this modal had no way to navigate its own
// list at all. Same real fix LibraryPickerOverlay already uses: claim
// focus on open, trap exit, and focusRestorer() on the grid itself so
// a directional search always finds something.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RowListModal(
    title: String,
    items: List<BaseItemDto>,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    onDismiss: () -> Unit,
    fetchAll: (suspend () -> List<BaseItemDto>)? = null,
) {
    BackHandler(onBack = onDismiss)
    var displayedItems by remember(items) { mutableStateOf(items) }
    var isLoadingRest by remember(items) { mutableStateOf(fetchAll != null) }
    var initialFocusRequested by remember(items) { mutableStateOf(false) }
    val listFocusRequester = remember { FocusRequester() }
    val gridState = rememberTvGridState()
    val focusController = rememberGridFocusController()
    LaunchedEffect(items, fetchAll) {
        if (fetchAll == null) return@LaunchedEffect
        val fullItems = runCatching { fetchAll() }.getOrNull()
        isLoadingRest = false
        if (fullItems != null) {
            val existingIds = items.mapTo(hashSetOf()) { it.Id }
            val merged = items + fullItems.filterNot { it.Id in existingIds }
            if (merged.size > items.size) {
                displayedItems = merged
            }
        }
    }
    LaunchedEffect(displayedItems.isNotEmpty()) {
        if (!initialFocusRequested && displayedItems.isNotEmpty()) {
            initialFocusRequested = true
            listFocusRequester.requestFocus()
        }
    }
    // A page of its own: the row's title over a poster grid, like a
    // library page, the rest filling in when fetchAll lands.
    val firstCard = focusController.requesterFor(0)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .background(JellioBg)
            .focusProperties { onExit = { FocusRequester.Cancel } },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 32.dp)) {
            Text(
                text = title,
                color = JellioText,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = if (isLoadingRest) "${displayedItems.size} titles, loading the rest…" else "${displayedItems.size} titles",
                color = JellioTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val scaledPosterWidth = PosterWidth.scaled()
                val spacing = 16.dp
                val columnCount = maxOf(1, ((maxWidth + spacing) / (scaledPosterWidth + spacing)).toInt())
                val shownItems = displayedItems
                val totalCount = shownItems.size
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnCount),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(listFocusRequester)
                        .focusRestorer(firstCard),
                ) {
                    items(totalCount, key = { "${shownItems[it].Id}-$it" }, contentType = { "poster_card" }) { index ->
                        val item = shownItems[index]
                        RegisterGridFocusItem(focusController, index)
                        PosterCard(
                            item = item,
                            imageUrl = imageUrl,
                            onClick = {
                                onDismiss()
                                onItemClick(item)
                            },
                            canExpand = false,
                            modifier = Modifier.gridColumnFocus(
                                controller = focusController,
                                index = index,
                                totalCount = totalCount,
                                columnCount = columnCount,
                                allowExitUp = false,
                                allowExitLeft = false,
                            ),
                        )
                    }
                    if (isLoadingRest) {
                        items(6, key = { "placeholder-$it" }, contentType = { "placeholder" }) {
                            SkeletonPoster()
                        }
                    }
                }
            }
        }
    }
}
