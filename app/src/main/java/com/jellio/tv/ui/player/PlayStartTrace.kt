package com.jellio.tv.ui.player

import android.util.Log

// One line per step from pressing Play to the first frame, under the log
// tag JellioPlayStart, so a device can report where the time goes:
//   adb logcat -s JellioPlayStart
object PlayStartTrace {
    private const val TAG = "JellioPlayStart"
    private const val STALE_MS = 120_000L

    private var itemId: String? = null
    private var startedAt = 0L
    private var lastAt = 0L

    @Synchronized
    fun begin(from: String) {
        val now = System.currentTimeMillis()
        itemId = null
        startedAt = now
        lastAt = now
        Log.i(TAG, "begin from=$from")
    }

    // The first step with an id adopts the run begun on the title page; a
    // step for another title, or long after, starts a new run.
    @Synchronized
    fun mark(itemId: String, step: String, detail: String = "") {
        val now = System.currentTimeMillis()
        if (startedAt == 0L || now - startedAt > STALE_MS || (this.itemId != null && this.itemId != itemId)) {
            startedAt = now
            lastAt = now
            Log.i(TAG, "begin from=player")
        }
        this.itemId = itemId
        Log.i(TAG, "$itemId $step +${now - lastAt}ms total=${now - startedAt}ms${if (detail.isNotEmpty()) " $detail" else ""}")
        lastAt = now
    }

    // Closes whichever run is open; the player only knows its current title.
    @Synchronized
    fun end(step: String) {
        val id = itemId ?: return
        if (startedAt == 0L) return
        mark(id, step)
        startedAt = 0L
        itemId = null
    }
}
