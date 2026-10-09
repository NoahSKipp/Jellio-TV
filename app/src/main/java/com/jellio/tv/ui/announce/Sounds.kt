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
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.PendingSoundDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

private const val SOUND_POLL_MS = 5_000L
private const val MAX_SOUND_MS = 90_000L

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
            val pending = runCatching { repository.getPendingSounds(after) }
                .onFailure { android.util.Log.w("JellioSounds", "pending sounds poll failed", it) }
                .getOrNull()
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
        val dataSource = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(repository.soundRequestHeaders())
        val player = ExoPlayer.Builder(context, DirectionalRenderersFactory(context, sound.Direction))
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSource))
            .build()
        try {
            player.volume = (sound.Volume / 100f).coerceIn(0f, 1f)
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
            // Never longer than this, so a clip that stalls can't leave
            // playback turned down.
            withTimeoutOrNull(MAX_SOUND_MS) {
                suspendCancellableCoroutine { continuation ->
                    player.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            // Turned down only once the clip actually plays.
                            if (isPlaying) PlaybackDuck.level = (sound.DuckVolume / 100f).coerceIn(0f, 1f)
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            if ((state == Player.STATE_ENDED || state == Player.STATE_IDLE) && continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            android.util.Log.w("JellioSounds", "sound failed to play", error)
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    })
                }
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

// Sends the clip to one side, or to the rear pair of a 5.1 layout (front
// L, front R, center, LFE, rear L, rear R). A stereo TV folds the rear
// pair back into its two speakers, so there it plays from both sides.
@OptIn(UnstableApi::class)
private class DirectionalRenderersFactory(context: Context, private val direction: String) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
        val mixer = ChannelMixingAudioProcessor()
        directionMatrices(direction).forEach { mixer.putChannelMixingMatrix(it) }
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(mixer))
            .build()
    }
}

@OptIn(UnstableApi::class)
private fun directionMatrices(direction: String): List<ChannelMixingMatrix> = when (direction) {
    // Input rows, output columns: mono then stereo into two channels.
    "left" -> listOf(
        ChannelMixingMatrix(1, 2, floatArrayOf(1f, 0f)),
        ChannelMixingMatrix(2, 2, floatArrayOf(0.7f, 0f, 0.7f, 0f)),
    )
    "right" -> listOf(
        ChannelMixingMatrix(1, 2, floatArrayOf(0f, 1f)),
        ChannelMixingMatrix(2, 2, floatArrayOf(0f, 0.7f, 0f, 0.7f)),
    )
    "rear" -> listOf(
        ChannelMixingMatrix(1, 6, floatArrayOf(0f, 0f, 0f, 0f, 1f, 1f)),
        ChannelMixingMatrix(2, 6, floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)),
    )
    else -> listOf(
        ChannelMixingMatrix(1, 1, floatArrayOf(1f)),
        ChannelMixingMatrix(2, 2, floatArrayOf(1f, 0f, 0f, 1f)),
    )
}
