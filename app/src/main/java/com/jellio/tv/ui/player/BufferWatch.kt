package com.jellio.tv.ui.player

// Counts how much a stream stalls once it has started, so a stream that
// keeps buffering can be swapped for another.
internal class BufferWatch(
    private val totalLimitMs: Long = 12_000,
    private val countLimit: Int = 4,
    private val windowMs: Long = 120_000,
    private val seekGraceMs: Long = 4_000,
) {
    var firstFrameAt = 0L
    var lastSeekAt = 0L
    private var snoozedUntil = 0L
    private var stalledMs = 0L
    private var stallStartedAt = 0L
    private val stallStarts = ArrayDeque<Long>()

    fun reset() {
        firstFrameAt = 0L
        lastSeekAt = 0L
        clearCounts()
    }

    fun counts(now: Long, playing: Boolean, inGroup: Boolean, switching: Boolean): Boolean =
        firstFrameAt > 0 && playing && !inGroup && !switching &&
            now - lastSeekAt > seekGraceMs && now >= snoozedUntil

    fun stallStarted(now: Long) {
        stallStartedAt = now
        stallStarts.addLast(now)
        while (stallStarts.isNotEmpty() && stallStarts.first() < now - windowMs) stallStarts.removeFirst()
    }

    fun stallEnded(now: Long) {
        if (stallStartedAt > 0) stalledMs += now - stallStartedAt
        stallStartedAt = 0L
    }

    fun shouldOffer(now: Long): Boolean {
        if (now < snoozedUntil) return false
        val current = if (stallStartedAt > 0) now - stallStartedAt else 0L
        return stalledMs + current >= totalLimitMs || stallStarts.size >= countLimit
    }

    fun offered() = clearCounts()

    fun snooze(now: Long, forMs: Long) {
        snoozedUntil = now + forMs
        clearCounts()
    }

    private fun clearCounts() {
        stalledMs = 0L
        stallStartedAt = 0L
        stallStarts.clear()
    }
}
