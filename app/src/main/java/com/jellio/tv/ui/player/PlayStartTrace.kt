package com.jellio.tv.ui.player

import android.util.Log
import com.jellio.tv.data.model.PlayStartRecord
import com.jellio.tv.data.model.PlayStartStages

data class PlayStartMark(val step: String, val at: Long, val detail: String = "")

data class PlayStartRun(val itemId: String, val begunFrom: String, val startedAt: Long, val marks: List<PlayStartMark>)

object PlayStartTrace {
    private const val TAG = "JellioPlayStart"
    private const val STALE_MS = 120_000L

    private var itemId: String? = null
    private var begunFrom = "player"
    private var startedAt = 0L
    private var lastAt = 0L
    private val marks = mutableListOf<PlayStartMark>()

    @Synchronized
    fun begin(from: String) {
        val now = System.currentTimeMillis()
        itemId = null
        begunFrom = from
        startedAt = now
        lastAt = now
        marks.clear()
        Log.i(TAG, "begin from=$from")
    }

    // The first step with an id adopts the run begun on the title page; a
    // step for another title, or long after, starts a new run.
    @Synchronized
    fun mark(itemId: String, step: String, detail: String = "") {
        val now = System.currentTimeMillis()
        if (startedAt == 0L || now - startedAt > STALE_MS || (this.itemId != null && this.itemId != itemId)) {
            begunFrom = "player"
            startedAt = now
            lastAt = now
            marks.clear()
            Log.i(TAG, "begin from=player")
        }
        this.itemId = itemId
        marks.add(PlayStartMark(step, now, detail))
        Log.i(TAG, "$itemId $step +${now - lastAt}ms total=${now - startedAt}ms${if (detail.isNotEmpty()) " $detail" else ""}")
        lastAt = now
    }

    // A step each player open takes once, from wherever it happens first.
    @Synchronized
    fun markOnce(step: String) {
        val id = itemId ?: return
        val open = marks.indexOfLast { it.step == "player open" }
        if (startedAt == 0L || open < 0 || marks.subList(open, marks.size).any { it.step == step }) return
        mark(id, step)
    }

    // Closes whichever run is open; the player only knows its current title.
    @Synchronized
    fun end(step: String): PlayStartRun? {
        val id = itemId ?: return null
        if (startedAt == 0L) return null
        mark(id, step)
        val run = PlayStartRun(id, begunFrom, startedAt, marks.toList())
        startedAt = 0L
        itemId = null
        marks.clear()
        return run
    }
}

private val FROM_DETAIL = Regex("\\bfrom=(prewarmed|prefetched|early|fresh)\\b")

// The play-start report for a run closed at its first frame. Stages count
// from the last "player open", so a failover is measured on its own.
fun playStartRecord(
    run: PlayStartRun,
    mediaSourceId: String?,
    directPlay: Boolean?,
    warmAtPlay: Boolean?,
    startPositionTicks: Long,
): PlayStartRecord? {
    val open = run.marks.lastOrNull { it.step == "player open" } ?: return null
    val after = run.marks.dropWhile { it !== open }
    fun stage(step: String): Int? = after.firstOrNull { it.step == step }?.let { (it.at - open.at).coerceAtLeast(0L).toInt() }
    val firstFrame = after.lastOrNull { it.step == "first frame" } ?: return null
    // Same meaning as the web client: TotalMs counts from player open, like every stage.
    val firstFrameMs = (firstFrame.at - open.at).coerceAtLeast(0L).toInt()
    val trigger = when {
        open.detail == "failover" -> "failover"
        run.begunFrom == "title page" -> "detail"
        run.begunFrom == "next episode" -> "next-episode"
        startPositionTicks > 0 -> "resume"
        else -> "other"
    }
    val from = after.firstOrNull { it.step == "playback info" }
        ?.let { FROM_DETAIL.find(it.detail)?.groupValues?.get(1) }
        ?: "fresh"
    return PlayStartRecord(
        ItemId = run.itemId,
        MediaSourceId = mediaSourceId,
        Trigger = trigger,
        From = from,
        WarmAtPlay = warmAtPlay,
        DirectPlay = directPlay,
        Stages = PlayStartStages(
            DetailsMs = stage("details"),
            PlaybackInfoMs = stage("playback info"),
            PlayerReadyMs = stage("player ready"),
            FirstByteMs = stage("first byte"),
            FirstFrameMs = firstFrameMs,
        ),
        TotalMs = firstFrameMs,
    )
}
