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

// Left and Right move between pills: the neighbouring visible tab, or null at either end.
fun quickHubNeighbourTab(visible: List<QuickHubTab>, current: QuickHubTab, step: Int): QuickHubTab? {
    val index = visible.indexOf(current)
    if (index < 0) return null
    return visible.getOrNull(index + step)
}

// A pill that is not the selected tab never keeps focus.
fun quickHubPillHandsBack(pill: QuickHubTab, selected: QuickHubTab): Boolean = pill != selected

// The row to focus when `removed` is about to leave `keys`: the next one, else the previous one.
fun <K> quickHubFocusAfterRemoval(keys: List<K>, removed: K): K? {
    val index = keys.indexOf(removed)
    if (index < 0) return null
    return keys.getOrNull(index + 1) ?: keys.getOrNull(index - 1)
}

enum class QuickHubRecoveryTarget { BODY, SELECTED_PILL }

const val QUICK_HUB_RECOVERY_FIRST_DELAY_MS = 120L
const val QUICK_HUB_RECOVERY_RETRY_MS = 60L
const val QUICK_HUB_RECOVERY_BODY_ATTEMPTS = 6
const val QUICK_HUB_RECOVERY_ATTEMPTS = 8

// The body gets the first attempts while its layout settles; only a body that
// stays empty hands focus to the selected pill.
fun quickHubRecoveryTarget(attempt: Int): QuickHubRecoveryTarget =
    if (attempt < QUICK_HUB_RECOVERY_BODY_ATTEMPTS) QuickHubRecoveryTarget.BODY else QuickHubRecoveryTarget.SELECTED_PILL

// Caps how often recovery may start, so two nodes that keep removing each other's focus cannot loop.
class QuickHubRecoveryLimiter(private val maxRuns: Int = 4, private val windowMs: Long = 2_000L) {
    private val starts = ArrayDeque<Long>()

    fun tryAcquire(nowMs: Long): Boolean {
        while (starts.isNotEmpty() && nowMs - starts.first() >= windowMs) starts.removeFirst()
        if (starts.size >= maxRuns) return false
        starts.addLast(nowMs)
        return true
    }
}
