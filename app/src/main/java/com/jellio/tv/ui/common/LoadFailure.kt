package com.jellio.tv.ui.common

import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

// Why a screen could not load, without the raw exception text.
enum class LoadFailure {
    // No answer from the server: offline, DNS, refused, timed out.
    Unreachable,
    Failed,
}

// A null error means the request timed out without one.
fun loadFailureOf(error: Throwable?): LoadFailure {
    if (error == null) return LoadFailure.Unreachable
    var current: Throwable = error
    repeat(8) {
        if (current is IOException || current is TimeoutCancellationException) return LoadFailure.Unreachable
        val next = current.cause
        if (next == null || next === current) return LoadFailure.Failed
        current = next
    }
    return LoadFailure.Failed
}
