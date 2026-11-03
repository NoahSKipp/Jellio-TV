package com.jellio.tv.data

import com.jellio.tv.data.model.AchievementsDto
import com.jellio.tv.data.model.AuthenticateByNameRequest
import com.jellio.tv.data.model.AvatarPresetDto
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.model.CalendarEntryDto
import com.jellio.tv.data.model.ClientConfigDto
import com.jellio.tv.data.model.FeedEntryDto
import com.jellio.tv.data.model.ForgotPasswordPinRequest
import com.jellio.tv.data.model.ForgotPasswordRequest
import com.jellio.tv.data.model.IntroSkipperSegmentsDto
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.ProfileDto
import com.jellio.tv.data.model.RealWatchRequest
import com.jellio.tv.data.model.ReportDurationRequest
import com.jellio.tv.data.model.SetBioRequest
import com.jellio.tv.data.model.SetPrivacyRequest
import com.jellio.tv.data.model.SleepTimerStartRequest
import com.jellio.tv.data.model.SleepTimerStatusDto
import com.jellio.tv.data.model.MediaStreamDto
import com.jellio.tv.data.model.PlaybackInfoRequest
import com.jellio.tv.data.model.PlaybackReportRequest
import com.jellio.tv.data.model.UpdatePasswordRequest
import com.jellio.tv.data.model.UserConfigurationDto
import com.jellio.tv.data.model.UserDto
import com.jellio.tv.data.model.UserItemDataDto
import com.jellio.tv.data.network.JellyfinApi
import com.jellio.tv.data.recommend.CandidateEntry
import com.jellio.tv.data.session.RememberedUserEntry
import com.jellio.tv.data.session.RememberedUsersStore
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

// Live TV. Opening a channel holds one of the tuner slots until
// closeLiveStream; the player always closes what it opened.
suspend fun JellioRepository.getLiveChannels(userId: String): List<com.jellio.tv.data.model.LiveChannelDto> =
    cache.get("live:channels:$userId", 60_000L) { api.getLiveChannels(userId).Items }

fun JellioRepository.invalidateLiveChannels(userId: String) = cache.invalidatePrefix("live:channels:$userId")

// Ids of channels that failed to start for anyone recently; empty when
// the server can't say.
suspend fun JellioRepository.getBrokenLiveChannels(): Set<String> =
    runCatching { cache.get("live:health", 60_000L) { api.getLiveHealth().keys } }.getOrDefault(emptySet())

suspend fun JellioRepository.reportLiveChannel(channelId: String, ok: Boolean, error: String?) {
    cache.invalidatePrefix("live:health")
    runCatching { api.reportLiveChannel(channelId, com.jellio.tv.data.model.LiveChannelReportDto(ok, error?.take(60))) }
}

suspend fun JellioRepository.reportLiveWatching(body: com.jellio.tv.data.model.LiveWatchingDto) {
    runCatching { api.reportLiveWatching(body) }
}

suspend fun JellioRepository.stopLiveWatching() {
    runCatching { api.stopLiveWatching() }
}

suspend fun JellioRepository.reportStreamLinkPlay(linkId: String, ok: Boolean) {
    runCatching { api.reportStreamLinkPlay(linkId, com.jellio.tv.data.model.StreamLinkPlayReportDto(ok)) }
}

suspend fun JellioRepository.getLivePrograms(userId: String, channelId: String, fromMs: Long, untilMs: Long): List<com.jellio.tv.data.model.LiveProgramDto> =
    api.getLivePrograms(
        com.jellio.tv.data.model.LiveProgramsRequest(
            UserId = userId,
            ChannelIds = listOf(channelId),
            MinEndDate = java.time.Instant.ofEpochMilli(fromMs).toString(),
            MaxStartDate = java.time.Instant.ofEpochMilli(untilMs).toString(),
        ),
    ).Items

// One line per channel opened, to see on the device which ones get converted.
internal fun describeLivePath(channelId: String, audioCodec: String?, viaHls: Boolean): String =
    "channel $channelId: source audio ${audioCodec?.lowercase() ?: "unknown"}, " +
        if (viaHls) "server HLS with AAC" else "direct play"

