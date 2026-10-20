package com.jellio.tv.ui.onboarding

import android.content.Context

object OnboardingPrefs {
    private const val PREFS = "jellio_onboarding"
    private const val KEY_DONE_PREFIX = "onboarding_done_"

    fun shouldShowOnboarding(context: Context, userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getBoolean("$KEY_DONE_PREFIX$userId", false)) return false

        // Established user detection: if user has already used Jellio TV
        // (changelog history, display prefs, player prefs, or session sync),
        // mark onboarding complete and skip.
        val changelogPrefs = context.applicationContext.getSharedPreferences("jellio_changelog", Context.MODE_PRIVATE)
        val displayPrefs = context.applicationContext.getSharedPreferences("jellio_display_prefs", Context.MODE_PRIVATE)
        val playerPrefs = context.applicationContext.getSharedPreferences("jellio_player_prefs", Context.MODE_PRIVATE)
        val sessionSync = context.applicationContext.getSharedPreferences("jellio_session_sync", Context.MODE_PRIVATE)

        val hasHistory = changelogPrefs.all.isNotEmpty() ||
            displayPrefs.all.isNotEmpty() ||
            playerPrefs.all.isNotEmpty() ||
            sessionSync.all.isNotEmpty()

        if (hasHistory) {
            markOnboardingComplete(context, userId)
            return false
        }

        return true
    }

    fun markOnboardingComplete(context: Context, userId: String?) {
        if (userId.isNullOrBlank()) return
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putBoolean("$KEY_DONE_PREFIX$userId", true).apply()
    }

    fun resetOnboarding(context: Context, userId: String?) {
        if (userId.isNullOrBlank()) return
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().remove("$KEY_DONE_PREFIX$userId").apply()
    }
}
