package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single user-defined number series/prefix blocking rule.
 *
 * A unique index on [normalizedPrefix] prevents duplicate blocking rules such as
 * "+91 140" and "+91140" from being inserted twice.
 */
@Entity(
    tableName = "blocked_prefixes",
    indices = [Index(value = ["normalized_prefix"], unique = true)]
)
data class BlockedPrefixEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Display-friendly prefix string entered by the user (e.g., "+91140", "+1800", "0114"). */
    @ColumnInfo(name = "prefix_string")
    val prefixString: String,

    /** Normalized form stripped of spaces/dashes/parentheses for fast telecom matching. */
    @ColumnInfo(name = "normalized_prefix")
    val normalizedPrefix: String,

    /** Optional label or reason for blocking this series (e.g., "Telemarketer", "Spam"). */
    @ColumnInfo(name = "label")
    val label: String = "",

    /** Whether this prefix rule is currently enabled for incoming call screening. */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    /** Epoch timestamp (ms) when this rule was created. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
