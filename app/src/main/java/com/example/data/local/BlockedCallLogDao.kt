package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for persisting and observing blocked incoming call history.
 */
@Dao
interface BlockedCallLogDao {

    /**
     * Reactive stream of all blocked call logs ordered from most recent to oldest.
     */
    @Query("SELECT * FROM blocked_call_logs ORDER BY blocked_at DESC")
    fun observeAllBlockedCalls(): Flow<List<BlockedCallLogEntity>>

    /**
     * Inserts a new blocked call log entry and returns its generated primary key ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedCall(log: BlockedCallLogEntity): Long

    /**
     * Deletes a single blocked call log entry.
     */
    @Delete
    suspend fun deleteBlockedCall(log: BlockedCallLogEntity)

    /**
     * Deletes a single blocked call log entry by primary key ID.
     */
    @Query("DELETE FROM blocked_call_logs WHERE id = :id")
    suspend fun deleteBlockedCallById(id: Long)

    /**
     * Clears the entire blocked call history table.
     */
    @Query("DELETE FROM blocked_call_logs")
    suspend fun clearAllHistory()
}
