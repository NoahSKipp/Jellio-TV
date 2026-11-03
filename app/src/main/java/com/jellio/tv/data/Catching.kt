package com.jellio.tv.data

import kotlin.coroutines.cancellation.CancellationException

// runCatching for suspending work: a cancelled caller stays cancelled
// instead of carrying on with a default value.
internal inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
