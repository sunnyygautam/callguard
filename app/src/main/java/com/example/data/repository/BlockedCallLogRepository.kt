package com.example.data.repository

import com.example.data.local.BlockedCallLogDao
import com.example.data.local.BlockedCallLogEntity
import com.example.data.local.BlockedPrefixEntity
import com.example.telecom.PhoneNumberMatcher
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstracting [BlockedCallLogDao] for both the Compose ViewModel and
 * [com.example.telecom.PrefixCallScreeningService].
 */
class BlockedCallLogRepository(
    private val blockedCallLogDao: BlockedCallLogDao
) {
    /** Reactive stream of blocked calls ordered newest first. */
    val allBlockedCalls: Flow<List<BlockedCallLogEntity>> =
        blockedCallLogDao.observeAllBlockedCalls()

    /** Returns a fresh full snapshot of all blocked calls ordered newest first. */
    suspend fun getAllBlockedCallsSnapshot(): List<BlockedCallLogEntity> =
        blockedCallLogDao.getAllBlockedCalls()

    /**
     * Records a blocked incoming call along with the rule that matched it.
     */
    suspend fun logBlockedCall(
        incomingRawNumber: String,
        matchedRule: BlockedPrefixEntity,
        blockedAt: Long = System.currentTimeMillis()
    ): BlockedCallLogEntity = logBlockedCall(
        incomingRawNumber = incomingRawNumber,
        matchedPrefix = matchedRule.prefixString,
        reasonLabel = matchedRule.label,
        blockedAt = blockedAt
    )

    /**
     * Records a blocked incoming call with an explicit matched prefix/category, reason label,
     * and on-device ML spam probability score.
     */
    suspend fun logBlockedCall(
        incomingRawNumber: String?,
        matchedPrefix: String,
        reasonLabel: String,
        blockedAt: Long = System.currentTimeMillis(),
        spamProbability: Int = -1,
        mlReason: String = ""
    ): BlockedCallLogEntity {
        val rawTrimmed = incomingRawNumber.orEmpty().trim()
        val normalized = PhoneNumberMatcher.normalize(rawTrimmed)
        val displayNumber = when {
            normalized.isNotEmpty() -> normalized
            rawTrimmed.isNotEmpty() -> rawTrimmed
            else -> "Private / Unknown"
        }
        val entry = BlockedCallLogEntity(
            phoneNumber = displayNumber,
            normalizedNumber = normalized,
            matchedPrefix = matchedPrefix,
            matchedLabel = reasonLabel,
            blockedAt = blockedAt,
            spamProbability = spamProbability,
            mlReason = mlReason
        )
        val id = blockedCallLogDao.insertBlockedCall(entry)
        return entry.copy(id = id)
    }

    /**
     * Restores a deleted log entry (used for Undo Snackbar).
     */
    suspend fun restoreBlockedCall(log: BlockedCallLogEntity) {
        blockedCallLogDao.insertBlockedCall(log.copy(id = 0L))
    }

    /**
     * Removes a single entry from blocked call history.
     */
    suspend fun deleteBlockedCall(log: BlockedCallLogEntity) {
        blockedCallLogDao.deleteBlockedCall(log)
    }

    /**
     * Removes all entries from blocked call history.
     */
    suspend fun clearHistory() {
        blockedCallLogDao.clearAllHistory()
    }
}
