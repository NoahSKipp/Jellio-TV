package com.jellio.tv.ui.announce

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.PendingSoundDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

private const val SOUND_POLL_MS = 5_000L

// The volume the video player applies on top of its own: below 1 while a
// sent sound plays.
object PlaybackDuck {
    var level by mutableFloatStateOf(1f)
}

// Sounds an admin sends from the Jellio dashboard: no toast, the clip
// just plays over whatever screen is open, turning playback down to the
// sound's DuckVolume until it ends.
@HiltViewModel
class SoundViewModel @Inject constructor(
    private val repository: JellioRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private var after = -1L

    suspend fun pollWhileStarted() {
        while (coroutineContext.isActive) {
            val pending = runCatching { repository.getPendingSounds(after) }.getOrNull()
            if (pending != null) {
                val first = after < 0
                after = pending.Latest
                if (!first) pending.Sounds.forEach { play(it) }
            }
            delay(SOUND_POLL_MS)
        }
    }

    private suspend fun play(sound: PendingSoundDto) {
        val url = repository.soundUrl(sound.SoundId) ?: return
        val player = ExoPlayer.Builder(context).build()
        try {
            player.volume = (sound.Volume / 100f).coerceIn(0f, 1f)
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
            PlaybackDuck.level = (sound.DuckVolume / 100f).coerceIn(0f, 1f)
            suspendCancellableCoroutine { continuation ->
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED && continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                })
            }
        } finally {
            PlaybackDuck.level = 1f
            player.release()
        }
    }
}

@Composable
fun SoundPlayerHost(viewModel: SoundViewModel = hiltViewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.pollWhileStarted()
        }
    }
}
