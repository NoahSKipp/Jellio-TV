package com.jellio.tv.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioBg
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioSecondary
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary

// Real feedback live: a real per-app-open update check with nothing
// on screen to surface it isn't a real notification at all. A small
// floating card rather than a real full-screen scrim: this is
// information, not a real blocking choice the rest of the screen
// needs to wait on.
// Focus still claimed on appearance and trapped while it's up though,
// same real pattern every other overlay in this app already uses:
// unprompted content appearing with no D-pad path to it yet is a real
// dead end regardless of how much of the screen it covers.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UpdateToast(
    version: String,
    downloading: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { downloadFocusRequester.requestFocus() }
    BackHandler(onBack = onDismiss)

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .padding(bottom = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(JellioBgElevated.copy(alpha = 0.98f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(24.dp)
                .focusProperties { exit = { FocusRequester.Cancel } },
        ) {
            Text(text = "Update available", style = MaterialTheme.typography.titleMedium, color = JellioText)
            Text(
                text = "Jellio TV $version is ready to download.",
                style = MaterialTheme.typography.bodyMedium,
                color = JellioTextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = onDismiss,
                    enabled = !downloading,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), contentColor = JellioText),
                ) {
                    Text(text = "Cancel", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
                Surface(
                    onClick = onDownload,
                    enabled = !downloading,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioSecondary, contentColor = JellioBg),
                    modifier = Modifier.focusRequester(downloadFocusRequester),
                ) {
                    Text(text = if (downloading) "Downloading..." else "Download", modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        }
    }
}
