package com.jellio.tv.ui.settings

import com.jellio.tv.ui.common.dismissOnBack
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.tv.data.model.AvatarPresetDto
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AvatarPickerOverlay(
    presets: List<AvatarPresetDto>,
    status: String?,
    busyKey: String?,
    presetImageUrl: (String) -> String,
    onSelectPreset: (String) -> Unit,
    onUpload: (ByteArray, String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val firstEntryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstEntryFocusRequester.requestFocus() }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes != null) {
                val contentType = context.contentResolver.getType(uri) ?: "image/*"
                onUpload(bytes, contentType)
            }
        }
    }

    val loosePresets = remember(presets) { presets.filter { it.Category.isNullOrBlank() } }
    val groupedPresets = remember(presets) {
        presets.filter { !it.Category.isNullOrBlank() }.groupBy { it.Category!! }
    }
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .focusGroup().focusProperties { onExit = { FocusRequester.Cancel } }
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.8f)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(32.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {}),
        ) {
            Text(text = com.jellio.tv.ui.i18n.string("avatar_choose"), color = JellioText, style = MaterialTheme.typography.titleLarge)
            status?.let {
                Text(text = it, color = JellioTextSecondary, modifier = Modifier.padding(top = 8.dp))
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize().padding(top = 20.dp).focusRestorer(),
            ) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AvatarUploadTile(
                            busy = busyKey == "upload",
                            onClick = { launcher.launch("image/*") },
                            focusRequester = firstEntryFocusRequester,
                        )
                        loosePresets.forEach { preset ->
                            AvatarPresetTile(
                                imageUrl = presetImageUrl(preset.Id),
                                busy = busyKey == preset.Id,
                                onClick = { onSelectPreset(preset.Id) },
                            )
                        }
                    }
                }
                groupedPresets.forEach { (category, categoryPresets) ->
                    val expanded = expandedCategories[category] ?: true
                    item(key = "header:$category") {
                        AvatarCategoryHeader(
                            category = category,
                            expanded = expanded,
                            onToggle = { expandedCategories[category] = !expanded },
                        )
                    }
                    if (expanded) {
                        item(key = "grid:$category") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                categoryPresets.forEach { preset ->
                                    AvatarPresetTile(
                                        imageUrl = presetImageUrl(preset.Id),
                                        busy = busyKey == preset.Id,
                                        onClick = { onSelectPreset(preset.Id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarCategoryHeader(category: String, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = JellioTextSecondary,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f),
            )
            Text(
                text = category,
                color = JellioTextSecondary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun AvatarUploadTile(busy: Boolean, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBg),
        modifier = Modifier.width(120.dp).aspectRatio(1f).let {
            if (focusRequester != null) it.focusRequester(focusRequester) else it
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = Icons.Filled.AddAPhoto, contentDescription = com.jellio.tv.ui.i18n.Strings.get("avatar_upload_prompt"), tint = JellioText)
            Text(text = com.jellio.tv.ui.i18n.string("avatar_upload_btn"), color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun AvatarPresetTile(imageUrl: String, busy: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBg),
        modifier = Modifier.width(120.dp).aspectRatio(1f),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
