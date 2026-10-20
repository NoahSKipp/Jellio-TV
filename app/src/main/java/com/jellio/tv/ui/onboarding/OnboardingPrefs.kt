package com.jellio.tv.ui.onboarding

import android.content.Context

object OnboardingPrefs {
    private const val PREFS = "jellio_onboarding"
    private const val KEY_DONE_PREFIX = "onboarding_done_"
    private const val KEY_SETUP_INITIALIZED = "setup_initialized"
    private const val KEY_INITIAL_INSTALL = "is_initial_install"

    fun init(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sp.getBoolean(KEY_SETUP_INITIALIZED, false)) {
            val changelogPrefs = context.applicationContext.getSharedPreferences("jellio_changelog", Context.MODE_PRIVATE)
            val displayPrefs = context.applicationContext.getSharedPreferences("jellio_display", Context.MODE_PRIVATE)
            val playerPrefs = context.applicationContext.getSharedPreferences("jellio_player", Context.MODE_PRIVATE)
            val sessionSync = context.applicationContext.getSharedPreferences("jellio_session_sync", Context.MODE_PRIVATE)

            // If the device already carried changelog history, display configuration,
            // player choices, or an existing server session before this first check,
            // it is an established install (not a first-time install).
            val hasPreExistingInstall = changelogPrefs.all.isNotEmpty() ||
                !sessionSync.getString("server_address", null).isNullOrBlank() ||
                !sessionSync.getString("access_token", null).isNullOrBlank() ||
                displayPrefs.contains("overscan_percent") ||
                displayPrefs.contains("display_language") ||
                playerPrefs.all.isNotEmpty()

            sp.edit()
                .putBoolean(KEY_SETUP_INITIALIZED, true)
                .putBoolean(KEY_INITIAL_INSTALL, !hasPreExistingInstall)
                .apply()
        }
    }

    fun isInitialInstall(context: Context): Boolean {
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_INITIAL_INSTALL, false)
    }

    fun shouldShowOnboarding(context: Context, userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getBoolean("$KEY_DONE_PREFIX$userId", false)) return false

        // Only surface onboarding automatically on initial install
        if (!sp.getBoolean(KEY_INITIAL_INSTALL, false)) {
            markOnboardingComplete(context, userId)
            return false
        }

        return true
    }

    fun markOnboardingComplete(context: Context, userId: String?) {
        if (userId.isNullOrBlank()) return
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean("$KEY_DONE_PREFIX$userId", true)
            .putBoolean(KEY_INITIAL_INSTALL, false)
            .apply()
    }

    fun resetOnboarding(context: Context, userId: String?) {
        if (userId.isNullOrBlank()) return
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().remove("$KEY_DONE_PREFIX$userId").apply()
    }
}
