package com.jellio.tv.ui.player

import com.jellio.tv.ui.i18n.Strings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.Border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.annotation.OptIn
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.SUBTITLE_BACKGROUNDS
import com.jellio.tv.data.model.SUBTITLE_SIZES
import com.jellio.tv.data.model.SUBTITLE_COLORS
import com.jellio.tv.data.model.SUBTITLE_EDGES
import com.jellio.tv.data.model.SUBTITLE_POSITIONS
import com.jellio.tv.data.model.SubtitleStyle
import com.jellio.tv.data.model.subtitleBackgroundOption
import com.jellio.tv.data.model.subtitleSizeOption
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
import kotlinx.coroutines.isActive

// Speed drawer matching SubtitleMenu and AudioMenu right drawers.
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
internal fun SpeedMenu(selectedSpeed: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val focusRequesters = remember { PLAYBACK_SPEEDS.map { FocusRequester() } }
    val activeIndex = PLAYBACK_SPEEDS.indexOfFirst { it == selectedSpeed }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching {
            focusRequesters.getOrNull(activeIndex)?.requestFocus() ?: focusRequesters.firstOrNull()?.requestFocus()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(vertical = 48.dp),
        ) {
            Text(
                text = Strings.get("tv_playback_speed"),
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LazyColumn {
                itemsIndexed(PLAYBACK_SPEEDS) { index, speed ->
                    val req = focusRequesters.getOrNull(index) ?: remember { FocusRequester() }
                    SubtitleMenuRow(
                        label = formatSpeed(speed),
                        isSelected = speed == selectedSpeed,
                        onClick = {
                            onSelect(speed)
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(req),
                    )
                }
            }
        }
    }
}

// Group Watch reactions: a row of preset emoji sent to the group's chat,
// in place of typing on a remote.
@Composable
internal fun ReactMenu(replyTo: com.jellio.tv.data.model.GroupWatchMessageDto?, onReact: (String) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { first.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 140.dp)) {
        if (replyTo != null) {
            Text(
                text = Strings.format("party_replying_to", replyTo.UserName ?: Strings.get("tv_someone"), replyTo.Text),
                color = JellioTextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 560.dp).padding(bottom = 10.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(JellioBgElevated.copy(alpha = 0.95f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            com.jellio.tv.ui.groupwatch.GROUP_REACTIONS.forEachIndexed { index, emoji ->
                androidx.tv.material3.Surface(
                    onClick = { onReact(emoji) },
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.25f),
                    ),
                    scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                ) {
                    Text(text = emoji, fontSize = 30.sp, modifier = Modifier.padding(10.dp))
                }
            }
        }
        }
    }
}

// Joining a group well into its title: follow the group or start over alone.
@Composable
internal fun CatchUpPrompt(groupAtTicks: Long, onFollow: () -> Unit, onSolo: () -> Unit) {
    BackHandler(onBack = onFollow)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { first.requestFocus() }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(JellioBgElevated.copy(alpha = 0.97f))
                .padding(horizontal = 28.dp, vertical = 22.dp),
        ) {
            Text(
                text = Strings.format("party_catchup_title", com.jellio.tv.ui.common.formatPlaybackTime(groupAtTicks / TICKS_PER_MS)),
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.jellio.tv.ui.groupwatch.GroupButton(
                    label = Strings.get("party_catchup_join"),
                    primary = true,
                    onClick = onFollow,
                    modifier = Modifier.focusRequester(first),
                )
                com.jellio.tv.ui.groupwatch.GroupButton(label = Strings.get("party_catchup_solo"), onClick = onSolo)
            }
        }
    }
}

