package com.jellio.tv.ui.player

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.Border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.jellio.tv.data.network.APP_VERSION
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.SUBTITLE_BACKGROUNDS
import com.jellio.tv.data.model.SUBTITLE_SIZES
import com.jellio.tv.data.model.SUBTITLE_COLORS
import com.jellio.tv.data.model.SUBTITLE_EDGES
import com.jellio.tv.data.model.SUBTITLE_POSITIONS
import com.jellio.tv.data.model.SubtitleStyle
import com.jellio.tv.data.model.subtitleBackgroundOption
import com.jellio.tv.data.model.subtitleSizeOption
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.detail.FilterChipRow
import com.jellio.tv.ui.detail.QUALITY_ORDER
import com.jellio.tv.ui.detail.SourceCard
import com.jellio.tv.ui.detail.sourceAudioLanguages
import com.jellio.tv.ui.detail.sourceQuality
import com.jellio.tv.data.model.languageName
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

@Composable
internal fun BufferingOverlay(info: PauseOverlayInfo, modifier: Modifier = Modifier) {
    com.jellio.tv.ui.common.TitleLoadingArt(info.art, info.logoUrl, info.title, modifier)
}

// Shown after a few seconds paused, in place of the controls. Layout follows
// NuvioTV's PauseOverlay: everything bottom left, the time top right.
@Composable
internal fun PauseOverlay(info: PauseOverlayInfo, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        com.jellio.tv.ui.common.TitleArtBackdrop(info.art, Modifier.fillMaxSize())
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.92f))),
            ),
        )
        PauseClock(modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 48.dp))
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 96.dp, bottom = 72.dp)
                .widthIn(max = 720.dp),
        ) {
            Text(text = com.jellio.tv.ui.i18n.Strings.get("player_youre_watching"), color = JellioTextSecondary, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(10.dp))
            var logoFailed by remember(info.logoUrl) { mutableStateOf(false) }
            if (info.logoUrl != null && !logoFailed) {
                AsyncImage(
                    model = info.logoUrl,
                    contentDescription = info.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    onError = { logoFailed = true },
                    modifier = Modifier.height(88.dp).widthIn(max = 420.dp),
                )
            } else {
                Text(
                    text = info.title,
                    color = JellioText,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val details = listOfNotNull(info.year, info.episodeCode?.takeIf { info.isEpisode }, info.officialRating)
            if (details.isNotEmpty() || info.rating != null) {
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (details.isNotEmpty()) {
                        Text(text = details.joinToString("  •  "), color = JellioTextSecondary, fontSize = 17.sp)
                    }
                    info.rating?.let { com.jellio.tv.ui.home.RatingChip(it, fontSize = 15.sp, badgeSize = 15.dp) }
                }
            }
            if (info.isEpisode) {
                info.episodeTitle?.takeIf { it.isNotEmpty() }?.let {
                    Text(
                        text = it,
                        color = JellioText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            info.overview?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = it,
                    color = JellioTextSecondary,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

@Composable
internal fun PauseClock(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val formatter = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay((60_000L - now % 60_000L).coerceAtLeast(1_000L))
        }
    }
    Text(text = formatter.format(java.util.Date(now)), color = JellioText, fontSize = 30.sp, modifier = modifier)
}

@Composable
internal fun UpNextOverlay(
    info: UpNextInfo,
    secondsRemaining: Int,
    onPlayNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { playFocus.requestFocus() }
    }
    Row(
        modifier = modifier
            .widthIn(min = 440.dp, max = 540.dp)
            .background(JellioBgElevated, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (info.thumbnailUrl != null) {
            AsyncImage(
                model = info.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = com.jellio.tv.ui.i18n.Strings.get("player_next_episode"), color = JellioTextSecondary, style = androidx.tv.material3.MaterialTheme.typography.labelSmall)
            Text(
                text = info.title,
                color = JellioText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onPlayNow,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = com.jellio.tv.ui.common.primaryButtonColors(),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.5.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
                    ),
                    modifier = Modifier.focusRequester(playFocus),
                ) {
                    Text(text = com.jellio.tv.ui.i18n.Strings.format("player_play_now_countdown", secondsRemaining), maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
                Surface(
                    onClick = onDismiss,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.28f),
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
                    ),
                ) {
                    Text(text = com.jellio.tv.ui.i18n.Strings.get("notifications_dismiss"), maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
        }
    }
}

// The web player's end screen (buildEndScreenModal) for the TV: shown when
// an episode or movie finishes, over the player and its controls.
internal const val END_SCREEN_INPUT_DELAY_MS = 800L

@Composable
internal fun EndScreen(
    finishedTitle: String,
    finishedSubtitle: String,
    next: UpNextInfo?,
    countdown: Int?,
    onPlayNext: () -> Unit,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    art: List<com.jellio.tv.ui.common.ArtCandidate>,
    info: PauseOverlayInfo?,
) {
    val firstFocus = remember { FocusRequester() }
    // A press meant for the screen before (Play Next held or tapped twice)
    // must not land on this one's buttons.
    val shownAt = remember { System.currentTimeMillis() }
    fun armed(action: () -> Unit): () -> Unit = {
        if (System.currentTimeMillis() - shownAt >= END_SCREEN_INPUT_DELAY_MS) action()
    }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }
    // The backdrop drifts in slowly behind the panel.
    val drift = rememberInfiniteTransition(label = "endDrift")
    val zoom by drift.animateFloat(
        initialValue = 1.04f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(20_000), RepeatMode.Reverse),
        label = "endZoom",
    )
    Box(modifier = Modifier.fillMaxSize().background(JellioBg)) {
        com.jellio.tv.ui.common.TitleArtBackdrop(
            art,
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = zoom; scaleY = zoom }
                .alpha(0.7f),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to JellioBg, 0.45f to JellioBg.copy(alpha = 0.85f), 1f to Color.Transparent),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to JellioBg),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 72.dp).widthIn(max = 760.dp),
        ) {
            if (next != null) {
                Text(text = com.jellio.tv.ui.i18n.Strings.get("player_up_next").uppercase(), color = JellioSecondary, style = androidx.tv.material3.MaterialTheme.typography.labelLarge, letterSpacing = 1.5.sp)
                next.seriesName?.let {
                    Text(text = it, color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                }
                Row(modifier = Modifier.padding(top = 20.dp), verticalAlignment = Alignment.Top) {
                    if (next.thumbnailUrl != null) {
                        AsyncImage(
                            model = next.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(320.dp).height(180.dp).clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                        )
                    }
                    Column(modifier = Modifier.padding(start = if (next.thumbnailUrl != null) 24.dp else 0.dp)) {
                        Text(text = next.title, color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        next.overview?.let {
                            Text(text = it, color = JellioTextSecondary, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            } else {
                // Finished: a check badge, the title's logo (or its name), its
                // details and a few lines of what it was about.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(JellioSecondary.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    androidx.tv.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = JellioSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = com.jellio.tv.ui.i18n.Strings.get("action_done").uppercase(),
                        color = JellioSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                val logo = info?.logoUrl
                if (logo != null) {
                    AsyncImage(
                        model = logo,
                        contentDescription = finishedTitle,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.padding(top = 18.dp).height(120.dp).widthIn(max = 520.dp),
                    )
                } else {
                    Text(
                        text = finishedTitle,
                        color = JellioText,
                        style = androidx.tv.material3.MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                if (finishedSubtitle.isNotEmpty()) {
                    Text(text = finishedSubtitle, color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                val details = listOfNotNull(info?.year, info?.officialRating)
                if (details.isNotEmpty() || info?.rating != null) {
                    Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (details.isNotEmpty()) {
                            Text(text = details.joinToString("  ·  "), color = JellioTextSecondary)
                        }
                        info?.rating?.let { com.jellio.tv.ui.home.RatingChip(it, fontSize = 15.sp, badgeSize = 15.dp) }
                    }
                }
                info?.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, color = JellioTextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 28.dp)) {
                if (next != null) {
                    EndScreenButton(
                        label = if (countdown != null && countdown > 0) com.jellio.tv.ui.i18n.Strings.format("player_play_next_episode_countdown", countdown) else com.jellio.tv.ui.i18n.Strings.get("player_play_next_episode"),
                        primary = true,
                        icon = Icons.Filled.PlayArrow,
                        onClick = armed(onPlayNext),
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    EndScreenButton(label = com.jellio.tv.ui.i18n.Strings.get("player_replay"), icon = androidx.compose.material.icons.Icons.Filled.Replay, onClick = armed(onReplay))
                    EndScreenButton(label = com.jellio.tv.ui.i18n.Strings.get("player_back_to_show"), icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, onClick = armed(onBack))
                } else {
                    EndScreenButton(label = com.jellio.tv.ui.i18n.Strings.get("player_replay"), primary = true, icon = androidx.compose.material.icons.Icons.Filled.Replay, onClick = armed(onReplay), modifier = Modifier.focusRequester(firstFocus))
                    EndScreenButton(label = com.jellio.tv.ui.i18n.Strings.get("nav_back"), icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, onClick = armed(onBack))
                }
            }
        }
    }
}

@Composable
internal fun EndScreenButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) JellioSecondary else Color.White.copy(alpha = 0.12f),
            contentColor = if (primary) JellioBg else JellioText,
            focusedContainerColor = if (primary) Color.White else Color.White.copy(alpha = 0.32f),
            focusedContentColor = if (primary) JellioBg else JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(999.dp)),
        ),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
            if (icon != null) {
                androidx.tv.material3.Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp).padding(end = 0.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = label, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
internal fun SkipSegmentButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.32f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.5.dp, Color.White),
                shape = RoundedCornerShape(999.dp),
            )
        ),
        modifier = modifier,
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
    }
}

