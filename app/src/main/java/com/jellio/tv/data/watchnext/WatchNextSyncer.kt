package com.jellio.tv.data.watchnext

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
import com.jellio.tv.data.session.SessionManager
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// Real gap Google TV's own launcher home leaves entirely to each app:
// its own "Continue Watching" row (the one real feedback pointed at,
// Netflix/Disney+ populate it the exact same way) is not this app's
// own HomeScreen row at all, it is a system level shared "Watch Next"
// channel every app on the device writes its own rows into
// (TvContractCompat.WatchNextPrograms, androidx.tvprovider, stable
// since API 26, confirmed against Android's own developer guide before
// writing this rather than guessed). No special permission needed for
// this channel specifically, unlike a real regular TvContractCompat
// Channel/Program pair, which is gated behind a signature|system
// android.permission.WRITE_EPG_DATA this app could never hold.
//
// Kept as its own class rather than folded into JellioRepository:
// every other real repository call here is a plain network fetch, this
// one is real local ContentResolver I/O against a system provider, a
// different enough real concern (and real Context dependency) to earn
// its own file.
@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun parseInstant(raw: String): Long = java.time.Instant.parse(raw).toEpochMilli()

// What the last sync did, for Settings > About.
data class WatchNextStatus(
    val at: Long,
    val published: Int,
    val candidates: Int,
    val onLauncher: Int,
    val hiddenByViewer: Int,
    val error: String?,
)

