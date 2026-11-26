package com.example.data.backup

import com.example.data.local.BlockedCallLogEntity
import com.example.data.repository.DeviceContactEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Typed payload representing a CallGuard local JSON backup containing blocked call history
 * and device contacts.
 */
data class CallGuardBackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appName: String = "CallGuard",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val blockedCallHistory: List<BlockedCallLogEntity>,
    val contacts: List<DeviceContactEntry>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/**
 * Pure Kotlin RFC 8259 JSON serializer for [CallGuardBackupPayload].
 *
 * Uses zero external reflection dependencies so output is deterministic, ProGuard-safe,
 * pretty-printed for human readability, and directly unit-testable on the JVM.
 */
object BackupJsonSerializer {

    /**
     * Generates a filesystem-safe default filename such as `callguard_backup_2026-11-26_045851.json`.
     */
    fun createDefaultFileName(timestampMillis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
        return "callguard_backup_${formatter.format(Date(timestampMillis))}.json"
    }

    /**
     * Formats an epoch millisecond timestamp into ISO-8601 UTC (`yyyy-MM-dd'T'HH:mm:ss'Z'`).
     */
    fun formatIsoUtc(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(timestampMillis))
    }

    /**
     * Serializes [payload] into a pretty-printed, RFC 8259–compliant UTF-8 JSON document.
     */
    fun serialize(payload: CallGuardBackupPayload): String = buildString {
        append("{\n")
        append("  \"schemaVersion\": ").append(payload.schemaVersion).append(",\n")
        append("  \"appName\": ").append(quote(payload.appName)).append(",\n")
        append("  \"exportedAtIso\": ").append(quote(formatIsoUtc(payload.exportedAtMillis))).append(",\n")
        append("  \"exportedAtMillis\": ").append(payload.exportedAtMillis).append(",\n")

        // Summary block
        append("  \"summary\": {\n")
        append("    \"blockedCallCount\": ").append(payload.blockedCallHistory.size).append(",\n")
        append("    \"contactCount\": ").append(payload.contacts.size).append("\n")
        append("  },\n")

        // Blocked Call History array
        append("  \"blockedCallHistory\": [")
        if (payload.blockedCallHistory.isNotEmpty()) {
            append("\n")
            payload.blockedCallHistory.forEachIndexed { index, log ->
                append("    {\n")
                append("      \"id\": ").append(log.id).append(",\n")
                append("      \"phoneNumber\": ").append(quote(log.phoneNumber)).append(",\n")
                append("      \"normalizedNumber\": ").append(quote(log.normalizedNumber)).append(",\n")
                append("      \"matchedPrefix\": ").append(quote(log.matchedPrefix)).append(",\n")
                append("      \"matchedLabel\": ").append(quote(log.matchedLabel)).append(",\n")
                append("      \"blockedAtMillis\": ").append(log.blockedAt).append(",\n")
                append("      \"blockedAtIso\": ").append(quote(formatIsoUtc(log.blockedAt))).append(",\n")
                append("      \"spamProbability\": ").append(log.spamProbability).append(",\n")
                append("      \"mlReason\": ").append(quote(log.mlReason)).append("\n")
                append("    }")
                if (index < payload.blockedCallHistory.lastIndex) append(",")
                append("\n")
            }
            append("  ")
        }
        append("],\n")

        // Contacts array
        append("  \"contacts\": [")
        if (payload.contacts.isNotEmpty()) {
            append("\n")
            payload.contacts.forEachIndexed { index, contact ->
                append("    {\n")
                append("      \"id\": ").append(contact.id).append(",\n")
                append("      \"displayName\": ").append(quote(contact.displayName)).append(",\n")
                append("      \"phoneNumber\": ").append(quote(contact.phoneNumber)).append(",\n")
                append("      \"normalizedNumber\": ").append(quote(contact.normalizedNumber)).append("\n")
                append("    }")
                if (index < payload.contacts.lastIndex) append(",")
                append("\n")
            }
            append("  ")
        }
        append("]\n")
        append("}\n")
    }

    /**
     * Escapes a Kotlin [String] as a valid RFC 8259 JSON string literal.
     */
    internal fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append(String.format(Locale.US, "\\u%04x", ch.code))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }
}