suspend fun JellioRepository.openLiveChannel(userId: String, channelId: String): OpenedLiveStream {
    val profile = com.jellio.tv.data.model.LiveDeviceProfile(
        DirectPlayProfiles = listOf(
            com.jellio.tv.data.model.LiveDirectPlayProfile(
                Container = "ts,mpegts,m2ts",
                VideoCodec = "h264,hevc,mpeg2video",
                AudioCodec = "aac",
            ),
        ),
        TranscodingProfiles = listOf(
            com.jellio.tv.data.model.LiveTranscodingProfile(
                Container = "ts",
                VideoCodec = "h264",
                AudioCodec = "aac",
                MaxAudioChannels = "6",
                MinSegments = 1,
                BreakOnNonKeyFrames = true,
            ),
        ),
    )
    val response = api.openLiveChannel(channelId, userId, com.jellio.tv.data.model.LivePlaybackInfoRequest(userId, DeviceProfile = profile))
    response.ErrorCode?.let { throw IllegalStateException(it) }
    val source = response.MediaSources.firstOrNull() ?: throw IllegalStateException("No playable live stream")
    val liveStreamId = source.LiveStreamId ?: throw IllegalStateException("No live stream was opened")
    val server = com.jellio.tv.di.normalizeServerAddress(sessionManager.serverAddress() ?: throw IllegalStateException("Not signed in"))
    val transcoding = source.TranscodingUrl
    val viaHls = !transcoding.isNullOrBlank() && source.SupportsDirectPlay != true
    val audioCodec = source.MediaStreams?.firstOrNull { it.Type == "Audio" }?.Codec
    android.util.Log.i("JellioLive", describeLivePath(channelId, audioCodec, viaHls))
    if (viaHls && transcoding != null) {
        val path = if (transcoding.startsWith("/")) transcoding else "/$transcoding"
        return OpenedLiveStream(
            channelId, liveStreamId, server + path,
            androidx.media3.common.MimeTypes.APPLICATION_M3U8, null, null, response.PlaySessionId,
        )
    }
    val url = server + "/Videos/" + channelId + "/stream?static=true" +
        "&mediaSourceId=" + java.net.URLEncoder.encode(source.Id ?: channelId, "UTF-8") +
        "&liveStreamId=" + java.net.URLEncoder.encode(liveStreamId, "UTF-8") +
        "&DeviceId=" + java.net.URLEncoder.encode(sessionManager.getCachedDeviceId(), "UTF-8")
    return OpenedLiveStream(channelId, liveStreamId, url)
}

suspend fun JellioRepository.closeLiveStream(liveStreamId: String, playSessionId: String? = null) {
    runCatching { api.closeLiveStream(liveStreamId) }
    // A transcode keeps reading the channel after the live stream closes, and a
    // provider that allows one connection would refuse the next channel meanwhile.
    if (!playSessionId.isNullOrBlank()) {
        runCatching { api.stopEncoding(sessionManager.getCachedDeviceId(), playSessionId) }
    }
}

suspend fun JellioRepository.getStreamLinks(): List<com.jellio.tv.data.model.StreamLinkDto> = streamLinkCall { api.getStreamLinks() }

// Plays the link itself first (lighter on the server); the signed
// proxy address is the fallback when the TV can't reach it directly.
suspend fun JellioRepository.playStreamLink(channelId: String, linkId: String, entry: Int?): OpenedLiveStream {
    val info = streamLinkCall { api.playStreamLink(linkId, entry) }
    val server = com.jellio.tv.di.normalizeServerAddress(sessionManager.serverAddress() ?: throw IllegalStateException("Not signed in"))
    val proxy = info.ProxyPath?.let { "$server/$it" }
    val mime = when (info.Format) {
        "hls" -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
        "ts" -> androidx.media3.common.MimeTypes.VIDEO_MP2T
        "mp4" -> androidx.media3.common.MimeTypes.VIDEO_MP4
        else -> null
    }
    val direct = info.DirectUrl
    if (direct.isNullOrBlank()) {
        return OpenedLiveStream(channelId, null, proxy ?: throw StreamLinkException("no_stream_found"), mime, emptyMap())
    }
    val headers = buildMap {
        info.UserAgent?.let { put("User-Agent", it) }
        info.Referer?.let {
            put("Referer", it)
            runCatching { java.net.URI(it) }.getOrNull()?.let { uri -> put("Origin", uri.scheme + "://" + uri.authority) }
        }
    }
    return OpenedLiveStream(channelId, null, direct, mime, headers, proxy)
}

suspend fun JellioRepository.removeStreamLink(id: String) {
    streamLinkCall { api.removeStreamLink(id) }
}

suspend fun JellioRepository.removeStreamLinkEntry(id: String, index: Int) {
    streamLinkCall { api.removeStreamLinkEntry(id, index) }
}

suspend fun JellioRepository.refreshStreamLink(id: String) {
    streamLinkCall { api.refreshStreamLink(id) }
}

suspend fun JellioRepository.createStreamPairing(): com.jellio.tv.data.model.StreamPairingDto = streamLinkCall { api.createStreamPairing() }

// Null once the code has expired.
suspend fun JellioRepository.streamPairingStatus(code: String): com.jellio.tv.data.model.StreamPairingStatusDto? {
    val response = api.streamPairingStatus(code)
    if (response.code() == 404) return null
    return response.body()
}

private suspend fun <T> JellioRepository.streamLinkCall(block: suspend () -> T): T = try {
    block()
} catch (err: retrofit2.HttpException) {
    val body = runCatching { err.response()?.errorBody()?.string() }.getOrNull().orEmpty()
    val code = Regex("\"[Ee]rror\"\\s*:\\s*\"([a-z_]+)\"").find(body)?.groupValues?.get(1)
    throw StreamLinkException(code ?: if (err.code() == 404) "not_found" else "failed")
}

suspend fun JellioRepository.setChannelFavorite(userId: String, channelId: String, favorite: Boolean): Boolean {
    val result = if (favorite) api.addFavorite(userId, channelId) else api.removeFavorite(userId, channelId)
    invalidateLiveChannels(userId)
    return result.IsFavorite
}
