package com.jellio.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

// Ported from components/streamPicker.js's own real card shape
// (resolution/bitrate/size/container/audio tags off the same real
// MediaSourceInfo), skipping that file's own remember-my-stream/
// language-filter chrome for now: a real but secondary layer over the
// same real core job, picking one of Gelato's own resolved sources.
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

// Real MediaStream.BitRate, per stream video track, left blank rather
// than estimated when a source carries none, same real reasoning
// streamPicker.js's own sourceBitrateLabel() documents: an invented
// figure here would read as more real data than Gelato actually
// reported for this one source.
private fun sourceBitrateLabel(source: MediaSourceDto): String {
    val bitRate = source.MediaStreams?.firstOrNull { it.Type == "Video" }?.BitRate ?: return ""
    return "%.1f Mbps".format(bitRate / 1_000_000.0)
}

internal fun sourceDescription(source: MediaSourceDto): String =
    (source.Name ?: "").split("\n").drop(1).joinToString(" ").trim()

private const val TICKS_PER_SECOND = 10_000_000L

// mm:ss, or h:mm:ss past the first real hour, same real tick unit
// (the .NET TimeSpan constant) streamPicker.js's own formatResumeLabel()
// renders off of, not a second unit conversion invented here.
private fun formatResumeLabel(ticks: Long): String {
    val totalSeconds = ticks / TICKS_PER_SECOND
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
}

private const val REGIONAL_INDICATOR_BASE = 0x1F1E6

// One flag per major real source country for each language
// LANGUAGE_OPTIONS already covers, not every real ISO 3166 territory
// that happens to speak it, ported verbatim from streamPicker.js's
// own FLAG_COUNTRY_TO_LANGUAGE.
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

// AIOStreams' own real stream titles carry a flag emoji per embedded
// audio language right in source.Name, real signal every source
// actually has unlike MediaStreams, which streamPicker.js's own real
// bug report found only fully populated for whichever one source had
// already been played before. Walks by real Unicode code point, not a
// plain string index: a flag emoji is a surrogate pair per regional
// indicator on the JVM same as in JS.
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

// Reads both real MediaStreams.Language and the flag-emoji codes
// above and keeps whichever either one finds, same real fix
// streamPicker.js's own sourceAudioLanguages() documents: most of a
// real 38+ result set from Gelato comes back with no MediaStreams
// audio entries at all, only the flag emoji in source.Name still
// tells the two languages apart.
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

