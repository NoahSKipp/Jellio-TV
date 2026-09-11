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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RowListModal(
    title: String,
    items: List<BaseItemDto>,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    onDismiss: () -> Unit,
    fetchAll: (suspend () -> List<BaseItemDto>)? = null,
    fetchPage: (suspend (Int, Int) -> List<BaseItemDto>)? = null,
) {
    BackHandler(onBack = onDismiss)
    var displayedItems by remember(items) { mutableStateOf(items) }
    var isLoadingRest by remember(items) { mutableStateOf(fetchAll != null || fetchPage != null) }
    // Paged rows load ROW_PAGE_SIZE titles at a time, the next page once the
    // focus nears the end, until the server runs out.
    var rawNextStart by remember(items) { mutableStateOf(items.size) }
    var consecutiveEmptyPages by remember(items) { mutableStateOf(0) }
    var exhausted by remember(items) { mutableStateOf(fetchPage == null) }
    var columns by remember { mutableStateOf(1) }
    var initialFocusRequested by remember(items) { mutableStateOf(false) }
    val listFocusRequester = remember { FocusRequester() }
    val gridState = rememberTvGridState()
    val focusController = rememberGridFocusController()
    suspend fun loadNextPage() {
        val page = fetchPage ?: return
        if (exhausted) return
        isLoadingRest = true
        while (!exhausted) {
            val start = rawNextStart
            val result = runCatching { page(start, ROW_PAGE_SIZE) }.getOrNull()
            if (result == null) {
                break
            }
            rawNextStart = start + ROW_PAGE_SIZE
            if (result.isEmpty()) {
                exhausted = true
                break
            }
            val existingIds = displayedItems.mapTo(hashSetOf()) { it.Id }
            val existingKeys = displayedItems.mapTo(hashSetOf()) { com.jellio.tv.data.recommend.titleKey(it) }
            val fresh = result.filter { item ->
                existingIds.add(item.Id) && existingKeys.add(com.jellio.tv.data.recommend.titleKey(item))
            }
            if (fresh.isNotEmpty()) {
                consecutiveEmptyPages = 0
                displayedItems = displayedItems + fresh
                break
            } else {
                consecutiveEmptyPages += 1
                if (consecutiveEmptyPages >= 2) {
                    exhausted = true
                    break
                }
            }
        }
        isLoadingRest = false
    }
    LaunchedEffect(items, fetchPage) {
        if (fetchPage == null) return@LaunchedEffect
        if (displayedItems.isEmpty()) {
            loadNextPage()
        }
        // Keep loading while the focus sits within two rows of the end.
        androidx.compose.runtime.snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= displayedItems.size - columns * 2 && !exhausted && !isLoadingRest
        }.collect { wantMore -> if (wantMore) loadNextPage() }
    }
    LaunchedEffect(items, fetchAll) {
        if (fetchAll == null || fetchPage != null) return@LaunchedEffect
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
                text = when {
                    isLoadingRest -> com.jellio.tv.ui.i18n.Strings.format("row_titles_loading_more", displayedItems.size)
                    !exhausted -> com.jellio.tv.ui.i18n.Strings.format("row_titles_plus", displayedItems.size)
                    else -> com.jellio.tv.ui.i18n.Strings.format("row_titles_count", displayedItems.size)
                },
                color = JellioTextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val scaledPosterWidth = PosterWidth.scaled()
                val spacing = 16.dp
                val columnCount = maxOf(1, ((maxWidth + spacing) / (scaledPosterWidth + spacing)).toInt())
                columns = columnCount
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

internal const val ROW_PAGE_SIZE = 60
