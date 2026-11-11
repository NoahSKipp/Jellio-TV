package com.jellio.tv.ui.onboarding

import com.jellio.tv.data.i18n.StringsDe
import com.jellio.tv.data.i18n.StringsEn
import com.jellio.tv.data.i18n.StringsEs
import com.jellio.tv.data.i18n.StringsFr
import com.jellio.tv.data.i18n.StringsIt
import com.jellio.tv.data.i18n.StringsPl
import com.jellio.tv.data.i18n.StringsPt
import com.jellio.tv.data.prefs.QuickHubButton
import com.jellio.tv.data.prefs.QuickHubTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLogicTest {
    @Test
    fun quickHubStepSitsBetweenOverscanAndSummary() {
        assertEquals(
            listOf(
                OnboardingStep.LANGUAGE,
                OnboardingStep.PLAYBACK,
                OnboardingStep.OVERSCAN,
                OnboardingStep.QUICK_HUB,
                OnboardingStep.SUMMARY,
            ),
            onboardingSteps(hasSharedAccountSettings = false),
        )
        assertEquals(
            listOf(
                OnboardingStep.LANGUAGE,
                OnboardingStep.OVERSCAN,
                OnboardingStep.QUICK_HUB,
                OnboardingStep.SUMMARY,
            ),
            onboardingSteps(hasSharedAccountSettings = true),
        )
    }

    @Test
    fun amazonDetectionMatchesManufacturerOrBrandIgnoringCase() {
        assertTrue(isAmazonDevice("Amazon", "Amazon"))
        assertTrue(isAmazonDevice("amazon", null))
        assertTrue(isAmazonDevice("Other", "AMAZON"))
        assertFalse(isAmazonDevice("Google", "google"))
        assertFalse(isAmazonDevice(null, null))
    }

    @Test
    fun deviceFamilyPrefersFireTvThenGoogleTvThenGeneric() {
        assertEquals(DeviceFamily.FIRE_TV, deviceFamily(isAmazon = true, isGoogleTv = false))
        assertEquals(DeviceFamily.FIRE_TV, deviceFamily(isAmazon = true, isGoogleTv = true))
        assertEquals(DeviceFamily.GOOGLE_TV, deviceFamily(isAmazon = false, isGoogleTv = true))
        assertEquals(DeviceFamily.GENERIC, deviceFamily(isAmazon = false, isGoogleTv = false))
    }

    @Test
    fun recommendedDeviceLineKeyFollowsTheFamily() {
        assertEquals("onboarding_quick_hub_recommended_for_fire_tv", DeviceFamily.FIRE_TV.recommendedForKey)
        assertEquals("onboarding_quick_hub_recommended_for_google_tv", DeviceFamily.GOOGLE_TV.recommendedForKey)
        assertEquals("onboarding_quick_hub_recommended_for_generic", DeviceFamily.GENERIC.recommendedForKey)
    }

    @Test
    fun recommendedBadgeIsOnTheDeviceDefaultOnly() {
        val amazon = QUICK_HUB_SIMPLE_BUTTONS.filter { isRecommendedButton(it, isAmazon = true) }
        val other = QUICK_HUB_SIMPLE_BUTTONS.filter { isRecommendedButton(it, isAmazon = false) }
        assertEquals(listOf(QuickHubButton.PLAY_PAUSE), amazon)
        assertEquals(listOf(QuickHubButton.BACK), other)
        assertFalse(isRecommendedButton(QuickHubButton.CUSTOM, isAmazon = false))
        assertFalse(isRecommendedButton(QuickHubButton.NONE, isAmazon = true))
    }

    @Test
    fun recommendedTriggerIsLongPress() {
        assertTrue(isRecommendedTrigger(QuickHubTrigger.LONG_PRESS))
        assertFalse(isRecommendedTrigger(QuickHubTrigger.SHORT_PRESS))
    }

    @Test
    fun recommendedButtonIsAlwaysOneOfTheSimpleCards() {
        assertTrue(QuickHubButton.default(true) in QUICK_HUB_SIMPLE_BUTTONS)
        assertTrue(QuickHubButton.default(false) in QUICK_HUB_SIMPLE_BUTTONS)
        assertEquals(
            listOf(QuickHubButton.BACK, QuickHubButton.MENU, QuickHubButton.PLAY_PAUSE),
            QUICK_HUB_SIMPLE_BUTTONS,
        )
    }

    @Test
    fun preselectionKeepsStoredChoiceAndRepairsUnusableCustomKey() {
        assertEquals(QuickHubButton.MENU, preselectedButton(QuickHubButton.MENU, customKeyValid = false, isAmazon = false))
        assertEquals(QuickHubButton.NONE, preselectedButton(QuickHubButton.NONE, customKeyValid = false, isAmazon = true))
        assertEquals(QuickHubButton.CUSTOM, preselectedButton(QuickHubButton.CUSTOM, customKeyValid = true, isAmazon = true))
        assertEquals(QuickHubButton.BACK, preselectedButton(QuickHubButton.CUSTOM, customKeyValid = false, isAmazon = false))
        assertEquals(QuickHubButton.PLAY_PAUSE, preselectedButton(QuickHubButton.CUSTOM, customKeyValid = false, isAmazon = true))
    }

    @Test
    fun summaryTemplateFollowsTriggerAndNone() {
        assertEquals(
            "onboarding_quick_hub_summary_long",
            quickHubSummaryTemplateKey(QuickHubButton.BACK, QuickHubTrigger.LONG_PRESS),
        )
        assertEquals(
            "onboarding_quick_hub_summary_short",
            quickHubSummaryTemplateKey(QuickHubButton.MENU, QuickHubTrigger.SHORT_PRESS),
        )
        assertEquals(
            "onboarding_quick_hub_summary_long",
            quickHubSummaryTemplateKey(QuickHubButton.CUSTOM, QuickHubTrigger.LONG_PRESS),
        )
        assertEquals(
            "onboarding_quick_hub_summary_none",
            quickHubSummaryTemplateKey(QuickHubButton.NONE, QuickHubTrigger.SHORT_PRESS),
        )
    }

    @Test
    fun shortNamesExistOnlyForTheSimpleButtons() {
        QUICK_HUB_SIMPLE_BUTTONS.forEach { assertTrue(shortNameKey(it) != null) }
        assertEquals(null, shortNameKey(QuickHubButton.CUSTOM))
        assertEquals(null, shortNameKey(QuickHubButton.NONE))
    }

    @Test
    fun everyKeyTheLogicNamesIsTranslatedInAllLanguages() {
        val keys = DeviceFamily.entries.map { it.recommendedForKey } +
            QUICK_HUB_SIMPLE_BUTTONS.mapNotNull { shortNameKey(it) } +
            QuickHubButton.entries.flatMap { b -> QuickHubTrigger.entries.map { quickHubSummaryTemplateKey(b, it) } }
        val tables = mapOf(
            "en" to StringsEn.MAP, "de" to StringsDe.MAP, "es" to StringsEs.MAP, "fr" to StringsFr.MAP,
            "it" to StringsIt.MAP, "pl" to StringsPl.MAP, "pt" to StringsPt.MAP,
        )
        for ((lang, table) in tables) {
            for (key in keys.toSet()) {
                assertTrue("$lang is missing $key", !table[key].isNullOrBlank())
            }
        }
    }
}
