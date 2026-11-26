package com.example

import com.example.telecom.BatteryOptimizationHelper
import com.example.telecom.BatteryOptimizationStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOptimizationHelperTest {

    @Test
    fun `isUnrestricted requires ignoring optimizations and no background restriction`() {
        assertTrue(
            BatteryOptimizationStatus(
                isIgnoringBatteryOptimizations = true,
                isBackgroundRestricted = false
            ).isUnrestricted
        )
        assertFalse(
            BatteryOptimizationStatus(
                isIgnoringBatteryOptimizations = false,
                isBackgroundRestricted = false
            ).isUnrestricted
        )
        assertFalse(
            BatteryOptimizationStatus(
                isIgnoringBatteryOptimizations = true,
                isBackgroundRestricted = true
            ).isUnrestricted
        )
    }

    @Test
    fun `shouldShowGuidedPrompt returns true only when active, restricted, and not dismissed`() {
        val restricted = BatteryOptimizationStatus(isIgnoringBatteryOptimizations = false)
        val unrestricted = BatteryOptimizationStatus(isIgnoringBatteryOptimizations = true)

        assertTrue(
            BatteryOptimizationHelper.shouldShowGuidedPrompt(
                status = restricted,
                isPromptDismissed = false,
                isBackgroundFeatureActive = true
            )
        )
        assertFalse(
            BatteryOptimizationHelper.shouldShowGuidedPrompt(
                status = unrestricted,
                isPromptDismissed = false,
                isBackgroundFeatureActive = true
            )
        )
        assertFalse(
            BatteryOptimizationHelper.shouldShowGuidedPrompt(
                status = restricted,
                isPromptDismissed = true,
                isBackgroundFeatureActive = true
            )
        )
        assertFalse(
            BatteryOptimizationHelper.shouldShowGuidedPrompt(
                status = restricted,
                isPromptDismissed = false,
                isBackgroundFeatureActive = false
            )
        )
    }
}
