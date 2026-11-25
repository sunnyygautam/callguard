package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Normalized call direction / outcome types mapped from Android's [CallLog.Calls.TYPE].
 */
enum class DeviceCallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED
}

/**
 * Represents a single call entry retrieved from Android's system [CallLog.Calls] provider.
 */
data class DeviceCallLogEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val type: DeviceCallType,
    val dateMillis: Long,
    val durationSeconds: Long
)

/**
 * Repository reading the device's system call history via [CallLog.Calls.CONTENT_URI].
 */
class DeviceCallLogRepository(
    private val context: Context
) {

    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Queries up to [limit] most recent calls from Android's [CallLog.Calls] provider on [Dispatchers.IO].
     * Returns an empty list safely if permission has not been granted.
     */
    suspend fun fetchRecentCalls(limit: Int = 50): List<DeviceCallLogEntry> =
        withContext(Dispatchers.IO) {
            if (!hasCallLogPermission()) return@withContext emptyList()

            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            val entries = mutableListOf<DeviceCallLogEntry>()
            try {
                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                    val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                    val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                    val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                    val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                    val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

                    while (cursor.moveToNext() && entries.size < limit) {
                        val rawType = cursor.getInt(typeIdx)
                        val mappedType = when (rawType) {
                            CallLog.Calls.INCOMING_TYPE -> DeviceCallType.INCOMING
                            CallLog.Calls.OUTGOING_TYPE -> DeviceCallType.OUTGOING
                            CallLog.Calls.MISSED_TYPE -> DeviceCallType.MISSED
                            CallLog.Calls.REJECTED_TYPE,
                            CallLog.Calls.BLOCKED_TYPE -> DeviceCallType.REJECTED
                            else -> DeviceCallType.INCOMING
                        }

                        entries.add(
                            DeviceCallLogEntry(
                                id = cursor.getLong(idIdx),
                                number = cursor.getString(numberIdx).orEmpty().trim(),
                                cachedName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() },
                                type = mappedType,
                                dateMillis = cursor.getLong(dateIdx),
                                durationSeconds = cursor.getLong(durationIdx).coerceAtLeast(0L)
                            )
                        )
                    }
                }
            } catch (_: SecurityException) {
                return@withContext emptyList()
            }

            entries
        }
}
