package com.jellio.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.jellio.tv.data.prefs.PlayerPrefs
import kotlinx.coroutines.flow.collectLatest

// Dialogue boost, the media session and the dashboard sound duck.
@Composable
internal fun PlayerAudioEffects(player: ExoPlayer) {
    val context = LocalContext.current
    DisposableEffect(player, PlayerPrefs.dialogueBoost) {
        val sessionId = player.audioSessionId
        val loudnessEnhancer = if (sessionId != C.AUDIO_SESSION_ID_UNSET && PlayerPrefs.dialogueBoost) {
            runCatching {
                android.media.audiofx.LoudnessEnhancer(sessionId).apply {
                    setTargetGain(600)
                    enabled = true
                }
            }.getOrNull()
        } else {
            null
        }
        onDispose { runCatching { loudnessEnhancer?.release() } }
    }

    DisposableEffect(player) {
        val mediaSession = MediaSession.Builder(context, player).build()
        onDispose { mediaSession.release() }
    }

    // Turned down while a sound sent from the dashboard plays.
    LaunchedEffect(player) {
        androidx.compose.runtime.snapshotFlow { com.jellio.tv.ui.announce.PlaybackDuck.level }
            .collect { player.volume = it }
    }
}

// Volume commands from a remote control session.
@Composable
internal fun RemoteVolumeCommands(player: ExoPlayer, remoteControlManager: com.jellio.tv.data.remote.RemoteControlManager?) {
    LaunchedEffect(player, remoteControlManager) {
        val manager = remoteControlManager ?: return@LaunchedEffect
        manager.generalCommands.collectLatest { command ->
            when (command.name.lowercase()) {
                "volumeup" -> {
                    player.volume = (player.volume + 0.1f).coerceAtMost(1f)
                }
                "volumedown" -> {
                    player.volume = (player.volume - 0.1f).coerceAtLeast(0f)
                }
                "mute" -> {
                    player.volume = 0f
                }
                "unmute" -> {
                    player.volume = 1f
                }
                "togglemute" -> {
                    player.volume = if (player.volume > 0f) 0f else 1f
                }
                "setvolume" -> {
                    command.arguments["Volume"]?.toFloatOrNull()?.let { v ->
                        player.volume = (v / 100f).coerceIn(0f, 1f)
                    }
                }
            }
        }
    }
}

// Cleared when the player closes, so a party's countdown and ready
// check don't carry over to other screens.
@Composable
internal fun PartyPlaylistItemEffect(party: com.jellio.tv.data.syncplay.PartyManager, syncPlaylistItemId: String?) {
    DisposableEffect(syncPlaylistItemId) {
        party.setPlaylistItem(syncPlaylistItemId)
        onDispose { party.setPlaylistItem(null) }
    }
}
