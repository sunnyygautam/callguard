package com.example.telecom

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Snapshot of Android Call Screening role and runtime phone permission readiness.
 */
data class CallScreeningStatus(
    val isRoleSupported: Boolean,
    val isRoleAvailable: Boolean,
    val isRoleHeld: Boolean,
    val hasPhonePermissions: Boolean
)

/**
 * Helper for checking and requesting [RoleManager.ROLE_CALL_SCREENING] on Android 10+ (API 29+)
 * as well as verifying optional/supplemental phone permissions.
 */
object CallScreeningRoleHelper {

    val REQUIRED_PHONE_PERMISSIONS: Array<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
    }.toTypedArray()

    /**
     * Inspects current device support, [RoleManager.ROLE_CALL_SCREENING] status,
     * and runtime phone permissions.
     */
    fun getStatus(context: Context): CallScreeningStatus {
        val hasPermissions = REQUIRED_PHONE_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return CallScreeningStatus(
                isRoleSupported = false,
                isRoleAvailable = false,
                isRoleHeld = false,
                hasPhonePermissions = hasPermissions
            )
        }

        val roleManager = context.getSystemService(RoleManager::class.java)
        val isAvailable = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
        val isHeld = isAvailable && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

        return CallScreeningStatus(
            isRoleSupported = true,
            isRoleAvailable = isAvailable,
            isRoleHeld = isHeld,
            hasPhonePermissions = hasPermissions
        )
    }

    /**
     * Builds the system prompt [Intent] asking the user to set this app as the default
     * Caller ID & Spam app via [RoleManager.ROLE_CALL_SCREENING].
     *
     * @return The system Intent on Android 10+ if the role is available, or null otherwise.
     */
    fun createRequestCallScreeningRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }
}
