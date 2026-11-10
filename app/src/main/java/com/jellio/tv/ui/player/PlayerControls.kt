package com.jellio.tv.ui.player

import com.jellio.tv.data.i18n.Strings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.tv.material3.Border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

@Composable
internal fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    positionMs: () -> Long,
    durationMs: Long,
    hasSubtitleTracks: Boolean,
    hasAudioTracks: Boolean,
    hasSourceOptions: Boolean,
    hasEpisodes: Boolean,
    speedLabel: String,
    sleepTimerActive: Boolean,
    enabled: Boolean = true,
    onPlayPause: () -> Unit,
    onSkip: (Long) -> Unit,
    onScrubEnd: (Long?) -> Unit,
    onOpenSubtitleMenu: () -> Unit,
    onOpenSpeedMenu: () -> Unit,
    onOpenSleepMenu: () -> Unit,
    onOpenAudioMenu: () -> Unit,
    onOpenSourceMenu: () -> Unit,
    onOpenEpisodesMenu: () -> Unit,
    inGroup: Boolean = false,
    onOpenReactMenu: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().focusProperties { canFocus = enabled }) {
        val playPauseFocusRequester = remember { FocusRequester() }
        LaunchedEffect(enabled) {
            if (enabled) {
                playPauseFocusRequester.requestFocus()
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.Center),
        ) {
            TransportButton(icon = Icons.Filled.Replay10, label = Strings.get("player_back_10s"), size = 68.dp, onClick = { onSkip(-SEEK_STEP_MS) })
            TransportButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (isPlaying) Strings.get("action_pause") else Strings.get("action_play"),
                size = 96.dp,
                onClick = onPlayPause,
                modifier = Modifier.focusRequester(playPauseFocusRequester),
            )
            TransportButton(icon = Icons.Filled.Forward10, label = Strings.get("player_forward_10s"), size = 68.dp, onClick = { onSkip(SEEK_STEP_MS) })
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(
                Brush.verticalGradient(listOf(Color.Transparent, JellioBg)),
            ).padding(horizontal = 48.dp, vertical = 32.dp),
        ) {
            // What's playing sits with the seek bar, as in NuvioTV.
            Text(
                text = title,
                color = JellioText,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = JellioText.copy(alpha = 0.85f),
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onScrubEnd = onScrubEnd,
                onPlayPause = onPlayPause,
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(JellioBgElevated.copy(alpha = 0.82f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(999.dp)),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(6.dp),
                    ) {
                        PlayerPillButton(icon = Icons.Filled.Speed, label = speedLabel, onClick = onOpenSpeedMenu)
                        if (hasSubtitleTracks) {
                            PlayerPillButton(icon = Icons.Filled.ClosedCaption, label = Strings.get("player_subtitle_track"), onClick = onOpenSubtitleMenu)
                        }
                        if (hasAudioTracks) {
                            PlayerPillButton(icon = Icons.Filled.GraphicEq, label = Strings.get("player_audio_track"), onClick = onOpenAudioMenu)
                        }
                        if (hasSourceOptions) {
                            PlayerPillButton(icon = Icons.Filled.SwapHoriz, label = Strings.get("detail_streams"), onClick = onOpenSourceMenu)
                        }
                        if (hasEpisodes) {
                            PlayerPillButton(icon = Icons.Filled.VideoLibrary, label = Strings.get("detail_episodes"), onClick = onOpenEpisodesMenu)
                        }
                        PlayerPillButton(
                            icon = Icons.Filled.Bedtime,
                            label = Strings.get("settings_sleep_timer"),
                            active = sleepTimerActive,
                            onClick = onOpenSleepMenu,
                        )
                        if (inGroup) {
                            PlayerPillButton(icon = Icons.Filled.EmojiEmotions, label = Strings.get("player_react"), onClick = onOpenReactMenu)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.35f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = CircleShape,
            )
        ),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = label, tint = JellioText, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
internal fun SeekBar(
    positionMs: () -> Long,
    durationMs: Long,
    onScrubEnd: (Long?) -> Unit,
    onPlayPause: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Long?>(null) }
    val shown = target ?: positionMs()
    val progress = if (durationMs > 0) (shown.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused && target != null) {
                    target = null
                    onScrubEnd(null)
                }
            }
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (durationMs <= 0) return@onPreviewKeyEvent true
                        if (event.type == KeyEventType.KeyDown) {
                            val held = event.nativeKeyEvent.repeatCount
                            val step = when {
                                held > 40 -> 60_000L
                                held > 15 -> 30_000L
                                else -> SEEK_STEP_MS
                            }
                            val next = ((target ?: positionMs()) + if (event.key == Key.DirectionRight) step else -step).coerceIn(0L, durationMs)
                            target = next
                        } else if (event.type == KeyEventType.KeyUp) {
                            val landing = target
                            target = null
                            onScrubEnd(landing)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (event.type == KeyEventType.KeyUp) {
                            val landing = target
                            if (landing != null) {
                                target = null
                                onScrubEnd(landing)
                            } else {
                                onPlayPause()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        val barHeight = if (focused) 8.dp else 4.dp
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth().height(20.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(barHeight).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(barHeight)
                        .background(if (focused) JellioSecondary else JellioText, RoundedCornerShape(4.dp)),
                )
            }
            if (focused) {
                Box(modifier = Modifier.fillMaxWidth(progress), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(text = com.jellio.tv.ui.common.formatPlaybackTime(shown), color = if (target != null) JellioText else JellioTextSecondary)
            Text(text = " / " + com.jellio.tv.ui.common.formatPlaybackTime(durationMs), color = JellioTextSecondary)
            if (focused && target == null) {
                Text(
                    text = "   " + Strings.get("player_scrub_hint"),
                    color = JellioTextSecondary,
                    style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

@Composable
internal fun PlayerPillButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (active) JellioSecondary else JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = if (active) JellioSecondary else JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(999.dp),
            )
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label)
            Text(text = label, style = androidx.tv.material3.MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
