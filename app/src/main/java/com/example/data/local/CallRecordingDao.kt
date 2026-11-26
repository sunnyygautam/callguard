package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for managing persisted call recording metadata and on-device conversation summaries.
 */
@Dao
interface CallRecordingDao {

    @Query("SELECT * FROM call_recordings ORDER BY recorded_at DESC")
    fun observeAllRecordings(): Flow<List<CallRecordingEntity>>

    @Query("SELECT * FROM call_recordings ORDER BY recorded_at DESC")
    suspend fun getAllRecordingsSnapshot(): List<CallRecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: CallRecordingEntity): Long

    @Query(
        """
        UPDATE call_recordings
        SET transcript = :transcript,
            summary_text = :summaryText,
            summary_key_points = :summaryKeyPoints,
            summary_topics = :summaryTopics,
            summary_mode = :summaryMode,
            summary_generated_at = :generatedAt
        WHERE id = :recordingId
        """
    )
    suspend fun updateSummary(
        recordingId: Long,
        transcript: String,
        summaryText: String,
        summaryKeyPoints: String,
        summaryTopics: String,
        summaryMode: String,
        generatedAt: Long
    )

    @Delete
    suspend fun delete(recording: CallRecordingEntity)

    @Query("DELETE FROM call_recordings")
    suspend fun deleteAll()
}
