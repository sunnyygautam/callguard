package com.example.telecom

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.incall.InCallActivity

/**
 * Handles Answer, Decline, and End Call actions invoked directly from CallGuard notifications.
 */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER_CALL -> CallSessionManager.answerCall()
            ACTION_DECLINE_OR_END_CALL -> CallSessionManager.rejectOrDisconnectCall()
        }
    }

    companion object {
        const val ACTION_ANSWER_CALL = "com.example.telecom.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_OR_END_CALL = "com.example.telecom.ACTION_DECLINE_OR_END_CALL"
    }
}

/**
 * Manages Android notification channels and notifications for:
 * 1. High-priority incoming & ongoing calls (with full-screen intent and Answer/Decline/End actions).
 * 2. Blocked spam call alerts posted by [PrefixCallScreeningService].
 * 3. Missed incoming call alerts.
 */
object CallNotificationManager {

    private const val CHANNEL_CALLS = "callguard_calls_channel"
    private const val CHANNEL_SPAM_ALERTS = "callguard_spam_alerts_channel"
    private const val CHANNEL_MISSED_CALLS = "callguard_missed_calls_channel"

    private const val NOTIF_ID_ACTIVE_CALL = 1001
    private const val NOTIF_ID_SPAM_BASE = 2000
    private const val NOTIF_ID_MISSED_BASE = 3000

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val callsChannel = NotificationChannel(
            CHANNEL_CALLS,
            context.getString(R.string.notif_channel_calls_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_calls_desc)
            setSound(null, null) // Audio & vibration handled directly by IncomingCallRinger
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val spamChannel = NotificationChannel(
            CHANNEL_SPAM_ALERTS,
            context.getString(R.string.notif_channel_spam_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_spam_desc)
        }

        val missedChannel = NotificationChannel(
            CHANNEL_MISSED_CALLS,
            context.getString(R.string.notif_channel_missed_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_missed_desc)
        }

        manager.createNotificationChannel(callsChannel)
        manager.createNotificationChannel(spamChannel)
        manager.createNotificationChannel(missedChannel)
    }

    /**
     * Shows or updates the active incoming/outgoing/ongoing call notification with full-screen
     * intent and direct call control buttons.
     */
    fun showCallNotification(
        context: Context,
        callState: ActiveCallUiState
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            100,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerTitle = callState.callerDisplayName ?: callState.phoneNumber
        val statusText = when (callState.state) {
            CallLifecycleState.RINGING -> context.getString(R.string.incall_state_ringing)
            CallLifecycleState.DIALING -> context.getString(R.string.incall_state_dialing)
            CallLifecycleState.CONNECTING -> context.getString(R.string.incall_state_connecting)
            CallLifecycleState.ACTIVE -> context.getString(R.string.incall_state_active)
            CallLifecycleState.HOLDING -> context.getString(R.string.incall_state_holding)
            CallLifecycleState.DISCONNECTING -> context.getString(R.string.incall_state_disconnecting)
            CallLifecycleState.DISCONNECTED -> context.getString(R.string.incall_state_disconnected)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(callerTitle)
            .setContentText(statusText)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent)
            .setSilent(true)

        if (callState.state == CallLifecycleState.RINGING) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)

            val answerIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_ANSWER_CALL
            }
            val answerPendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_DECLINE_OR_END_CALL
            }
            val declinePendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                android.R.drawable.sym_action_call,
                context.getString(R.string.incall_action_answer),
                answerPendingIntent
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.incall_action_decline),
                declinePendingIntent
            )
        } else {
            val endIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_DECLINE_OR_END_CALL
            }
            val endPendingIntent = PendingIntent.getBroadcast(
                context,
                103,
                endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.incall_action_end),
                endPendingIntent
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_ACTIVE_CALL, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    fun cancelCallNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_ACTIVE_CALL)
    }

    /**
     * Posts a notification informing the user that CallGuard blocked a spam call.
     */
    fun showBlockedSpamNotification(
        context: Context,
        phoneNumber: String,
        matchedRuleLabel: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            200,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SPAM_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(context.getString(R.string.notif_blocked_spam_title))
            .setContentText(
                context.getString(R.string.notif_blocked_spam_body, phoneNumber, matchedRuleLabel)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val notifId = NOTIF_ID_SPAM_BASE + ((System.currentTimeMillis() % 900).toInt())
        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    /**
     * Posts a missed call notification when an incoming call disconnects without being answered.
     */
    fun showMissedCallNotification(
        context: Context,
        callerLabel: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val openCallsIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_DIAL
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            300,
            openCallsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED_CALLS)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(context.getString(R.string.notif_missed_call_title))
            .setContentText(context.getString(R.string.notif_missed_call_body, callerLabel))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val notifId = NOTIF_ID_MISSED_BASE + ((System.currentTimeMillis() % 900).toInt())
        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }
}