@Singleton
class WatchNextSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellioRepository,
    private val sessionManager: SessionManager,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private val _status = kotlinx.coroutines.flow.MutableStateFlow<WatchNextStatus?>(null)
    val status: kotlinx.coroutines.flow.StateFlow<WatchNextStatus?> = _status

    // Google requires the Content ID on every item (API 28+ column).
    private fun valuesOf(program: WatchNextProgram, itemId: String): android.content.ContentValues =
        program.toContentValues().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) put(android.media.tv.TvContract.WatchNextPrograms.COLUMN_CONTENT_ID, itemId)
        }

    private fun deepLinkUri(itemId: String): Uri = appLink("https://jellio.tv/play?id=$itemId")

    private fun detailLinkUri(itemId: String): Uri = appLink("https://jellio.tv/item?id=$itemId")

    private fun appLink(url: String): Uri {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(context.packageName)
        return Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
    }

    private class Art(val itemId: String, val tag: String, val type: String, val landscape: Boolean)

    // Google TV's Continue Watching row is landscape: an episode's show
    // Thumb or backdrop, a movie's Thumb or backdrop, the poster last.
    private fun artFor(item: BaseItemDto): Art? {
        val tags = item.ImageTags.orEmpty()
        if (item.Type == "Episode") {
            // The show's art, the episode's own still only as a last resort.
            val thumbOwner = item.ParentThumbItemId
            val thumbTag = item.ParentThumbImageTag
            if (thumbOwner != null && thumbTag != null) return Art(thumbOwner, thumbTag, "Thumb", true)
            val backdropOwner = item.ParentBackdropItemId
            val backdropTag = item.ParentBackdropImageTags?.firstOrNull()
            if (backdropOwner != null && backdropTag != null) return Art(backdropOwner, backdropTag, "Backdrop", true)
            // The episode didn't carry its show's art: ask the show itself.
            showInfo[item.SeriesId]?.let { show ->
                val showTags = show.ImageTags.orEmpty()
                showTags["Thumb"]?.let { return Art(show.Id, it, "Thumb", true) }
                show.BackdropImageTags?.firstOrNull()?.let { return Art(show.Id, it, "Backdrop", true) }
                showTags["Primary"]?.let { return Art(show.Id, it, "Primary", false) }
            }
            return tags["Primary"]?.let { Art(item.Id, it, "Primary", true) }
        }
        tags["Thumb"]?.let { return Art(item.Id, it, "Thumb", true) }
        item.BackdropImageTags?.firstOrNull()?.let { return Art(item.Id, it, "Backdrop", true) }
        tags["Primary"]?.let { return Art(item.Id, it, "Primary", false) }
        return null
    }

    // Shows (and items) looked up for the current sync, by id.
    @Volatile
    private var showInfo: Map<String, BaseItemDto> = emptyMap()

    private fun artDir(): File = File(context.filesDir, WatchNextArtProvider.DIR).apply { mkdirs() }

    private fun artFile(itemId: String, tag: String): File = File(artDir(), "${safe(itemId)}-${safe(tag)}.jpg")

    private fun safe(value: String): String = value.filter { it.isLetterOrDigit() || it == '-' }

    // Downloads once per image tag; a changed tag (new artwork) fetches again.
    private fun ensureArt(session: Session, item: BaseItemDto, art: Art): File? {
        val file = artFile(item.Id, art.tag)
        if (file.length() > 0) return file
        val width = if (art.landscape) 640 else 400
        val url = repository.imageUrl(session.serverAddress, art.itemId, art.tag, art.type, width, session.accessToken)
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val tmp = File(file.parentFile, file.name + ".part")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                if (!tmp.renameTo(file)) return null
                artDir().listFiles()?.forEach { other ->
                    if (other != file && other.name.startsWith(safe(item.Id) + "-")) other.delete()
                }
                file
            }
        } catch (err: Exception) {
            Log.w(TAG, "artwork for ${item.Id} failed", err)
            null
        }
    }

    private fun displayTitle(item: BaseItemDto): String =
        if (item.Type == "Episode" && !item.SeriesName.isNullOrBlank()) item.SeriesName else item.Name.orEmpty()

    private fun engagedAt(item: BaseItemDto): Long {
        val raw = item.UserData?.LastPlayedDate ?: return System.currentTimeMillis()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return System.currentTimeMillis()
        return runCatching { parseInstant(raw) }.getOrDefault(System.currentTimeMillis())
    }

    private fun toWatchNextProgram(session: Session, item: BaseItemDto, next: Boolean, engaged: Long, overview: String?): WatchNextProgram? {
        val runtimeMs = ((item.RunTimeTicks ?: 0L) / 10_000).toInt()
        val positionMs = ((item.UserData?.PlaybackPositionTicks ?: 0L) / 10_000).toInt()
        if (runtimeMs <= 0) return null
        val art = artFor(item) ?: return null
        val file = ensureArt(session, item, art) ?: return null
        val type = if (item.Type == "Episode") {
            TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
        } else {
            TvContractCompat.WatchNextPrograms.TYPE_MOVIE
        }
        val builder = WatchNextProgram.Builder()
            .setType(type)
            .setWatchNextType(
                if (next) TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT else TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE,
            )
            .setTitle(displayTitle(item))
            .setPosterArtUri(WatchNextArtProvider.uriFor(context, file))
            .setPosterArtAspectRatio(
                if (art.landscape) TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9 else TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER,
            )
            .setIntentUri(deepLinkUri(item.Id))
            .setInternalProviderId(item.Id)
            .setDurationMillis(runtimeMs)
            .setLastPlaybackPositionMillis(positionMs)
            .setLastEngagementTimeUtcMillis(engaged)
        overview?.let { builder.setDescription(it) }
        if (item.Type == "Episode") {
            item.ParentIndexNumber?.let { builder.setSeasonNumber(it) }
            item.IndexNumber?.let { builder.setEpisodeNumber(it) }
            item.Name?.let { builder.setEpisodeTitle(it) }
        }
        return builder.build()
    }

    // Called on every Home load and after playback stops. Upserts by
    // INTERNAL_PROVIDER_ID and removes rows Continue Watching no longer
    // lists. A row the viewer removed from the launcher stays removed until
    // they watch that title again.
    suspend fun sync(session: Session, continueWatching: List<BaseItemDto>, upNext: List<BaseItemDto> = emptyList()) {
        if (!supported()) {
            _status.value = WatchNextStatus(System.currentTimeMillis(), 0, continueWatching.size, 0, 0, "This device has no Continue Watching row")
            return
        }
        locked {
            try {
                val existing = queryExisting()
                // One entry per series, the most recently played episode.
                // Unfinished titles as CONTINUE, then the next episode of other
                // shows being watched as NEXT; one entry per series.
                // Google TV shows five cards per app: unfinished titles take
                // them first, next episodes fill the rest and sort just after.
                val continuing = continueWatching.distinctBy { it.SeriesId ?: it.Id }
                val seen = continuing.map { it.SeriesId ?: it.Id }.toSet()
                val nexts = upNext.filter { (it.SeriesId ?: it.Id) !in seen }.distinctBy { it.SeriesId ?: it.Id }
                val candidates = (continuing + nexts).take(MAX_ROWS)
                val oldest = continuing.minOfOrNull { engagedAt(it) } ?: System.currentTimeMillis()
                // Rows carry neither overviews nor always their show's art:
                // one lookup covers every listed title and its show.
                val listed = (continueWatching.take(CHANNEL_SIZE) + upNext.take(CHANNEL_SIZE))
                val info = runCatching {
                    repository.getItemsInfo(session.userId, listed.flatMap { listOfNotNull(it.Id, it.SeriesId) })
                }.getOrDefault(emptyMap())
                showInfo = info
                val overviews = info.mapNotNull { (id, found) -> found.Overview?.takeIf { it.isNotBlank() }?.let { id to it } }.toMap()
                val wanted = candidates.mapIndexedNotNull { index, item ->
                    val next = index >= continuing.size
                    val engaged = if (next) oldest - (index + 1) * 60_000L else engagedAt(item)
                    val overview = item.Overview ?: overviews[item.Id] ?: item.SeriesId?.let { overviews[it] }
                    toWatchNextProgram(session, item, next, engaged, overview)?.let { item.Id to it }
                }.toMap()

                wanted.forEach { (itemId, program) ->
                    val row = existing[itemId]
                    when {
                        row == null -> context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, valuesOf(program, itemId))
                        !row.browsable && row.engagedAt >= program.lastEngagementTimeUtcMillis -> Unit
                        !row.browsable -> {
                            context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(row.id), null, null)
                            context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, valuesOf(program, itemId))
                        }
                        // A NEXT entry keeps its time; art and text still update.
                        program.watchNextType == TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT ->
                            context.contentResolver.update(
                                TvContractCompat.buildWatchNextProgramUri(row.id),
                                valuesOf(program, itemId).apply {
                                    put(TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, row.engagedAt)
                                },
                                null,
                                null,
                            )
                        else -> context.contentResolver.update(TvContractCompat.buildWatchNextProgramUri(row.id), valuesOf(program, itemId), null, null)
                    }
                }

                existing.forEach { (itemId, row) ->
                    if (!wanted.containsKey(itemId)) {
                        context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(row.id), null, null)
                    }
                }
                val channelIds = runCatching { syncChannels(session, continueWatching, upNext, overviews) }
                    .onFailure { Log.w(TAG, "channels failed", it) }
                    .getOrDefault(emptySet())
                val keep = (wanted.keys + channelIds).map { safe(it) + "-" }
                artDir().listFiles()?.forEach { file -> if (keep.none { file.name.startsWith(it) }) file.delete() }
                val after = queryExisting()
                _status.value = WatchNextStatus(
                    at = System.currentTimeMillis(),
                    published = wanted.size,
                    candidates = candidates.size,
                    onLauncher = after.values.count { it.browsable },
                    hiddenByViewer = after.values.count { !it.browsable },
                    error = null,
                )
                Log.i(TAG, "synced ${wanted.size} of ${candidates.size} titles, ${after.size} rows in the provider")
            } catch (err: Exception) {
                _status.value = WatchNextStatus(System.currentTimeMillis(), 0, continueWatching.size, 0, 0, err.javaClass.simpleName + ": " + err.message)
                Log.w(TAG, "sync failed", err)
            }
        }
    }

    // After a stop report, without waiting for the viewer to go back Home.
    suspend fun refresh() {
        if (!supported()) return
        val session = sessionManager.sessionFlow.first() ?: return
        val items = runCatching { repository.getContinueWatching(session.userId) }.getOrNull() ?: return
        val upNext = runCatching { repository.getNextUp(session.userId) }.getOrDefault(emptyList())
        sync(session, items, upNext)
    }

    // Sign out: nothing of this account stays on the launcher.
    suspend fun clear() {
        if (!supported()) return
        locked {
            runCatching {
                queryExisting().values.forEach { row ->
                    context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(row.id), null, null)
                }
                CHANNELS.forEach { channel ->
                    channelPrefs.getLong(channel.key, -1L).takeIf { it >= 0 }?.let { id ->
                        context.contentResolver.delete(TvContractCompat.buildChannelUri(id), null, null)
                    }
                }
                channelPrefs.edit().clear().apply()
                artDir().listFiles()?.forEach { it.delete() }
            }
        }
    }

    // ---- Home screen channels -------------------------------------------
    // Launchers that show app channels (Projectivy, the Android TV home)
    // get a row per list. Continue Watching and Next Up play straight
    // away; Watchlist and Recently Added open the title page.

    private class ChannelSpec(val key: String, val name: String, val plays: Boolean)

    private val channelPrefs by lazy { context.getSharedPreferences("jellio_channels", Context.MODE_PRIVATE) }

    @Volatile
    private var topPicks: List<BaseItemDto>? = null

    // Home's "Top Picks for You", once its recommendations are built.
    suspend fun updateTopPicks(session: Session, picks: List<BaseItemDto>) {
        if (!supported()) return
        topPicks = picks
        val spec = CHANNELS.first { it.key == "picks" }
        locked {
            runCatching {
                val id = channelId(spec) ?: return@runCatching
                context.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(id), null, null)
                picks.take(CHANNEL_SIZE).forEachIndexed { index, item ->
                    val program = toPreviewProgram(session, id, item, plays = false, weight = CHANNEL_SIZE - index, overview = null) ?: return@forEachIndexed
                    context.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
                }
            }.onFailure { Log.w(TAG, "top picks channel failed", it) }
        }
    }

    // A channel from an earlier version that is no longer published.
    private fun dropChannel(key: String) {
        val id = channelPrefs.getLong(key, -1L).takeIf { it >= 0 } ?: return
        context.contentResolver.delete(TvContractCompat.buildChannelUri(id), null, null)
        channelPrefs.edit().remove(key).apply()
    }

    private fun channelId(spec: ChannelSpec): Long? {
        val known = channelPrefs.getLong(spec.key, -1L)
        if (known >= 0) {
            val exists = context.contentResolver.query(TvContractCompat.buildChannelUri(known), null, null, null, null)
                ?.use { it.count > 0 } == true
            if (exists) return known
        }
        val channel = androidx.tvprovider.media.tv.Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(spec.name)
            .setAppLinkIntentUri(appLink("https://jellio.tv/home"))
            .setInternalProviderId(spec.key)
            .build()
        val uri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues()) ?: return null
        val id = android.content.ContentUris.parseId(uri)
        channelPrefs.edit().putLong(spec.key, id).apply()
        runCatching {
            val logo = androidx.core.content.ContextCompat.getDrawable(context, com.jellio.tv.R.mipmap.ic_launcher)
            if (logo != null) {
                val bitmap = android.graphics.Bitmap.createBitmap(160, 160, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                logo.setBounds(0, 0, 160, 160)
                logo.draw(canvas)
                androidx.tvprovider.media.tv.ChannelLogoUtils.storeChannelLogo(context, id, bitmap)
            }
        }
        // Only the first channel may ask to be shown; the rest are turned on
        // in the launcher's channel settings.
        if (spec.key == CHANNELS.first().key) runCatching { TvContractCompat.requestChannelBrowsable(context, id) }
        return id
    }

    private fun toPreviewProgram(
        session: Session,
        channelId: Long,
        item: BaseItemDto,
        plays: Boolean,
        weight: Int,
        overview: String?,
    ): androidx.tvprovider.media.tv.PreviewProgram? {
        val art = artFor(item) ?: return null
        val file = ensureArt(session, item, art) ?: return null
        val type = when (item.Type) {
            "Episode" -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
            "Series" -> TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
            else -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
        }
        val builder = androidx.tvprovider.media.tv.PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(type)
            .setTitle(displayTitle(item))
            .setPosterArtUri(WatchNextArtProvider.uriFor(context, file))
            .setPosterArtAspectRatio(
                if (art.landscape) TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9 else TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER,
            )
            .setIntentUri(if (plays) deepLinkUri(item.Id) else detailLinkUri(item.Id))
            .setInternalProviderId(item.Id)
            .setWeight(weight)
        (overview ?: item.Overview)?.takeIf { it.isNotBlank() }?.let { builder.setDescription(it) }
        val runtimeMs = ((item.RunTimeTicks ?: 0L) / 10_000).toInt()
        if (runtimeMs > 0) builder.setDurationMillis(runtimeMs)
        val positionMs = ((item.UserData?.PlaybackPositionTicks ?: 0L) / 10_000).toInt()
        if (positionMs > 0) builder.setLastPlaybackPositionMillis(positionMs)
        if (item.Type == "Episode") {
            item.ParentIndexNumber?.let { builder.setSeasonNumber(it) }
            item.IndexNumber?.let { builder.setEpisodeNumber(it) }
            item.Name?.let { builder.setEpisodeTitle(it) }
        }
        return builder.build()
    }

    // Replaces each channel's programs; returns every item id shown, so
    // their artwork is kept.
    private suspend fun syncChannels(
        session: Session,
        continueWatching: List<BaseItemDto>,
        upNext: List<BaseItemDto>,
        overviews: Map<String, String>,
    ): Set<String> {
        val watchlist = runCatching { repository.getWatchlistItems(session.userId, CHANNEL_SIZE) }.getOrDefault(emptyList())
        dropChannel("latest")
        val lists = buildMap {
            put("continue", continueWatching)
            put("nextup", upNext)
            put("watchlist", watchlist)
            // Left as they are until Home has worked out the picks.
            topPicks?.let { put("picks", it) }
        }
        val shown = mutableSetOf<String>()
        CHANNELS.forEach { spec ->
            val items = lists[spec.key]?.take(CHANNEL_SIZE) ?: return@forEach
            val id = channelId(spec) ?: return@forEach
            context.contentResolver.delete(TvContractCompat.buildPreviewProgramsUriForChannel(id), null, null)
            items.forEachIndexed { index, item ->
                val overview = overviews[item.Id] ?: item.SeriesId?.let { overviews[it] }
                val program = toPreviewProgram(session, id, item, spec.plays, items.size - index, overview) ?: return@forEachIndexed
                context.contentResolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
                shown += item.Id
            }
        }
        Log.i(TAG, "channels: " + lists.entries.joinToString { it.key + " " + it.value.size.coerceAtMost(CHANNEL_SIZE) })
        return shown
    }

    private class Row(val id: Long, val browsable: Boolean, val engagedAt: Long)

    // A plain query against this shared table only returns this app's rows.
    // Home loads and playback stops can sync at the same moment; both used
    // to see no row yet and insert the same title twice.
    private val syncLock = kotlinx.coroutines.sync.Mutex()

    private suspend fun locked(block: suspend () -> Unit) {
        syncLock.withLock { withContext(Dispatchers.IO) { block() } }
    }

    // One row per title: extra copies (left by that race) are deleted.
    private fun queryExisting(): Map<String, Row> {
        val result = mutableMapOf<String, Row>()
        val cursor = context.contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            WatchNextProgram.PROJECTION,
            null,
            null,
            null,
        ) ?: return result
        cursor.use {
            while (it.moveToNext()) {
                val program = WatchNextProgram.fromCursor(it)
                val internalId = program.internalProviderId ?: continue
                if (result.containsKey(internalId)) {
                    context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(program.id), null, null)
                    continue
                }
                result[internalId] = Row(program.id, program.isBrowsable, program.lastEngagementTimeUtcMillis)
            }
        }
        return result
    }

    private companion object {
        const val TAG = "JellioWatchNext"
        const val MAX_ROWS = 5
        const val CHANNEL_SIZE = 15
        private val CHANNELS = listOf(
            ChannelSpec("continue", "Continue Watching", plays = true),
            ChannelSpec("nextup", "Next Up", plays = true),
            ChannelSpec("watchlist", "Watchlist", plays = false),
            ChannelSpec("picks", "Top Picks for You", plays = false),
        )
    }
}
