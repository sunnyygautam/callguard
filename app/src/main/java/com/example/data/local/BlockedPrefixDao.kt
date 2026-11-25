package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for querying and mutating [BlockedPrefixEntity] records.
 */
@Dao
interface BlockedPrefixDao {

    /**
     * Reactive stream of all saved prefix rules, ordered newest first, for Jetpack Compose UI.
     */
    @Query("SELECT * FROM blocked_prefixes ORDER BY created_at DESC")
    fun observeAllPrefixes(): Flow<List<BlockedPrefixEntity>>

    /**
     * One-shot suspend query returning only currently active rules.
     * Used inside [com.example.telecom.PrefixCallScreeningService.onScreenCall] on Dispatchers.IO.
     */
    @Query("SELECT * FROM blocked_prefixes WHERE is_active = 1")
    suspend fun getActivePrefixes(): List<BlockedPrefixEntity>

    /**
     * Checks whether a rule with the exact normalized prefix already exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_prefixes WHERE normalized_prefix = :normalizedPrefix)")
    suspend fun existsByNormalizedPrefix(normalizedPrefix: String): Boolean

    /**
     * Inserts a new prefix rule. Returns the row ID, or -1L if ignored due to conflict.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPrefix(prefix: BlockedPrefixEntity): Long

    /**
     * Enables or disables a specific prefix rule without deleting it.
     */
    @Query("UPDATE blocked_prefixes SET is_active = :isActive WHERE id = :id")
    suspend fun updateActiveState(id: Long, isActive: Boolean)

    /**
     * Deletes a specific prefix rule by entity reference.
     */
    @Delete
    suspend fun deletePrefix(prefix: BlockedPrefixEntity)

    /**
     * Deletes a specific prefix rule by primary key ID.
     */
    @Query("DELETE FROM blocked_prefixes WHERE id = :id")
    suspend fun deletePrefixById(id: Long)
}
