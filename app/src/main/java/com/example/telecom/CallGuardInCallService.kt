package com.example.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.example.ui.incall.InCallActivity

/**
 * Android Telecom [InCallService] implementation giving CallGuard complete ownership of
 * incoming and outgoing call states and UI once the user grants [android.app.role.RoleManager.ROLE_DIALER].
 *
 * When Android binds this service with `IN_CALL_SERVICE_UI=true`, third-party dialers such as
 * Truecaller no longer receive default InCall UI control over CallGuard's active calls.
 */
class CallGuardInCallService : InCallService() {

    private val callCallbacks = mutableMapOf<Call, Call.Callback>()

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.i(TAG, "onCallAdded: handle=${call.details?.handle}")

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                Log.i(TAG, "onStateChanged: state=$state")
                CallSessionManager.onCallUpdated(call)
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                CallSessionManager.onCallUpdated(call)
            }
        }

        callCallbacks[call] = callback
        call.registerCallback(callback)
        CallSessionManager.onCallAdded(this, call)

        // Immediately bring CallGuard's dedicated full-screen InCallActivity to the foreground
        // for both incoming (ringing) and outgoing (dialing) calls.
        launchInCallUi()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.i(TAG, "onCallRemoved: handle=${call.details?.handle}")

        callCallbacks.remove(call)?.let { callback ->
            call.unregisterCallback(callback)
        }
        CallSessionManager.onCallRemoved(call)
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
        CallSessionManager.detachService(this)
        return super.onUnbind(intent)
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