// Sleep timer drawer matching SubtitleMenu and AudioMenu right drawers.
@Composable
internal fun SleepMenuHeading(text: String) {
    Text(
        text = text,
        color = JellioTextSecondary,
        style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
internal fun SleepMenu(
    activeEpisodes: Int?,
    minutesActive: Boolean,
    onSelect: (Int) -> Unit,
    onSelectEpisodes: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstItemFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { firstItemFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(vertical = 48.dp),
        ) {
            Text(
                text = Strings.get("tv_sleep_timer"),
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LazyColumn {
                item {
                    SubtitleMenuRow(
                        label = Strings.get("settings_sleep_timer_cancel"),
                        isSelected = false,
                        onClick = {
                            onCancel()
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(firstItemFocusRequester),
                    )
                }
                item { SleepMenuHeading(if (minutesActive) Strings.get("player_stop_after") + " (" + Strings.get("state_on") + ")" else Strings.get("player_stop_after")) }
                items(SLEEP_TIMER_OPTIONS) { minutes ->
                    SubtitleMenuRow(
                        label = Strings.format("player_minutes_short", minutes),
                        isSelected = false,
                        onClick = {
                            onSelect(minutes)
                            onDismiss()
                        },
                    )
                }
                item { SleepMenuHeading(Strings.get("player_or_stop_after")) }
                items(EPISODE_SLEEP_TIMER_OPTIONS) { count ->
                    SubtitleMenuRow(
                        label = if (count == 1) Strings.get("player_this_episode") else Strings.format("player_stop_episodes", count),
                        isSelected = activeEpisodes == count,
                        onClick = {
                            onSelectEpisodes(count)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

internal fun selectAudioTrack(
    player: Player,
    audioTracks: List<AudioTrackUiState>,
    targetIndex: Int?,
    defaultIndex: Int?,
) {
    val targetStreamIndex = targetIndex ?: defaultIndex ?: return
    val targetTrack = audioTracks.firstOrNull { it.streamIndex == targetStreamIndex } ?: return
    val ordinal = audioTracks.indexOf(targetTrack)
    if (ordinal < 0) return

    val candidates = mutableListOf<Pair<androidx.media3.common.Tracks.Group, Int>>()
    for (group in player.currentTracks.groups) {
        if (group.type == C.TRACK_TYPE_AUDIO) {
            for (i in 0 until group.length) candidates.add(group to i)
        }
    }
    if (candidates.isEmpty()) return

    val wanted = normalizeLanguage(targetTrack.language)
    fun languageOf(pair: Pair<androidx.media3.common.Tracks.Group, Int>) = normalizeLanguage(pair.first.getTrackFormat(pair.second).language)
    val byPosition = candidates.getOrNull(ordinal)
    val match = when {
        byPosition != null && (wanted == null || languageOf(byPosition) == null || languageOf(byPosition) == wanted) -> byPosition
        wanted != null -> candidates.firstOrNull { languageOf(it) == wanted } ?: byPosition
        else -> byPosition
    } ?: return
    if (!match.first.isTrackSupported(match.second)) return

    val override = TrackSelectionOverride(match.first.mediaTrackGroup, match.second)
    if (player.trackSelectionParameters.overrides[match.first.mediaTrackGroup] == override) return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setOverrideForType(override)
        .build()
}

internal fun applySubtitleSelection(
    player: Player,
    subtitleTracks: List<SubtitleTrackUiState>,
    selectedSubtitleIndex: Int?,
    language: String?,
): Boolean {
    val params = player.trackSelectionParameters.buildUpon()
    params.clearOverridesOfType(C.TRACK_TYPE_TEXT)
    var found = selectedSubtitleIndex == null
    if (selectedSubtitleIndex == null) {
        params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    } else {
        params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        val targetId = SIDELOADED_SUBTITLE_PREFIX + selectedSubtitleIndex
        fun matchesSideloaded(id: String?) = id != null && (id == targetId || id.endsWith(":$targetId"))
        fun isSideloaded(id: String?) = id != null && (id.startsWith(SIDELOADED_SUBTITLE_PREFIX) || id.contains(":$SIDELOADED_SUBTITLE_PREFIX"))
        val imageSubtitleMimeTypes = setOf(MimeTypes.APPLICATION_PGS, MimeTypes.APPLICATION_VOBSUB, MimeTypes.APPLICATION_DVBSUBS)
        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        var choice: Pair<androidx.media3.common.Tracks.Group, Int>? = null
        for (group in textGroups) {
            val i = (0 until group.length).firstOrNull { matchesSideloaded(group.getTrackFormat(it).id) } ?: continue
            choice = group to i
            break
        }
        val targetTrack = subtitleTracks.firstOrNull { it.streamIndex == selectedSubtitleIndex }
        val wanted = normalizeLanguage(language ?: targetTrack?.language)
        if (choice == null) {
            val embeddedTextCandidates = mutableListOf<Pair<androidx.media3.common.Tracks.Group, Int>>()
            for (group in textGroups) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (!isSideloaded(format.id) && format.sampleMimeType !in imageSubtitleMimeTypes) {
                        embeddedTextCandidates.add(group to i)
                    }
                }
            }
            fun languageOf(pair: Pair<androidx.media3.common.Tracks.Group, Int>) =
                normalizeLanguage(pair.first.getTrackFormat(pair.second).language)
            val ordinal = targetTrack?.embeddedTextOrdinal ?: -1
            val byPosition = if (ordinal in embeddedTextCandidates.indices) embeddedTextCandidates[ordinal] else null
            choice = when {
                byPosition != null && (wanted == null || languageOf(byPosition) == null || languageOf(byPosition) == wanted) -> byPosition
                wanted != null -> embeddedTextCandidates.firstOrNull { languageOf(it) == wanted } ?: byPosition
                else -> byPosition
            }
        }
        if (choice != null) {
            params.setOverrideForType(TrackSelectionOverride(choice.first.mediaTrackGroup, choice.second))
            found = true
        }
    }
    val built = params.build()
    if (built != player.trackSelectionParameters) player.trackSelectionParameters = built
    return found
}

// Audio menu right drawer matching SubtitleMenu: lists all audio tracks with
// auto-focus on the active track and high-contrast TV selection.
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
internal fun AudioMenu(
    tracks: List<AudioTrackUiState>,
    selectedStreamIndex: Int?,
    defaultStreamIndex: Int?,
    onSelect: (Int) -> Unit,
    onToggleDialogueBoost: () -> Unit,
    audioDelayMs: Int,
    onAudioDelay: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val focusRequesters = remember(tracks) { tracks.map { FocusRequester() } }
    val activeIndex = tracks.indexOfFirst {
        if (selectedStreamIndex == null) it.streamIndex == defaultStreamIndex else it.streamIndex == selectedStreamIndex
    }.coerceAtLeast(0)

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching {
            focusRequesters.getOrNull(activeIndex)?.requestFocus() ?: focusRequesters.firstOrNull()?.requestFocus()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(vertical = 48.dp),
        ) {
            Text(
                text = Strings.get("tv_audio"),
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            LazyColumn {
                itemsIndexed(tracks) { index, track ->
                    val isActive = if (selectedStreamIndex == null) {
                        track.streamIndex == defaultStreamIndex
                    } else {
                        track.streamIndex == selectedStreamIndex
                    }
                    val req = focusRequesters.getOrNull(index) ?: remember { FocusRequester() }
                    SubtitleMenuRow(
                        label = track.label,
                        isSelected = isActive,
                        onClick = {
                            if (!isActive) onSelect(track.streamIndex)
                            onDismiss()
                        },
                        modifier = Modifier.focusRequester(req),
                    )
                }
            }
            if (tracks.isNotEmpty()) {
                Box(
                    Modifier
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }
            SubtitleMenuRow(
                label = Strings.format("player_dialogue_boost", if (PlayerPrefs.dialogueBoost) Strings.get("state_on") else Strings.get("state_off")),
                isSelected = PlayerPrefs.dialogueBoost,
                onClick = onToggleDialogueBoost,
            )
            SyncAdjustRow(
                label = Strings.get("player_audio_sync"),
                valueMs = audioDelayMs,
                stepMs = 50,
                onChange = onAudioDelay,
            )
        }
    }
}

// Sources side panel right drawer.
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
internal fun SourcePanel(
    sources: List<MediaSourceDto>,
    currentMediaSourceId: String?,
    onSelect: (MediaSourceDto) -> Unit,
    onBlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { firstFocusRequester.requestFocus() }
    }
    // Streams the server would have to re-encode stay listed, last, marked
    // (Video transcode policy Avoid or Block).
    val policy = com.jellio.tv.data.playback.VideoTranscodePolicy
    val needsReencode = remember(sources) {
        sources.filter { policy.avoiding && policy.needsReencode(it) }.mapNotNull { it.Id }.toSet()
    }
    // Same edition, language, and quality chips as the stream picker before playback,
    // combinable the same way.
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf<String?>(null) }
    var selectedEdition by remember { mutableStateOf<String?>(null) }
    val editions = remember(sources) { com.jellio.tv.ui.detail.EditionParser.getAvailableEditions(sources) }
    val showEditions = editions.size > 1
    val editionFocus = remember(editions) { (listOf<String?>(null) + editions).associateWith { FocusRequester() } }
    val languages = remember(sources) {
        val counts = linkedMapOf<String, Int>()
        sources.forEach { source -> sourceAudioLanguages(source).forEach { code -> counts[code] = (counts[code] ?: 0) + 1 } }
        counts.keys.sortedWith(compareByDescending<String> { counts[it] ?: 0 }.thenBy { languageName(it) })
    }
    val qualities = remember(sources) {
        val present = sources.mapNotNull { sourceQuality(it) }.toSet()
        QUALITY_ORDER.filter { it in present }
    }
    val languageFocus = remember(languages) { (listOf<String?>(null) + languages).associateWith { FocusRequester() } }
    val qualityFocus = remember(qualities) { (listOf<String?>(null) + qualities).associateWith { FocusRequester() } }
    val showLanguages = languages.size > 1
    val showQualities = qualities.size > 1
    val filtered = sources.filter { source ->
        (selectedLanguage == null || sourceAudioLanguages(source).contains(selectedLanguage)) &&
            (selectedQuality == null || sourceQuality(source) == selectedQuality) &&
            (selectedEdition == null || com.jellio.tv.ui.detail.EditionParser.detectSourceEdition(source, sources) == selectedEdition)
    }.sortedBy { if (it.Id in needsReencode) 1 else 0 }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 48.dp),
        ) {
            Text(text = Strings.get("detail_streams"), color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (showEditions) {
                    item(key = "editions") {
                        FilterChipRow(
                            chips = listOf<Pair<String?, String>>(null to Strings.get("filter_all")) + editions.map { it to it },
                            selected = selectedEdition,
                            onSelect = { selectedEdition = it },
                            chipFocusRequesters = editionFocus,
                            topPadding = 12.dp,
                        )
                    }
                }
                if (showLanguages) {
                    item(key = "languages") {
                        FilterChipRow(
                            chips = listOf<Pair<String?, String>>(null to Strings.get("filter_all")) + languages.map { it to languageName(it) },
                            selected = selectedLanguage,
                            onSelect = { selectedLanguage = it },
                            chipFocusRequesters = languageFocus,
                            upTarget = if (showEditions) editionFocus[selectedEdition] else null,
                            topPadding = if (showEditions) 0.dp else 12.dp,
                        )
                    }
                }
                if (showQualities) {
                    item(key = "qualities") {
                        FilterChipRow(
                            chips = listOf<Pair<String?, String>>(null to Strings.get("filter_all")) + qualities.map { it to it },
                            selected = selectedQuality,
                            onSelect = { selectedQuality = it },
                            chipFocusRequesters = qualityFocus,
                            upTarget = if (showLanguages) languageFocus[selectedLanguage] else if (showEditions) editionFocus[selectedEdition] else null,
                            topPadding = if (showLanguages || showEditions) 0.dp else 12.dp,
                        )
                    }
                }
                item(key = "count") {
                    Text(
                        text = if (filtered.isEmpty()) Strings.get("player_streams_none_match")
                        else if (filtered.size == 1) Strings.format("player_streams_count_one", filtered.size)
                        else Strings.format("player_streams_count_many", filtered.size),
                        color = JellioTextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                itemsIndexed(filtered, key = { _, it -> it.Id ?: it.hashCode() }) { index, source ->
                    val blocked = policy.blocking && source.Id in needsReencode && source.Id != currentMediaSourceId
                    SourceCard(
                        source = source,
                        onClick = {
                            if (blocked) {
                                onBlocked()
                            } else {
                                if (source.Id != currentMediaSourceId) onSelect(source)
                                onDismiss()
                            }
                        },
                        isActive = source.Id == currentMediaSourceId,
                        transcodeMark = true,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
                    )
                }
            }
        }
    }
}

// Episodes side panel right drawer: season tabs plus episode list.
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
internal fun EpisodesPanel(
    seasons: List<BaseItemDto>,
    selectedSeasonId: String?,
    episodes: List<EpisodePanelEntry>,
    currentItemId: String,
    onSelectSeason: (String) -> Unit,
    onSelectEpisode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstFocusRequester = remember { FocusRequester() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val seasonRowState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentIndex = episodes.indexOfFirst { it.itemId == currentItemId }
    // Opens on the playing episode: scrolled to and focused, its season
    // tab in view; another season opens at its first episode.
    LaunchedEffect(episodes) {
        if (episodes.isEmpty()) return@LaunchedEffect
        seasons.indexOfFirst { it.Id == selectedSeasonId }.takeIf { it >= 0 }?.let { seasonRowState.scrollToItem(it) }
        listState.scrollToItem(if (currentIndex > 0) currentIndex - 1 else 0)
        kotlinx.coroutines.delay(60)
        runCatching { firstFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 48.dp),
        ) {
            Text(text = Strings.get("detail_episodes"), color = JellioText, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
            LazyRow(
                state = seasonRowState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                items(seasons, key = { it.Id }) { season ->
                    val isActive = season.Id == selectedSeasonId
                    Surface(
                        onClick = { onSelectSeason(season.Id) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (isActive) JellioSecondary else Color.White.copy(alpha = 0.08f),
                            contentColor = if (isActive) JellioBg else JellioText,
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
                        Text(text = com.jellio.tv.ui.i18n.seasonDisplayName(season), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                itemsIndexed(episodes, key = { _, it -> it.itemId }) { index, episode ->
                    EpisodeRow(
                        episode = episode,
                        isActive = episode.itemId == currentItemId,
                        onClick = { onSelectEpisode(episode.itemId) },
                        modifier = if (index == (if (currentIndex >= 0) currentIndex else 0)) Modifier.focusRequester(firstFocusRequester) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
internal fun EpisodeRow(
    episode: EpisodePanelEntry,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) JellioSecondary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp),
            )
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.width(120.dp).height(68.dp).background(Color.Black, RoundedCornerShape(8.dp))) {
                if (episode.thumbnailUrl != null) {
                    AsyncImage(
                        model = episode.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    )
                }
                if (episode.rating != null) {
                    Text(
                        text = episode.rating,
                        color = JellioText,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                    )
                }
                if (episode.episodeCode != null) {
                    Text(
                        text = episode.episodeCode,
                        color = JellioText,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = com.jellio.tv.ui.i18n.episodeDisplayName(episode.title), color = JellioText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (episode.overview != null) {
                    Text(
                        text = episode.overview,
                        color = JellioTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
internal fun SubtitleMenu(
    tracks: List<SubtitleTrackUiState>,
    selectedIndex: Int?,
    onSelect: (SubtitleTrackUiState?) -> Unit,
    subtitleStyle: SubtitleStyle,
    onSetSubtitleSize: (String) -> Unit,
    onSetSubtitleBackground: (String) -> Unit,
    onSetSubtitleLook: (SubtitleStyle) -> Unit,
    subtitleOffsetMs: Int,
    onSubtitleOffset: (Int) -> Unit,
    canSyncSubtitles: Boolean,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { firstFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxSize()
                .background(JellioBgElevated)
                .padding(vertical = 48.dp),
        ) {
            Text(
                text = Strings.get("tv_subtitles"),
                color = JellioText,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            SubtitlePreview(style = subtitleStyle)
            LazyColumn {
                item {
                    SubtitleMenuRow(
                        label = Strings.get("state_off"),
                        isSelected = selectedIndex == null,
                        onClick = { onSelect(null) },
                        modifier = Modifier.focusRequester(firstFocusRequester),
                    )
                }
                items(tracks) { track ->
                    SubtitleMenuRow(label = track.label, isSelected = selectedIndex == track.streamIndex, onClick = { onSelect(track) })
                }
                item {
                    SubtitleStyleGroup(
                        label = com.jellio.tv.ui.i18n.string("player_subtitle_size"),
                        options = SUBTITLE_SIZES.map { it.value to Strings.get(it.key) },
                        selected = subtitleStyle.size,
                        onSelect = onSetSubtitleSize,
                    )
                }
                item {
                    SubtitleStyleGroup(
                        label = com.jellio.tv.ui.i18n.string("player_subtitle_bg"),
                        options = SUBTITLE_BACKGROUNDS.map { it.value to Strings.get(it.key) },
                        selected = subtitleStyle.background,
                        onSelect = onSetSubtitleBackground,
                    )
                }
                item {
                    SubtitleStyleGroup(
                        label = Strings.get("player_subtitle_color"),
                        options = SUBTITLE_COLORS.map { it.first to Strings.get("subtitle_color_" + it.first) },
                        selected = subtitleStyle.color,
                        onSelect = { onSetSubtitleLook(subtitleStyle.copy(color = it)) },
                    )
                }
                item {
                    SubtitleStyleGroup(
                        label = Strings.get("player_subtitle_edge"),
                        options = SUBTITLE_EDGES.map { it to Strings.get("subtitle_edge_" + it) },
                        selected = subtitleStyle.edge,
                        onSelect = { onSetSubtitleLook(subtitleStyle.copy(edge = it)) },
                    )
                }
                item {
                    SubtitleStyleGroup(
                        label = Strings.get("player_subtitle_position"),
                        options = SUBTITLE_POSITIONS.map { it.first to Strings.get("subtitle_pos_" + it.first) },
                        selected = subtitleStyle.position,
                        onSelect = { onSetSubtitleLook(subtitleStyle.copy(position = it)) },
                    )
                }
                if (canSyncSubtitles) {
                    item {
                        SyncAdjustRow(
                            label = Strings.get("player_subtitle_sync"),
                            valueMs = subtitleOffsetMs,
                            stepMs = 100,
                            onChange = onSubtitleOffset,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SubtitleStyleGroup(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = label,
            color = JellioTextSecondary,
            style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, optionLabel) ->
                Surface(
                    onClick = { onSelect(value) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (value == selected) Color.White.copy(alpha = 0.18f) else JellioBg,
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
                    Text(
                        text = optionLabel,
                        style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

internal fun subtitleCaptionStyle(style: SubtitleStyle): CaptionStyleCompat {
    val backgroundColor = when (subtitleBackgroundOption(style.background).value) {
        "none" -> Color.Transparent.toArgb()
        "solid" -> android.graphics.Color.argb(230, 0, 0, 0)
        else -> android.graphics.Color.argb(128, 0, 0, 0)
    }
    val textColor = SUBTITLE_COLORS.firstOrNull { it.first == style.color }?.second ?: Color.White.toArgb()
    val edgeType = when (style.edge) {
        "outline" -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        "shadow" -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        "raised" -> CaptionStyleCompat.EDGE_TYPE_RAISED
        else -> CaptionStyleCompat.EDGE_TYPE_NONE
    }
    return CaptionStyleCompat(
        textColor,
        backgroundColor,
        Color.Transparent.toArgb(),
        edgeType,
        android.graphics.Color.BLACK,
        null,
    )
}

internal fun subtitleFractionalTextSize(style: SubtitleStyle): Float {
    val option = subtitleSizeOption(style.size)
    val medium = SUBTITLE_SIZES.first { it.value == "medium" }
    return SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (option.rem / medium.rem)
}

// A sample line drawn the way subtitles will look, updating as the menu changes.
@Composable
internal fun SubtitlePreview(style: SubtitleStyle) {
    val textColor = Color(SUBTITLE_COLORS.firstOrNull { it.first == style.color }?.second ?: android.graphics.Color.WHITE)
    val background = when (style.background) {
        "none" -> Color.Transparent
        "solid" -> Color.Black.copy(alpha = 0.9f)
        else -> Color.Black.copy(alpha = 0.5f)
    }
    val shadow = when (style.edge) {
        "outline" -> androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset.Zero, 6f)
        "shadow" -> androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(3f, 3f), 4f)
        "raised" -> androidx.compose.ui.graphics.Shadow(Color.Black, androidx.compose.ui.geometry.Offset(-2f, -2f), 2f)
        else -> null
    }
    val size = subtitleSizeOption(style.size).rem
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(84.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF2B3A55), Color(0xFF151B28))), RoundedCornerShape(10.dp)),
    ) {
        Text(
            text = Strings.get("player_subtitle_preview"),
            color = textColor,
            fontSize = (13f * size).sp,
            style = androidx.compose.ui.text.TextStyle(shadow = shadow),
            modifier = Modifier.background(background, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// Moves subtitles or sound earlier (-) or later (+) in steps, with a reset.
@Composable
internal fun SyncAdjustRow(label: String, valueMs: Int, stepMs: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(text = label, color = JellioTextSecondary, style = androidx.tv.material3.MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            SyncButton("−") { onChange((valueMs - stepMs).coerceAtLeast(-10_000)) }
            Text(
                text = (if (valueMs > 0) "+" else "") + String.format(java.util.Locale.US, "%.2f s", valueMs / 1000f),
                color = JellioText,
                modifier = Modifier.width(76.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            SyncButton("+") { onChange((valueMs + stepMs).coerceAtMost(10_000)) }
            if (valueMs != 0) SyncButton(Strings.get("player_sync_reset")) { onChange(0) }
        }
    }
}

@Composable
internal fun SyncButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.1f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.3f),
            focusedContentColor = JellioText,
        ),
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
internal fun SubtitleMenuRow(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(8.dp),
            )
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        )
    }
}

// Stands in for a title with no logo image: its name, pulsing slowly
// the way the logo does.
@Composable
internal fun PulsingTitle(text: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing_title")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "title_alpha",
    )
    Text(
        text = text,
        color = JellioText.copy(alpha = alpha),
        style = androidx.tv.material3.MaterialTheme.typography.displaySmall,
        modifier = modifier.padding(horizontal = 48.dp),
    )
}
