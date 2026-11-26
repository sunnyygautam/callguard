package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for managing persisted call recording metadata.
 */
@Dao
interface CallRecordingDao {

    @Query("SELECT * FROM call_recordings ORDER BY recorded_at DESC")
    fun observeAllRecordings(): Flow<List<CallRecordingEntity>>

    @Query("SELECT * FROM call_recordings ORDER BY recorded_at DESC")
    suspend fun getAllRecordingsSnapshot(): List<CallRecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: CallRecordingEntity): Long

    @Delete
    suspend fun delete(recording: CallRecordingEntity)

    @Query("DELETE FROM call_recordings")
    suspend fun deleteAll()
}
