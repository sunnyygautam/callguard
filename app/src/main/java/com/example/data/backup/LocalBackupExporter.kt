package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.repository.BlockedCallLogRepository
import com.example.data.repository.DeviceContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Result of exporting CallGuard user data to a local JSON file via Storage Access Framework.
 */
sealed interface BackupExportResult {
    data class Success(
        val fileName: String,
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
 * Exports blocked call history and device contacts to a user-selected local JSON file
 * using Android's zero-permission Storage Access Framework ([Uri] + [android.content.ContentResolver]).
 */
class LocalBackupExporter(
    private val context: Context,
    private val blockedCallLogRepository: BlockedCallLogRepository,
    private val deviceContactsRepository: DeviceContactsRepository
) {

    fun createSuggestedFileName(): String = BackupJsonSerializer.createDefaultFileName()

    /**
     * Collects fresh snapshots of blocked call history and/or contacts, serializes them to JSON,
     * and writes UTF-8 output to [destinationUri] on [Dispatchers.IO].
     */
    suspend fun exportToUri(
        destinationUri: Uri,
        includeBlockedHistory: Boolean,
        includeContacts: Boolean
    ): BackupExportResult = withContext(Dispatchers.IO) {
        try {
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
