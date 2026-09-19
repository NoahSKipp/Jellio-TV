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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
    // wandering back out into DetailScreen underneath either. Real
    // feedback live also removed this overlay's own visual Back button
    // below (the remote's own real system Back, already wired via
    // BackHandler, does the same real job everywhere in this app now),
    // so this can no longer anchor to that always-present real target -
    // reattached below to whichever real element is first in reading
    // order for whatever SourcesState this overlay is actually in
    // (Retry on Error/empty, Resume/the first language chip/the first
    // source card on Loaded, exactly one of those at a time), refired
    // once state actually leaves Loading rather than once on open,
    // since neither of those real targets exists yet during it.
    val initialFocusRequester = remember { FocusRequester() }
    val resumeFocusRequester = remember { FocusRequester() }
    // Still attached to the first card below (and still the initial-
    // focus target when there's neither a Resume button nor more than
    // one language) even though nothing calls requestFocus() on it for
    // Down anymore - see the LazyColumn's own focusRestorer() comment
    // for why Down doesn't need that here.
    val firstSourceCardFocusRequester = remember { FocusRequester() }
    var firstCardHasFocus by remember { mutableStateOf(false) }
    // Hoisted so the pre-warm effect below can scrollToItem(0) on the
    // exact same real list focusRestorer() further down still reads.
    val streamListState = rememberLazyListState()
    // Real fix, confirmed against Nuvio's own StreamSourcesSidePanel.kt
    // (a real shipped app solving this identical problem): its own
    // LazyColumn's focusRestorer()-equivalent state only ever needs to
    // work cold in ui/home/HomeScreen.kt, since that screen's LazyColumn
    // is the very first thing ever focused there - nothing else
    // competes for initial focus first. Here Resume/the first language
    // chip take real initial focus instead, so this list's own
    // focusRestorer() never once got a genuine successful focus landing
    // inside it to remember before a later Down press ever needed to
    // restore to one - confirmed live via Logcat across this file's
    // entire debugging history: "first card focus=" never logged once,
    // through every prior fix attempt. Nuvio's own real fix for the
    // identical shape (a stream list sitting below a chip row that
    // takes real initial focus first): explicitly pre-warm real focus
    // onto the first item the moment data loads - scrollToItem(0) plus
    // a real requestFocus() retried across real frames, runCatching
    // wrapped since the target may not exist yet in every SourcesState -
    // then move real focus on to wherever this overlay's own actual
    // initial resting position is supposed to be, leaving
    // focusRestorer() with a genuine remembered child instead of a cold
    // one for the first time.
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
            SourcesState.Loaded(loadSources())
        } catch (err: Exception) {
            SourcesState.Error(err.message ?: "Could not load streams")
        }
    }
    LaunchedEffect(item.Id) { remembered = rememberedSourceId() }
    LaunchedEffect(item.Id) { selectedLanguage = null }
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
                    Text(text = "Loading...", color = JellioTextSecondary)
                }
                is SourcesState.Error -> Column(modifier = Modifier.padding(top = 48.dp)) {
                    Text(text = currentState.message, color = JellioTextSecondary)
                    RetryButton(onClick = { reloadKey++ }, focusRequester = initialFocusRequester)
                }
                is SourcesState.Loaded -> if (currentState.sources.isEmpty()) {
                    Column(modifier = Modifier.padding(top = 48.dp)) {
                        Text(text = "No streams found for this title.", color = JellioTextSecondary)
                        RetryButton(onClick = { reloadKey++ }, focusRequester = initialFocusRequester)
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
                    val chipFocusRequesters = remember(languages) {
                        val map = mutableMapOf<String?, FocusRequester>()
                        map[null] = FocusRequester()
                        languages.forEach { map[it] = FocusRequester() }
                        map
                    }
                    val filteredSources = selectedLanguage?.let { code ->
                        currentState.sources.filter { sourceAudioLanguages(it).contains(code) }
                    } ?: currentState.sources

                    android.util.Log.d(
                        "StreamPickerDpad",
                        "loaded sources=${currentState.sources.size} filtered=${filteredSources.size} " +
                            "resumeTicks=$resumeTicks languages=${languages.size}",
                    )
                    // Real root cause, confirmed live over several
                    // rounds of Logcat tracing spanning two separate
                    // sessions: a direct firstSourceCardFocusRequester.
                    // requestFocus() call - from dispatchKeyEvent, from a
                    // real Compose LaunchedEffect, retried across ten
                    // real frames, even paired with an explicit
                    // scrollTo(0) on a plain verticalScroll() Column
                    // standing in for this LazyColumn - never once
                    // landed, no exception, no onFocusChanged. None of
                    // those were ever the actual problem. The one thing
                    // never tried across any of that: a real stable
                    // key for each item below. Real gap ui/home/
                    // HomeScreen.kt's own header already documents and
                    // already fixes there the identical way: plain
                    // Compose Foundation LazyColumn advertises no
                    // default D-pad entry point of its own for a system
                    // that has never focused anything inside it yet,
                    // and without a real per-item key, its own item
                    // recycling can silently break a remembered
                    // FocusRequester's own association with whichever
                    // node it was last attached to (the documented real
                    // upstream class this file's own prior LazyColumn
                    // attempt matched, JetBrains/compose-multiplatform
                    // #3526) - the standard real fix for that is a
                    // stable key, not abandoning LazyColumn outright.
                    // focusRestorer() plus a real per-item key below
                    // (source.Id, falling back to index for the one
                    // pathological source with none) is the same
                    // proven-working shape HomeScreen.kt already ships,
                    // neither piece ever combined here before.
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
                                        Text(text = "Resume from ${formatResumeLabel(resumeTicks)}", modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }
                        item(key = "header_chips") {
                            if (languages.size > 1) {
                                LanguageFilterChips(
                                    languages = languages,
                                    selected = selectedLanguage,
                                    onSelect = { selectedLanguage = it },
                                    firstChipFocusRequester = if (resumeTicks <= 0) initialFocusRequester else null,
                                    chipFocusRequesters = chipFocusRequesters,
                                )
                            }
                        }
                        item(key = "header_text") {
                            Text(
                                text = "${filteredSources.size} stream${if (filteredSources.size == 1) "" else "s"} found",
                                color = JellioTextSecondary,
                                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                            )
                        }
                        itemsIndexed(filteredSources, key = { index, source -> source.Id ?: index }) { index, source ->
                            SourceCard(
                                source = source,
                                onClick = { onSelect(source) },
                                modifier = if (index == 0) {
                                    Modifier
                                        .focusRequester(firstSourceCardFocusRequester)
                                        .let { if (resumeTicks <= 0 && languages.size <= 1) it.focusRequester(initialFocusRequester) else it }
                                        .onFocusChanged {
                                            firstCardHasFocus = it.hasFocus
                                            android.util.Log.d("StreamPickerDpad", "first card focus=${it.hasFocus}")
                                        }
                                        .focusProperties {
                                            up = if (languages.size > 1) {
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
private fun LanguageFilterChips(
    languages: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    firstChipFocusRequester: FocusRequester? = null,
    chipFocusRequesters: Map<String?, FocusRequester>,
) {
    val chips = buildList {
        add(null to "All")
        languages.forEach { code -> add(code to languageName(code)) }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .padding(top = 20.dp)
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .focusProperties { 
                up = FocusRequester.Cancel
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
        Text(text = "Retry", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
    }
}

@Composable
internal fun SourceCard(source: MediaSourceDto, onClick: () -> Unit, isActive: Boolean = false, modifier: Modifier = Modifier) {
    // Real .jellio-stream-picker-card-active treatment: a
    // JellioSecondary border plus a slightly brighter fill, ported as a
    // plain color swap here rather than a real border stroke, the same
    // simplification the rest of this app's own selected-state cards
    // (DetailScreen's own season tabs) already use.
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        // Real bug found live, on a real screenshot: this card set no
        // real focusedContainerColor/focusedContentColor of its own, so
        // TV Material3's own real default focused pair took over
        // instead - a real bright near-white fill this app never
        // designed its own explicit JellioTextSecondary tag/language
        // text against, reading as low contrast once focused. The same
        // real dark-fill/bright-text pair every other real selectable
        // text row in this app already uses instead.
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) JellioSecondary.copy(alpha = 0.16f) else JellioBgElevated,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
        ),
        // Real bug found live, on a real screenshot: this card's own
        // real tags/language line ran close enough to its own edge that
        // TV Material3's own default focus grow pushed it past this
        // card's own clipped bounds on focus, reading as "badges cut
        // off on the side when selected" - same real class of fix as
        // ui/library/LibraryFilterFieldOverlay.kt's own header covers.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        // Real feedback live: shrunk from 16dp/2dp/6dp/4dp so more of
        // this real list fits on screen at once without scrolling.
        Column(modifier = Modifier.padding(12.dp)) {
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
                sourceResolutionLabel(source).ifEmpty { null },
                sourceBitrateLabel(source).ifEmpty { null },
                formatFileSize(source.Size).ifEmpty { null },
                source.Container?.uppercase(),
                sourceAudioLabel(source).ifEmpty { null },
            )
            if (tags.isNotEmpty()) {
                Text(text = tags.joinToString(" · "), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
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
                Text(text = languageNames.joinToString(" · "), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
