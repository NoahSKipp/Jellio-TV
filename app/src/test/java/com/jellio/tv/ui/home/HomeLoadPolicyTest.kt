package com.jellio.tv.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLoadPolicyTest {
    private fun decide(
        key: String = "u1:en",
        loadedKey: String? = "u1:en",
        inFlight: Boolean = false,
        freshAtMs: Long? = null,
        nowMs: Long = 100_000,
        force: Boolean = false,
    ) = decideHomeLoad(key, loadedKey, inFlight, freshAtMs, nowMs, freshForMs = 10_000, force = force)

    @Test
    fun firstLoadIsFull() = assertEquals(HomeLoadAction.FULL, decide(loadedKey = null))

    @Test
    fun secondCallWhileLoadingIsSkipped() = assertEquals(HomeLoadAction.SKIP, decide(inFlight = true))

    @Test
    fun retryWhileLoadingIsSkipped() = assertEquals(HomeLoadAction.SKIP, decide(inFlight = true, force = true))

    @Test
    fun freshResultIsKept() = assertEquals(HomeLoadAction.SKIP, decide(freshAtMs = 95_000))

    @Test
    fun staleResultIsRefreshed() = assertEquals(HomeLoadAction.REFRESH, decide(freshAtMs = 80_000))

    @Test
    fun failedLoadIsRefreshed() = assertEquals(HomeLoadAction.REFRESH, decide(freshAtMs = null))

    @Test
    fun retryIgnoresFreshness() = assertEquals(HomeLoadAction.REFRESH, decide(freshAtMs = 99_000, force = true))

    @Test
    fun clockGoingBackRefreshes() = assertEquals(HomeLoadAction.REFRESH, decide(freshAtMs = 200_000))

    @Test
    fun languageChangeIsFullEvenWhileLoading() =
        assertEquals(HomeLoadAction.FULL, decide(key = "u1:de", inFlight = true, freshAtMs = 99_000))

    @Test
    fun userSwitchIsFull() = assertEquals(HomeLoadAction.FULL, decide(key = "u2:en", freshAtMs = 99_000))
}