@Composable
internal fun ResumePrompt(percent: Int?, resumeTicks: Long, onResume: () -> Unit, onRestart: () -> Unit, modifier: Modifier = Modifier) {
    val resumeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { resumeFocusRequester.requestFocus() }
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.background(JellioBgElevated, RoundedCornerShape(16.dp)).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = com.jellio.tv.ui.i18n.Strings.get("player_resume_prompt"), color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            // Where it picks up, then how much of it that is.
            val at = resumeTicks / TICKS_PER_MS / 1000
            val atLabel = if (at >= 3600) "%d:%02d:%02d".format(at / 3600, (at % 3600) / 60, at % 60) else "%d:%02d".format(at / 60, at % 60)
            Text(
                text = com.jellio.tv.ui.i18n.Strings.format("stream_resume_from", atLabel) +
                    (percent?.let { " · " + com.jellio.tv.ui.i18n.Strings.format("player_percent_watched", it) } ?: ""),
                color = JellioTextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = onResume,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = com.jellio.tv.ui.common.primaryButtonColors(),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.5.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
                    ),
                    modifier = Modifier.focusRequester(resumeFocusRequester),
                ) {
                    Text(text = com.jellio.tv.ui.i18n.Strings.get("player_resume"), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
                Surface(
                    onClick = onRestart,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = JellioText,
                        focusedContainerColor = Color.White.copy(alpha = 0.28f),
                        focusedContentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(999.dp),
                        )
                    ),
                ) {
                    Text(text = com.jellio.tv.ui.i18n.Strings.get("player_start_over"), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
internal fun SwitchingCard(label: String, step: String, modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "switchSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)),
        label = "switchAngle",
    )
    Row(
        modifier = modifier
            .widthIn(max = 640.dp)
            .background(JellioBgElevated.copy(alpha = 0.95f), RoundedCornerShape(16.dp))
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(32.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawArc(color = Color.White.copy(alpha = 0.2f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color = Color.White, startAngle = angle, sweepAngle = 90f, useCenter = false, style = stroke)
        }
        Column {
            Text(
                text = com.jellio.tv.ui.i18n.Strings.format("tv_switching_to", label),
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(text = step, color = JellioTextSecondary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
internal fun BufferSwitchPrompt(onSwitch: () -> Unit, onKeep: () -> Unit, onAlways: () -> Unit, modifier: Modifier = Modifier) {
    val switchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { switchFocusRequester.requestFocus() } }
    val secondary = ClickableSurfaceDefaults.colors(
        containerColor = Color.White.copy(alpha = 0.12f),
        contentColor = JellioText,
        focusedContainerColor = Color.White.copy(alpha = 0.28f),
        focusedContentColor = JellioText,
    )
    val border = ClickableSurfaceDefaults.border(
        focusedBorder = Border(border = BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(999.dp)),
    )
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .padding(bottom = 64.dp)
                .background(JellioBgElevated.copy(alpha = 0.96f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = com.jellio.tv.ui.i18n.Strings.get("player_buffer_prompt"), color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = onSwitch,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = com.jellio.tv.ui.common.primaryButtonColors(),
                    border = border,
                    modifier = Modifier.focusRequester(switchFocusRequester),
                ) {
                    Text(text = com.jellio.tv.ui.i18n.Strings.get("player_buffer_switch"), modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
                }
                Surface(onClick = onKeep, shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)), colors = secondary, border = border) {
                    Text(text = com.jellio.tv.ui.i18n.Strings.get("player_buffer_keep"), modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
                }
                Surface(onClick = onAlways, shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)), colors = secondary, border = border) {
                    Text(text = com.jellio.tv.ui.i18n.Strings.get("player_buffer_always"), modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp))
                }
            }
        }
    }
}

@Composable
internal fun PlayerToast(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(JellioBgElevated, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = message, color = JellioText)
    }
}
