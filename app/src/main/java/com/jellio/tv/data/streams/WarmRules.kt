package com.jellio.tv.data.streams

// Which sources the server has warm, and every source's quality tier.
data class ReadyInfo(val warmIds: Set<String>, val tiers: Map<String, String>) {
    fun isReady(sourceId: String?) = normalizeSourceId(sourceId) in warmIds
    fun tierOf(sourceId: String?) = tiers[normalizeSourceId(sourceId)]
}

fun normalizeSourceId(id: String?): String = id.orEmpty().replace("-", "").lowercase()

// The first ready source of the leading source's quality tier, and no worse
// a pick by rank, goes first. Never across tiers: a ready 720p must not
// replace a 4K that is merely slower to start.
fun <T> preferReadySource(
    sources: List<T>,
    isReady: (T) -> Boolean,
    tierOf: (T) -> String?,
    rankOf: (T) -> Int = { 0 },
): List<T> {
    if (sources.size < 2) return sources
    val leader = sources[0]
    if (isReady(leader)) return sources
    val tier = tierOf(leader)
    if (tier.isNullOrEmpty() || tier == "unknown") return sources
    val leaderRank = rankOf(leader)
    val index = sources.indices.firstOrNull { i ->
        i > 0 && isReady(sources[i]) && tierOf(sources[i]) == tier && rankOf(sources[i]) <= leaderRank
    } ?: return sources
    return listOf(sources[index]) + sources.filterIndexed { i, _ -> i != index }
}

fun interface Cancellable {
    fun cancel()
}

fun interface WarmScheduler {
    fun schedule(delayMs: Long, block: () -> Unit): Cancellable
}

// Search: once results have stood still for settleMs, warm the top ones in
// one batch; a result keeping focus for focusMs is warmed on its own. A new
// query drops whatever the last one had pending.
class SearchWarmer<T>(
    private val scheduler: WarmScheduler,
    private val onSettled: (List<T>) -> Unit,
    private val onFocused: (String) -> Unit = {},
    private val onQueryChanged: () -> Unit = {},
    private val settleMs: Long = 700,
    private val focusMs: Long = 500,
) {
    private var settle: Cancellable? = null
    private var focus: Cancellable? = null
    private var generation = 0

    fun query() {
        generation++
        settle?.cancel()
        settle = null
        focus?.cancel()
        focus = null
        onQueryChanged()
    }

    fun results(items: List<T>) {
        settle?.cancel()
        val mine = generation
        settle = scheduler.schedule(settleMs) {
            settle = null
            if (mine == generation) onSettled(items)
        }
    }

    fun focus(itemId: String) {
        focus?.cancel()
        val mine = generation
        focus = scheduler.schedule(focusMs) {
            focus = null
            if (mine == generation) onFocused(itemId)
        }
    }

    fun blur() {
        focus?.cancel()
        focus = null
    }
}
