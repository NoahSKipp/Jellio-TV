package com.jellio.tv.ui.player

import com.jellio.tv.data.prefs.PlayerPrefs
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.ExoPlayer
import com.jellio.tv.data.network.APP_VERSION
import com.jellio.tv.data.network.buildEmbyAuthorizationHeader
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

// Timeouts, redirects and trust come from the shared stream client.
@OptIn(UnstableApi::class)
internal fun streamDataSourceFactory(client: okhttp3.Call.Factory, deviceId: String, accessToken: String): OkHttpDataSource.Factory =
    OkHttpDataSource.Factory(client)
        .setUserAgent("Jellio-TV/$APP_VERSION")
        .setTransferListener(FirstByteListener)
        .setDefaultRequestProperties(
            mapOf(
                "X-Emby-Token" to accessToken,
                "Authorization" to buildEmbyAuthorizationHeader(deviceId, APP_VERSION, accessToken),
            )
        )

// Marks the first response of the video stream itself, not a side-loaded subtitle.
@OptIn(UnstableApi::class)
private object FirstByteListener : androidx.media3.datasource.TransferListener {
    override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}

    override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {
        if (isNetwork && dataSpec.uri.path?.contains("/stream.", ignoreCase = true) == true) PlayStartTrace.markOnce("first byte")
    }

    override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) {}

    override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
}

// Renderers, audio sink and load control; the media goes in once the URL is known.
@OptIn(UnstableApi::class)
internal fun buildStreamPlayer(context: Context, dataSourceFactory: androidx.media3.datasource.DataSource.Factory): ExoPlayer {
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
    val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(dataSourceFactory)
    val audioSink = DefaultAudioSink.Builder(context)
        .setAudioCapabilities(
            if (PlayerPrefs.audioPassthrough) {
                AudioCapabilities.getCapabilities(context)
            } else {
                AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
            }
        )
        .build()
    val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
            return audioSink
        }
    }
    return ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(buildLoadControl(PlayerPrefs.prebuffer))
        .setAudioAttributes(audioAttributes, true)
        .build()
}

// Holds the player built ahead for the first stream. Whoever takes it
// releases it; one never taken is released with the screen. Main thread only.
internal class PrebuiltPlayer {
    private var player: ExoPlayer? = null
    private var done = false

    val wanted: Boolean get() = !done

    fun offer(built: ExoPlayer) {
        if (done) built.release() else player = built
    }

    fun take(): ExoPlayer? {
        done = true
        return player.also { player = null }
    }

    fun release() {
        done = true
        player?.release()
        player = null
    }
}
