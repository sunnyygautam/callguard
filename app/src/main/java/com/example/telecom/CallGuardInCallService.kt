package com.example.telecom

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.util.Log
import com.example.data.repository.SpamBlockerPreferencesRepository
import com.example.ui.incall.InCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Telecom [InCallService] implementation giving CallGuard complete ownership of
 * incoming and outgoing call states, ringtone, vibration, call notifications, and UI once
 * the user grants [android.app.role.RoleManager.ROLE_DIALER].
 */
class CallGuardInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callCallbacks = mutableMapOf<Call, Call.Callback>()
    private val ringingCalls = mutableSetOf<Call>()

    private val ringer: IncomingCallRinger by lazy {
        IncomingCallRinger(applicationContext)
    }

    private val prefsRepository: SpamBlockerPreferencesRepository by lazy {
        SpamBlockerPreferencesRepository(applicationContext)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(TAG, "onCallAdded: handle=${call.details?.handle}")

        val initialState = extractCallState(call)
        if (initialState == Call.STATE_RINGING) {
            ringingCalls.add(call)
        }

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                Log.i(TAG, "onStateChanged: state=$state")
                CallSessionManager.onCallUpdated(call)
                handleCallStateTransition(call, state)
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                CallSessionManager.onCallUpdated(call)
                syncActiveCallNotification()
            }
        }

        callCallbacks[call] = callback
        call.registerCallback(callback)
        CallSessionManager.onCallAdded(this, call)

        handleCallStateTransition(call, initialState)
        launchInCallUi()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i(TAG, "onCallRemoved: handle=${call.details?.handle}")

        callCallbacks.remove(call)?.let { callback ->
            call.unregisterCallback(callback)
        }
        ringingCalls.remove(call)
        ringer.stop()

        CallSessionManager.onCallRemoved(call)
        CallNotificationManager.cancelCallNotification(applicationContext)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        @Suppress("DEPRECATION")
        super.onCallAudioStateChanged(audioState)
        CallSessionManager.onAudioStateChanged(audioState)
    }

    override fun onUnbind(intent: Intent): Boolean {
        callCallbacks.forEach { (call, callback) ->
            call.unregisterCallback(callback)
        }
        callCallbacks.clear()
        ringingCalls.clear()
        ringer.stop()
        CallNotificationManager.cancelCallNotification(applicationContext)
        CallSessionManager.detachService(this)
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    private fun handleCallStateTransition(call: Call, state: Int) {
        when (state) {
            Call.STATE_RINGING -> {
                ringingCalls.add(call)
                serviceScope.launch {
                    val settings = prefsRepository.getCurrentSettings()
                    // Re-verify call is still ringing after reading DataStore preferences
                    if (extractCallState(call) == Call.STATE_RINGING) {
                        ringer.start(
                            enableRingtone = settings.ringtoneEnabled,
                            enableVibration = settings.vibrationEnabled
                        )
                        if (settings.notificationsEnabled) {
                            CallSessionManager.activeCallState.value?.let { callState ->
                                CallNotificationManager.showCallNotification(
                                    applicationContext,
                                    callState
                                )
                            }
                        }
                    }
                }
            }

            Call.STATE_DIALING,
            Call.STATE_CONNECTING,
            Call.STATE_ACTIVE,
            Call.STATE_HOLDING -> {
                ringingCalls.remove(call)
                ringer.stop()
                syncActiveCallNotification()
            }

            Call.STATE_DISCONNECTING,
            Call.STATE_DISCONNECTED -> {
                val wasUnansweredIncoming = ringingCalls.remove(call)
                ringer.stop()
                CallNotificationManager.cancelCallNotification(applicationContext)

                val disconnectCode = call.details?.disconnectCause?.code
                if (wasUnansweredIncoming && disconnectCode == DisconnectCause.MISSED) {
                    val callerLabel = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                        ?: call.details?.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
                        ?: "Unknown Caller"
                    serviceScope.launch {
                        val settings = prefsRepository.getCurrentSettings()
                        if (settings.notificationsEnabled) {
                            CallNotificationManager.showMissedCallNotification(
                                applicationContext,
                                callerLabel
                            )
                        }
                    }
                }
            }
        }
    }

    private fun syncActiveCallNotification() {
        val currentState = CallSessionManager.activeCallState.value ?: return
        if (currentState.state == CallLifecycleState.DISCONNECTED ||
            currentState.state == CallLifecycleState.DISCONNECTING
        ) {
            CallNotificationManager.cancelCallNotification(applicationContext)
            return
        }
        serviceScope.launch {
            val settings = prefsRepository.getCurrentSettings()
            if (settings.notificationsEnabled) {
                CallNotificationManager.showCallNotification(applicationContext, currentState)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractCallState(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            call.state
        }
    }

    private fun launchInCallUi() {
        val intent = Intent(applicationContext, InCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        startActivity(intent)
    }

    companion object {
        private const val TAG = "CallGuardInCallService"
    }
}
