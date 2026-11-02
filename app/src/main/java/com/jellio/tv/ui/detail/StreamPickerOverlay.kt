package com.jellio.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.languageName
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

internal val QUALITY_ORDER = listOf("4K", "QHD", "FHD", "HD", "SD")
private val QUALITY_NAME_PATTERNS = listOf(
    // Names say "720p" or just "HD", "Full HD", "Ultra HD"; HDR and HDTV
    // aren't resolutions.
    "4K" to Regex("(^|[^a-z0-9])(2160p|4k|uhd|ultra[ .-]?hd)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
    "QHD" to Regex("(^|[^a-z0-9])(1440p|qhd|2k)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
    "FHD" to Regex("(^|[^a-z0-9])(1080[pi]|fhd|full[ .-]?hd)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
    "HD" to Regex("(^|[^a-z0-9])(720p|hd)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
    "SD" to Regex("(^|[^a-z0-9])(576p|540p|480p|360p|240p|sd|dvdrip)([^a-z0-9+]|$)", RegexOption.IGNORE_CASE),
)

private fun matchQuality(text: String): String? =
    QUALITY_NAME_PATTERNS.firstOrNull { it.second.containsMatchIn(text) }?.first

// The tier the addon's own label (the name's first line) states, or null.
internal fun labelQuality(source: MediaSourceDto): String? =
    source.Name?.substringBefore('\n')?.let { matchQuality(it) }

internal fun sourceQuality(source: MediaSourceDto): String? {
    labelQuality(source)?.let { return it }
    val video = source.MediaStreams?.firstOrNull { it.Type == "Video" }
    val height = video?.Height ?: 0
    val width = video?.Width ?: 0
    if (height > 0 || width > 0) {
        return when {
            height >= 2000 || width >= 3200 -> "4K"
            height >= 1300 || width >= 2400 -> "QHD"
            height >= 900 || width >= 1800 -> "FHD"
            height >= 650 || width >= 1200 -> "HD"
            else -> "SD"
        }
    }
    return source.Name?.let { matchQuality(it) }
}

internal fun sourceResolutionLabel(source: MediaSourceDto): String {
    val height = source.MediaStreams?.firstOrNull { it.Type == "Video" }?.Height ?: return ""
    return if (height >= 2000) "4K" else "${height}p"
}

internal fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1) return "%.1f GB".format(gb)
    val mb = (bytes / (1024.0 * 1024.0)).toInt()
    return if (mb > 0) "$mb MB" else ""
}

internal fun sourceAudioLabel(source: MediaSourceDto): String {
    val audio = source.MediaStreams?.firstOrNull { it.Type == "Audio" } ?: return ""
    val parts = mutableListOf<String>()
    audio.Codec?.let { parts.add(it.uppercase()) }
    audio.Channels?.let { parts.add("${it}ch") }
    return parts.joinToString(" ")
}

private fun sourceBitrateLabel(source: MediaSourceDto): String {
    val bitRate = source.MediaStreams?.firstOrNull { it.Type == "Video" }?.BitRate ?: return ""
    return "%.1f Mbps".format(bitRate / 1_000_000.0)
}

internal fun sourceDescription(source: MediaSourceDto): String =
    (source.Name ?: "").split("\n").drop(1).joinToString(" ").trim()

private const val TICKS_PER_SECOND = 10_000_000L

private fun formatResumeLabel(ticks: Long): String {
    val totalSeconds = ticks / TICKS_PER_SECOND
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
}

private const val REGIONAL_INDICATOR_BASE = 0x1F1E6

private val FLAG_COUNTRY_TO_LANGUAGE = mapOf(
    "DE" to "ger", "AT" to "ger", "CH" to "ger",
    "GB" to "eng", "US" to "eng", "CA" to "eng", "AU" to "eng", "IE" to "eng",
    "FR" to "fre",
    "ES" to "spa", "MX" to "spa", "AR" to "spa",
    "IT" to "ita",
    "JP" to "jpn",
    "KR" to "kor",
    "CN" to "chi", "TW" to "chi", "HK" to "chi",
    "RU" to "rus",
    "PT" to "por", "BR" to "por",
    "NL" to "dut",
    "SA" to "ara", "AE" to "ara",
    "PL" to "pol",
    "SE" to "swe",
    "TR" to "tur",
)

private fun flagLanguages(text: String?): List<String> {
    if (text.isNullOrEmpty()) return emptyList()
    val codes = mutableListOf<String>()
    val points = text.codePoints().toArray()
    var i = 0
    while (i < points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        if (a < REGIONAL_INDICATOR_BASE || a > REGIONAL_INDICATOR_BASE + 25) {
            i++
            continue
        }
        if (b < REGIONAL_INDICATOR_BASE || b > REGIONAL_INDICATOR_BASE + 25) {
            i++
            continue
        }
        val country = "" + ('A' + (a - REGIONAL_INDICATOR_BASE)) + ('A' + (b - REGIONAL_INDICATOR_BASE))
        val code = FLAG_COUNTRY_TO_LANGUAGE[country]
        if (code != null && !codes.contains(code)) codes.add(code)
        i += 2
    }
    return codes
}

internal fun sourceAudioLanguages(source: MediaSourceDto): List<String> {
    val codes = mutableListOf<String>()
    source.MediaStreams?.forEach { stream ->
        if (stream.Type == "Audio" && !stream.Language.isNullOrEmpty()) {
            val code = stream.Language.lowercase()
            if (!codes.contains(code)) codes.add(code)
        }
    }
    flagLanguages(source.Name).forEach { code -> if (!codes.contains(code)) codes.add(code) }
    return codes
}

private sealed interface SourcesState {
    data object Loading : SourcesState
    data class Loaded(val sources: List<MediaSourceDto>) : SourcesState
    data class Error(val message: String) : SourcesState
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StreamPickerOverlay(
    item: BaseItemDto,
    backdropUrl: String?,
    loadSources: suspend (forceFresh: Boolean) -> List<MediaSourceDto>,
    rememberedSourceId: suspend () -> String?,
    onSelect: (MediaSourceDto) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf<SourcesState>(SourcesState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var remembered by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableStateOf<String?>(null) }
    val initialFocusRequester = remember { FocusRequester() }
    val resumeFocusRequester = remember { FocusRequester() }
    val firstSourceCardFocusRequester = remember { FocusRequester() }
    var firstCardHasFocus by remember { mutableStateOf(false) }
    val streamListState = rememberLazyListState()
    LaunchedEffect(state) {
        val current = state
        if (current is SourcesState.Loaded && current.sources.isNotEmpty()) {
            streamListState.scrollToItem(0)
            for (attempt in 0 until 30) {
                withFrameNanos {}
                if (firstCardHasFocus) break
                runCatching { firstSourceCardFocusRequester.requestFocus() }
            }
        }
        if (current !is SourcesState.Loading) {
            runCatching { initialFocusRequester.requestFocus() }
        }
    }
    LaunchedEffect(item.Id, reloadKey) {
        state = SourcesState.Loading
        state = try {
            val policy = com.jellio.tv.data.playback.VideoTranscodePolicy
            val loaded = loadSources(reloadKey > 0)
            val kept = if (policy.blocking) loaded.filterNot { policy.needsReencode(it) } else loaded
            if (policy.blocking && loaded.isNotEmpty() && kept.isEmpty()) {
                SourcesState.Error(com.jellio.tv.ui.i18n.Strings.get("stream_policy_blocked"))
            } else {
                SourcesState.Loaded(
                    kept.sortedBy {
                        (if (com.jellio.tv.ui.player.BadStreams.isBad(item.Id, it)) 2 else 0) +
                            (if (policy.avoiding && policy.needsReencode(it)) 1 else 0)
                    },
                )
            }
        } catch (err: Exception) {
            SourcesState.Error(err.message ?: com.jellio.tv.ui.i18n.Strings.get("stream_load_error"))
        }
    }
    LaunchedEffect(item.Id) { remembered = rememberedSourceId() }
    LaunchedEffect(item.Id) {
        selectedLanguage = null
        selectedQuality = null
    }
    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier.fillMaxSize()
            .focusGroup().focusProperties { onExit = { FocusRequester.Cancel } }
            .background(JellioBg),
    ) {
        if (backdropUrl != null) {
            coil3.compose.AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(JellioBg.copy(alpha = 0.55f), JellioBg), startX = 0f),
            ),
        )

        val isEpisode = item.Type == "Episode" && item.SeriesName != null
        Column(modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = 560.dp).fillMaxSize().padding(48.dp)) {
            Box(modifier = Modifier.padding(top = 24.dp)) {
                Text(text = if (isEpisode) item.SeriesName.orEmpty() else item.Name.orEmpty(), style = MaterialTheme.typography.titleLarge, color = JellioText)
            }
            if (isEpisode) {
                val code = if (item.ParentIndexNumber != null && item.IndexNumber != null) "S${item.ParentIndexNumber}E${item.IndexNumber} - " else ""
                Text(text = code + item.Name.orEmpty(), color = JellioTextSecondary, modifier = Modifier.padding(top = 4.dp))
            }

            when (val currentState = state) {
                is SourcesState.Loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Text(text = com.jellio.tv.ui.i18n.string("status_loading"), color = JellioTextSecondary)
                }
                is SourcesState.Error -> Column(modifier = Modifier.padding(top = 48.dp)) {
                    Text(text = currentState.message, color = JellioTextSecondary)
                    RetryButton(onClick = { reloadKey++ }, focusRequester = initialFocusRequester)
                }
                is SourcesState.Loaded -> if (currentState.sources.isEmpty()) {
                    val refreshRequester = remember { FocusRequester() }
                    val allChipRequester = remember { FocusRequester() }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 20.dp)
                                .focusProperties { up = FocusRequester.Cancel },
                        ) {
                            Surface(
                                onClick = { reloadKey++ },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = JellioBgElevated,
                                    contentColor = JellioText,
                                    focusedContainerColor = Color.White.copy(alpha = 0.18f),
                                    focusedContentColor = JellioText,
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier
                                    .focusRequester(refreshRequester)
                                    .focusProperties {
                                        down = initialFocusRequester
                                        right = allChipRequester
                                        left = FocusRequester.Cancel
                                    },
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = com.jellio.tv.ui.i18n.string("stream_refresh"),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Surface(
                                onClick = {},
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = JellioSecondary,
                                    contentColor = JellioBg,
                                    focusedContainerColor = Color.White.copy(alpha = 0.18f),
                                    focusedContentColor = JellioText,
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier = Modifier
                                    .focusRequester(allChipRequester)
                                    .focusProperties {
                                        down = initialFocusRequester
                                        left = refreshRequester
                                        right = FocusRequester.Cancel
                                    },
                            ) {
                                Text(text = com.jellio.tv.ui.i18n.string("stream_all"), modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                            }
                        }
                        Surface(
                            onClick = { reloadKey++ },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = JellioBgElevated,
                                contentColor = JellioText,
                                focusedContainerColor = Color.White.copy(alpha = 0.28f),
                                focusedContentColor = JellioText,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = androidx.tv.material3.Border(
                                    border = androidx.compose.foundation.BorderStroke(2.5.dp, Color.White),
                                    shape = RoundedCornerShape(12.dp),
                                ),
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                            modifier = Modifier
                                .padding(top = 20.dp)
                                .fillMaxWidth()
                                .focusRequester(initialFocusRequester)
                                .focusProperties {
                                    up = refreshRequester
                                    down = FocusRequester.Cancel
                                    left = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                },
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = com.jellio.tv.ui.i18n.string("stream_release_filter_title"),
                                    color = JellioText,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = com.jellio.tv.ui.i18n.string("stream_release_filter_desc"),
                                    color = JellioTextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                } else {
                    val resumeTicks = item.UserData?.PlaybackPositionTicks ?: 0
                    val languageCounts = linkedMapOf<String, Int>()
                    currentState.sources.forEach { source ->
                        sourceAudioLanguages(source).forEach { code -> languageCounts[code] = (languageCounts[code] ?: 0) + 1 }
                    }
                    val languages = languageCounts.keys.sortedWith(
                        compareByDescending<String> { languageCounts[it] ?: 0 }.thenBy { languageName(it) },
                    )
                    val chipFocusRequesters = remember(languages) {
                        val map = mutableMapOf<String?, FocusRequester>()
                        map[null] = FocusRequester()
                        languages.forEach { map[it] = FocusRequester() }
                        map
                    }
                    val qualities = remember(currentState.sources) {
                        val present = currentState.sources.mapNotNull { sourceQuality(it) }.toSet()
                        QUALITY_ORDER.filter { it in present }
                    }
                    val qualityFocusRequesters = remember(qualities) {
                        val map = mutableMapOf<String?, FocusRequester>()
                        map[null] = FocusRequester()
                        qualities.forEach { map[it] = FocusRequester() }
                        map
                    }
                    val showLanguages = languages.size > 1
                    val showQualities = qualities.size > 1
                    // Language and quality combine: a source has to match both.
                    val filteredSources = currentState.sources.filter { source ->
                        (selectedLanguage == null || sourceAudioLanguages(source).contains(selectedLanguage)) &&
                            (selectedQuality == null || sourceQuality(source) == selectedQuality)
                    }

                    android.util.Log.d(
                        "StreamPickerDpad",
                        "loaded sources=${currentState.sources.size} filtered=${filteredSources.size} " +
                            "resumeTicks=$resumeTicks languages=${languages.size}",
                    )
                    LazyColumn(
                        state = streamListState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).focusRestorer(),
                    ) {
                        item(key = "header_resume") {
                            if (resumeTicks > 0) {
                                Surface(
                                    onClick = {
                                        val target = currentState.sources.find { it.Id == remembered } ?: currentState.sources.firstOrNull()
                                        target?.let { onSelect(it) }
                                    },
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioText, contentColor = JellioBg),
                                    modifier = Modifier
                                        .padding(top = 24.dp)
                                        .focusRequester(initialFocusRequester)
                                        .focusRequester(resumeFocusRequester)
                                        .onFocusChanged { android.util.Log.d("StreamPickerDpad", "resume focus=${it.isFocused}") },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                                        Text(text = com.jellio.tv.ui.i18n.formatString("stream_resume_from", formatResumeLabel(resumeTicks)), modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }
                        item(key = "header_chips") {
                            if (showLanguages) {
                                FilterChipRow(
                                    chips = listOf<Pair<String?, String>>(null to com.jellio.tv.ui.i18n.string("stream_all")) + languages.map { it to languageName(it) },
                                    selected = selectedLanguage,
                                    onSelect = { selectedLanguage = it },
                                    firstChipFocusRequester = if (resumeTicks <= 0) initialFocusRequester else null,
                                    chipFocusRequesters = chipFocusRequesters,
                                    // Up from the badges goes back to Resume from.
                                    upTarget = if (resumeTicks > 0) resumeFocusRequester else null,
                                )
                            }
                        }
                        item(key = "header_quality_chips") {
                            if (showQualities) {
                                FilterChipRow(
                                    chips = listOf<Pair<String?, String>>(null to "All") + qualities.map { it to it },
                                    selected = selectedQuality,
                                    onSelect = { selectedQuality = it },
                                    firstChipFocusRequester = if (resumeTicks <= 0 && !showLanguages) initialFocusRequester else null,
                                    chipFocusRequesters = qualityFocusRequesters,
                                    upTarget = if (showLanguages) chipFocusRequesters[selectedLanguage] else if (resumeTicks > 0) resumeFocusRequester else null,
                                    topPadding = if (showLanguages) 12.dp else 20.dp,
                                )
                            }
                        }
                        item(key = "header_text") {
                            Text(
                                text = if (filteredSources.isEmpty()) com.jellio.tv.ui.i18n.string("stream_no_matches") else com.jellio.tv.ui.i18n.formatString("stream_count_found", filteredSources.size),
                                color = JellioTextSecondary,
                                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                            )
                        }
                        itemsIndexed(filteredSources, key = { index, source -> source.Id ?: index }) { index, source ->
                            SourceCard(
                                source = source,
                                recentlyFailed = com.jellio.tv.ui.player.BadStreams.isBad(item.Id, source),
                                onClick = { onSelect(source) },
                                modifier = if (index == 0) {
                                    Modifier
                                        .focusRequester(firstSourceCardFocusRequester)
                                        .let { if (resumeTicks <= 0 && !showLanguages && !showQualities) it.focusRequester(initialFocusRequester) else it }
                                        .onFocusChanged {
                                            firstCardHasFocus = it.hasFocus
                                            android.util.Log.d("StreamPickerDpad", "first card focus=${it.hasFocus}")
                                        }
                                        .focusProperties {
                                            up = if (showQualities) {
                                                qualityFocusRequesters[selectedQuality] ?: FocusRequester.Default
                                            } else if (showLanguages) {
                                                chipFocusRequesters[selectedLanguage] ?: FocusRequester.Default
                                            } else if (resumeTicks > 0) {
                                                resumeFocusRequester
                                            } else {
                                                FocusRequester.Default
                                            }
                                        }
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FilterChipRow(
    chips: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    firstChipFocusRequester: FocusRequester? = null,
    chipFocusRequesters: Map<String?, FocusRequester>,
    upTarget: FocusRequester? = null,
    topPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(top = topPadding)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .focusProperties {
                up = upTarget ?: FocusRequester.Cancel
            },
    ) {
        chips.forEachIndexed { index, pair ->
            val (code, label) = pair
            val isSelected = code == selected
            Surface(
                onClick = { onSelect(code) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) JellioSecondary else JellioBgElevated,
                    contentColor = if (isSelected) JellioBg else JellioText,
                    focusedContainerColor = Color.White.copy(alpha = 0.18f),
                    focusedContentColor = JellioText,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier
                    .focusRequester(chipFocusRequesters[code]!!)
                    .let { if (index == 0 && firstChipFocusRequester != null) it.focusRequester(firstChipFocusRequester) else it }
                    .onFocusChanged {
                        if (index == 0) android.util.Log.d("StreamPickerDpad", "chip[0] focus=${it.isFocused}")
                    },
            ) {
                Text(text = label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
        modifier = Modifier.padding(top = 20.dp).let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
    ) {
        Text(text = com.jellio.tv.ui.i18n.string("action_retry"), modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
    }
}

@Composable
internal fun SourceCard(
    source: MediaSourceDto,
    onClick: () -> Unit,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
    recentlyFailed: Boolean = false,
    // In the player: mark a stream that needs a video re-encode, yellow
    // under Avoid, red under Block.
    transcodeMark: Boolean = false,
) {
    val policy = com.jellio.tv.data.playback.VideoTranscodePolicy
    val classification = remember(source) {
        if (policy.avoiding && com.jellio.tv.data.playback.videoInfoOf(source) != null) policy.classify(source) else null
    }
    val reencode = classification?.takeIf { transcodeMark && it.needsVideoTranscode }
    val blocked = reencode != null && policy.blocking
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                isActive -> JellioSecondary.copy(alpha = 0.22f)
                blocked -> Color(0xFFEF5350).copy(alpha = 0.12f)
                reencode != null -> Color(0xFFFFD54F).copy(alpha = 0.08f)
                else -> JellioBgElevated
            },
            contentColor = if (blocked) JellioText.copy(alpha = 0.6f) else JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.5.dp, Color.White),
                shape = RoundedCornerShape(12.dp)
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (recentlyFailed) {
                Text(text = com.jellio.tv.ui.i18n.string("stream_bad_flag"), color = Color(0xFFFFB74D), style = MaterialTheme.typography.labelSmall)
            }
            if (classification != null) {
                OutlookBadge(com.jellio.tv.data.playback.playbackOutlook(classification, policy.policy))
            }
            Text(
                text = (source.Name ?: "Source").substringBefore('\n'),
                color = JellioText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            val description = sourceDescription(source)
            if (description.isNotEmpty()) {
                Text(text = description, color = JellioTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 1.dp))
            }
            val tags = listOfNotNull(
                labelQuality(source) ?: sourceResolutionLabel(source).ifEmpty { sourceQuality(source) },
                sourceBitrateLabel(source).ifEmpty { null },
                formatFileSize(source.Size).ifEmpty { null },
                source.Container?.uppercase(),
                sourceAudioLabel(source).ifEmpty { null },
            )
            if (tags.isNotEmpty()) {
                Text(text = tags.joinToString(" · "), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            val languageNames = sourceAudioLanguages(source)
                .mapNotNull { code -> languageName(code).takeIf { it != "Unknown" } }
                .distinct()
            if (languageNames.isNotEmpty()) {
                Text(text = languageNames.joinToString(" · "), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

// "Plays smoothly" / "Likely to stutter: ..." / "Won't play: ...", shown
// while the Video transcode policy is Avoid or Block.
@Composable
private fun OutlookBadge(outlook: com.jellio.tv.data.playback.PlaybackOutlook) {
    val (color, icon) = when (outlook.tone) {
        com.jellio.tv.data.playback.OutlookTone.Good -> Color(0xFF81C784) to Icons.Filled.CheckCircle
        com.jellio.tv.data.playback.OutlookTone.Warn -> Color(0xFFFFD54F) to Icons.Filled.Warning
        com.jellio.tv.data.playback.OutlookTone.Block -> Color(0xFFEF5350) to Icons.Filled.Block
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(bottom = 2.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            .padding(start = 6.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = outlook.text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}
