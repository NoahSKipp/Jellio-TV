package com.jellio.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.ui.i18n.formatString
import com.jellio.tv.ui.i18n.string
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun RecordRemoteButtonDialog(
    onDismiss: () -> Unit,
    onSave: (keyCode: Int, label: String, trigger: com.jellio.tv.data.prefs.QuickHubTrigger) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var recordedCode by remember { mutableStateOf<Int?>(null) }
    var recordedLabel by remember { mutableStateOf("") }
    var isRestricted by remember { mutableStateOf(false) }
    var isOkKey by remember { mutableStateOf(false) }
    var selectedTrigger by remember { mutableStateOf(com.jellio.tv.data.prefs.QuickHubTrigger.LONG_PRESS) }

    val listeningFocusRequester = remember { FocusRequester() }
    val actionFocusRequester = remember { FocusRequester() }
    val currentActivity = LocalContext.current as? com.jellio.tv.MainActivity

    androidx.compose.runtime.DisposableEffect(recordedCode) {
        if (recordedCode == null) {
            val openedAtMs = android.os.SystemClock.uptimeMillis()
            currentActivity?.isRecordingShortcut = true
            currentActivity?.onShortcutKeyRecorded = { code, event ->
                if (android.os.SystemClock.uptimeMillis() - openedAtMs < 220L) {
                    true
                } else if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (code == android.view.KeyEvent.KEYCODE_BACK) {
                        onDismiss()
                        true
                    } else if (code == android.view.KeyEvent.KEYCODE_UNKNOWN) {
                        // No usable key code to match later; keep listening.
                        true
                    } else {
                        val name = com.jellio.tv.data.prefs.QuickHubPreferences.getKeyDisplayName(code)
                        recordedCode = code
                        recordedLabel = name
                        isOkKey = com.jellio.tv.data.prefs.QuickHubKeys.isOkKey(code)
                        isRestricted = isOkKey || com.jellio.tv.data.prefs.QuickHubPreferences.isSystemRestrictedKey(code)
                        true
                    }
                } else {
                    true
                }
            }
        } else {
            currentActivity?.isRecordingShortcut = false
            currentActivity?.onShortcutKeyRecorded = null
        }
        onDispose {
            currentActivity?.isRecordingShortcut = false
            currentActivity?.onShortcutKeyRecorded = null
        }
    }

    LaunchedEffect(recordedCode) {
        if (recordedCode == null) {
            repeat(10) {
                if (runCatching { listeningFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(40)
            }
        } else {
            repeat(10) {
                if (runCatching { actionFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(40)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .focusProperties { onExit = { FocusRequester.Cancel } }
            .then(
                if (recordedCode == null) {
                    Modifier
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                val code = keyEvent.nativeKeyEvent.keyCode
                                if (code == android.view.KeyEvent.KEYCODE_BACK) {
                                    onDismiss()
                                    return@onPreviewKeyEvent true
                                }
                                if (code == android.view.KeyEvent.KEYCODE_UNKNOWN) return@onPreviewKeyEvent true
                                val name = com.jellio.tv.data.prefs.QuickHubPreferences.getKeyDisplayName(code)
                                recordedCode = code
                                recordedLabel = name
                                isOkKey = com.jellio.tv.data.prefs.QuickHubKeys.isOkKey(code)
                                isRestricted = isOkKey || com.jellio.tv.data.prefs.QuickHubPreferences.isSystemRestrictedKey(code)
                                return@onPreviewKeyEvent true
                            }
                            false
                        }
                        .focusRequester(listeningFocusRequester)
                        .focusable()
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(JellioBgElevated)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(16.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val code = recordedCode
            if (code == null) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SettingsRemote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = string("settings_quick_hub_dialog_title"),
                    style = MaterialTheme.typography.titleLarge,
                    color = JellioText,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = string("settings_quick_hub_dialog_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JellioTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = string("settings_quick_hub_dialog_listening"),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = string("settings_quick_hub_dialog_hint"),
                    style = MaterialTheme.typography.labelMedium,
                    color = JellioTextSecondary,
                )
            } else if (isRestricted) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = string("settings_quick_hub_dialog_reserved_title"),
                    style = MaterialTheme.typography.titleLarge,
                    color = JellioText,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = formatString(
                        if (isOkKey) "settings_quick_hub_dialog_ok_warning" else "settings_quick_hub_dialog_reserved_warning",
                        recordedLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = JellioTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        onClick = { recordedCode = null },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            focusedContainerColor = Color.White.copy(alpha = 0.9f),
                            focusedContentColor = Color.Black,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                        modifier = Modifier.focusRequester(actionFocusRequester),
                    ) {
                        Text(
                            text = string("settings_quick_hub_dialog_try_again"),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.22f),
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                    ) {
                        Text(
                            text = string("action_cancel"),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = string("settings_quick_hub_dialog_detected_title"),
                    style = MaterialTheme.typography.titleLarge,
                    color = JellioText,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatString("settings_quick_hub_dialog_detected_btn", recordedLabel),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                val isNavKey = com.jellio.tv.data.prefs.QuickHubKeys.requiresLongPress(code)
                if (isNavKey) {
                    Text(
                        text = string("settings_quick_hub_dialog_nav_note"),
                        style = MaterialTheme.typography.bodySmall,
                        color = JellioTextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                } else {
                    Text(
                        text = string("settings_quick_hub_dialog_trigger_title"),
                        style = MaterialTheme.typography.labelLarge,
                        color = JellioTextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val isLong = selectedTrigger == com.jellio.tv.data.prefs.QuickHubTrigger.LONG_PRESS
                        Surface(
                            onClick = { selectedTrigger = com.jellio.tv.data.prefs.QuickHubTrigger.LONG_PRESS },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isLong) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                                contentColor = if (isLong) Color.White else JellioTextSecondary,
                                focusedContainerColor = Color.White.copy(alpha = 0.32f),
                                focusedContentColor = Color.White,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = if (isLong) Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))) else Border.None,
                                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = string("settings_quick_hub_trigger_long"),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            )
                        }
                        Surface(
                            onClick = { selectedTrigger = com.jellio.tv.data.prefs.QuickHubTrigger.SHORT_PRESS },
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (!isLong) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                                contentColor = if (!isLong) Color.White else JellioTextSecondary,
                                focusedContainerColor = Color.White.copy(alpha = 0.32f),
                                focusedContentColor = Color.White,
                            ),
                            border = ClickableSurfaceDefaults.border(
                                border = if (!isLong) Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))) else Border.None,
                                focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                            ),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = string("settings_quick_hub_trigger_short"),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        onClick = { onSave(code, recordedLabel, selectedTrigger) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            focusedContainerColor = Color.White.copy(alpha = 0.9f),
                            focusedContentColor = Color.Black,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                        modifier = Modifier.focusRequester(actionFocusRequester),
                    ) {
                        Text(
                            text = string("settings_quick_hub_dialog_save"),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    Surface(
                        onClick = { recordedCode = null },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.22f),
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                    ) {
                        Text(
                            text = string("settings_quick_hub_dialog_try_again"),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                    Surface(
                        onClick = onDismiss,
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.22f),
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                        ),
                    ) {
                        Text(
                            text = string("action_cancel"),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}
