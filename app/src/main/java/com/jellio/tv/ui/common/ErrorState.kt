package com.jellio.tv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jellio.tv.ui.i18n.string
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import com.jellio.tv.ui.theme.JellioTextTertiary

@Composable
fun RetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
        ),
        modifier = modifier,
    ) {
        Text(text = string("action_try_again"), modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
    }
}

// A load that failed: what failed, why if the server was out of reach, and
// Try again. Takes focus when shown unless something else on screen should.
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    failure: LoadFailure? = null,
    takeFocus: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = JellioTextSecondary, textAlign = TextAlign.Center)
            if (failure == LoadFailure.Unreachable) {
                Text(
                    text = string("error_server_unreachable"),
                    color = JellioTextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            RetryButton(onClick = onRetry, modifier = Modifier.padding(top = 20.dp).focusRequester(focus))
        }
    }
    if (takeFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

// One line under rows that did load, for the ones that did not.
@Composable
fun InlineErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = message, color = JellioTextSecondary)
        RetryButton(onClick = onRetry)
    }
}
