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

