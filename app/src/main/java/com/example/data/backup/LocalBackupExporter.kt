package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.repository.BlockedCallLogRepository
import com.example.data.repository.DeviceContactsRepository
import com.example.data.repository.PrefixRepository
import com.example.data.repository.SpamBlockerPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Result of exporting CallGuard user data to a local JSON file via Storage Access Framework.
 */
sealed interface BackupExportResult {
    data class Success(
        val fileName: String,
        val blockedNumberCount: Int,
        val includesCallSettings: Boolean,
        val blockedCallCount: Int,
        val contactCount: Int,
        val byteSize: Int,
        val exportedAtMillis: Long
    ) : BackupExportResult

    data class Failure(
        val reason: String
    ) : BackupExportResult
}

/**
 * Result of restoring CallGuard blocked numbers, call settings, and blocked history
 * from a previously saved local JSON backup file.
 */
sealed interface BackupRestoreResult {
    data class Success(
        val fileName: String,
        val restoredBlockedNumbers: Int,
        val restoredCallSettings: Boolean,
        val restoredBlockedHistory: Int,
        val restoredAtMillis: Long
    ) : BackupRestoreResult

    data class Failure(
        val reason: String
    ) : BackupRestoreResult
}

/**
 * Exports and restores CallGuard blocked phone numbers/prefixes, call & shield settings,
 * blocked call history, and device contacts using Android's zero-permission Storage Access
 * Framework ([Uri] + [android.content.ContentResolver]).
 */
