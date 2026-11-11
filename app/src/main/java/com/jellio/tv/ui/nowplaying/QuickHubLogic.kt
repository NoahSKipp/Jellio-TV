package com.jellio.tv.ui.nowplaying

enum class QuickHubTab {
    NOW_PLAYING,
    NOTIFICATIONS,
    GROUP_WATCH,
    FRIENDS,
}

// Friends is hidden entirely when the server plugin has no friends feature.
fun visibleQuickHubTabs(friendsAvailable: Boolean): List<QuickHubTab> =
    QuickHubTab.entries.filter { it != QuickHubTab.FRIENDS || friendsAvailable }

fun resolveQuickHubTab(tab: QuickHubTab, friendsAvailable: Boolean): QuickHubTab =
    if (tab in visibleQuickHubTabs(friendsAvailable)) tab else QuickHubTab.NOW_PLAYING

// Back leaves the add-friend list first and closes the hub only from the friends list.
fun quickHubBackLeavesAddList(tab: QuickHubTab, adding: Boolean): Boolean = tab == QuickHubTab.FRIENDS && adding

const val PILL_WEIGHT_COMPACT = 1f
const val PILL_WEIGHT_EXPANDED = 2.2f

// Only the selected or focused tab shows its label; the others are icon + badge.
fun quickHubPillExpanded(selected: Boolean, focused: Boolean): Boolean = selected || focused

fun quickHubPillWeight(selected: Boolean, focused: Boolean): Float =
    if (quickHubPillExpanded(selected, focused)) PILL_WEIGHT_EXPANDED else PILL_WEIGHT_COMPACT

// Tabs select when a pill gains focus. When the focused button of a tab goes
// away focus can fall onto the first pill; a short guard after such a change
// keeps that from switching tabs.
const val FOCUS_SHIFT_GUARD_MS = 800L

fun quickHubMaySelectTab(nowMs: Long, blockedUntilMs: Long): Boolean = nowMs >= blockedUntilMs

// Focus that was in the hub and is now nowhere is brought back to the active tab.
fun quickHubShouldRecoverFocus(hadFocus: Boolean, hasFocus: Boolean): Boolean = hadFocus && !hasFocus
