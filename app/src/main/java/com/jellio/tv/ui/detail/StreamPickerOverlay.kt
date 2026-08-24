package com.jellio.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Ported from components/streamPicker.js's own real card shape
// (resolution/bitrate/size/container/audio tags off the same real
// MediaSourceInfo), skipping that file's own remember-my-stream/
// language-filter chrome for now: a real but secondary layer over the
// same real core job, picking one of Gelato's own resolved sources.
private fun sourceResolutionLabel(source: MediaSourceDto): String {
    val height = source.MediaStreams?.firstOrNull { it.Type == "Video" }?.Height ?: return ""
    return if (height >= 2000) "4K" else "${height}p"
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1) return "%.1f GB".format(gb)
    val mb = (bytes / (1024.0 * 1024.0)).toInt()
    return if (mb > 0) "$mb MB" else ""
}

private fun sourceAudioLabel(source: MediaSourceDto): String {
    val audio = source.MediaStreams?.firstOrNull { it.Type == "Audio" } ?: return ""
    val parts = mutableListOf<String>()
    audio.Codec?.let { parts.add(it.uppercase()) }
    audio.Channels?.let { parts.add("${it}ch") }
    return parts.joinToString(" ")
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

@Composable
fun StreamPickerOverlay(
    item: BaseItemDto,
    backdropUrl: String?,
    loadSources: suspend () -> List<MediaSourceDto>,
    onSelect: (MediaSourceDto) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf<SourcesState>(SourcesState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(item.Id, reloadKey) {
        state = SourcesState.Loading
        state = try {
            SourcesState.Loaded(loadSources())
        } catch (err: Exception) {
            SourcesState.Error(err.message ?: "Could not load streams")
        }
    }
    BackHandler(onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
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
                    Text(
                        text = "${currentState.sources.size} stream${if (currentState.sources.size == 1) "" else "s"} found",
                        color = JellioTextSecondary,
                        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(currentState.sources) { source -> SourceCard(source = source, onClick = { onSelect(source) }) }
                    }
                }
            }
        }

        Surface(
            onClick = onDismiss,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.4f)),
            modifier = Modifier.padding(top = 32.dp, start = 32.dp),
        ) {
            Text(text = "Back", color = JellioText, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText),
        modifier = Modifier.padding(top = 20.dp),
    ) {
        Text(text = "Retry", modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
    }
}

@Composable
private fun SourceCard(source: MediaSourceDto, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = (source.Name ?: "Source").substringBefore('\n'),
                color = JellioText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val tags = listOfNotNull(
                sourceResolutionLabel(source).ifEmpty { null },
                formatFileSize(source.Size).ifEmpty { null },
                source.Container?.uppercase(),
                sourceAudioLabel(source).ifEmpty { null },
            )
            if (tags.isNotEmpty()) {
                Text(text = tags.joinToString(" · "), color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
