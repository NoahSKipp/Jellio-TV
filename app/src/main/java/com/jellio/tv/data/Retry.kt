package com.jellio.tv.data

import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

internal suspend fun <T> withRetry(maxAttempts: Int = 3, initialDelayMs: Long = 1000L, block: suspend () -> T): T {
    var currentDelay = initialDelayMs
    repeat(maxAttempts - 1) {
        try {
            return block()
        } catch (e: Exception) {
            val isTransient = (e is HttpException && e.code() in 500..504) || e is IOException
            if (isTransient) {
                delay(currentDelay)
                currentDelay = (currentDelay * 1.5).toLong()
            } else {
                throw e
            }
        }
    }
    return block()
}
