package com.jellio.tv.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.session.Session
import com.jellio.tv.ui.common.JellioTextField
import com.jellio.tv.ui.common.ScreenSpinner
import com.jellio.tv.ui.i18n.Strings
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.JellioTextTertiary
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val accent = Color(0xFFFF9800)

// A big channel list shows this many rows until a search narrows it.
private const val PLAYLIST_PREVIEW = 120

internal fun formatClock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

internal fun languageLabel(language: String): String {
    val code = mapOf(
        "English" to "en", "German" to "de", "French" to "fr", "Spanish" to "es", "Portuguese" to "pt",
        "Italian" to "it", "Dutch" to "nl", "Polish" to "pl", "Turkish" to "tr", "Arabic" to "ar", "Greek" to "el",
    )[language] ?: return language
    val display = Locale.forLanguageTag(com.jellio.tv.data.prefs.DisplayPreferences.displayLanguage.localeTag)
    return Locale.forLanguageTag(code).getDisplayLanguage(display).replaceFirstChar { it.titlecase(display) }
}

@Composable
internal fun ChannelLogo(channel: LiveChannel, imageUrl: (String, String?) -> String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(width = 84.dp, height = 56.dp).clip(RoundedCornerShape(10.dp)).background(JellioBg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = channel.name.split(Regex("[\\s\\-_.]+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() },
            color = JellioTextSecondary,
            fontWeight = FontWeight.Bold,
        )
        val logo = when {
            channel.imageTag != null -> imageUrl(channel.id, channel.imageTag)
            channel.logoUrl != null -> channel.logoUrl
            else -> null
        }
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) JellioSecondary else JellioBgElevated,
            contentColor = if (selected) JellioBg else JellioText,
            focusedContainerColor = if (selected) JellioSecondary else Color.White.copy(alpha = 0.24f),
            focusedContentColor = if (selected) JellioBg else JellioText,
        ),
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(3.dp).fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.14f))) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(accent))
    }
}

@Composable
private fun ChannelRow(
    channel: LiveChannel,
    nowMs: Long,
    imageUrl: (String, String?) -> String,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onGuide: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onPlay,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBgElevated,
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.18f),
                focusedContentColor = JellioText,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                ChannelLogo(channel, imageUrl)
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = channel.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (channel.failing) Badge(Strings.get("live_link_failing"), failing = true)
                    }
                    val now = channel.now
                    if (now != null) {
                        Text(text = now.name.ifBlank { Strings.get("live_nothing_listed") }, color = JellioTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = formatClock(now.start) + " – " + formatClock(now.end), color = JellioTextTertiary, style = MaterialTheme.typography.bodySmall)
                        ProgressBar(now.progress(nowMs), Modifier.padding(top = 4.dp))
                    } else {
                        Text(text = Strings.get("live_nothing_listed"), color = JellioTextTertiary)
                    }
                }
            }
        }
        IconAction(if (channel.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, if (channel.favorite) accent else JellioTextSecondary, onFavorite)
        IconAction(Icons.Filled.ViewList, JellioTextSecondary, onGuide)
    }
}

@Composable
private fun Badge(text: String, failing: Boolean = false) {
    Text(
        text = text,
        color = if (failing) Color(0xFFEF5350) else JellioTextSecondary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (failing) Color(0xFFEF5350).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun UserStreamRow(
    channel: LiveChannel,
    imageUrl: (String, String?) -> String,
    onPlay: () -> Unit,
    onRemove: (() -> Unit)?,
    onCheck: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            onClick = onPlay,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBgElevated,
                contentColor = JellioText,
                focusedContainerColor = Color.White.copy(alpha = 0.18f),
                focusedContentColor = JellioText,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            modifier = Modifier.weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                ChannelLogo(channel, imageUrl)
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = channel.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        channel.quality?.let { Badge(it) }
                        if (channel.failing && channel.entryIndex == null) Badge(Strings.get("live_link_failing"), failing = true)
                    }
                    if (!channel.description.isNullOrBlank()) {
                        Text(text = channel.description, color = JellioTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!channel.group.isNullOrBlank()) {
                        Text(text = channel.group, color = JellioTextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (onCheck != null) IconAction(Icons.Filled.Refresh, JellioTextSecondary, onCheck)
        if (onRemove != null) ConfirmRemove(onRemove)
    }
}

// Removing takes a second press within a few seconds.
@Composable
private fun ConfirmRemove(onRemove: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(4_000)
            armed = false
        }
    }
    IconAction(if (armed) Icons.Filled.DeleteForever else Icons.Filled.Delete, if (armed) Color(0xFFEF5350) else JellioTextSecondary) {
        if (armed) {
            armed = false
            onRemove()
        } else {
            armed = true
        }
    }
}

@Composable
private fun BrokenToggle(count: Int, open: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = JellioTextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
        ),
        modifier = Modifier.padding(top = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Icon(imageVector = if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text = Strings.format("live_broken_section", count), modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun PlaylistHeader(name: String, count: Int, failing: Boolean, onRefresh: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (failing) Badge(Strings.get("live_link_failing"), failing = true)
        Text(text = Strings.format("live_channel_count", count), color = JellioTextTertiary)
        IconAction(Icons.Filled.Refresh, JellioTextSecondary, onRefresh)
        ConfirmRemove(onRemove)
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.2f),
        ),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.padding(12.dp).size(24.dp))
    }
}

