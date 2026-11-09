package com.jellio.tv.ui.common

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class LoadFailureTest {

    @Test
    fun networkErrorsMeanTheServerIsUnreachable() {
        assertEquals(LoadFailure.Unreachable, loadFailureOf(IOException("reset")))
        assertEquals(LoadFailure.Unreachable, loadFailureOf(SocketTimeoutException()))
        assertEquals(LoadFailure.Unreachable, loadFailureOf(UnknownHostException("jellyfin.local")))
        assertEquals(LoadFailure.Unreachable, loadFailureOf(ConnectException()))
    }

    @Test
    fun aMissingErrorIsATimeout() {
        assertEquals(LoadFailure.Unreachable, loadFailureOf(null))
    }

    @Test
    fun aCoroutineTimeoutIsUnreachable() {
        val timeout = runBlocking {
            try {
                withTimeout(1) { delay(1_000) }
                null
            } catch (e: TimeoutCancellationException) {
                e
            }
        }
        assertEquals(LoadFailure.Unreachable, loadFailureOf(timeout))
    }

    @Test
    fun aWrappedNetworkErrorIsStillUnreachable() {
        assertEquals(LoadFailure.Unreachable, loadFailureOf(IllegalStateException("load", SocketTimeoutException())))
    }

    @Test
    fun otherErrorsAreGenericFailures() {
        assertEquals(LoadFailure.Failed, loadFailureOf(IllegalStateException("bad json")))
        assertEquals(LoadFailure.Failed, loadFailureOf(RuntimeException("HTTP 500")))
    }

    @Test
    fun aSelfReferencingCauseDoesNotLoop() {
        val looping = object : RuntimeException("loop") {
            override val cause: Throwable get() = this
        }
        assertEquals(LoadFailure.Failed, loadFailureOf(looping))
    }
}
