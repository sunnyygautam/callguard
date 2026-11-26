package com.example.telecom

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.util.Log
import com.example.data.local.CallGuardDatabase
import com.example.data.local.CallRecordingEntity
import com.example.data.repository.CallRecordingRepository
import com.example.data.repository.SpamBlockerPreferencesRepository
import com.example.ui.incall.InCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Telecom [InCallService] implementation giving CallGuard complete ownership of
 * incoming and outgoing call states, ringtone, vibration, call recording, call notifications,
 * and UI once the user grants [android.app.role.RoleManager.ROLE_DIALER].
 */
class CallGuardInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callCallbacks = mutableMapOf<Call, Call.Callback>()
    private val ringingCalls = mutableSetOf<Call>()
    private val autoRecordAttemptedCalls = mutableSetOf<Call>()

    private val ringer: IncomingCallRinger by lazy {
        IncomingCallRinger(applicationContext)
    }

    private val audioRecorder: CallAudioRecorder by lazy {
        CallAudioRecorder(applicationContext)
    }

    private val recordingRepository: CallRecordingRepository by lazy {
        val db = CallGuardDatabase.getInstance(applicationContext)
        CallRecordingRepository(applicationContext, db.callRecordingDao())
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
        autoRecordAttemptedCalls.remove(call)
        ringer.stop()
        stopAndPersistActiveRecording()

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
        autoRecordAttemptedCalls.clear()
        ringer.stop()
        stopAndPersistActiveRecording()
        CallNotificationManager.cancelCallNotification(applicationContext)
        CallSessionManager.detachService(this)
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    /**
     * Toggles manual call recording during an active/outgoing call from [InCallActivity].
     */
    fun toggleCallRecording() {
        val currentCall = CallSessionManager.activeCallState.value ?: return
        if (audioRecorder.isRecording) {
            stopAndPersistActiveRecording()
            syncActiveCallNotification()
        } else {
            startRecordingForCall(
                phoneNumber = currentCall.phoneNumber,
                callerName = currentCall.callerDisplayName,
                isIncoming = currentCall.isIncoming
            )
        }
    }

    private fun startRecordingForCall(
        phoneNumber: String,
        callerName: String?,
        isIncoming: Boolean
    ) {
        when (
            val result = audioRecorder.startRecording(
                phoneNumber = phoneNumber,
                callerName = callerName,
                isIncoming = isIncoming
            )
        ) {
            is StartRecordingResult.Started -> {
                CallSessionManager.onRecordingStateChanged(
                    isRecording = true,
                    startedAtMillis = result.startedAtMillis,
                    errorMessage = null
                )
                syncActiveCallNotification()
            }
            StartRecordingResult.MissingPermission -> {
                CallSessionManager.onRecordingStateChanged(
                    isRecording = false,
                    errorMessage = "Microphone permission is required to record calls."
                )
            }
            is StartRecordingResult.Failed -> {
                CallSessionManager.onRecordingStateChanged(
                    isRecording = false,
                    errorMessage = result.reason
                )
            }
        }
    }

    private fun stopAndPersistActiveRecording() {
        if (!audioRecorder.isRecording) {
            CallSessionManager.onRecordingStateChanged(isRecording = false)
            return
        }
        val completed = audioRecorder.stopRecording()
        CallSessionManager.onRecordingStateChanged(isRecording = false)
        if (completed != null) {
            persistenceScope.launch {
                runCatching {
                    recordingRepository.saveRecording(
                        CallRecordingEntity(
                            phoneNumber = completed.phoneNumber,
                            callerName = completed.callerName,
                            isIncoming = completed.isIncoming,
                            filePath = completed.filePath,
                            fileName = completed.fileName,
                            durationMillis = completed.durationMillis,
                            fileSizeBytes = completed.fileSizeBytes,
                            recordedAt = completed.recordedAt
                        )
                    )
                }.onFailure { err ->
                    Log.e(TAG, "Failed to save call recording metadata to Room", err)
                }
            }
        }
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

            Call.STATE_ACTIVE -> {
                ringingCalls.remove(call)
                ringer.stop()
                if (autoRecordAttemptedCalls.add(call)) {
                    serviceScope.launch {
                        val settings = prefsRepository.getCurrentSettings()
                        if (settings.callRecordingEnabled &&
                            settings.autoRecordCalls &&
                            extractCallState(call) == Call.STATE_ACTIVE &&
                            !audioRecorder.isRecording
                        ) {
                            val current = CallSessionManager.activeCallState.value
                            startRecordingForCall(
                                phoneNumber = current?.phoneNumber
                                    ?: call.details?.handle?.schemeSpecificPart.orEmpty(),
                                callerName = current?.callerDisplayName
                                    ?: call.details?.callerDisplayName,
                                isIncoming = current?.isIncoming ?: true
                            )
                        }
                    }
                }
                syncActiveCallNotification()
            }

            Call.STATE_DIALING,
            Call.STATE_CONNECTING,
            Call.STATE_HOLDING -> {
                ringingCalls.remove(call)
                ringer.stop()
                syncActiveCallNotification()
            }

            Call.STATE_DISCONNECTING,
            Call.STATE_DISCONNECTED -> {
                val wasUnansweredIncoming = ringingCalls.remove(call)
                autoRecordAttemptedCalls.remove(call)
                ringer.stop()
                stopAndPersistActiveRecording()
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
