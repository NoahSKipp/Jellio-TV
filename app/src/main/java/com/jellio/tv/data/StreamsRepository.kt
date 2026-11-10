package com.jellio.tv.data

import com.jellio.tv.data.cache.lruMap
import com.jellio.tv.data.model.MediaSourceDto
import com.jellio.tv.data.model.PlaybackInfoRequest
import com.jellio.tv.data.network.JellyfinApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val FAILOVER_FIRST_TIMEOUT_SECONDS = 20
private const val PREFETCH_SLOT_WAIT_MS = 3_000L
private const val PREFETCH_WINDOW_MS = 8 * 60_000L
private const val WARM_SEEN_TTL_MS = 10 * 60_000L

// Stream lists for a title, failover reloads and the server-side warm-ups
// that make a later Play start fast.
@Singleton
class StreamsRepository @Inject constructor(
    private val api: JellyfinApi,
    private val config: JellioConfigRepository,
    private val prefetch: PrefetchScope,
) {
    private val sourceCache = com.jellio.tv.data.streams.SourceCache<MediaSourceDto>()
    private val sourcesInFlight = ConcurrentHashMap<String, Deferred<List<MediaSourceDto>>>()
    private val lastSources = lruMap<String, com.jellio.tv.data.streams.LastList<MediaSourceDto>>(MAX_HINTS)
    private val prefetchedAt = lruMap<String, Long>(MAX_HINTS)
    // One warm-up request to the server at a time, whoever asks.
    private val prefetchSlots = java.util.concurrent.Semaphore(1)
    // Titles the server said are already warm.
    private val knownWarm = ConcurrentHashMap.newKeySet<String>()

    fun clear() {
        queuedPrefetch = null
        sourcesInFlight.clear()
        sourceCache.clear()
        lastSources.clear()
        prefetchedAt.clear()
        knownWarm.clear()
    }

    suspend fun getMediaSources(
        userId: String,
        itemId: String,
        reason: String = "play",
        forceFresh: Boolean = false,
    ): List<MediaSourceDto> {
        // Stream lists are ranked and badged by it.
        config.loadVideoTranscodePolicy()
        if (!forceFresh) {
            sourceCache.get(itemId, System.currentTimeMillis())?.let {
                com.jellio.tv.data.streams.StreamLookupLog.cacheHit()
                return it
            }
        }
        val request = sourcesInFlight.computeIfAbsent(itemId) {
            prefetch.scope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                val started = System.currentTimeMillis()
                val sources = fetchMediaSources(userId, itemId)
                if (sources.isNotEmpty()) {
                    sourceCache.put(itemId, sources, System.currentTimeMillis())
                    lastSources[itemId] = com.jellio.tv.data.streams.LastList(sources, System.currentTimeMillis())
                }
                com.jellio.tv.data.streams.StreamLookupLog.lookup(itemId, reason, sources.size, System.currentTimeMillis() - started)
                sources
            }
        }
        request.start()
        try {
            return request.await()
        } finally {
            sourcesInFlight.remove(itemId, request)
        }
    }

    private val streamInfoNamePattern = Regex(
        "(^|[^a-z0-9])(web-?dl|web-?rip|bluray|blu-ray|bdrip|brrip|remux|hdtv|hevc|[xh]\\.?265|[xh]\\.?264|avc|av1|vp9|hdr10?\\+?|dovi|dolby\\s?vision|hlg|10\\s?bit|aac|e?-?ac-?3|ddp|dd\\+|dts(-?hd|-?x)?|truehd|atmos|flac|l?pcm|[257]\\.[01]|dual[-.\\s]?audio|multi[-.\\s]?audio|\\d+(\\.\\d+)?\\s*(gb|mb|gib|mib|mbps|kbps))([^a-z0-9]|$)",
        RegexOption.IGNORE_CASE,
    )

    private fun hasFlagEmoji(text: String): Boolean {
        if (text.isEmpty()) return false
        val points = text.codePoints().toArray()
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (a in 0x1F1E6..0x1F1FF && b in 0x1F1E6..0x1F1FF) return true
        }
        return false
    }

    private fun isRealStreamValue(value: String?): Boolean {
        val clean = value?.trim()?.lowercase() ?: return false
        return clean.isNotEmpty() && clean != "unknown" && clean != "und"
    }

    private fun hasStreamInfo(source: MediaSourceDto): Boolean {
        if (com.jellio.tv.data.streams.sourceQuality(source) != null) return true
        if ((source.Size ?: 0L) >= 1024L * 1024L) return true

        val hasProbedStream = source.MediaStreams?.any { s ->
            when (s.Type) {
                "Video" -> (s.Width ?: 0) > 0 || (s.Height ?: 0) > 0 || (s.BitRate ?: 0L) > 0L
                "Audio" -> isRealStreamValue(s.Codec) || (s.Channels ?: 0) > 0 || isRealStreamValue(s.Language)
                else -> false
            }
        } == true
        if (hasProbedStream) return true

        val name = source.Name.orEmpty()
        if (streamInfoNamePattern.containsMatchIn(name)) return true
        if (hasFlagEmoji(name)) return true

        return false
    }

    // The other streams after one failed: a fresh list, then one retry with a
    // longer timeout, then the last list loaded for the title. Errors are kept.
    suspend fun getFailoverSources(userId: String, itemId: String, fresh: Boolean = false): com.jellio.tv.data.streams.FailoverLoad<MediaSourceDto> {
        config.loadVideoTranscodePolicy()
        return com.jellio.tv.data.streams.loadFailoverSources(lastSources[itemId], System::currentTimeMillis, skipRecent = fresh) { timeout ->
            val started = System.currentTimeMillis()
            val sources = fetchMediaSources(userId, itemId, timeout ?: FAILOVER_FIRST_TIMEOUT_SECONDS)
            com.jellio.tv.data.streams.StreamLookupLog.lookup(itemId, "failover", sources.size, System.currentTimeMillis() - started)
            if (sources.isNotEmpty()) {
                val now = System.currentTimeMillis()
                sourceCache.put(itemId, sources, now)
                lastSources[itemId] = com.jellio.tv.data.streams.LastList(sources, now)
            }
            sources
        }
    }

    suspend fun logFailover(body: com.jellio.tv.data.model.FailoverLogBody) {
        catching { api.logFailover(body) }
    }

    private suspend fun fetchMediaSources(userId: String, itemId: String, readTimeoutSeconds: Int? = null): List<MediaSourceDto> {
        val fields = "MediaSources,OriginalTitle,PremiereDate,ProductionYear"
        val item = if (readTimeoutSeconds == null) {
            withRetry { api.getItem(userId, itemId, fields = fields) }
        } else {
            api.getItem(userId, itemId, fields = fields, readTimeoutSeconds = readTimeoutSeconds)
        }
        val sources = item.MediaSources ?: emptyList()
        if (sources.size == 1 && !hasStreamInfo(sources[0])) {
            return emptyList()
        }
        val seenMultiLine = mutableMapOf<String, Int>()
        val result = mutableListOf<MediaSourceDto>()
        for (s in sources) {
            val name = s.Name?.trim().orEmpty()
            if (!name.contains("\n")) {
                result.add(s)
                continue
            }
            val existingIdx = seenMultiLine[name]
            if (existingIdx != null) {
                val existing = result[existingIdx]
                val sHasProbed = s.MediaStreams?.any { it.Type == "Video" } == true
                val exHasProbed = existing.MediaStreams?.any { it.Type == "Video" } == true
                val merged = if (!sHasProbed && exHasProbed) {
                    s.copy(
                        MediaStreams = existing.MediaStreams,
                        RunTimeTicks = s.RunTimeTicks ?: existing.RunTimeTicks,
                        Size = s.Size ?: existing.Size,
                        Container = s.Container ?: existing.Container,
                    )
                } else {
                    s
                }
                result[existingIdx] = merged
            } else {
                seenMultiLine[name] = result.size
                result.add(s)
            }
        }
        return result
    }

    private fun isKnownWarm(itemId: String): Boolean =
        knownWarm.contains(com.jellio.tv.data.streams.normalizeSourceId(itemId))

    fun prefetchStreams(itemId: String) {
        val now = System.currentTimeMillis()
        if (isKnownWarm(itemId) || hasRecentStreams(itemId, now)) return
        prefetchedAt[itemId] = now
        prefetch.scope.launch {
            // Waits its turn briefly; given up, a later dwell tries again.
            if (!acquirePrefetchSlot(PREFETCH_SLOT_WAIT_MS)) {
                prefetchedAt.remove(itemId)
                return@launch
            }
            com.jellio.tv.data.streams.StreamLookupLog.prefetch(itemId)
            try {
                catching { api.prefetchGelatoStreams(itemId) }
            } finally {
                prefetchSlots.release()
            }
        }
    }

    // Warmed a short while ago, or its stream list is cached or loading.
    private fun hasRecentStreams(itemId: String, now: Long): Boolean {
        val last = prefetchedAt[itemId]
        if (last != null && now - last < PREFETCH_WINDOW_MS) return true
        return sourceCache.get(itemId, now) != null || sourcesInFlight.containsKey(itemId)
    }

    private suspend fun acquirePrefetchSlot(waitMs: Long): Boolean {
        val until = System.currentTimeMillis() + waitMs
        while (!prefetchSlots.tryAcquire()) {
            if (System.currentTimeMillis() >= until) return false
            delay(100)
        }
        return true
    }

    // Stream lists only, a few titles in one call; the job is cancelled
    // when the search that asked for it changes.
    fun prefetchStreamsBatch(itemIds: List<String>): kotlinx.coroutines.Job? {
        val ids = itemIds.filter { it.isNotBlank() && !isKnownWarm(it) }.distinct()
        if (ids.isEmpty()) return null
        return prefetch.scope.launch {
            if (!acquirePrefetchSlot(PREFETCH_SLOT_WAIT_MS)) return@launch
            try {
                val response = catching {
                    api.prefetchGelatoBatch(com.jellio.tv.data.model.PrefetchBatchRequest(ids, Probe = false))
                }.getOrNull()
                if (knownWarm.size > MAX_HINTS) knownWarm.clear()
                response?.body()?.AlreadyWarm.orEmpty().forEach {
                    knownWarm.add(com.jellio.tv.data.streams.normalizeSourceId(it))
                }
            } finally {
                prefetchSlots.release()
            }
        }
    }

    // { sourceId: warmedAt } for sources that start fast now; null when the
    // server can't say.
    suspend fun getWarmSources(itemId: String): Map<String, String>? = catching {
        val response = api.getWarmSources(itemId)
        if (response.isSuccessful) response.body().orEmpty() else null
    }.getOrNull()?.also { warm ->
        if (warmSeen.size > 64) warmSeen.clear()
        warmSeen[itemId] = System.currentTimeMillis() to warm.keys.map { com.jellio.tv.data.streams.normalizeSourceId(it) }.toSet()
    }

    private val warmSeen = ConcurrentHashMap<String, Pair<Long, Set<String>>>()

    // The title's warm sources as last asked, if that was recent; no request.
    fun recentWarmSources(itemId: String): Set<String>? =
        warmSeen[itemId]?.takeIf { System.currentTimeMillis() - it.first < WARM_SEEN_TTL_MS }?.second

    suspend fun getQualityTiers(itemId: String): Map<String, String> = catching {
        val response = api.getQualityTiers(itemId)
        if (response.isSuccessful) response.body().orEmpty() else emptyMap()
    }.getOrDefault(emptyMap())

    // The probe Play would run for one particular source, so picking it
    // later starts fast.
    fun probeSource(userId: String, itemId: String, mediaSourceId: String) {
        prefetch.scope.launch {
            if (!acquirePrefetchSlot(PREFETCH_SLOT_WAIT_MS)) return@launch
            try {
                catching {
                    api.getPlaybackInfo(itemId, PlaybackInfoRequest(UserId = userId, MediaSourceId = mediaSourceId))
                }
            } finally {
                prefetchSlots.release()
            }
        }
    }

    private var queuedPrefetch: kotlinx.coroutines.Job? = null

    // One title at a time, so a slot stays free for whatever gets focus.
    fun prefetchStreamsInTurn(itemIds: List<String>) {
        queuedPrefetch?.cancel()
        queuedPrefetch = prefetch.scope.launch {
            for (itemId in itemIds) {
                if (hasRecentStreams(itemId, System.currentTimeMillis())) continue
                while (!prefetchSlots.tryAcquire()) delay(500)
                prefetchedAt[itemId] = System.currentTimeMillis()
                com.jellio.tv.data.streams.StreamLookupLog.prefetch(itemId)
                try {
                    catching { api.prefetchGelatoStreams(itemId) }
                } finally {
                    prefetchSlots.release()
                }
            }
        }
    }
}
