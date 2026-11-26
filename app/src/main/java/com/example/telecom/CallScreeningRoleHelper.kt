package com.example.telecom

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * Snapshot of Android Telecom Default Dialer ([RoleManager.ROLE_DIALER]),
 * Call Screening ([RoleManager.ROLE_CALL_SCREENING]), overlay, and runtime phone permissions.
 */
data class CallScreeningStatus(
    val isRoleSupported: Boolean,
    val isRoleAvailable: Boolean,
    val isRoleHeld: Boolean,
    val isDialerRoleAvailable: Boolean = true,
    val isDefaultDialer: Boolean = false,
    val hasPhonePermissions: Boolean = false,
    val canDrawOverlays: Boolean = false
) {
    /** True when CallGuard owns both Call Screening AND the Default Phone / Dialer call UI. */
    val hasFullTelecomControl: Boolean
        get() = isRoleHeld && isDefaultDialer
}

/**
 * Helper for checking and requesting Android Telecom roles:
 * - [RoleManager.ROLE_DIALER] (Default Phone App) so Android Telecom hands over full
 *   incoming/outgoing call UI control to CallGuard's [CallGuardInCallService].
 * - [RoleManager.ROLE_CALL_SCREENING] (Default Caller ID & Spam App) for silent background screening.
 */
object CallScreeningRoleHelper {

    val REQUIRED_PHONE_PERMISSIONS: Array<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
    }.toTypedArray()

    /**
     * Inspects current Default Dialer role, Call Screening role, overlay permission,
     * and runtime phone permissions.
     */
    fun getStatus(context: Context): CallScreeningStatus {
        val hasPermissions = REQUIRED_PHONE_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
        val canOverlay = Settings.canDrawOverlays(context)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            val isDefaultDialer = telecomManager?.defaultDialerPackage == context.packageName
            return CallScreeningStatus(
                isRoleSupported = false,
                isRoleAvailable = false,
                isRoleHeld = false,
                isDialerRoleAvailable = true,
                isDefaultDialer = isDefaultDialer,
                hasPhonePermissions = hasPermissions,
                canDrawOverlays = canOverlay
            )
        }

        val roleManager = context.getSystemService(RoleManager::class.java)
        val isScreeningAvailable =
            roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
        val isScreeningHeld =
            isScreeningAvailable && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

        val isDialerAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        val isDialerHeld = isDialerAvailable && roleManager.isRoleHeld(RoleManager.ROLE_DIALER)

        return CallScreeningStatus(
            isRoleSupported = true,
            isRoleAvailable = isScreeningAvailable,
            isRoleHeld = isScreeningHeld,
            isDialerRoleAvailable = isDialerAvailable,
            isDefaultDialer = isDialerHeld,
            hasPhonePermissions = hasPermissions,
            canDrawOverlays = canOverlay
        )
    }

    /**
     * Builds the system prompt [Intent] requesting [RoleManager.ROLE_DIALER] on Android 10+
     * (or [TelecomManager.ACTION_CHANGE_DEFAULT_DIALER] on Android 7–9) so Android Telecom
     * routes all incoming/outgoing call screens exclusively to CallGuard's [CallGuardInCallService].
     */
    @Suppress("DEPRECATION")
    fun createRequestDefaultDialerIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        }

        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                context.packageName
            )
        }
    }

    /**
     * Builds the system prompt [Intent] asking the user to set CallGuard as the default
     * Caller ID & Spam app via [RoleManager.ROLE_CALL_SCREENING].
     */
    fun createRequestCallScreeningRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    /**
     * Builds an Intent to open the system "Display over other apps" permission screen for CallGuard.
     */
    fun createManageOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }
}
