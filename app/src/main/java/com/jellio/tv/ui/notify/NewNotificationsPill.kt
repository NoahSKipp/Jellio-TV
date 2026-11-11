package com.jellio.tv.ui.notify

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.data.catching
import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.data.model.JellioNotificationDto
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.ui.announce.AnnouncementViewModel
import com.jellio.tv.ui.groupwatch.GroupWatchViewModel
import com.jellio.tv.ui.perf.MotionLevel
import com.jellio.tv.ui.perf.MotionPolicy
import com.jellio.tv.ui.theme.JellioText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val LOAD_TIMEOUT_MS = 8_000L
private const val SETTLE_MS = 1_200L
private const val TOAST_WAIT_MAX_MS = 40_000L
private const val SHOW_MS = 3_000L

// Reads the notifications without marking them read, unlike the drawer's
// load, so the Quick Hub badge still has them afterwards.
@HiltViewModel
class NewNotificationsViewModel @Inject constructor(private val api: JellyfinApi) : ViewModel() {
    private val _items = MutableStateFlow<List<JellioNotificationDto>?>(null)
    val items: StateFlow<List<JellioNotificationDto>?> = _items.asStateFlow()

    // Each open (cold start or a return after a long away) gets one number.
    private val _openId = MutableStateFlow(1)
    val openId: StateFlow<Int> = _openId.asStateFlow()
    var handledOpenId = 0
    var lastAnnounced: Set<String>? = null
    private var backgroundedAt: Long? = null

    fun onStarted() {
        if (reopensAfterBackground(backgroundedAt, SystemClock.elapsedRealtime())) _openId.value += 1
        backgroundedAt = null
    }

    fun onStopped() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    suspend fun refresh() {
        _items.value = null
        _items.value = catching { api.getJellioNotifications() }
            .getOrDefault(emptyList())
            .filter { it.Kind != "manga" && !it.Id.startsWith("changelog:") }
    }

    fun reset() {
        lastAnnounced = null
        backgroundedAt = null
        _items.value = null
        _openId.value += 1
    }
}

// A small pill that drops from the top centre once per open when
// notifications are waiting. Purely visual: it never takes focus or keys.
@Composable
fun NewNotificationsPill(
    immersive: Boolean,
    modifier: Modifier = Modifier,
    viewModel: NewNotificationsViewModel = hiltViewModel(),
    groupWatch: GroupWatchViewModel = hiltViewModel(),
    announcements: AnnouncementViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onStarted()
                Lifecycle.Event.ON_STOP -> viewModel.onStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Leaving the signed-in app (logout) forgets what was announced.
    DisposableEffect(Unit) {
        onDispose {
            if ((context as? android.app.Activity)?.isChangingConfigurations != true) viewModel.reset()
        }
    }

    val immersiveNow by rememberUpdatedState(immersive)
    var visible by remember { mutableStateOf(false) }
    var shownCount by remember { mutableIntStateOf(0) }
    val openId by viewModel.openId.collectAsState()
    LaunchedEffect(openId) {
        if (viewModel.handledOpenId == openId) return@LaunchedEffect
        viewModel.handledOpenId = openId
        viewModel.refresh()
        val items = withTimeoutOrNull(LOAD_TIMEOUT_MS) { viewModel.items.filterNotNull().first() }
            ?: return@LaunchedEffect
        // Invites arrive a moment after the app starts.
        delay(SETTLE_MS)
        val unread = items.filter { !it.Read }
        var waited = 0L
        while (true) {
            val invites = groupWatch.invites.value
            val count = notificationBadgeCount(unread.size, invites.size)
            val signature = notificationSignature(unread.map { it.Id }, invites.map { it.GroupId })
            when (decidePill(count, signature, viewModel.lastAnnounced, immersiveNow, announcements.current.value != null)) {
                PillDecision.SKIP -> return@LaunchedEffect
                PillDecision.WAIT -> {
                    delay(500)
                    waited += 500
                    if (waited > TOAST_WAIT_MAX_MS) return@LaunchedEffect
                }
                PillDecision.SHOW -> {
                    viewModel.lastAnnounced = signature
                    shownCount = count
                    visible = true
                    delay(SHOW_MS)
                    visible = false
                    return@LaunchedEffect
                }
            }
        }
    }

    val still = MotionPolicy.level != MotionLevel.FULL
    val enter = if (still) {
        fadeIn(tween(200))
    } else {
        slideInVertically(spring<IntOffset>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { -it - 40 } + fadeIn(tween(160))
    }
    val exit = if (still) {
        fadeOut(tween(200))
    } else {
        slideOutVertically(tween<IntOffset>(260)) { -it - 40 } + fadeOut(tween(200))
    }
    AnimatedVisibility(visible = visible && !immersive, enter = enter, exit = exit, modifier = modifier) {
        val shape = RoundedCornerShape(50)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 24.dp)
                .shadow(16.dp, shape)
                .clip(shape)
                .background(Color(0xE6141414))
                .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                .padding(start = 18.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Icon(imageVector = Icons.Filled.Notifications, contentDescription = null, tint = JellioText, modifier = Modifier.size(24.dp))
            Text(
                text = if (shownCount == 1) Strings.get(pillTextKey(1)) else Strings.format(pillTextKey(shownCount), shownCount),
                color = JellioText,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
