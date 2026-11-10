package com.jellio.tv.ui.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.NowPlayingSessionDto
import com.jellio.tv.data.nowplaying.NowPlayingManager
import com.jellio.tv.data.session.Session
import com.jellio.tv.di.normalizeServerAddress
import com.jellio.tv.data.i18n.Strings
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.scaled
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val GreenDot = Color(0xFF22C55E)

@Composable
fun NowPlayingPill(
    activeCount: Int,
    topSession: NowPlayingSessionDto? = null,
    localPaused: NowPlayingManager.LocalPausedMedia?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    sessions: List<NowPlayingSessionDto> = emptyList(),
    session: Session? = null,
) {
    val isVisible = activeCount > 0 || localPaused != null
    var isFocused by remember { mutableStateOf(false) }

    var currentIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(sessions.size) {
        if (sessions.size <= 1) {
            currentIndex = 0
        } else {
            currentIndex = currentIndex % sessions.size
            while (isActive) {
                delay(4500L)
                currentIndex = (currentIndex + 1) % sessions.size
            }
        }
    }
    val displayedSession = if (sessions.isNotEmpty()) sessions[currentIndex % sessions.size] else topSession

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { -it / 2 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 },
        modifier = modifier,
    ) {
        val pillShape = RoundedCornerShape(24.dp)
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape = pillShape),
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1.04f,
                pressedScale = 0.97f,
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.70f),
                focusedContainerColor = Color.White.copy(alpha = 0.22f),
                contentColor = JellioText,
                focusedContentColor = Color.White,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    shape = pillShape,
                ),
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color.White),
                    shape = pillShape,
                ),
            ),
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                // Animated Equalizer waveform
                EqualizerBars(isAnimating = localPaused == null || activeCount > 0)

                Spacer(modifier = Modifier.width(10.dp))

                // Pulsing activity indicator dot
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (localPaused != null && activeCount == 0) Color.White.copy(alpha = 0.40f) else GreenDot),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(verticalArrangement = Arrangement.Center) {
                    val headline = when {
                        localPaused != null && activeCount == 0 -> Strings.get("now_playing_this_tv")
                        activeCount == 1 -> Strings.get("now_playing_active_stream")
                        else -> Strings.format("now_playing_active_streams", activeCount)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isFocused) Color.White else JellioText,
                            maxLines = 1,
                        )
                        if (isFocused) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• " + Strings.get("now_playing_press_select"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 10.sp,
                            )
                        }
                    }

                    val targetKey = if (localPaused != null && activeCount == 0) {
                        "local:${localPaused.itemId}"
                    } else {
                        displayedSession?.Id ?: displayedSession?.UserId ?: "local"
                    }

                    AnimatedContent(
                        targetState = targetKey,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 })
                                .togetherWith(fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 })
                        },
                        label = "now_playing_pill_subtitle",
                    ) { _ ->
                        val activeSess = displayedSession
                        val subtitle = when {
                            localPaused != null && activeCount == 0 -> localPaused.title
                            activeSess?.Item != null -> {
                                val user = activeSess.UserName ?: Strings.get("tv_someone")
                                val mediaName = activeSess.Item.SeriesName?.takeIf { it.isNotBlank() }
                                    ?: activeSess.Item.Name.orEmpty()
                                if (mediaName.isNotBlank()) "$user · $mediaName" else null
                            }
                            else -> null
                        }

                        if (subtitle != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                if (!(localPaused != null && activeCount == 0) && activeSess != null) {
                                    val userName = activeSess.UserName ?: Strings.get("tv_someone")
                                    val initial = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                                    val avatarUrl = if (session != null && !activeSess.UserId.isNullOrBlank()) {
                                        val cleanServer = normalizeServerAddress(session.serverAddress)
                                        "$cleanServer/Users/${activeSess.UserId}/Images/Primary?maxWidth=64&ApiKey=${session.accessToken}&api_key=${session.accessToken}"
                                    } else null

                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = initial,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp,
                                            ),
                                            color = Color.White,
                                        )
                                        if (avatarUrl != null) {
                                            AsyncImage(
                                                model = avatarUrl,
                                                contentDescription = userName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JellioTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(170.dp.scaled()),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerBars(isAnimating: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar1",
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar2",
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar3",
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        modifier = Modifier.height(14.dp),
    ) {
        val maxHeight = 14.dp
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(maxHeight * (if (isAnimating) bar1 else 0.4f))
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White),
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(maxHeight * (if (isAnimating) bar2 else 0.7f))
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White),
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(maxHeight * (if (isAnimating) bar3 else 0.5f))
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color.White),
        )
    }
}