class LocalBackupExporter(
    private val context: Context,
    private val prefixRepository: PrefixRepository,
    private val spamPrefsRepository: SpamBlockerPreferencesRepository,
    private val blockedCallLogRepository: BlockedCallLogRepository,
    private val deviceContactsRepository: DeviceContactsRepository
) {

    companion object {
        private const val MAX_RESTORE_BYTES = 10 * 1024 * 1024 // 10 MB safety cap
    }

    fun createSuggestedFileName(): String = BackupJsonSerializer.createDefaultFileName()

    /**
     * Collects fresh snapshots of the selected CallGuard datasets, serializes them to JSON,
     * and writes UTF-8 output to [destinationUri] on [Dispatchers.IO].
     */
    suspend fun exportToUri(
        destinationUri: Uri,
        includeBlockedNumbers: Boolean = true,
        includeCallSettings: Boolean = true,
        includeBlockedHistory: Boolean = true,
        includeContacts: Boolean = true
    ): BackupExportResult = withContext(Dispatchers.IO) {
        try {
            val blockedNumbers = if (includeBlockedNumbers) {
                prefixRepository.getAllPrefixesSnapshot()
            } else {
                emptyList()
            }

            val callSettings = if (includeCallSettings) {
                spamPrefsRepository.getCurrentSettings()
            } else {
                null
            }

            val blockedCalls = if (includeBlockedHistory) {
                blockedCallLogRepository.getAllBlockedCallsSnapshot()
            } else {
                emptyList()
            }

            val contacts = if (includeContacts && deviceContactsRepository.hasContactsPermission()) {
                deviceContactsRepository.fetchContacts(limit = Int.MAX_VALUE)
            } else {
                emptyList()
            }

            val exportedAt = System.currentTimeMillis()
            val json = BackupJsonSerializer.serialize(
                CallGuardBackupPayload(
                    exportedAtMillis = exportedAt,
                    blockedNumbers = blockedNumbers,
                    callSettings = callSettings,
                    blockedCallHistory = blockedCalls,
                    contacts = contacts
                )
            )
            val bytes = json.toByteArray(Charsets.UTF_8)

            val outputStream = context.contentResolver.openOutputStream(destinationUri, "wt")
                ?: context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext BackupExportResult.Failure(
                    "Unable to open the selected file for writing."
                )

            outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }

            val resolvedName = resolveDisplayName(destinationUri)
                ?: BackupJsonSerializer.createDefaultFileName(exportedAt)

            BackupExportResult.Success(
                fileName = resolvedName,
                blockedNumberCount = blockedNumbers.size,
                includesCallSettings = callSettings != null,
                blockedCallCount = blockedCalls.size,
                contactCount = contacts.size,
                byteSize = bytes.size,
                exportedAtMillis = exportedAt
            )
        } catch (se: SecurityException) {
            BackupExportResult.Failure(
                se.localizedMessage ?: "Permission denied while writing backup file."
            )
        } catch (ioe: IOException) {
            BackupExportResult.Failure(
                ioe.localizedMessage ?: "I/O error while saving backup file."
            )
        } catch (e: Exception) {
            BackupExportResult.Failure(
                e.localizedMessage ?: "Unexpected error while creating backup."
            )
        }
    }

    /**
     * Reads a previously exported CallGuard JSON backup from [sourceUri], validates its structure,
     * and restores blocked phone numbers/prefixes, call & shield settings, and blocked call history
     * back into Room and DataStore on [Dispatchers.IO].
     */
    suspend fun restoreFromUri(sourceUri: Uri): BackupRestoreResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext BackupRestoreResult.Failure(
                    "Unable to open the selected backup file."
                )

            val rawBytes = inputStream.use { stream ->
                val buffer = stream.readBytes()
                if (buffer.size > MAX_RESTORE_BYTES) {
                    return@withContext BackupRestoreResult.Failure(
                        "Backup file exceeds maximum supported size (10 MB)."
                    )
                }
                buffer
            }

            val jsonText = rawBytes.toString(Charsets.UTF_8).trim()
            if (jsonText.isEmpty()) {
                return@withContext BackupRestoreResult.Failure("Selected backup file is empty.")
            }

            val payload = BackupJsonSerializer.deserialize(jsonText)

            val hasRestorableData = payload.blockedNumbers.isNotEmpty() ||
                payload.callSettings != null ||
                payload.blockedCallHistory.isNotEmpty()
            if (!hasRestorableData) {
                return@withContext BackupRestoreResult.Failure(
                    "No blocked numbers, settings, or blocked call history found in this JSON file."
                )
            }

            val restoredNumbersCount = if (payload.blockedNumbers.isNotEmpty()) {
                prefixRepository.restoreBlockedNumbers(payload.blockedNumbers)
            } else {
                0
            }

            val restoredSettings = if (payload.callSettings != null) {
                spamPrefsRepository.restoreSettings(payload.callSettings)
                true
            } else {
                false
            }

            var restoredHistoryCount = 0
            if (payload.blockedCallHistory.isNotEmpty()) {
                val existingSnapshots = blockedCallLogRepository.getAllBlockedCallsSnapshot()
                val existingKeys = existingSnapshots.mapTo(HashSet()) {
                    "${it.normalizedNumber}|${it.blockedAt}"
                }
                for (log in payload.blockedCallHistory) {
                    val key = "${log.normalizedNumber}|${log.blockedAt}"
                    if (existingKeys.add(key)) {
                        blockedCallLogRepository.restoreBlockedCall(log)
                        restoredHistoryCount++
                    }
                }
            }

            val resolvedName = resolveDisplayName(sourceUri) ?: "backup.json"
            BackupRestoreResult.Success(
                fileName = resolvedName,
                restoredBlockedNumbers = restoredNumbersCount,
                restoredCallSettings = restoredSettings,
                restoredBlockedHistory = restoredHistoryCount,
                restoredAtMillis = System.currentTimeMillis()
            )
        } catch (iae: IllegalArgumentException) {
            BackupRestoreResult.Failure(
                iae.message ?: "Selected file is not a valid CallGuard JSON backup."
            )
        } catch (se: SecurityException) {
            BackupRestoreResult.Failure(
                se.localizedMessage ?: "Permission denied while reading backup file."
            )
        } catch (ioe: IOException) {
            BackupRestoreResult.Failure(
                ioe.localizedMessage ?: "I/O error while reading backup file."
            )
        } catch (e: Exception) {
            BackupRestoreResult.Failure(
                e.localizedMessage ?: "Unexpected error while restoring backup."
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
