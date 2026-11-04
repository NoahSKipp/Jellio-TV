package com.jellio.tv.data.streams

import java.io.IOException
import java.io.InterruptedIOException
import retrofit2.HttpException

// Finding the next stream after one failed. A list that could not be loaded
// is never taken for an empty one.
const val RECENT_LIST_MS = 60_000L
const val RETRY_TIMEOUT_SECONDS = 30

enum class ListOrigin { RECENT, FRESH, RETRY, FALLBACK, FAILED }

data class ListError(val kind: String, val ms: Long, val recovered: Boolean)

data class FailoverLoad<T>(val sources: List<T>?, val origin: ListOrigin, val error: ListError?)

data class LastList<T>(val sources: List<T>, val at: Long)

fun errorKind(err: Throwable?): String = when {
    err == null -> "unknown"
    err is InterruptedIOException -> "timeout"
    err is HttpException && err.code() >= 500 -> "server"
    err is HttpException -> "http ${err.code()}"
    err is IOException -> "network"
    else -> "error"
}

// fetch(null) uses the normal timeout, fetch(RETRY_TIMEOUT_SECONDS) the longer one.
suspend fun <T> loadFailoverSources(
    last: LastList<T>?,
    now: () -> Long,
    fetch: suspend (timeoutSeconds: Int?) -> List<T>,
): FailoverLoad<T> {
    if (last != null && last.sources.isNotEmpty() && now() - last.at < RECENT_LIST_MS) {
        return FailoverLoad(last.sources, ListOrigin.RECENT, null)
    }
    val started = now()
    var failure: Throwable
    try {
        return FailoverLoad(fetch(null), ListOrigin.FRESH, null)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        failure = e
    }
    try {
        val sources = fetch(RETRY_TIMEOUT_SECONDS)
        return FailoverLoad(sources, ListOrigin.RETRY, ListError(errorKind(failure), now() - started, recovered = true))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        failure = e
    }
    val error = ListError(errorKind(failure), now() - started, recovered = false)
    if (last != null && last.sources.isNotEmpty()) return FailoverLoad(last.sources, ListOrigin.FALLBACK, error)
    return FailoverLoad(null, ListOrigin.FAILED, error)
}

data class FailoverCounts(
    val total: Int,
    val tried: Int,
    val badHard: Int,
    val badSoft: Int,
    val afterLanguage: Int,
    val afterTranscode: Int,
)

data class FailoverPlan<T>(val ordered: List<T>, val counts: FailoverCounts)

enum class BadKind { HARD, SOFT }

// needsReencode is null when re-encoding is not avoided.
fun <T> planFailover(
    sources: List<T>?,
    tried: Collection<String>,
    id: (T) -> String?,
    isBad: (T) -> BadKind?,
    byLanguage: (List<T>) -> List<T>,
    needsReencode: ((T) -> Boolean)? = null,
    blockReencode: Boolean = false,
    preferred: ((T) -> Boolean)? = null,
): FailoverPlan<T> {
    val list = sources.orEmpty()
    var triedCount = 0
    var hard = 0
    var soft = 0
    val candidates = mutableListOf<T>()
    for (source in list) {
        val sourceId = id(source)
        if (sourceId == null) continue
        if (sourceId in tried) {
            triedCount++
            continue
        }
        when (isBad(source)) {
            BadKind.HARD -> hard++
            BadKind.SOFT -> soft++
            null -> candidates.add(source)
        }
    }
    val afterLanguage = byLanguage(candidates)
    var ordered = afterLanguage
    if (needsReencode != null) {
        val (reencode, copyable) = candidates.partition(needsReencode)
        ordered = byLanguage(copyable) + if (blockReencode) emptyList() else byLanguage(reencode)
    }
    val afterTranscode = ordered.size
    if (preferred != null) {
        val (first, rest) = ordered.partition(preferred)
        ordered = first + rest
    }
    return FailoverPlan(
        ordered,
        FailoverCounts(list.size, triedCount, hard, soft, afterLanguage.size, afterTranscode),
    )
}

sealed class FailoverOutcome {
    data class Navigate(val sourceId: String) : FailoverOutcome()
    data class ListFailed(val kind: String) : FailoverOutcome()
    object NoneLeft : FailoverOutcome()
}

fun <T> failoverOutcome(loaded: FailoverLoad<T>, plan: FailoverPlan<T>, id: (T) -> String?): FailoverOutcome {
    plan.ordered.firstOrNull()?.let { id(it) }?.let { return FailoverOutcome.Navigate(it) }
    if (loaded.origin == ListOrigin.FAILED || loaded.origin == ListOrigin.FALLBACK) {
        return FailoverOutcome.ListFailed(loaded.error?.kind ?: "unknown")
    }
    return FailoverOutcome.NoneLeft
}

fun outcomeLabel(outcome: FailoverOutcome): String = when (outcome) {
    is FailoverOutcome.Navigate -> "navigate"
    is FailoverOutcome.ListFailed -> "list-failed"
    FailoverOutcome.NoneLeft -> "none-left"
}

// One line for Logcat and the server log; ids and counts only.
fun describeFailover(reason: String, counts: FailoverCounts, loaded: FailoverLoad<*>, outcome: FailoverOutcome): String {
    val c = counts
    val list = loaded.origin.name.lowercase() + (loaded.error?.let { " (${it.kind} after ${it.ms} ms)" } ?: "")
    val next = if (outcome is FailoverOutcome.Navigate) "next ${outcome.sourceId}" else outcomeLabel(outcome)
    return "Jellio failover: $reason; sources ${c.total}, tried ${c.tried}, bad ${c.badHard} hard / ${c.badSoft} soft" +
        ", after language ${c.afterLanguage}, after transcode rule ${c.afterTranscode}; list $list; $next"
}
