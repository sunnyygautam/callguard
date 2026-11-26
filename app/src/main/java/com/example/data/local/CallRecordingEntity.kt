package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity storing metadata for a locally recorded phone call (.m4a audio file).
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
    val recordedAt: Long = System.currentTimeMillis()
)
