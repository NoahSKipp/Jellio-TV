package com.jellio.tv.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTvRowState(): LazyListState =
    rememberLazyListState(prefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = 9) })

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTvColumnState(): LazyListState =
    rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f) })

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTvGridState(): LazyGridState =
    rememberLazyGridState(cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f) })

class GridFocusController {
    private val requesters = HashMap<Int, FocusRequester>()
    private val composedIndices = HashSet<Int>()

    fun requesterFor(index: Int): FocusRequester =
        requesters.getOrPut(index) { FocusRequester() }

    fun markComposed(index: Int) {
        composedIndices.add(index)
    }

    fun markDisposed(index: Int) {
        composedIndices.remove(index)
    }

    fun composedRequesterOrDefault(targetIndex: Int): FocusRequester =
        if (targetIndex in composedIndices) requesterFor(targetIndex) else FocusRequester.Default
}

@Composable
fun rememberGridFocusController(): GridFocusController =
    remember { GridFocusController() }

@Composable
fun RegisterGridFocusItem(controller: GridFocusController, index: Int) {
    DisposableEffect(controller, index) {
        controller.markComposed(index)
        onDispose { controller.markDisposed(index) }
    }
}

fun Modifier.gridColumnFocus(
    controller: GridFocusController,
    index: Int,
    totalCount: Int,
    columnCount: Int,
    allowExitUp: Boolean = false,
    allowExitLeft: Boolean = false,
): Modifier {
    if (totalCount <= 0 || columnCount <= 0) return this
    val requester = controller.requesterFor(index)
    val row = index / columnCount
    val col = index % columnCount
    val lastRow = (totalCount - 1) / columnCount
    return this
        .focusRequester(requester)
        .focusProperties {
            up = when {
                row > 0 -> controller.composedRequesterOrDefault(index - columnCount)
                allowExitUp -> FocusRequester.Default
                else -> FocusRequester.Cancel
            }
            down = when {
                row < lastRow -> controller.composedRequesterOrDefault(minOf(index + columnCount, totalCount - 1))
                else -> FocusRequester.Cancel
            }
            left = when {
                col > 0 -> controller.composedRequesterOrDefault(index - 1)
                allowExitLeft -> FocusRequester.Default
                else -> FocusRequester.Cancel
            }
            right = when {
                col < columnCount - 1 && index + 1 < totalCount -> controller.composedRequesterOrDefault(index + 1)
                else -> FocusRequester.Cancel
            }
        }
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.rowEntry(first: FocusRequester): Modifier = focusRestorer(first)

@androidx.compose.runtime.Composable
fun RowEntryTarget(first: FocusRequester?, content: @androidx.compose.runtime.Composable () -> Unit) {
    if (first == null) {
        content()
    } else {
        Box(Modifier.focusRequester(first)) { content() }
    }
}
