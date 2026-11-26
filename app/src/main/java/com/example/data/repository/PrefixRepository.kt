package com.example.data.repository

import com.example.data.local.BlockedMatchType
import com.example.data.local.BlockedPrefixDao
import com.example.data.local.BlockedPrefixEntity
import com.example.telecom.PhoneNumberMatcher
import kotlinx.coroutines.flow.Flow

/**
 * Result sealed interface for adding a new manual phone number or prefix rule.
 */
sealed interface AddPrefixResult {
    data class Success(val entity: BlockedPrefixEntity) : AddPrefixResult
    data object EmptyInput : AddPrefixResult
    data object InvalidFormat : AddPrefixResult
    data object DuplicatePrefix : AddPrefixResult
}

/**
 * Repository abstracting Room DAO persistence from the UI ViewModel and CallScreeningService.
 */
class PrefixRepository(
    private val blockedPrefixDao: BlockedPrefixDao
) {
    /** Reactive list of all stored blocked numbers and prefixes for UI observation. */
    val allPrefixes: Flow<List<BlockedPrefixEntity>> = blockedPrefixDao.observeAllPrefixes()

    /**
     * Validates, normalizes, and inserts a new blocked phone number or prefix rule into Room.
     */
    suspend fun addPrefix(
        rawPrefix: String,
        rawLabel: String,
        matchType: String = BlockedMatchType.PREFIX
    ): AddPrefixResult {
        val trimmedInput = rawPrefix.trim()
        if (trimmedInput.isEmpty()) return AddPrefixResult.EmptyInput

        val normalized = PhoneNumberMatcher.normalize(trimmedInput)
        val isValid = if (matchType == BlockedMatchType.EXACT_NUMBER) {
            PhoneNumberMatcher.isValidExactNumber(normalized)
        } else {
            PhoneNumberMatcher.isValidPrefix(normalized)
        }
        if (!isValid) {
            return AddPrefixResult.InvalidFormat
        }

        if (blockedPrefixDao.existsByNormalizedPrefix(normalized)) {
            return AddPrefixResult.DuplicatePrefix
        }

        val entity = BlockedPrefixEntity(
            prefixString = normalized,
            normalizedPrefix = normalized,
            label = rawLabel.trim(),
            isActive = true,
            matchType = matchType
        )

        val insertedId = blockedPrefixDao.insertPrefix(entity)
        return if (insertedId == -1L) {
            AddPrefixResult.DuplicatePrefix
        } else {
            AddPrefixResult.Success(entity.copy(id = insertedId))
        }
    }

    /**
     * Restores a previously deleted prefix rule (used for Undo Snackbar action).
     */
    suspend fun restorePrefix(entity: BlockedPrefixEntity) {
        blockedPrefixDao.insertPrefix(entity.copy(id = 0L))
    }

    /**
     * Toggles whether a saved prefix rule actively blocks calls.
     */
    suspend fun setPrefixActive(id: Long, isActive: Boolean) {
        blockedPrefixDao.updateActiveState(id = id, isActive = isActive)
    }

    /**
     * Deletes a saved prefix rule.
     */
    suspend fun deletePrefix(entity: BlockedPrefixEntity) {
        blockedPrefixDao.deletePrefix(entity)
    }

    /**
     * Fetches all currently enabled prefixes for incoming call screening.
     */
    suspend fun getActivePrefixes(): List<BlockedPrefixEntity> {
        return blockedPrefixDao.getActivePrefixes()
    }
}
