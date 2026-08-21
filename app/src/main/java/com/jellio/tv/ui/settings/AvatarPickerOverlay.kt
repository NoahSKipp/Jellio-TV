package com.jellio.tv.ui.settings

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// Real port of components/avatarPicker.js's own openAvatarPicker():
// a grid of real preset tiles (Jellio's own AvatarsController) plus
// an Upload tile, the exact same real tile shape (a button wrapping
// an image) already reading as "pick this option" for either kind.
// Uses Android's own document picker for the upload tile rather than
// that file's own hidden <input type="file">, the closest real
// platform equivalent.
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
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Reading a picked file's own bytes through ContentResolver is
        // blocking I/O, off the main thread here the same real reason
        // JellioRepository's own preset-bytes read is too.
        scope.launch(Dispatchers.IO) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes != null) {
                val contentType = context.contentResolver.getType(uri) ?: "image/*"
                onUpload(bytes, contentType)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
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
            Text(text = "Choose an avatar", color = JellioText, style = MaterialTheme.typography.titleLarge)
            status?.let {
                Text(text = it, color = JellioTextSecondary, modifier = Modifier.padding(top = 8.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize().padding(top = 20.dp),
            ) {
                // Appended unconditionally, ahead of the real preset
                // list: uploading a real device file has no real
                // dependency on the preset fetch ever succeeding at
                // all, same real reasoning that file's own comment
                // documents for its own upload tile.
                item {
                    AvatarUploadTile(busy = busyKey == "upload", onClick = { launcher.launch("image/*") })
                }
                items(presets, key = { it.Id }) { preset ->
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

@Composable
private fun AvatarUploadTile(busy: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = JellioBg),
        modifier = Modifier.aspectRatio(1f),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = Icons.Filled.AddAPhoto, contentDescription = "Upload your own picture or gif", tint = JellioText)
            Text(text = "Upload", color = JellioTextSecondary, modifier = Modifier.padding(top = 6.dp))
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
        modifier = Modifier.aspectRatio(1f),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
