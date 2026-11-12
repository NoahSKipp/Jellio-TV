package com.jellio.tv.ui.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.hilt.navigation.compose.hiltViewModel
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.data.nowplaying.NowPlayingManager
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.friends.FriendsViewModel
import com.jellio.tv.ui.groupwatch.GroupWatchViewModel
import com.jellio.tv.ui.notify.NewNotificationsViewModel
import com.jellio.tv.ui.notify.NotificationsViewModel
import com.jellio.tv.ui.perf.MotionLevel
import com.jellio.tv.ui.perf.MotionPolicy
import kotlinx.coroutines.delay

private const val REFOCUS_TRIES = 12
private const val REFOCUS_STEP_MS = 50L
// The hub marks notifications read a moment after it loads them.
private const val BADGE_REFRESH_DELAY_MS = 1_500L

// The one top-right slot on Home: the Now Playing pill while someone is
// watching, the Quick Hub pill otherwise. Both share one focus requester.
@Composable
fun QuickHubEntry(
    activeCount: Int,
    topSession: NowPlayingSessionDto?,
    localPaused: NowPlayingManager.LocalPausedMedia?,
    sessions: List<NowPlayingSessionDto>,
    session: Session?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    newNotificationsViewModel: NewNotificationsViewModel = hiltViewModel(),
    notificationsViewModel: NotificationsViewModel = hiltViewModel(),
    groupWatchViewModel: GroupWatchViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel(),
) {
    val pill = homePillFor(activeCount, localPaused != null)
    val focusRequester = remember { FocusRequester() }
    val holdsFocus = remember { BooleanArray(1) }
    val lastPill = remember { arrayOf(pill) }

    // A swap while the pill is focused hands focus to the new pill.
    LaunchedEffect(pill) {
        if (lastPill[0] == pill) return@LaunchedEffect
        lastPill[0] = pill
        if (!holdsFocus[0]) return@LaunchedEffect
        repeat(REFOCUS_TRIES) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(REFOCUS_STEP_MS)
        }
    }

    val unreadItems by newNotificationsViewModel.items.collectAsState()
    val hubItems by notificationsViewModel.items.collectAsState()
    // Opening the hub marks everything read, so read the list again after.
    LaunchedEffect(hubItems) {
        if (hubItems == null) return@LaunchedEffect
        delay(BADGE_REFRESH_DELAY_MS)
        newNotificationsViewModel.refresh()
    }
    val invites by groupWatchViewModel.invites.collectAsState()
    val friends by friendsViewModel.state.collectAsState()
    val badgeCount = quickHubPillCount(
        unreadNotifications = unreadItems?.count { !it.Read } ?: 0,
        pendingInvites = invites.size,
        incomingRequests = friends.incoming.size,
    )

    val still = MotionPolicy.level != MotionLevel.FULL
    val enter = if (still) fadeIn(tween(250)) else fadeIn(tween(250)) + slideInVertically(tween(250)) { -it / 2 }
    val exit = if (still) fadeOut(tween(200)) else fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 }

    Box(modifier = modifier.onFocusChanged { holdsFocus[0] = it.hasFocus }) {
        AnimatedVisibility(visible = pill == HomePill.QUICK_HUB, enter = enter, exit = exit) {
            QuickHubPill(
                badgeCount = badgeCount,
                onClick = onClick,
                focusRequester = if (pill == HomePill.QUICK_HUB) focusRequester else null,
            )
        }
        AnimatedVisibility(visible = pill == HomePill.NOW_PLAYING, enter = enter, exit = exit) {
            NowPlayingPill(
                activeCount = activeCount,
                topSession = topSession,
                localPaused = localPaused,
                onClick = onClick,
                focusRequester = if (pill == HomePill.NOW_PLAYING) focusRequester else null,
                sessions = sessions,
                session = session,
            )
        }
    }
}
