package com.jellio.tv.ui.nowplaying

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border

/**
 * Focus and selection model of the Quick Hub.
 *
 * Levels
 * 1. The hub: a closed focus group. Left at its left edge closes it (the root
 *    `onExit`), Back leaves a sub-state first (the friends add list) and then
 *    closes it. Root key handler and `BackHandler` share one `onBack`.
 * 2. The tab row: one pill per visible tab. The selected pill is the single
 *    owner of "tab row focus".
 * 3. The active tab body: a nested focus group with a [FocusRequester] and
 *    `focusRestorer()`. It holds sections, buttons and rows.
 *
 * Selection
 * - `selectedTab` is state of the hub and is decoupled from focus. A tab is
 *   selected only by clicking its pill (Enter/OK), by Left/Right between pills
 *   (the pill row handles the key, selects the neighbour, focuses its pill and
 *   consumes the event; Left at the first pill is not consumed so the hub
 *   closes, Right at the last pill is a no-op), or programmatically
 *   (`initialTab`, a notification that opens Group Watch, `friendsAvailable`
 *   turning false).
 * - A pill that is not the selected tab and receives focus by any other route
 *   hands focus to the selected pill. It never selects.
 *
 * Recovery
 * - Focus that was in the hub and is now nowhere (a focused node was removed)
 *   is recovered by one routine: first the active body (the restorer returns
 *   the last focused child if it still exists, otherwise the first focusable
 *   child), and only when the body stays empty the selected pill. It retries
 *   briefly because the removed node and the new layout settle in different
 *   frames, and a limiter stops it from looping. It never focuses a
 *   non-selected pill, never selects a tab and never leaves the hub.
 *
 * Predictable removals
 * - Removals the user triggers (dismiss a card, answer a request or invite,
 *   dismiss a paused card) move focus first, in the same click handler and
 *   before the state change: to the next row, else the previous row, else the
 *   body's header action ([QuickHubRowFocus]). Removals nobody triggers (a
 *   session ends, a list reloads) fall to recovery.
 *
 * Highlight
 * - Three states, defined once in [QuickHubHighlight]: selected (tab pill is
 *   filled and shows its label), focused (white ring plus slight scale, on
 *   every focusable in the hub) and plain. The selected pill stays visibly
 *   selected while focus is in its body.
 *
 * Ownership
 * - The hub (`NowPlayingDrawer`) owns selection, recovery and the tab row.
 *   Tab bodies own their rows' requesters and header actions and only ever
 *   request focus on elements inside themselves.
 */

// One requester per tab pill. The hub attaches each to its pill.
class QuickHubTabFocus {
    private val nowPlaying = FocusRequester()
    private val notifications = FocusRequester()
    private val groupWatch = FocusRequester()
    private val friends = FocusRequester()

    fun requesterFor(tab: QuickHubTab): FocusRequester = when (tab) {
        QuickHubTab.NOW_PLAYING -> nowPlaying
        QuickHubTab.NOTIFICATIONS -> notifications
        QuickHubTab.GROUP_WATCH -> groupWatch
        QuickHubTab.FRIENDS -> friends
    }
}

// A stable requester per row key. A row attaches its requester to its first
// button; a removal moves focus to the neighbour before the state changes.
class QuickHubRowFocus {
    private val requesters = HashMap<String, FocusRequester>()

    fun requesterFor(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun modifierFor(key: String): Modifier = Modifier.focusRequester(requesterFor(key))

    // `keys` is the displayed order. The next row takes focus, else the previous
    // one, else `header`. A requester whose row is not composed is skipped.
    fun moveAfterRemoval(keys: List<String>, removed: String, header: FocusRequester? = null) {
        val target = quickHubFocusAfterRemoval(keys, removed)?.let { requesters[it] }
        if (target != null && runCatching { target.requestFocus() }.isSuccess) return
        if (header != null) runCatching { header.requestFocus() }
    }
}

@Composable
fun rememberQuickHubRowFocus(): QuickHubRowFocus = remember { QuickHubRowFocus() }

// The three highlight states, defined once: plain, selected (filled tab pill
// with its label) and focused (white ring plus a slight scale).
object QuickHubHighlight {
    val PillShape = RoundedCornerShape(999.dp)
    const val FocusScale = 1.02f
    const val RowFocusScale = 1f
    const val SelectedFillAlpha = 0.18f
    const val SelectedBorderAlpha = 0.25f
    const val PrimaryRestAlpha = 0.86f

    fun focusBorder(shape: Shape): Border = Border(border = BorderStroke(2.dp, Color.White), shape = shape)
}
