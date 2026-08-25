package com.jellio.tv.ui.update

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jellio.tv.R

// AppBootGate's own real loading wait (usually ~8s for Home's own
// prefetch to finish) used to just sit behind a plain ProgressSweep;
// this real jellio_load.webm (res/raw, ~10s, VP9/Opus re-encode of the
// original clip, a quarter the size of the mp4 it replaced) plays
// instead, muted (a splash is not the place for unprompted audio on a
// TV) and looping past its own real end in case that prefetch runs
// long, so the reveal never freezes on a static last frame. ExoPlayer's
// own default extractors sniff the container regardless of the
// android.resource:// URI's own lack of a real file extension, so no
// separate mp4 fallback is needed here the way the web plugin's own
// <video> element needs one for Safari's still-spotty VP9 support.
@Composable
fun BootSplashVideo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.jellio_load}")
            setMediaItem(MediaItem.fromUri(uri))
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = false
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
