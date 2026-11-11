package com.jellio.tv.ui.onboarding

import com.jellio.tv.data.prefs.QuickHubButton
import com.jellio.tv.data.prefs.QuickHubTrigger

internal enum class OnboardingStep {
    LANGUAGE,
    PLAYBACK,
    OVERSCAN,
    QUICK_HUB,
    SUMMARY,
}

// With audio/subtitle preferences synced from the account, the playback step is skipped.
internal fun onboardingSteps(hasSharedAccountSettings: Boolean): List<OnboardingStep> =
    if (hasSharedAccountSettings) {
        listOf(OnboardingStep.LANGUAGE, OnboardingStep.OVERSCAN, OnboardingStep.QUICK_HUB, OnboardingStep.SUMMARY)
    } else {
        listOf(
            OnboardingStep.LANGUAGE,
            OnboardingStep.PLAYBACK,
            OnboardingStep.OVERSCAN,
            OnboardingStep.QUICK_HUB,
            OnboardingStep.SUMMARY,
        )
    }

internal enum class DeviceFamily(val recommendedForKey: String) {
    FIRE_TV("onboarding_quick_hub_recommended_for_fire_tv"),
    GOOGLE_TV("onboarding_quick_hub_recommended_for_google_tv"),
    GENERIC("onboarding_quick_hub_recommended_for_generic"),
}

// Same rule QuickHubPreferences.init uses for the default button.
internal fun isAmazonDevice(manufacturer: String?, brand: String?): Boolean =
    "Amazon".equals(manufacturer, ignoreCase = true) || "Amazon".equals(brand, ignoreCase = true)

internal fun deviceFamily(isAmazon: Boolean, isGoogleTv: Boolean): DeviceFamily = when {
    isAmazon -> DeviceFamily.FIRE_TV
    isGoogleTv -> DeviceFamily.GOOGLE_TV
    else -> DeviceFamily.GENERIC
}

// Shown in this order; the three plain choices. Custom and None have their own controls.
internal val QUICK_HUB_SIMPLE_BUTTONS: List<QuickHubButton> =
    listOf(QuickHubButton.BACK, QuickHubButton.MENU, QuickHubButton.PLAY_PAUSE)

internal fun shortNameKey(button: QuickHubButton): String? = when (button) {
    QuickHubButton.BACK -> "onboarding_quick_hub_key_back"
    QuickHubButton.MENU -> "onboarding_quick_hub_key_menu"
    QuickHubButton.PLAY_PAUSE -> "onboarding_quick_hub_key_play_pause"
    QuickHubButton.CUSTOM, QuickHubButton.NONE -> null
}

// What the step shows as selected on entry: whatever is stored, or the device default
// when the stored choice is a custom key that is no longer usable.
internal fun preselectedButton(current: QuickHubButton, customKeyValid: Boolean, isAmazon: Boolean): QuickHubButton =
    if (current == QuickHubButton.CUSTOM && !customKeyValid) QuickHubButton.default(isAmazon) else current

internal fun isRecommendedButton(button: QuickHubButton, isAmazon: Boolean): Boolean =
    button == QuickHubButton.default(isAmazon)

internal fun isRecommendedTrigger(trigger: QuickHubTrigger): Boolean = trigger == QuickHubTrigger.LONG_PRESS

// Summary line: a template taking the key name (or the recorded key label for custom).
internal fun quickHubSummaryTemplateKey(button: QuickHubButton, trigger: QuickHubTrigger): String = when {
    button == QuickHubButton.NONE -> "onboarding_quick_hub_summary_none"
    trigger == QuickHubTrigger.LONG_PRESS -> "onboarding_quick_hub_summary_long"
    else -> "onboarding_quick_hub_summary_short"
}