@Composable
fun LiveTvScreen(
    session: Session,
    imageUrl: (String, String?) -> String,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(session.userId) {
        viewModel.load(session)
        // What is on now moves on: refresh each minute.
        while (true) {
            delay(60_000)
            viewModel.load(session, fresh = true)
        }
    }
    val nowMs = remember(uiState.channels) { System.currentTimeMillis() }
    var brokenOpen by remember { mutableStateOf(false) }
    // A single stream sent from the phone starts right away.
    LaunchedEffect(uiState.pendingPlay) {
        val id = uiState.pendingPlay ?: return@LaunchedEffect
        viewModel.consumePendingPlay()
        viewModel.closePairing()
        onPlay(id)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 32.dp, start = 48.dp, end = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(text = Strings.get("nav_live_tv"), style = MaterialTheme.typography.titleLarge)
                if (uiState.channels.isNotEmpty() || uiState.links.isNotEmpty()) {
                    Text(text = Strings.format("live_channel_count", uiState.arranged.all.size + uiState.arranged.broken.size + uiState.myStreams.size), color = JellioTextTertiary)
                }
                Surface(
                    onClick = { viewModel.openPairing(session) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = JellioBgElevated,
                        contentColor = JellioText,
                        focusedContainerColor = JellioSecondary,
                        focusedContentColor = JellioBg,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Icon(imageVector = Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(text = Strings.get("live_link_add_title"), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            JellioTextField(
                value = uiState.query,
                onValueChange = viewModel::setQuery,
                label = Strings.get("live_search"),
                modifier = Modifier.padding(top = 16.dp).width(520.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                val filters = uiState.filters
                item { Chip(Strings.get("live_type_all"), filters.kind == null) { viewModel.setFilters(filters.copy(kind = null)) } }
                item { Chip(Strings.get("live_type_sports"), filters.kind == LiveKind.SPORTS) { viewModel.setFilters(filters.copy(kind = LiveKind.SPORTS)) } }
                item { Chip(Strings.get("live_type_tv"), filters.kind == LiveKind.TV) { viewModel.setFilters(filters.copy(kind = LiveKind.TV)) } }
                if (uiState.languages.size > 1) {
                    item { Chip(Strings.get("live_language_all"), filters.language == null) { viewModel.setFilters(filters.copy(language = null)) } }
                    items(uiState.languages, key = { it.first }) { (language, count) ->
                        Chip("${languageLabel(language)} · $count", filters.language == language) {
                            viewModel.setFilters(filters.copy(language = if (filters.language == language) null else language))
                        }
                    }
                }
            }
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ScreenSpinner() }
                uiState.error != null -> Text(text = uiState.error.orEmpty(), color = JellioTextSecondary, modifier = Modifier.padding(top = 24.dp))
                uiState.channels.isEmpty() && uiState.links.isEmpty() -> Text(text = Strings.get("live_no_channels"), color = JellioTextSecondary, modifier = Modifier.padding(top = 24.dp))
                uiState.arranged.all.isEmpty() && uiState.arranged.broken.isEmpty() && uiState.myStreams.isEmpty() -> Text(text = Strings.get("live_no_results"), color = JellioTextSecondary, modifier = Modifier.padding(top = 24.dp))
                else -> {
                    val arranged = uiState.arranged
                    val visibleIds = uiState.myStreams.map { it.id }.toSet()
                    val linkGroups = uiState.links.map { link -> link to flattenStreamLinks(listOf(link)).filter { it.id in visibleIds } }
                        .filter { it.second.isNotEmpty() }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 48.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (linkGroups.isNotEmpty()) {
                            item(key = "h-mine") { SectionTitle(Strings.get("live_link_section")) }
                            linkGroups.forEach { (link, streams) ->
                                if (link.Kind == "playlist") {
                                    item(key = "p-" + link.Id) {
                                        PlaylistHeader(link.Name.orEmpty(), streams.size, link.Health == "failing", { viewModel.refreshLink(link.Id) }, { viewModel.removeLink(link.Id) })
                                    }
                                    items(streams.take(PLAYLIST_PREVIEW), key = { "u-" + it.id }) { channel ->
                                        UserStreamRow(channel, imageUrl, { onPlay(channel.id) }, channel.entryIndex?.let { index -> { viewModel.removeEntry(link.Id, index) } })
                                    }
                                    if (streams.size > PLAYLIST_PREVIEW) {
                                        item(key = "more-" + link.Id) {
                                            Text(text = Strings.format("live_link_more", streams.size - PLAYLIST_PREVIEW), color = JellioTextTertiary)
                                        }
                                    }
                                } else {
                                    val channel = streams.first()
                                    item(key = "u-" + channel.id) {
                                        UserStreamRow(
                                            channel,
                                            imageUrl,
                                            { onPlay(channel.id) },
                                            { viewModel.removeLink(link.Id) },
                                            if (channel.failing) ({ viewModel.refreshLink(link.Id) }) else null,
                                        )
                                    }
                                }
                            }
                        }
                        if (arranged.favorites.isNotEmpty()) {
                            item(key = "h-fav") { SectionTitle(Strings.get("live_favorites")) }
                            items(arranged.favorites, key = { "f-" + it.id }) { channel ->
                                ChannelRow(channel, nowMs, imageUrl, { onPlay(channel.id) }, { viewModel.toggleFavorite(session, channel) }, { viewModel.openGuide(session, channel) })
                            }
                        }
                        if (arranged.others.isNotEmpty()) {
                            item(key = "h-all") { SectionTitle(Strings.get("live_all")) }
                            items(arranged.others, key = { "a-" + it.id }) { channel ->
                                ChannelRow(channel, nowMs, imageUrl, { onPlay(channel.id) }, { viewModel.toggleFavorite(session, channel) }, { viewModel.openGuide(session, channel) })
                            }
                        }
                        // Channels that failed to start recently, folded away at the end.
                        if (arranged.broken.isNotEmpty()) {
                            item(key = "h-broken") {
                                BrokenToggle(arranged.broken.size, brokenOpen) { brokenOpen = !brokenOpen }
                            }
                            if (brokenOpen) {
                                item(key = "h-broken-hint") {
                                    Text(text = Strings.get("live_broken_hint"), color = JellioTextTertiary, style = MaterialTheme.typography.bodySmall)
                                }
                                items(arranged.broken, key = { "b-" + it.id }) { channel ->
                                    ChannelRow(channel, nowMs, imageUrl, { onPlay(channel.id) }, { viewModel.toggleFavorite(session, channel) }, { viewModel.openGuide(session, channel) })
                                }
                            }
                        }
                    }
                }
            }
        }
        val pairing = uiState.pairing
        if (pairing != null) {
            PairingOverlay(pairing, onNewCode = { viewModel.openPairing(session) }, onClose = viewModel::closePairing)
        }
        val guideChannel = uiState.guideChannel
        if (guideChannel != null) {
            GuideSheet(
                channel = guideChannel,
                programmes = uiState.guide,
                imageUrl = imageUrl,
                onPlay = { viewModel.closeGuide(); onPlay(guideChannel.id) },
                onClose = viewModel::closeGuide,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

// The next 24 hours of one channel, over whatever screen is showing.
@Composable
internal fun GuideSheet(
    channel: LiveChannel,
    programmes: List<LiveProgramme>?,
    imageUrl: (String, String?) -> String,
    onPlay: (() -> Unit)?,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val focus = remember { FocusRequester() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.CenterEnd) {
        Column(modifier = Modifier.fillMaxHeight().width(560.dp).background(JellioBgElevated).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(channel, imageUrl)
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(text = channel.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = Strings.get("live_guide"), color = JellioTextTertiary)
                }
                if (onPlay != null) {
                    Surface(
                        onClick = onPlay,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = com.jellio.tv.ui.common.primaryButtonColors(),
                        modifier = Modifier.focusRequester(focus),
                    ) {
                        Text(text = Strings.get("action_play"), modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    }
                }
            }
            val nowMs = System.currentTimeMillis()
            when {
                programmes == null -> Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) { ScreenSpinner() }
                programmes.isEmpty() -> Text(text = Strings.get("live_guide_empty"), color = JellioTextSecondary, modifier = Modifier.padding(top = 24.dp))
                else -> LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(programmes, key = { (it.id ?: "") + it.start }) { programme ->
                        val airing = programme.airing(nowMs)
                        Surface(
                            onClick = {},
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.12f)),
                            modifier = if (onPlay == null && airing) Modifier.focusRequester(focus) else Modifier,
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = formatClock(programme.start) + " – " + formatClock(programme.end),
                                    color = JellioTextSecondary,
                                    modifier = Modifier.width(140.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = programme.name.ifBlank { Strings.get("live_nothing_listed") },
                                        fontWeight = if (airing) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (airing) ProgressBar(programme.progress(nowMs), Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(programmes != null) { runCatching { focus.requestFocus() } }
}
