package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.CallRecordingDao
import com.example.data.local.CallRecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Result of exporting a local call recording file to a user-chosen Storage Access Framework URI.
 */
sealed interface RecordingExportResult {
    data class Success(val fileName: String) : RecordingExportResult
    data class Failure(val reason: String) : RecordingExportResult
}

/**
 * Repository coordinating Room persistence and app-private audio file lifecycle
 * for recorded phone calls.
 */
class CallRecordingRepository(
    private val context: Context,
    private val dao: CallRecordingDao
) {
    val allRecordings: Flow<List<CallRecordingEntity>> = dao.observeAllRecordings()

    suspend fun saveRecording(recording: CallRecordingEntity): Long = withContext(Dispatchers.IO) {
        dao.insert(recording)
    }

    suspend fun deleteRecording(recording: CallRecordingEntity) = withContext(Dispatchers.IO) {
        dao.delete(recording)
        runCatching {
            val file = File(recording.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    suspend fun clearAllRecordings() = withContext(Dispatchers.IO) {
        val existing = dao.getAllRecordingsSnapshot()
        dao.deleteAll()
        existing.forEach { recording ->
            runCatching {
                val file = File(recording.filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    suspend fun exportRecordingToUri(
        recording: CallRecordingEntity,
        destinationUri: Uri
    ): RecordingExportResult = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(recording.filePath)
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                return@withContext RecordingExportResult.Failure("Recording file is no longer available on disk.")
            }
            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext RecordingExportResult.Failure("Unable to open destination file.")

            outputStream.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
            RecordingExportResult.Success(recording.fileName)
        } catch (e: Exception) {
            RecordingExportResult.Failure(e.localizedMessage ?: "Failed to export recording.")
        }
    }
}
