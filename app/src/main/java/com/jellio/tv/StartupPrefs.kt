package com.jellio.tv

import android.content.Context
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Preference holders the first frame does not need, loaded off the main thread.
// Their readers sit behind the splash, which waits for this; key handling,
// which can come sooner, blocks on it only in that short window.
object StartupPrefs {
    private val started = AtomicBoolean(false)
    private val ready = CountDownLatch(1)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.jellio.tv.data.prefs.QuickHubPreferences.init(app)
                com.jellio.tv.data.prefs.PlayerPrefs.init(app)
                com.jellio.tv.ui.player.BadStreams.init(app)
                com.jellio.tv.ui.onboarding.OnboardingPrefs.init(app)
            } finally {
                ready.countDown()
            }
        }
    }

    suspend fun await() {
        if (started.get() && ready.count > 0) withContext(Dispatchers.IO) { ready.await() }
    }

    fun awaitBlocking() {
        if (started.get() && ready.count > 0) ready.await()
    }
}
