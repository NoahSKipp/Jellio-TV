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

    private fun deepLinkUri(itemId: String): Uri {
        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jellio.tv/play?id=$itemId"))
            .setPackage(context.packageName)
        return Uri.parse(playIntent.toUri(Intent.URI_INTENT_SCHEME))
    }

    private class Art(val itemId: String, val tag: String, val type: String, val landscape: Boolean)

    // Google TV's Continue Watching row is landscape: an episode's own
    // still, a movie's Thumb or backdrop, the poster only as a last resort.
    private fun artFor(item: BaseItemDto): Art? {
        val tags = item.ImageTags.orEmpty()
        if (item.Type == "Episode") {
            tags["Primary"]?.let { return Art(item.Id, it, "Primary", true) }
            val thumbOwner = item.ParentThumbItemId
            val thumbTag = item.ParentThumbImageTag
            if (thumbOwner != null && thumbTag != null) return Art(thumbOwner, thumbTag, "Thumb", true)
            val backdropOwner = item.ParentBackdropItemId
            val backdropTag = item.ParentBackdropImageTags?.firstOrNull()
            if (backdropOwner != null && backdropTag != null) return Art(backdropOwner, backdropTag, "Backdrop", true)
            return null
        }
        tags["Thumb"]?.let { return Art(item.Id, it, "Thumb", true) }
        item.BackdropImageTags?.firstOrNull()?.let { return Art(item.Id, it, "Backdrop", true) }
        tags["Primary"]?.let { return Art(item.Id, it, "Primary", false) }
        return null
    }

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

    private fun toWatchNextProgram(session: Session, item: BaseItemDto): WatchNextProgram? {
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
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setTitle(displayTitle(item))
            .setPosterArtUri(WatchNextArtProvider.uriFor(context, file))
            .setPosterArtAspectRatio(
                if (art.landscape) TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9 else TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER,
            )
            .setIntentUri(deepLinkUri(item.Id))
            .setInternalProviderId(item.Id)
            .setDurationMillis(runtimeMs)
            .setLastPlaybackPositionMillis(positionMs)
            .setLastEngagementTimeUtcMillis(engagedAt(item))
        item.Overview?.takeIf { it.isNotBlank() }?.let { builder.setDescription(it) }
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
    suspend fun sync(session: Session, continueWatching: List<BaseItemDto>) {
        if (!supported()) {
            _status.value = WatchNextStatus(System.currentTimeMillis(), 0, continueWatching.size, 0, 0, "This device has no Continue Watching row")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val existing = queryExisting()
                // One entry per series, the most recently played episode.
                val candidates = continueWatching.distinctBy { it.SeriesId ?: it.Id }.take(MAX_ROWS)
                val wanted = candidates.mapNotNull { item -> toWatchNextProgram(session, item)?.let { item.Id to it } }.toMap()

                wanted.forEach { (itemId, program) ->
                    val row = existing[itemId]
                    when {
                        row == null -> context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, valuesOf(program, itemId))
                        !row.browsable && row.engagedAt >= program.lastEngagementTimeUtcMillis -> Unit
                        !row.browsable -> {
                            context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(row.id), null, null)
                            context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, valuesOf(program, itemId))
                        }
                        else -> context.contentResolver.update(TvContractCompat.buildWatchNextProgramUri(row.id), valuesOf(program, itemId), null, null)
                    }
                }

                existing.forEach { (itemId, row) ->
                    if (!wanted.containsKey(itemId)) {
                        context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(row.id), null, null)
                    }
                }
                val keep = wanted.keys.map { safe(it) + "-" }
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
        sync(session, items)
    }

    // Sign out: nothing of this account stays on the launcher.
    suspend fun clear() {
        if (!supported()) return
        withContext(Dispatchers.IO) {
            runCatching {
                queryExisting().values.forEach { row ->
                    context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(row.id), null, null)
                }
                artDir().listFiles()?.forEach { it.delete() }
            }
        }
    }

    private class Row(val id: Long, val browsable: Boolean, val engagedAt: Long)

    // A plain query against this shared table only returns this app's rows.
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
                result[internalId] = Row(program.id, program.isBrowsable, program.lastEngagementTimeUtcMillis)
            }
        }
        return result
    }

    private companion object {
        const val TAG = "JellioWatchNext"
        const val MAX_ROWS = 20
    }
}
