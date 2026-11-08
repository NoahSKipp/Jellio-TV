package com.jellio.tv.data.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLossDetectorTest {
    private var now = 1_000_000L
    private val detector = SessionLossDetector(nowMs = { now }, recheckAfterMs = 60_000L)

    private fun candidate(
        status: Int = 401,
        host: String = "media.example.com",
        server: String? = "media.example.com",
        path: String = "/Users/abc/Items",
        own: Boolean = false,
    ) = SessionLossDetector.isCandidate(status, host, server, path, own)

    @Test
    fun only401OnTheServerCounts() {
        assertTrue(candidate())
        assertTrue(candidate(host = "MEDIA.example.com"))
        assertFalse(candidate(status = 403))
        assertFalse(candidate(status = 500))
        assertFalse(candidate(status = 503))
        assertFalse(candidate(status = 200))
        assertFalse(candidate(host = "api.github.com"))
        assertFalse(candidate(host = "image.tmdb.org"))
        assertFalse(candidate(server = null))
    }

    @Test
    fun authEndpointsAndOwnCredentialsAreIgnored() {
        assertFalse(candidate(path = "/Users/AuthenticateByName"))
        assertFalse(candidate(path = "/jellyfin/Users/AuthenticateByName"))
        assertFalse(candidate(path = "/Users/AuthenticateWithQuickConnect"))
        assertFalse(candidate(path = "/QuickConnect/Connect"))
        assertFalse(candidate(path = "/QuickConnect/Initiate"))
        assertFalse(candidate(path = "/Users/ForgotPassword"))
        assertFalse(candidate(path = "/Users/ForgotPassword/Pin"))
        assertFalse(candidate(path = "/Users/abc", own = true))
        assertTrue(candidate(path = "/Users/abc/Views"))
    }

    @Test
    fun confirmed401MeansLostOnce() {
        assertTrue(detector.beginCheck("t1"))
        assertTrue(detector.finishCheck("t1", 401))
        assertTrue(detector.isLost("t1"))
        assertFalse(detector.beginCheck("t1"))
        assertFalse(detector.finishCheck("t1", 401))
    }

    @Test
    fun parallel401sStartOneCheck() {
        assertTrue(detector.beginCheck("t1"))
        repeat(20) { assertFalse(detector.beginCheck("t1")) }
        assertTrue(detector.finishCheck("t1", 401))
    }

    @Test
    fun aWorkingTokenIsNotRecheckedForAWhile() {
        assertTrue(detector.beginCheck("t1"))
        assertFalse(detector.finishCheck("t1", 200))
        assertFalse(detector.isLost("t1"))
        now += 30_000L
        assertFalse(detector.beginCheck("t1"))
        now += 31_000L
        assertTrue(detector.beginCheck("t1"))
    }

    @Test
    fun networkErrorsForbiddenAndServerErrorsAreNotLoss() {
        for (status in listOf(null, 403, 404, 500, 502, 503)) {
            now += 61_000L
            assertTrue(detector.beginCheck("t1"))
            assertFalse(detector.finishCheck("t1", status))
            assertFalse(detector.isLost("t1"))
        }
    }

    @Test
    fun blankTokenNeverChecks() {
        assertFalse(detector.beginCheck(""))
    }

    @Test
    fun anotherTokenCanBeCheckedAfterTheFirstFinishes() {
        assertTrue(detector.beginCheck("t1"))
        assertFalse(detector.beginCheck("t2"))
        assertTrue(detector.finishCheck("t1", 401))
        assertTrue(detector.beginCheck("t2"))
        assertFalse(detector.finishCheck("t2", 200))
        assertFalse(detector.isLost("t2"))
    }
}