// Mirrors components/streamPicker.js's own openStreamPicker(): a real
// try/catch around getMediaSources, a failure rendered as its own
// distinct real state rather than an unhandled throw out of this
// screen's own LaunchedEffect (a real crash risk this used to carry,
// nothing here caught it before).
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
    loadSources: suspend () -> List<MediaSourceDto>,
    rememberedSourceId: suspend () -> String?,
    onSelect: (MediaSourceDto) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf<SourcesState>(SourcesState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var remembered by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    // Real bug found live testing on device, same real class every
    // other overlay in this app already had to fix: nothing here ever
    // requested initial D-pad focus, and nothing stopped focus
    // wandering back out into DetailScreen underneath either. The Back
    // button below is the one real target guaranteed to exist
    // regardless of which SourcesState this overlay is in (Loading/
    // Error/Loaded all still render it), so it is what claims focus on
    // open rather than a source card that might not exist yet.
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocusRequester.requestFocus() }

    LaunchedEffect(item.Id, reloadKey) {
        state = SourcesState.Loading
        state = try {
            SourcesState.Loaded(loadSources())
        } catch (err: Exception) {
            SourcesState.Error(err.message ?: "Could not load streams")
        }
    }
    LaunchedEffect(item.Id) { remembered = rememberedSourceId() }
    LaunchedEffect(item.Id) { selectedLanguage = null }
    BackHandler(onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize().focusProperties { exit = { FocusRequester.Cancel } }.background(JellioBg)) {
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
                    Text(text = "Loading...", color = JellioTextSecondary)
                }
                is SourcesState.Error -> Column(modifier = Modifier.padding(top = 48.dp)) {
                    Text(text = currentState.message, color = JellioTextSecondary)
                    RetryButton(onClick = { reloadKey++ })
                }
                is SourcesState.Loaded -> if (currentState.sources.isEmpty()) {
                    Column(modifier = Modifier.padding(top = 48.dp)) {
                        Text(text = "No streams found for this title.", color = JellioTextSecondary)
                        RetryButton(onClick = { reloadKey++ })
                    }
                } else {
                    // Real Jellyfin field, the same one the player's own
                    // resume logic already reads off item.UserData to
                    // seek on real playback start: this is a fast path
                    // onto that same real behaviour (streamPicker.js's
                    // own resumeTicks button), not a second resume
                    // mechanism, picking the remembered/first source and
                    // letting the player itself do the actual seek.
                    val resumeTicks = item.UserData?.PlaybackPositionTicks ?: 0
                    if (resumeTicks > 0) {
                        Surface(
                            onClick = {
                                val target = currentState.sources.find { it.Id == remembered } ?: currentState.sources.firstOrNull()
                                target?.let { onSelect(it) }
                            },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                            colors = ClickableSurfaceDefaults.colors(containerColor = JellioText, contentColor = JellioBg),
                            modifier = Modifier.padding(top = 24.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                                Text(text = "Resume from ${formatResumeLabel(resumeTicks)}", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    // Real port of streamPicker.js's own language filter
                    // chip bar: only worth showing when there is a real
                    // choice behind it, same real reasoning the whole
                    // picker already skips itself for a single-source
                    // title. Counted and ordered the same real way that
                    // file's own languageCounts/languages sort does, led
                    // by count then a real alphabetical languageName tie
                    // break.
                    val languageCounts = linkedMapOf<String, Int>()
                    currentState.sources.forEach { source ->
                        sourceAudioLanguages(source).forEach { code -> languageCounts[code] = (languageCounts[code] ?: 0) + 1 }
                    }
                    val languages = languageCounts.keys.sortedWith(
                        compareByDescending<String> { languageCounts[it] ?: 0 }.thenBy { languageName(it) },
                    )
                    val filteredSources = selectedLanguage?.let { code ->
                        currentState.sources.filter { sourceAudioLanguages(it).contains(code) }
                    } ?: currentState.sources

                    if (languages.size > 1) {
                        LanguageFilterChips(languages = languages, selected = selectedLanguage, onSelect = { selectedLanguage = it })
                    }
                    Text(
                        text = "${filteredSources.size} stream${if (filteredSources.size == 1) "" else "s"} found",
                        color = JellioTextSecondary,
                        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filteredSources) { source -> SourceCard(source = source, onClick = { onSelect(source) }) }
                    }
                }
            }
        }

        Surface(
            onClick = onDismiss,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.4f)),
            modifier = Modifier.padding(top = 32.dp, start = 32.dp).focusRequester(backFocusRequester),
        ) {
            Text(text = "Back", color = JellioText, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun LanguageFilterChips(languages: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    val chips = buildList {
        add(null to "All")
        languages.forEach { code -> add(code to languageName(code)) }
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 20.dp),
    ) {
        items(chips, key = { it.first ?: "all" }) { (code, label) ->
            val isSelected = code == selected
            Surface(
                onClick = { onSelect(code) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) JellioSecondary else JellioBgElevated,
                    contentColor = if (isSelected) JellioBg else JellioText,
                ),
            ) {
                Text(text = label, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
        modifier = Modifier.padding(top = 20.dp),
    ) {
        Text(text = "Retry", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
    }
}

@Composable
internal fun SourceCard(source: MediaSourceDto, onClick: () -> Unit, isActive: Boolean = false) {
    // Real .jellio-stream-picker-card-active treatment: a
    // JellioSecondary border plus a slightly brighter fill, ported as a
    // plain color swap here rather than a real border stroke, the same
    // simplification the rest of this app's own selected-state cards
    // (DetailScreen's own season tabs) already use.
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) JellioSecondary.copy(alpha = 0.16f) else JellioBgElevated,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = (source.Name ?: "Source").substringBefore('\n'),
                color = JellioText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = sourceDescription(source)
            if (description.isNotEmpty()) {
                Text(text = description, color = JellioTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            val tags = listOfNotNull(
                sourceResolutionLabel(source).ifEmpty { null },
                sourceBitrateLabel(source).ifEmpty { null },
                formatFileSize(source.Size).ifEmpty { null },
                source.Container?.uppercase(),
                sourceAudioLabel(source).ifEmpty { null },
            )
            if (tags.isNotEmpty()) {
                Text(text = tags.joinToString(" · "), color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
            }
            // Real bug streamPicker.js's own buildSourceCard() documents
            // and fixes: an embedded MediaStreams Language ("de", ISO
            // 639-1) and a flag-emoji-derived code ("ger", ISO 639-2/T)
            // can name the exact same real language under two different
            // strings, so this dedupes again against languageName()'s
            // own resolved display name rather than the raw code.
            val languageNames = sourceAudioLanguages(source)
                .mapNotNull { code -> languageName(code).takeIf { it != "Unknown" } }
                .distinct()
            if (languageNames.isNotEmpty()) {
                Text(text = languageNames.joinToString(" · "), color = JellioTextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
