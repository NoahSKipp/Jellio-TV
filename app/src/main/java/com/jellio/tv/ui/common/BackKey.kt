package com.jellio.tv.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

// Closes an overlay on the first Back press. Left to BackHandler, Android
// TV spends the first press moving focus out of the focused button, so an
// overlay needed Back twice.
fun Modifier.dismissOnBack(onDismiss: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.key != Key.Back) return@onPreviewKeyEvent false
    if (event.type == KeyEventType.KeyUp) onDismiss()
    true
}
