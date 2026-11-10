package com.jellio.tv.data

import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.MediaStreamDto
import com.jellio.tv.data.model.PlaybackInfoRequest
import com.jellio.tv.data.model.PlaybackReportRequest
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.session.SessionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val TRANSCODE_REASONS_PARAM = Regex("[?&]TranscodeReasons=([^&]*)", RegexOption.IGNORE_CASE)
private const val PLAYBACK_PREFETCH_TTL_MS = 45_000L
private const val FIRST_BYTES_RANGE_BYTES = 256L * 1024
private const val FIRST_BYTES_GRACE_MS = 3_000L
private const val COPY_RANGE_TYPES = "SDR,HDR10,HDR10Plus,HLG,DOVIWithHDR10,DOVIWithHLG,DOVIWithSDR,DOVIWithHDR10Plus"
private const val FALLBACK_VIDEO_BITRATE = 20000000L
private val SOFTWARE_AUDIO_CODECS = setOf("aac", "mp3", "opus", "vorbis", "flac", "pcm", "pcm_s16le", "pcm_s24le", "alac")
private val DIRECT_PLAY_AUDIO_CODECS = setOf("aac", "mp3", "opus", "vorbis", "flac", "ac3", "eac3", "dts", "truehd")
private val DIRECT_PLAY_CONTAINERS = setOf("mp4", "webm", "m4v", "mkv", "m2ts", "ts", "avi")
private val DIRECT_PLAY_VIDEO_CODECS = setOf("h264", "avc", "hevc", "h265", "vp8", "vp9", "av1", "mpeg4")
private const val PREWARM_RANGE_BYTES = 64L * 1024

