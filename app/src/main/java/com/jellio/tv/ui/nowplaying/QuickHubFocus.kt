package com.jellio.tv.ui.nowplaying

import androidx.compose.ui.focus.FocusRequester

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
