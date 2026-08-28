package com.jellio.tv.data.watchnext

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.jellio.tv.data.JellioRepository
import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.session.Session
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
@Singleton
class WatchNextSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JellioRepository,
) {
    // Real deep link scheme MainActivity's own manifest intent-filter
    // and handleIntent() match back against: an https URL rather than
    // a bare custom scheme so a real second real launcher surface
    // (Android's own default App Links flow) has something ordinary to
    // resolve too, even though setPackage below already routes it
    // straight back to this app without needing that.
    private fun deepLinkUri(itemId: String): Uri {
        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jellio.tv/play?id=$itemId"))
            .setPackage(context.packageName)
        return Uri.parse(playIntent.toUri(Intent.URI_INTENT_SCHEME))
    }

    private fun posterUri(session: Session, item: BaseItemDto): Uri? {
        val tag = item.ImageTags?.get("Primary") ?: return null
        return Uri.parse(repository.imageUrl(session.serverAddress, item.Id, tag, "Primary", 400))
    }

    private fun displayTitle(item: BaseItemDto): String =
        if (item.Type == "Episode" && !item.SeriesName.isNullOrBlank()) item.SeriesName else item.Name.orEmpty()

    private fun toWatchNextProgram(session: Session, item: BaseItemDto): WatchNextProgram? {
        val poster = posterUri(session, item) ?: return null
        val runtimeMs = ((item.RunTimeTicks ?: 0L) / 10_000).toInt()
        val positionMs = ((item.UserData?.PlaybackPositionTicks ?: 0L) / 10_000).toInt()
        if (runtimeMs <= 0) return null
        val type = if (item.Type == "Episode") {
            TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
        } else {
            TvContractCompat.WatchNextPrograms.TYPE_MOVIE
        }
        val builder = WatchNextProgram.Builder()
            .setType(type)
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setTitle(displayTitle(item))
            .setPosterArtUri(poster)
            .setIntentUri(deepLinkUri(item.Id))
            .setInternalProviderId(item.Id)
            .setDurationMillis(runtimeMs)
            .setLastPlaybackPositionMillis(positionMs)
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
        if (item.Type == "Episode") {
            item.ParentIndexNumber?.let { builder.setSeasonNumber(it) }
            item.IndexNumber?.let { builder.setEpisodeNumber(it) }
            item.Name?.let { builder.setEpisodeTitle(it) }
        }
        return builder.build()
    }

    // Real components/nowPlaying.js's own poll loop has no equivalent
    // here worth building: Continue Watching only ever changes on a
    // real playback stop/progress report, not tick by tick, so this is
    // called once per real HomeViewModel.load() (every real app open
    // or account switch) rather than kept running. Upserts by
    // INTERNAL_PROVIDER_ID (this app's own itemId, stable across a
    // real resync unlike the provider's own row id) so a title still
    // in progress keeps its own real Watch Next position, and removes
    // every row this app itself owns that Continue Watching no longer
    // lists (finished, or its own saved position cleared elsewhere,
    // components/cardOptionsMenu.js's own Remove already covers that
    // real case web side, TvHomeViewModel/HomeViewModel's own real
    // caller not needing to know which).
    suspend fun sync(session: Session, continueWatching: List<BaseItemDto>) {
        withContext(Dispatchers.IO) {
            val existing = queryExisting()
            val wanted = continueWatching.mapNotNull { item -> toWatchNextProgram(session, item)?.let { item.Id to it } }.toMap()

            wanted.forEach { (itemId, program) ->
                val rowId = existing[itemId]
                if (rowId != null) {
                    context.contentResolver.update(
                        TvContractCompat.buildWatchNextProgramUri(rowId),
                        program.toContentValues(),
                        null,
                        null,
                    )
                } else {
                    context.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
                }
            }

            existing.forEach { (itemId, rowId) ->
                if (!wanted.containsKey(itemId)) {
                    context.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(rowId), null, null)
                }
            }
        }
    }

    // itemId -> this provider's own real row id, scoped to rows this
    // exact app package already owns (WatchNextPrograms.CONTENT_URI is
    // a shared system table, but a plain query against it only ever
    // returns this app's own rows back, the same real system level
    // isolation TvContractCompat documents rather than something this
    // class has to filter for itself).
    private fun queryExisting(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
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
                result[internalId] = program.id
            }
        }
        return result
    }
}