// PlaybackInfo, the stream URL the player opens, its warm-ups and the
// playback reports sent back to Jellyfin.
@Singleton
class PlaybackRepository @Inject constructor(
    private val api: JellyfinApi,
    private val sessionManager: SessionManager,
    private val prefetch: PrefetchScope,
    apiClient: okhttp3.OkHttpClient,
) {
    // Video streams on the API client's pool and trust settings, so a
    // connection warmed by either is reused. Without its interceptors:
    // stream URLs are absolute and carry their token, and a 401 from a
    // stream must not end the session.
    val streamClient: okhttp3.OkHttpClient by lazy {
        apiClient.newBuilder()
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
            }
            // Its own dispatcher, so the player never queues behind API calls to the same host.
            .dispatcher(okhttp3.Dispatcher())
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    fun clear() {
        playbackPrefetch.clear()
    }

    // One line in the Jellyfin log per playback: play method and why.
    suspend fun logPlaybackDecision(itemId: String, mediaSource: MediaSourceDto, directPlay: Boolean) {
        val policy = com.jellio.tv.data.playback.VideoTranscodePolicy
        val classification = policy.classify(mediaSource)
        val serverReasons = mediaSource.TranscodingUrl
            ?.let { TRANSCODE_REASONS_PARAM.find(it)?.groupValues?.get(1) }
            ?.let { catching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        catching {
            api.logPlaybackDecision(
                com.jellio.tv.data.model.PlaybackDecisionBody(
                    ItemId = itemId,
                    MediaSourceId = mediaSource.Id,
                    PlayMethod = com.jellio.tv.data.playback.playMethodFor(classification, directPlay).name,
                    Reasons = classification.reasons,
                    ServerTranscodeReasons = serverReasons,
                    Policy = policy.policy,
                    Guessed = classification.guessed,
                ),
            )
        }
    }

    private class PrefetchedPlayback(val at: Long, val target: kotlinx.coroutines.Deferred<PlaybackTarget?>)
    private val playbackPrefetch = ConcurrentHashMap<String, PrefetchedPlayback>()

    private fun playbackKey(itemId: String, mediaSourceId: String?, startTicks: Long) = "$itemId|${mediaSourceId.orEmpty()}|$startTicks"

    fun prefetchPlayback(userId: String, itemId: String, mediaSourceId: String?, startTicks: Long) {
        val now = System.currentTimeMillis()
        playbackPrefetch.entries.removeIf { now - it.value.at > PLAYBACK_PREFETCH_TTL_MS }
        val key = playbackKey(itemId, mediaSourceId, startTicks)
        if (playbackPrefetch.containsKey(key)) return
        playbackPrefetch[key] = PrefetchedPlayback(
            now,
            prefetch.scope.async { catching { resolvePlayback(userId, itemId, mediaSourceId, startTicks) }.getOrNull() },
        )
    }

    // The prefetched target without taking it, for warming its stream.
    suspend fun awaitPrefetchedPlayback(itemId: String, mediaSourceId: String?, startTicks: Long): PlaybackTarget? {
        val entry = playbackPrefetch[playbackKey(itemId, mediaSourceId, startTicks)] ?: return null
        return entry.target.await()
    }

    private class FirstBytesWarm(val itemId: String, val job: kotlinx.coroutines.Job) {
        @Volatile var claimed = false
    }
    private val firstBytesLock = Any()
    private var firstBytes: FirstBytesWarm? = null

    // One at a time, a newer title's replaces it. When the caller goes away
    // (focus left, page closed) it ends after a short grace, unless the
    // player opened that title meanwhile: then it finishes, so its
    // connection goes back to the pool for the player.
    suspend fun warmFirstBytes(itemId: String, streamUrl: String) {
        val warm = synchronized(firstBytesLock) {
            firstBytes?.job?.cancel()
            FirstBytesWarm(itemId, prefetch.scope.launch { warmStreamStart(streamUrl, FIRST_BYTES_RANGE_BYTES) })
                .also { firstBytes = it }
        }
        try {
            warm.job.join()
        } finally {
            if (warm.job.isActive) {
                prefetch.scope.launch {
                    delay(FIRST_BYTES_GRACE_MS)
                    if (!warm.claimed) warm.job.cancel()
                }
            }
        }
    }

    fun claimFirstBytesWarm(itemId: String) {
        synchronized(firstBytesLock) { firstBytes?.takeIf { it.itemId == itemId }?.claimed = true }
    }

    suspend fun takePrefetchedPlayback(itemId: String, mediaSourceId: String?, startTicks: Long): PlaybackTarget? {
        val now = System.currentTimeMillis()
        val keys = listOf(playbackKey(itemId, mediaSourceId, startTicks), playbackKey(itemId, null, startTicks)).distinct()
        for (key in keys) {
            val entry = playbackPrefetch.remove(key) ?: continue
            if (now - entry.at > PLAYBACK_PREFETCH_TTL_MS) continue
            val target = entry.target.await() ?: continue
            if (mediaSourceId == null || target.mediaSource.Id == mediaSourceId) return target
        }
        return null
    }

    suspend fun resolvePlayback(
        userId: String,
        itemId: String,
        mediaSourceId: String?,
        startTimeTicks: Long,
        burnInSubtitleStreamIndex: Int? = null,
        audioStreamIndex: Int? = null,
        forceTranscode: Boolean = false,
        forceDirectPlay: Boolean = false,
    ): PlaybackTarget {
        val response = api.getPlaybackInfo(
            itemId,
            PlaybackInfoRequest(
                UserId = userId,
                StartTimeTicks = startTimeTicks,
                MediaSourceId = mediaSourceId,
                AudioStreamIndex = audioStreamIndex,
                SubtitleStreamIndex = burnInSubtitleStreamIndex,
            ),
        )
        val mediaSource = response.MediaSources.firstOrNull { it.Id == mediaSourceId }
            ?: response.MediaSources.firstOrNull()
            ?: if (mediaSourceId != null) {
                throw com.jellio.tv.data.streams.SourceGoneException()
            } else {
                throw IllegalStateException("No playable source for this title")
            }

        val targetAudioStream = if (audioStreamIndex != null) {
            mediaSource.MediaStreams?.firstOrNull { it.Type == "Audio" && it.Index == audioStreamIndex }
        } else {
            null
        }
        val audioNeedsTranscode = if (audioStreamIndex != null && audioStreamIndex != mediaSource.DefaultAudioStreamIndex) {
            targetAudioStream == null || !canDirectPlayAudio(targetAudioStream.Codec)
        } else {
            false
        }
        val shouldTranscode = forceTranscode ||
            burnInSubtitleStreamIndex != null ||
            audioNeedsTranscode
        val directPlay = forceDirectPlay || (!shouldTranscode && canDirectPlay(mediaSource, audioStreamIndex))
        val serverAddress = sessionManager.serverAddress() ?: throw IllegalStateException("Not signed in")
        val token = sessionManager.accessToken() ?: throw IllegalStateException("Not signed in")
        val deviceId = sessionManager.deviceId()
        val resolvedMediaSourceId = mediaSource.Id ?: itemId
        val parsedContainer = normalizeStreamContainer(mediaSource.Container)
        val container = if (directPlay) parsedContainer else "mp4"

        val streamUrl = buildString {
            append(serverAddress)
            append("/Videos/").append(itemId).append("/stream.").append(container)
            append("?MediaSourceId=").append(resolvedMediaSourceId)
            append("&DeviceId=").append(deviceId)
            append("&ApiKey=").append(token)
            append("&api_key=").append(token)
            append("&StartTimeTicks=").append(startTimeTicks)
            response.PlaySessionId?.let { append("&PlaySessionId=").append(it) }
            if (directPlay) {
                append("&Static=true")
            } else {
                if (com.jellio.tv.data.playback.VideoTranscodePolicy.avoiding) {
                    val codecs = com.jellio.tv.data.playback.copyableVideoCodecs(com.jellio.tv.data.playback.DeviceDecoderCaps)
                    append("&VideoCodec=").append(codecs.joinToString(","))
                    append("&AudioCodec=aac&AudioBitRate=192000")
                    append("&AllowVideoStreamCopy=true&AllowAudioStreamCopy=true")
                    val ranges = if (com.jellio.tv.data.playback.DeviceDecoderCaps.dolbyVision) "$COPY_RANGE_TYPES,DOVI" else COPY_RANGE_TYPES
                    codecs.filter { it != "h264" }.forEach { codec ->
                        append("&").append(codec).append("-videorangetype=").append(ranges)
                    }
                } else {
                    append("&VideoCodec=h264&AudioCodec=aac")
                    append("&VideoBitRate=").append(estimateVideoBitrate(mediaSource))
                    append("&AudioBitRate=192000")
                }
            }
            if (audioStreamIndex != null) {
                append("&AudioStreamIndex=").append(audioStreamIndex)
            }
            if (burnInSubtitleStreamIndex != null) {
                append("&SubtitleStreamIndex=").append(burnInSubtitleStreamIndex)
                append("&SubtitleMethod=Encode")
            }
        }

        return PlaybackTarget(streamUrl, mediaSource, response.PlaySessionId, startTimeTicks, directPlay)
    }

    fun getAudioStreams(mediaSource: MediaSourceDto): List<MediaStreamDto> =
        mediaSource.MediaStreams?.filter { it.Type == "Audio" } ?: emptyList()

    private fun estimateVideoBitrate(mediaSource: MediaSourceDto): Long {
        val video = mediaSource.MediaStreams?.firstOrNull { it.Type == "Video" }
        return video?.BitRate ?: mediaSource.Bitrate ?: FALLBACK_VIDEO_BITRATE
    }

    suspend fun buildSubtitleUrl(itemId: String, mediaSourceId: String, stream: MediaStreamDto, offsetTicks: Long = 0): String {
        val serverAddress = sessionManager.serverAddress() ?: throw IllegalStateException("Not signed in")
        if (stream.DeliveryMethod == "External" && !stream.DeliveryUrl.isNullOrEmpty()) {
            return if (stream.IsExternalUrl == true) stream.DeliveryUrl else serverAddress + stream.DeliveryUrl
        }
        val token = sessionManager.accessToken()
        val base = if (offsetTicks > 0) {
            "$serverAddress/Videos/$itemId/$mediaSourceId/Subtitles/${stream.Index}/$offsetTicks/Stream.vtt"
        } else {
            "$serverAddress/Videos/$itemId/$mediaSourceId/Subtitles/${stream.Index}/Stream.vtt"
        }
        return if (!token.isNullOrEmpty()) "$base?ApiKey=$token&api_key=$token" else base
    }

    internal fun canDirectPlayAudio(codec: String?): Boolean {
        val c = codec?.lowercase() ?: return false
        if (c in SOFTWARE_AUDIO_CODECS) return true
        if (com.jellio.tv.data.prefs.PlayerPrefs.audioPassthrough && c in DIRECT_PLAY_AUDIO_CODECS) return true
        val mimeType = when (c) {
            "ac3" -> "audio/ac3"
            "eac3" -> "audio/eac3"
            else -> return false // DTS, TrueHD, etc. have no native software decoder in Android/Media3 without passthrough
        }
        return hasAudioDecoder.getOrPut(mimeType) {
            try {
                val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
                list.codecInfos.any { info ->
                    !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    // The decoder list is fixed for the device, and building it is slow
    // enough to show when every stream of a title is checked.
    private val hasAudioDecoder = ConcurrentHashMap<String, Boolean>()

    // ignoreAudio: whether the picture and container alone can be played as is.
    internal fun canDirectPlay(mediaSource: MediaSourceDto, audioStreamIndex: Int? = null, ignoreAudio: Boolean = false): Boolean {
        if (mediaSource.SupportsDirectPlay != true) return false
        val container = mediaSource.Container?.lowercase() ?: return false
        if (container !in DIRECT_PLAY_CONTAINERS) return false

        val streams = mediaSource.MediaStreams ?: emptyList()
        val video = streams.firstOrNull { it.Type == "Video" }
        val audioStreams = streams.filter { it.Type == "Audio" }
        val targetAudioIndex = audioStreamIndex ?: mediaSource.DefaultAudioStreamIndex
        val audio = if (targetAudioIndex != null) {
            audioStreams.firstOrNull { it.Index == targetAudioIndex } ?: audioStreams.firstOrNull()
        } else {
            audioStreams.firstOrNull()
        }
        if (video != null && video.Codec?.lowercase() !in DIRECT_PLAY_VIDEO_CODECS) return false
        if (!ignoreAudio && audio != null && !canDirectPlayAudio(audio.Codec)) return false
        return true
    }

    suspend fun reportPlaybackStart(itemId: String, mediaSourceId: String?, positionTicks: Long) {
        catching {
            api.reportPlaybackStart(PlaybackReportRequest(itemId, mediaSourceId, positionTicks))
        }
    }

    // Fire and forget: an older plugin answers 404, and nothing waits on it.
    fun reportPlayStart(record: com.jellio.tv.data.model.PlayStartRecord) {
        prefetch.scope.launch { catching { api.reportPlayStart(record) } }
    }

    suspend fun reportPlaybackProgress(itemId: String, mediaSourceId: String?, positionTicks: Long, isPaused: Boolean) {
        catching {
            api.reportPlaybackProgress(PlaybackReportRequest(itemId, mediaSourceId, positionTicks, isPaused))
        }
    }

    suspend fun reportPlaybackStopped(itemId: String, mediaSourceId: String?, positionTicks: Long) {
        catching {
            api.reportPlaybackStopped(PlaybackReportRequest(itemId, mediaSourceId, positionTicks))
        }
    }

    suspend fun reportCapabilities(): Result<Unit> = catching {
        api.postCapabilities(com.jellio.tv.data.model.ClientCapabilitiesDto())
    }

    // Pre-warms the stream connection and Usenet/nzbdav reader for the upcoming episode.
    fun prewarmStreamUrl(streamUrl: String) {
        prefetch.scope.launch { warmStreamStart(streamUrl, PREWARM_RANGE_BYTES) }
    }

    // Reads the head of a stream through the player's client, so the
    // connection sits warm in the shared pool and the origin has the start
    // ready. Reading the whole range lets the connection go back to the pool.
    private suspend fun warmStreamStart(streamUrl: String, bytes: Long) {
        val request = okhttp3.Request.Builder()
            .url(streamUrl)
            .header("Range", "bytes=0-${bytes - 1}")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "Jellio-TV/${com.jellio.tv.data.network.APP_VERSION}")
            .build()
        val call = streamClient.newCall(request)
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    cont.resumeWith(Result.success(Unit))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    runCatching {
                        response.use {
                            if (it.code != 200 && it.code != 206) return@use
                            val source = it.body?.source() ?: return@use
                            val sink = okio.Buffer()
                            var left = bytes
                            while (left > 0) {
                                val read = source.read(sink, minOf(left, 64L * 1024))
                                if (read < 0) break
                                left -= read
                                sink.clear()
                            }
                        }
                    }
                    cont.resumeWith(Result.success(Unit))
                }
            })
        }
    }
}
