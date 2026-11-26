package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result of starting a call recording session.
 */
sealed interface StartRecordingResult {
    data class Started(val startedAtMillis: Long, val filePath: String) : StartRecordingResult
    data object MissingPermission : StartRecordingResult
    data class Failed(val reason: String) : StartRecordingResult
}

/**
 * Completed recording metadata returned when a call recording stops cleanly.
 */
data class CompletedCallRecording(
    val phoneNumber: String,
    val callerName: String,
    val isIncoming: Boolean,
    val filePath: String,
    val fileName: String,
    val durationMillis: Long,
    val fileSizeBytes: Long,
    val recordedAt: Long
)

/**
 * Manages real on-device call audio recording using Android's [MediaRecorder] and stores
 * MPEG-4 / AAC (.m4a) audio files in app-private storage (requiring zero storage permissions).
 *
 * Uses [MediaRecorder.AudioSource.VOICE_COMMUNICATION] first for echo-cancelled call audio
 * and falls back cleanly to [MediaRecorder.AudioSource.MIC] on devices whose HAL restricts
 * voice-communication capture.
 */
class CallAudioRecorder(
    private val context: Context
) {
    private val lock = Any()
    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null
    private var activePhoneNumber: String = ""
    private var activeCallerName: String = ""
    private var activeIsIncoming: Boolean = true
    private var startedAtMillis: Long = 0L

    val isRecording: Boolean
        get() = synchronized(lock) { recorder != null }

    fun startRecording(
        phoneNumber: String,
        callerName: String?,
        isIncoming: Boolean
    ): StartRecordingResult = synchronized(lock) {
        if (!hasRecordAudioPermission(context)) {
            return StartRecordingResult.MissingPermission
        }
        if (recorder != null) {
            return StartRecordingResult.Started(
                startedAtMillis = startedAtMillis,
                filePath = activeFile?.absolutePath.orEmpty()
            )
        }

        val dir = resolveRecordingsDirectory(context)
        val fileName = buildRecordingFileName(phoneNumber, System.currentTimeMillis())
        val outputFile = File(dir, fileName)

        val audioSources = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )

        var lastError: Exception? = null
        for (source in audioSources) {
            val candidate = createMediaRecorder(context)
            try {
                candidate.setAudioSource(source)
                candidate.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                candidate.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                candidate.setAudioEncodingBitRate(AUDIO_BIT_RATE)
                candidate.setAudioSamplingRate(AUDIO_SAMPLE_RATE)
                candidate.setOutputFile(outputFile.absolutePath)
                candidate.prepare()
                candidate.start()

                recorder = candidate
                activeFile = outputFile
                activePhoneNumber = phoneNumber.ifBlank { "Unknown Caller" }
                activeCallerName = callerName.orEmpty().trim()
                activeIsIncoming = isIncoming
                startedAtMillis = System.currentTimeMillis()
                Log.i(TAG, "Call recording started (source=$source, file=${outputFile.name})")
                return StartRecordingResult.Started(
                    startedAtMillis = startedAtMillis,
                    filePath = outputFile.absolutePath
                )
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "MediaRecorder failed with audioSource=$source: ${e.message}")
                runCatching { candidate.reset() }
                runCatching { candidate.release() }
                runCatching {
                    if (outputFile.exists()) outputFile.delete()
                }
            }
        }

        return StartRecordingResult.Failed(
            lastError?.localizedMessage ?: "Microphone is currently unavailable."
        )
    }

    fun stopRecording(): CompletedCallRecording? = synchronized(lock) {
        val activeRecorder = recorder ?: return null
        val file = activeFile
        val started = startedAtMillis
        val number = activePhoneNumber
        val name = activeCallerName
        val incoming = activeIsIncoming

        recorder = null
        activeFile = null
        startedAtMillis = 0L

        var stoppedCleanly = false
        try {
            activeRecorder.stop()
            stoppedCleanly = true
        } catch (e: RuntimeException) {
            // MediaRecorder.stop() throws RuntimeException if recording was < ~300ms with no frames.
            Log.w(TAG, "Call recording stopped before audio frames were written: ${e.message}")
        } finally {
            runCatching { activeRecorder.reset() }
            runCatching { activeRecorder.release() }
        }

        if (!stoppedCleanly || file == null || !file.exists() || file.length() == 0L) {
            runCatching { file?.delete() }
            return null
        }

        val durationMillis = (System.currentTimeMillis() - started).coerceAtLeast(1_000L)
        Log.i(TAG, "Call recording saved: ${file.name} (${durationMillis}ms, ${file.length()} bytes)")
        return CompletedCallRecording(
            phoneNumber = number,
            callerName = name,
            isIncoming = incoming,
            filePath = file.absolutePath,
            fileName = file.name,
            durationMillis = durationMillis,
            fileSizeBytes = file.length(),
            recordedAt = started
        )
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(context: Context): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    companion object {
        private const val TAG = "CallAudioRecorder"
        private const val AUDIO_BIT_RATE = 128_000
        private const val AUDIO_SAMPLE_RATE = 44_100

        fun hasRecordAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun resolveRecordingsDirectory(context: Context): File {
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
                ?: File(context.filesDir, "call_recordings")
            val recordingsDir = File(baseDir, "CallGuard")
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }
            return recordingsDir
        }

        fun buildRecordingFileName(rawPhoneNumber: String, timestampMillis: Long): String {
            val sanitizedNumber = rawPhoneNumber
                .filter { it.isDigit() }
                .takeLast(12)
                .ifBlank { "unknown" }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))
            return "CallGuard_${sanitizedNumber}_${stamp}.m4a"
        }

        fun formatDurationMillis(durationMillis: Long): String {
            val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
            val hours = totalSeconds / 3_600L
            val minutes = (totalSeconds % 3_600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0L) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return "0 KB"
            val kb = bytes / 1024.0
            return if (kb < 1024.0) {
                String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
            } else {
                String.format(Locale.US, "%.1f MB", kb / 1024.0)
            }
        }
    }
}
