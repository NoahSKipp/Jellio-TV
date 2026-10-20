package com.jellio.tv.ui.onboarding

import android.content.Context

object OnboardingPrefs {
    private const val PREFS = "jellio_onboarding"
    private const val KEY_DONE_PREFIX = "onboarding_done_"

    fun shouldShowOnboarding(context: Context, userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false
        val sp = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !sp.getBoolean("$KEY_DONE_PREFIX$userId", false)
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
