package com.example.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

/**
 * Simplified Telecom call lifecycle states observed by [com.example.ui.incall.InCallActivity].
 */
enum class CallLifecycleState {
    RINGING,
    DIALING,
    CONNECTING,
    ACTIVE,
    HOLDING,
    DISCONNECTING,
    DISCONNECTED
}

/**
 * Immutable UI snapshot of the active Telecom call owned by [CallGuardInCallService].
 */
data class ActiveCallUiState(
    val phoneNumber: String,
    val callerDisplayName: String?,
    val state: CallLifecycleState,
    val isIncoming: Boolean,
    val connectTimeMillis: Long,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isRecording: Boolean = false,
    val recordingStartedAtMillis: Long = 0L,
    val recordingError: String? = null
)

/**
 * Thread-safe bridge between Android Telecom's [CallGuardInCallService] and the
 * Jetpack Compose [com.example.ui.incall.InCallActivity].
 */
object CallSessionManager {

    private var activeCall: Call? = null
    private var serviceRef: WeakReference<CallGuardInCallService>? = null

    private val _activeCallState = MutableStateFlow<ActiveCallUiState?>(null)
    val activeCallState: StateFlow<ActiveCallUiState?> = _activeCallState.asStateFlow()

    fun attachService(service: CallGuardInCallService) {
        serviceRef = WeakReference(service)
    }

    fun detachService(service: CallGuardInCallService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
    }

    fun onCallAdded(service: CallGuardInCallService, call: Call) {
        attachService(service)
        activeCall = call
        publishCallState(call)
    }

    fun onCallUpdated(call: Call) {
        if (activeCall === call) {
            publishCallState(call)
        }
    }

    fun onCallRemoved(call: Call) {
        if (activeCall === call) {
            activeCall = null
            _activeCallState.value = null
        }
    }

    fun onAudioStateChanged(audioState: CallAudioState) {
        val isSpeaker = audioState.route == CallAudioState.ROUTE_SPEAKER
        _activeCallState.update { current ->
            current?.copy(
                isMuted = audioState.isMuted,
                isSpeakerOn = isSpeaker
            )
        }
    }

    fun onRecordingStateChanged(
        isRecording: Boolean,
        startedAtMillis: Long = 0L,
        errorMessage: String? = null
    ) {
        _activeCallState.update { current ->
            current?.copy(
                isRecording = isRecording,
                recordingStartedAtMillis = if (isRecording) startedAtMillis else 0L,
                recordingError = errorMessage
            )
        }
    }

    fun answerCall() {
        activeCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun rejectOrDisconnectCall() {
        val call = activeCall ?: return
        val currentState = extractCallState(call)
        if (currentState == Call.STATE_RINGING) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.reject(Call.REJECT_REASON_DECLINED)
            } else {
                @Suppress("DEPRECATION")
                call.reject(false, null)
            }
        } else {
            call.disconnect()
        }
    }

    fun toggleMute() {
        val service = serviceRef?.get() ?: return
        val currentlyMuted = _activeCallState.value?.isMuted ?: false
        service.setMuted(!currentlyMuted)
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val service = serviceRef?.get() ?: return
        val currentlySpeaker = _activeCallState.value?.isSpeakerOn ?: false
        val targetRoute = if (currentlySpeaker) {
            CallAudioState.ROUTE_WIRED_OR_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        service.setAudioRoute(targetRoute)
    }

    fun toggleHold() {
        val call = activeCall ?: return
        when (extractCallState(call)) {
            Call.STATE_HOLDING -> call.unhold()
            Call.STATE_ACTIVE -> call.hold()
        }
    }

    fun toggleRecording() {
        val service = serviceRef?.get() ?: return
        service.toggleCallRecording()
    }

    fun playDtmfDigit(digit: Char) {
        val call = activeCall ?: return
        call.playDtmfTone(digit)
        call.stopDtmfTone()
    }

    private fun publishCallState(call: Call) {
        val details = call.details
        val rawNumber = details?.handle?.schemeSpecificPart.orEmpty().trim()
        val callerName = details?.callerDisplayName?.takeIf { it.isNotBlank() }
        val callState = mapTelecomState(extractCallState(call))
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details?.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            callState == CallLifecycleState.RINGING
        }
        val previous = _activeCallState.value
        val connectTime = when {
            details != null && details.connectTimeMillis > 0L -> details.connectTimeMillis
            callState == CallLifecycleState.ACTIVE && (previous?.connectTimeMillis ?: 0L) == 0L ->
                System.currentTimeMillis()
            else -> previous?.connectTimeMillis ?: 0L
        }

        _activeCallState.value = ActiveCallUiState(
            phoneNumber = rawNumber.ifEmpty { "Unknown Caller" },
            callerDisplayName = callerName,
            state = callState,
            isIncoming = isIncoming,
            connectTimeMillis = connectTime,
            isMuted = previous?.isMuted ?: false,
            isSpeakerOn = previous?.isSpeakerOn ?: false,
            isRecording = previous?.isRecording ?: false,
            recordingStartedAtMillis = previous?.recordingStartedAtMillis ?: 0L,
            recordingError = previous?.recordingError
        )
    }

    @Suppress("DEPRECATION")
    private fun extractCallState(call: Call): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            call.details.state
        } else {
            call.state
        }
    }

    internal fun mapTelecomState(telecomState: Int): CallLifecycleState {
        return when (telecomState) {
            Call.STATE_RINGING -> CallLifecycleState.RINGING
            Call.STATE_DIALING -> CallLifecycleState.DIALING
            Call.STATE_CONNECTING,
            Call.STATE_SELECT_PHONE_ACCOUNT -> CallLifecycleState.CONNECTING
            Call.STATE_ACTIVE -> CallLifecycleState.ACTIVE
            Call.STATE_HOLDING -> CallLifecycleState.HOLDING
            Call.STATE_DISCONNECTING -> CallLifecycleState.DISCONNECTING
            Call.STATE_DISCONNECTED -> CallLifecycleState.DISCONNECTED
            else -> CallLifecycleState.CONNECTING
        }
    }
}
