package com.jellio.tv.ui.notify

// The number on the Quick Hub notifications badge: unread notifications
// plus group watch invites that are still waiting.
fun notificationBadgeCount(unreadNotifications: Int, pendingInvites: Int): Int =
    unreadNotifications.coerceAtLeast(0) + pendingInvites.coerceAtLeast(0)

// Identifies what the badge currently stands for, so the pill speaks up
// only when something other than what it last announced is waiting.
fun notificationSignature(unreadIds: Collection<String>, inviteGroupIds: Collection<String>): Set<String> =
    unreadIds.mapTo(HashSet()) { "n:$it" } + inviteGroupIds.map { "i:$it" }

enum class PillDecision { SHOW, WAIT, SKIP }

// WAIT means try again shortly (an announcement toast is on screen).
fun decidePill(
    count: Int,
    signature: Set<String>,
    lastAnnounced: Set<String>?,
    immersive: Boolean,
    toastShowing: Boolean,
): PillDecision = when {
    immersive -> PillDecision.SKIP
    count <= 0 -> PillDecision.SKIP
    signature == lastAnnounced -> PillDecision.SKIP
    toastShowing -> PillDecision.WAIT
    else -> PillDecision.SHOW
}

const val PILL_AWAY_MS = 10L * 60 * 1000

// Coming back from the background counts as a new open after a long away.
fun reopensAfterBackground(backgroundedAtMs: Long?, nowMs: Long, awayMs: Long = PILL_AWAY_MS): Boolean =
    backgroundedAtMs != null && nowMs - backgroundedAtMs > awayMs

fun pillTextKey(count: Int): String = if (count == 1) "new_notifications_pill_one" else "new_notifications_pill_many"
