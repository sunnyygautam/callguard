package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing metadata and optional on-device conversation summaries
 * for locally recorded phone calls (.m4a audio files).
 */
@Entity(
    tableName = "call_recordings",
    indices = [Index(value = ["recorded_at"])]
)
data class CallRecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,

    @ColumnInfo(name = "caller_name")
    val callerName: String = "",

    @ColumnInfo(name = "is_incoming")
    val isIncoming: Boolean = true,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long = 0L,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0L,

    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "transcript", defaultValue = "''")
    val transcript: String = "",

    @ColumnInfo(name = "summary_text", defaultValue = "''")
    val summaryText: String = "",

    @ColumnInfo(name = "summary_key_points", defaultValue = "''")
    val summaryKeyPoints: String = "",

    @ColumnInfo(name = "summary_topics", defaultValue = "''")
    val summaryTopics: String = "",

    @ColumnInfo(name = "summary_mode", defaultValue = "''")
    val summaryMode: String = "",

    @ColumnInfo(name = "summary_generated_at", defaultValue = "0")
    val summaryGeneratedAt: Long = 0L
) {
    val hasSummary: Boolean
        get() = summaryText.isNotBlank()

    val keyPointsList: List<String>
        get() = summaryKeyPoints
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    val topicsList: List<String>
        get() = summaryTopics
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
