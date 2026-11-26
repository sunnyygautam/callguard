package com.example.telecom

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Snapshot of Android's battery optimization and background restriction state for CallGuard.
 *
 * When [isIgnoringBatteryOptimizations] is true and [isBackgroundRestricted] is false,
 * [PrefixCallScreeningService] and background call recording can operate reliably without
 * being throttled by Doze mode or OEM task killers.
 */
data class BatteryOptimizationStatus(
    val isIgnoringBatteryOptimizations: Boolean = false,
    val isBackgroundRestricted: Boolean = false
) {
    /** True when CallGuard is excluded from battery optimizations and not background-restricted. */
    val isUnrestricted: Boolean
        get() = isIgnoringBatteryOptimizations && !isBackgroundRestricted
}

/**
 * Helper for inspecting Android battery optimization status and launching the system
 * battery optimization exemption flow with safe fallbacks across OEM devices.
 */
object BatteryOptimizationHelper {

    fun getStatus(context: Context): BatteryOptimizationStatus {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

        val isRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            activityManager?.isBackgroundRestricted == true
        } else {
            false
        }

        return BatteryOptimizationStatus(
            isIgnoringBatteryOptimizations = isIgnoring,
            isBackgroundRestricted = isRestricted
        )
    }

    /**
     * Builds the direct system dialog [Intent] requesting battery optimization exemption for
     * CallGuard. Falls back gracefully to the system battery optimization settings list or
     * App Details screen if an OEM firmware does not resolve the direct prompt.
     */
    fun createRequestExemptionIntent(context: Context): Intent {
        val directPromptIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (directPromptIntent.resolveActivity(context.packageManager) != null) {
            return directPromptIntent
        }
        return createBatterySettingsIntent(context)
    }

    /**
     * Builds an [Intent] opening Android's Battery Optimization settings list, falling back
     * to CallGuard's App Info settings page when unavailable.
     */
    fun createBatterySettingsIntent(context: Context): Intent {
        val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (settingsIntent.resolveActivity(context.packageManager) != null) {
            return settingsIntent
        }
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }

    /**
     * Pure gating rule deciding whether to automatically display the guided battery
     * optimization rationale modal after a user enables a background-sensitive feature
     * (such as Call Screening or Call Recording).
     */
    fun shouldShowGuidedPrompt(
        status: BatteryOptimizationStatus,
        isPromptDismissed: Boolean,
        isBackgroundFeatureActive: Boolean
    ): Boolean {
        if (!isBackgroundFeatureActive) return false
        if (status.isUnrestricted) return false
        return !isPromptDismissed
    }
}
