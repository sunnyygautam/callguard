package com.example.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.telecom.TelecomManager
import android.util.Log
import com.example.data.local.CallGuardDatabase
import com.example.data.repository.BlockedCallLogRepository
import com.example.data.repository.PrefixRepository
import com.example.data.repository.SpamBlockerPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Telecom [CallScreeningService] implementation that intercepts incoming calls
 * and evaluates them through [SpamBlockerEngine] using persisted [SpamBlockerPreferencesRepository]
 * settings and active Room prefix rules.
 *
 * Supports:
 * - Master Spam Blocker toggle
 * - Private / Unknown / Restricted caller blocking
 * - Android 11+ STIR/SHAKEN failed caller-ID verification (spoofed spam detection)
 * - Manual series/prefix rules from Room
 * - Built-in telemarketer and toll-free spam series
 * - Reject-and-silence vs Silence-only response modes
 */
class PrefixCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: CallGuardDatabase by lazy {
        CallGuardDatabase.getInstance(applicationContext)
    }

    private val prefixRepository: PrefixRepository by lazy {
        PrefixRepository(database.blockedPrefixDao())
    }

    private val callLogRepository: BlockedCallLogRepository by lazy {
        BlockedCallLogRepository(database.blockedCallLogDao())
    }

    private val spamPrefsRepository: SpamBlockerPreferencesRepository by lazy {
        SpamBlockerPreferencesRepository(applicationContext)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // 1. On Android 10+ (API 29+), screen incoming calls only (ignore outgoing calls).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondAllowCall(callDetails)
            return
        }

        // 2. Extract caller handle, presentation flags, and Android 11+ STIR/SHAKEN status.
        val rawIncomingNumber = callDetails.handle?.schemeSpecificPart
        val presentation = callDetails.handlePresentation
        val isHiddenOrPrivate = presentation == TelecomManager.PRESENTATION_RESTRICTED ||
            presentation == TelecomManager.PRESENTATION_UNKNOWN ||
            presentation == TelecomManager.PRESENTATION_PAYPHONE ||
            rawIncomingNumber.isNullOrBlank()

        val isStirShakenFailed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            callDetails.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED

        // 3. Evaluate spam blocker settings & active Room prefixes asynchronously on Dispatchers.IO.
        serviceScope.launch {
            try {
                val settings = spamPrefsRepository.getCurrentSettings()
                if (!settings.isSpamBlockerEnabled) {
                    respondAllowCall(callDetails)
                    return@launch
                }

                val activePrefixes = prefixRepository.getActivePrefixes()
                val decision = SpamBlockerEngine.evaluateIncomingCall(
                    rawIncomingNumber = rawIncomingNumber,
                    isHiddenOrPrivateCaller = isHiddenOrPrivate,
                    isStirShakenVerificationFailed = isStirShakenFailed,
                    activeManualPrefixes = activePrefixes,
                    settings = settings
                )

                when (decision) {
                    is SpamScreeningDecision.Block -> {
                        Log.i(
                            TAG,
                            "Blocking spam call via '${decision.matchedPrefix}' " +
                                "(reason='${decision.reasonLabel}', silenceOnly=${decision.silenceOnly})"
                        )
                        // Respond to Android Telecom immediately for minimal latency.
                        respondBlockCall(callDetails, silenceOnly = decision.silenceOnly)

                        // Persist the intercepted call into local Blocked Call History.
                        try {
                            callLogRepository.logBlockedCall(
                                incomingRawNumber = rawIncomingNumber,
                                matchedPrefix = decision.matchedPrefix,
                                reasonLabel = decision.reasonLabel
                            )
                        } catch (logError: Exception) {
                            Log.e(TAG, "Failed to log blocked call into Room history", logError)
                        }
                    }

                    SpamScreeningDecision.Allow -> {
                        respondAllowCall(callDetails)
                    }
                }
            } catch (e: Exception) {
                // Fail-open on unexpected error so legitimate calls are never dropped.
                Log.e(TAG, "Error screening incoming call; allowing call", e)
                respondAllowCall(callDetails)
            }
        }
    }

    /**
     * Builds a rejection or silence-only [CallResponse] based on the user's spam blocker mode.
     */
    private fun respondBlockCall(callDetails: Call.Details, silenceOnly: Boolean) {
        val responseBuilder = CallResponse.Builder()
            .setDisallowCall(!silenceOnly)
            .setRejectCall(!silenceOnly)
            .setSkipCallLog(false)
            .setSkipNotification(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            responseBuilder.setSilenceCall(true)
        }

        respondToCall(callDetails, responseBuilder.build())
    }

    /**
     * Allows the incoming call to ring normally.
     */
    private fun respondAllowCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PrefixCallScreening"
    }
}
