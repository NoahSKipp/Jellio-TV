package com.jellio.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.tv.ui.theme.JellioBgElevated
import com.jellio.tv.ui.theme.JellioText
import com.jellio.tv.ui.theme.JellioTextSecondary
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

// True while the player is open, so prompts that take focus can wait.
object PlayerPresence {
    var active by androidx.compose.runtime.mutableStateOf(false)
}

data class PlaybackProblem(val title: String, val detail: String)

private fun httpProblem(code: Int): PlaybackProblem = when (code) {
    401, 403 -> PlaybackProblem(
        com.jellio.tv.ui.i18n.Strings.get("playback_err_forbidden_title"),
        com.jellio.tv.ui.i18n.Strings.get("playback_err_forbidden_desc"),
    )
    404, 410 -> PlaybackProblem(
        com.jellio.tv.ui.i18n.Strings.get("playback_err_not_found_title"),
        com.jellio.tv.ui.i18n.Strings.get("playback_err_not_found_desc"),
    )
    in 500..599 -> PlaybackProblem(
        com.jellio.tv.ui.i18n.Strings.get("playback_err_5xx_title"),
        com.jellio.tv.ui.i18n.Strings.format("playback_err_5xx_desc", code),
    )
    else -> PlaybackProblem(
        com.jellio.tv.ui.i18n.Strings.get("playback_err_http_title"),
        com.jellio.tv.ui.i18n.Strings.format("playback_err_http_desc", code),
    )
}

// Plain words for what went wrong, with the next thing to try, instead of
// the raw exception text.
fun friendlyPlaybackError(error: Throwable): PlaybackProblem {
    var cause: Throwable? = error
    while (cause != null) {
        when (cause) {
            is HttpException -> return httpProblem(cause.code())
            is HttpDataSource.InvalidResponseCodeException -> return httpProblem(cause.responseCode)
            is UnknownHostException -> return PlaybackProblem(
                com.jellio.tv.ui.i18n.Strings.get("playback_err_unreachable_title"),
                com.jellio.tv.ui.i18n.Strings.get("playback_err_unreachable_desc"),
            )
            is SocketTimeoutException -> return PlaybackProblem(
                com.jellio.tv.ui.i18n.Strings.get("playback_err_timeout_title"),
                com.jellio.tv.ui.i18n.Strings.get("playback_err_timeout_desc"),
            )
        }
        cause = cause.cause
    }
    if (error is PlaybackException) {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackProblem(
                com.jellio.tv.ui.i18n.Strings.get("playback_err_connection_dropped_title"),
                com.jellio.tv.ui.i18n.Strings.get("playback_err_connection_dropped_desc"),
            )
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlaybackProblem(
                com.jellio.tv.ui.i18n.Strings.get("playback_err_cant_decode_title"),
                com.jellio.tv.ui.i18n.Strings.get("playback_err_cant_decode_desc"),
            )
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PlaybackProblem(
                com.jellio.tv.ui.i18n.Strings.get("playback_err_cant_read_title"),
                com.jellio.tv.ui.i18n.Strings.get("playback_err_cant_read_desc"),
            )
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> httpProblem(500)
            else -> PlaybackProblem(
                com.jellio.tv.ui.i18n.Strings.get("playback_err_stopped_title"),
                com.jellio.tv.ui.i18n.Strings.get("playback_err_stopped_desc"),
            )
        }
    }
    if (error is IOException) {
        return PlaybackProblem(
            com.jellio.tv.ui.i18n.Strings.get("playback_err_unreachable_title"),
            com.jellio.tv.ui.i18n.Strings.get("playback_err_unreachable_desc"),
        )
    }
    return PlaybackProblem(
        com.jellio.tv.ui.i18n.Strings.get("playback_err_cant_start_title"),
        com.jellio.tv.ui.i18n.Strings.get("playback_err_cant_start_desc"),
    )
}

@Composable
fun PlaybackErrorPanel(
    problem: PlaybackProblem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(problem) { runCatching { focus.requestFocus() } }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.Back -> onBack()
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> onRetry?.invoke()
                        else -> Unit
                    }
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = problem.title, color = JellioText, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text = problem.detail,
                color = JellioTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (onRetry != null) {
                Text(
                    text = com.jellio.tv.ui.i18n.Strings.get("tv_failover_press_ok"),
                    color = JellioText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            Text(
                text = com.jellio.tv.ui.i18n.Strings.get("tv_press_back"),
                color = JellioTextSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}
