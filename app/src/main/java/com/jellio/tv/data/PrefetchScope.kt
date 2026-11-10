package com.jellio.tv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import javax.inject.Inject
import javax.inject.Singleton

// The one scope stream and playback warm-ups run in, so signing out can
// cancel all of them at once.
@Singleton
class PrefetchScope @Inject constructor() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cancelAll() {
        scope.coroutineContext.cancelChildren()
    }
}
