package com.jellio.tv.ui.nowplaying

import com.jellio.tv.ui.notify.notificationBadgeCount

enum class HomePill { NOW_PLAYING, QUICK_HUB }

// The Now Playing pill takes the slot while someone is watching,
// otherwise the Quick Hub pill holds it.
fun homePillFor(activeCount: Int, hasLocalPaused: Boolean): HomePill =
    if (activeCount > 0 || hasLocalPaused) HomePill.NOW_PLAYING else HomePill.QUICK_HUB

// Things waiting in the hub: unread notifications, group watch invites
// and incoming friend requests.
fun quickHubPillCount(unreadNotifications: Int, pendingInvites: Int, incomingRequests: Int): Int =
    notificationBadgeCount(unreadNotifications, pendingInvites) + incomingRequests.coerceAtLeast(0)

// Null when nothing waits, so no badge is drawn.
fun quickHubPillBadgeText(count: Int): String? = when {
    count <= 0 -> null
    count > 9 -> "9+"
    else -> count.toString()
}
