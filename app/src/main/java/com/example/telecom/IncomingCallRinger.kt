package com.example.telecom

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Manages incoming call ringtone playback and rhythmic vibration while honoring the device's
 * system ringer mode ([AudioManager.RINGER_MODE_NORMAL], [AudioManager.RINGER_MODE_VIBRATE],
 * [AudioManager.RINGER_MODE_SILENT]) as well as user preferences.
 */
class IncomingCallRinger(
    private val context: Context
) {
    private var activeRingtone: Ringtone? = null
    private var isVibrating = false

    fun start(enableRingtone: Boolean, enableVibration: Boolean) {
        stop()

        val audioManager = context.getSystemService(AudioManager::class.java)
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }

        if (enableRingtone && ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            startRingtone()
        }

        if (enableVibration &&
            (ringerMode == AudioManager.RINGER_MODE_NORMAL ||
                ringerMode == AudioManager.RINGER_MODE_VIBRATE)
        ) {
            startVibration()
        }
    }

    fun stop() {
        stopRingtone()
        stopVibration()
    }

    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return

            val ringtone = RingtoneManager.getRingtone(context, ringtoneUri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = true
            }
            ringtone.play()
            activeRingtone = ringtone
        } catch (e: Exception) {
            Log.e(TAG, "Unable to play incoming call ringtone", e)
        }
    }

    private fun stopRingtone() {
        try {
            activeRingtone?.let { ringtone ->
                if (ringtone.isPlaying) {
                    ringtone.stop()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to stop ringtone cleanly", e)
        } finally {
            activeRingtone = null
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            val vibrator = getVibrator() ?: return
            if (!vibrator.hasVibrator()) return

            val pattern = longArrayOf(0L, 600L, 800L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    vibrator.vibrate(
                        effect,
                        android.os.VibrationAttributes.Builder()
                            .setUsage(android.os.VibrationAttributes.USAGE_RINGTONE)
                            .build()
                    )
                } else {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    vibrator.vibrate(effect, audioAttributes)
                }
            } else {
                vibrator.vibrate(pattern, 0)
            }
            isVibrating = true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start incoming call vibration", e)
        }
    }

    private fun stopVibration() {
        if (!isVibrating) return
        try {
            getVibrator()?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to cancel vibration cleanly", e)
        } finally {
            isVibrating = false
        }
    }

    @Suppress("DEPRECATION")
    private fun getVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    companion object {
        private const val TAG = "IncomingCallRinger"
    }
}
