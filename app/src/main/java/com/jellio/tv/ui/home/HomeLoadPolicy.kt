package com.jellio.tv.ui.home

internal enum class HomeLoadAction {
    // Already loading this Home, or loaded it moments ago.
    SKIP,
    // A new user or language: the saved snapshot first, then rows as they arrive.
    FULL,
    // The same Home again: rows are swapped in once all of them are back.
    REFRESH,
}

internal fun decideHomeLoad(
    key: String,
    loadedKey: String?,
    inFlight: Boolean,
    freshAtMs: Long?,
    nowMs: Long,
    freshForMs: Long,
    force: Boolean,
): HomeLoadAction = when {
    key != loadedKey -> HomeLoadAction.FULL
    inFlight -> HomeLoadAction.SKIP
    force -> HomeLoadAction.REFRESH
    freshAtMs != null && nowMs >= freshAtMs && nowMs - freshAtMs < freshForMs -> HomeLoadAction.SKIP
    else -> HomeLoadAction.REFRESH
}

// The fresh rows in their order, plus earlier rows whose replacement is still
// loading, each kept just before the fresh row it preceded, so a reload does
// not drop rows and add them back (and move focus) as answers come in.
internal fun <R> mergeKeepingPending(
    fresh: List<R>,
    previous: List<R>,
    keyOf: (R) -> String,
    stillLoading: (String) -> Boolean,
): List<R> {
    val freshKeys = fresh.mapTo(HashSet()) { keyOf(it) }
    val kept = previous.filter { val key = keyOf(it); key !in freshKeys && stillLoading(key) }
    if (kept.isEmpty()) return fresh
    val previousIndex = HashMap<String, Int>(previous.size)
    previous.forEachIndexed { index, row -> previousIndex.putIfAbsent(keyOf(row), index) }
    val result = ArrayList<R>(fresh.size + kept.size)
    var next = 0
    for (row in fresh) {
        val at = previousIndex[keyOf(row)]
        if (at != null) {
            while (next < kept.size && previousIndex.getValue(keyOf(kept[next])) < at) result.add(kept[next++])
        }
        result.add(row)
    }
    while (next < kept.size) result.add(kept[next++])
    return result
}
