package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity logging each incoming call that was automatically intercepted and blocked
 * by [com.example.telecom.PrefixCallScreeningService].
 *
 * Stores a snapshot of the matched prefix and label so historical records remain intact
 * even if the user later edits or deletes the corresponding prefix rule.
 */
@Entity(
    tableName = "blocked_call_logs",
    indices = [Index(value = ["blocked_at"])]
)
data class BlockedCallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Incoming caller number as delivered by Android Telecom (e.g., "+911408829911"). */
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,

    /** Normalized caller number stripped of formatting separators. */
    @ColumnInfo(name = "normalized_number")
    val normalizedNumber: String,

    /** The prefix rule string that triggered the block (e.g., "+91140"). */
    @ColumnInfo(name = "matched_prefix")
    val matchedPrefix: String,

    /** Snapshot of the rule's label/reason at the time the call was blocked. */
    @ColumnInfo(name = "matched_label")
    val matchedLabel: String = "",

    /** Epoch timestamp (ms) when the call was rejected. */
    @ColumnInfo(name = "blocked_at")
    val blockedAt: Long = System.currentTimeMillis(),

    /** On-device ML spam probability score (0..100), or -1 for legacy records prior to v3. */
    @ColumnInfo(name = "spam_probability", defaultValue = "-1")
    val spamProbability: Int = -1,

    /** Concise primary ML analysis factor explaining the spam probability score. */
    @ColumnInfo(name = "ml_reason", defaultValue = "''")
    val mlReason: String = ""
)
