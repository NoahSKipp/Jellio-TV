package com.jellio.tv.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// A row inside a vertically scrolling screen. When the screen builds the
// row ahead of time, it builds every card that will be on screen, not
// Compose's default two: the rest used to be composed inside the frames
// of the scroll animation, which is what made scrolling between rows
// stutter on the Google TV Streamer while moving without a scroll was
// smooth.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTvRowState(): LazyListState =
    rememberLazyListState(prefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = 9) })

// The vertical list of rows: keeps a full screen of rows below and half a
// screen above built while idle, so scrolling to a row further down never
// has to compose it (and start its images) inside the scroll animation.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTvColumnState(): LazyListState =
    rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = 0.5f) })

// Moving into a row from above or below lands on the card last used in
// that row, or its first card, instead of whichever card happened to sit
// closest on screen. Put rowEntry on the LazyRow and RowEntryTarget
// around the first item.
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
