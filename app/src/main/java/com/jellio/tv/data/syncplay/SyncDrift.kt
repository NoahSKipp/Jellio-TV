package com.jellio.tv.data.syncplay

import kotlin.math.abs

private const val TICKS_PER_MS = 10_000L

// Where a group command puts the player, as runtime/syncDrift.js has it. A
// paused group stays where the command says (nowhere in particular when it
// carries no position); a playing one has moved on since the command.
// estimateTicksNow moves a position on by the time since the command.
internal fun syncTargetTicks(command: String, positionTicks: Long?, estimateTicksNow: (Long) -> Long): Long? =
    if (command == "Pause") positionTicks else estimateTicksNow(positionTicks ?: 0L)

// 0 when the command has no target.
internal fun syncDriftMs(currentMs: Long, command: String, positionTicks: Long?, estimateTicksNow: (Long) -> Long): Long {
    val target = syncTargetTicks(command, positionTicks, estimateTicksNow) ?: return 0L
    return abs(currentMs - target / TICKS_PER_MS)
}
