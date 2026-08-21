package com.jellio.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// Hilt entry point. No component actually needs it yet at this
// scaffold stage; wired now so the real auth/session runtime and API
// client land as real @Inject-ed singletons later rather than a
// retrofit onto an app that never expected DI.
@HiltAndroidApp
class JellioTvApplication : Application()
