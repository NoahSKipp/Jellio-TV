package com.jellio.tv.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// Real hysteresis mirrors components/mobileNav.js's own real
// COMPACT_SCROLL_TOP/EXPAND_SCROLL_TOP pair (48px/12px rather than a
// literal 16px/4px, this app's own scroll units are raw device pixels
// rather than that file's own CSS px against a phone's own DPI, same
// real ratio kept between the two): a single shared threshold flickers
// compact/expanded back and forth for a scroll position sitting right
// on it, real feedback found live, twice over.
private const val COMPACT_THRESHOLD_PX = 48
private const val EXPAND_THRESHOLD_PX = 12

// Every non-immersive content screen's own top scrollable container
// reports its own real scroll position here (firstVisibleItemIndex/
// firstVisibleItemScrollOffset, the one pair both LazyListState and
// LazyGridState expose the exact same way), MainActivity threading the
// real Boolean this returns up to TopNavPill's own isCompact.
@Composable
fun rememberNavCompact(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int): Boolean {
    var compact by remember { mutableStateOf(false) }
    LaunchedEffect(firstVisibleItemIndex, firstVisibleItemScrollOffset) {
        compact = when {
            firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > COMPACT_THRESHOLD_PX -> true
            firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset < EXPAND_THRESHOLD_PX -> false
            else -> compact
        }
    }
    return compact
}
