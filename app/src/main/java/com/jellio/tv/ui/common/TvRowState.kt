package com.jellio.tv.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

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

// The vertical list of rows: keeps about one row below and one above
// composed, so a D-pad move that scrolls brings in a row that is
// already built.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTvColumnState(): LazyListState =
    rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = 420.dp, behind = 320.dp) })
