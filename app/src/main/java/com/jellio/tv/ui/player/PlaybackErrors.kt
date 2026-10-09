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

data class PlaybackProblem(val title: String, val detail: String)

private fun httpProblem(code: Int): PlaybackProblem = when (code) {
    401, 403 -> PlaybackProblem(
        "You can't play this title",
        "Your account doesn't have access to it, or your session has expired. Sign out and back in, or ask the server's admin.",
    )
    404, 410 -> PlaybackProblem(
        "This title isn't available",
        "The server couldn't find this stream any more. It may have been removed or moved.",
    )
    in 500..599 -> PlaybackProblem(
        "The server couldn't start this stream",
        "Jellyfin ran into a problem getting this title ready (error $code). Try again, or pick a different source from the title's page.",
    )
    else -> PlaybackProblem(
        "This stream didn't load",
        "The server answered with error $code. Try again, or pick a different source from the title's page.",
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
                "Can't reach your server",
                "Check that this TV is online and that the Jellyfin server is running, then try again.",
            )
            is SocketTimeoutException -> return PlaybackProblem(
                "The server took too long to answer",
                "It may be busy preparing the stream. Try again in a moment.",
            )
        }
        cause = cause.cause
    }
    if (error is PlaybackException) {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackProblem(
                "The connection to the server dropped",
                "Check this TV's network connection, then try again.",
            )
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlaybackProblem(
                "This TV can't play this video",
                "Its format is more than this device can decode. Pick a different source from the title's page, or try a lower quality one.",
            )
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> PlaybackProblem(
                "This stream couldn't be read",
                "The file looks damaged or in a format this player doesn't support. Try a different source.",
            )
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> httpProblem(500)
            else -> PlaybackProblem(
                "Playback stopped",
                "Something went wrong while playing this title. Try again, or pick a different source.",
            )
        }
    }
    if (error is IOException) {
        return PlaybackProblem(
            "Can't reach your server",
            "Check that this TV is online and that the Jellyfin server is running, then try again.",
        )
    }
    return PlaybackProblem(
        "Couldn't start playback",
        "Something went wrong getting this title ready. Try again, or pick a different source.",
    )
}

// No buttons: they fought the player's own controls for the D-pad. The
// panel keeps focus and swallows keys so the controls stay down, and Back
// leaves the player.
@Composable
fun PlaybackErrorPanel(
    problem: PlaybackProblem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) onBack()
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
            Text(
                text = "Press Back to return.",
                color = JellioTextSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}
