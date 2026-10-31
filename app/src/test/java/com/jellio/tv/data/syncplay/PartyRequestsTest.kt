package com.jellio.tv.data.syncplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyRequestsTest {
    @Test
    fun recentRequestIsFresh() {
        assertTrue(isFreshPartyRequest("2026-11-08T20:00:00.1234567Z", "2026-11-08T20:00:30.0000000Z"))
    }

    @Test
    fun requestFromBeforeJoiningIsStale() {
        assertFalse(isFreshPartyRequest("2026-11-08T19:30:00.0000000Z", "2026-11-08T20:00:00.0000000Z"))
    }

    @Test
    fun missingTimesCountAsFresh() {
        assertTrue(isFreshPartyRequest(null, "2026-11-08T20:00:00Z"))
        assertTrue(isFreshPartyRequest("2026-11-08T20:00:00Z", null))
    }
}
