package com.example.data.backup

import com.example.data.local.BlockedCallLogEntity
import com.example.data.local.BlockedMatchType
import com.example.data.local.BlockedPrefixEntity
import com.example.data.repository.DeviceContactEntry
import com.example.data.repository.SpamBlockerSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Typed payload representing a CallGuard local JSON backup containing:
 * - Blocked phone numbers & prefix rules ([blockedNumbers])
 * - Recent call & shield settings ([callSettings])
 * - Blocked call history logs ([blockedCallHistory])
 * - Device contacts ([contacts])
 */
data class CallGuardBackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appName: String = "CallGuard",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val blockedNumbers: List<BlockedPrefixEntity> = emptyList(),
    val callSettings: SpamBlockerSettings? = null,
    val blockedCallHistory: List<BlockedCallLogEntity> = emptyList(),
    val contacts: List<DeviceContactEntry> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

/**
 * Pure Kotlin RFC 8259 JSON serializer and deserializer for [CallGuardBackupPayload].
 *
 * Uses zero external reflection or Android framework dependencies so serialization and
 * restore parsing are deterministic, ProGuard-safe, backward-compatible with schema v1,
 * and directly unit-testable on the JVM.
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
        append("    \"blockedNumberCount\": ").append(payload.blockedNumbers.size).append(",\n")
        append("    \"includesCallSettings\": ").append(payload.callSettings != null).append(",\n")
        append("    \"blockedCallCount\": ").append(payload.blockedCallHistory.size).append(",\n")
        append("    \"contactCount\": ").append(payload.contacts.size).append("\n")
        append("  },\n")

        // Blocked Phone Numbers & Prefixes array
        append("  \"blockedNumbers\": [")
        if (payload.blockedNumbers.isNotEmpty()) {
            append("\n")
            payload.blockedNumbers.forEachIndexed { index, rule ->
                append("    {\n")
                append("      \"id\": ").append(rule.id).append(",\n")
                append("      \"prefixString\": ").append(quote(rule.prefixString)).append(",\n")
                append("      \"normalizedPrefix\": ").append(quote(rule.normalizedPrefix)).append(",\n")
                append("      \"label\": ").append(quote(rule.label)).append(",\n")
                append("      \"isActive\": ").append(rule.isActive).append(",\n")
                append("      \"matchType\": ").append(quote(rule.matchType)).append(",\n")
                append("      \"createdAtMillis\": ").append(rule.createdAt).append("\n")
                append("    }")
                if (index < payload.blockedNumbers.lastIndex) append(",")
                append("\n")
            }
            append("  ")
        }
        append("],\n")

        // Call & Shield Settings object (null when omitted)
        append("  \"callSettings\": ")
        val settings = payload.callSettings
        if (settings == null) {
            append("null,\n")
        } else {
            append("{\n")
            append("    \"isSpamBlockerEnabled\": ").append(settings.isSpamBlockerEnabled).append(",\n")
            append("    \"doNotDisturbEnabled\": ").append(settings.doNotDisturbEnabled).append(",\n")
            append("    \"blockBuiltInSpamSeries\": ").append(settings.blockBuiltInSpamSeries).append(",\n")
            append("    \"blockHiddenPrivateCalls\": ").append(settings.blockHiddenPrivateCalls).append(",\n")
            append("    \"blockSpoofedStirShakenCalls\": ").append(settings.blockSpoofedStirShakenCalls).append(",\n")
            append("    \"silenceOnlyMode\": ").append(settings.silenceOnlyMode).append(",\n")
            append("    \"ringtoneEnabled\": ").append(settings.ringtoneEnabled).append(",\n")
            append("    \"vibrationEnabled\": ").append(settings.vibrationEnabled).append(",\n")
            append("    \"notificationsEnabled\": ").append(settings.notificationsEnabled).append(",\n")
            append("    \"callRecordingEnabled\": ").append(settings.callRecordingEnabled).append(",\n")
            append("    \"autoRecordCalls\": ").append(settings.autoRecordCalls).append(",\n")
            append("    \"batteryOptimizationPromptDismissed\": ")
                .append(settings.batteryOptimizationPromptDismissed).append("\n")
            append("  },\n")
        }

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
     * Parses a CallGuard JSON backup document (supporting both schema v1 and v2) back into a
     * typed [CallGuardBackupPayload]. Throws [IllegalArgumentException] if the JSON is malformed
     * or not a valid JSON object.
     */
    fun deserialize(json: String): CallGuardBackupPayload {
        val root = MiniJsonParser(json).parseRootObject()

        val schemaVersion = root.intOrDefault("schemaVersion", 1)
        val appName = root.stringOrDefault("appName", "CallGuard")
        val exportedAtMillis = root.longOrDefault("exportedAtMillis", System.currentTimeMillis())

        val blockedNumbers = root.arrayOrEmpty("blockedNumbers").mapNotNull { elem ->
            val obj = elem as? Map<*, *> ?: return@mapNotNull null
            val prefixStr = obj.stringOrDefault("prefixString", "")
            val normalized = obj.stringOrDefault("normalizedPrefix", prefixStr)
            if (prefixStr.isBlank() && normalized.isBlank()) return@mapNotNull null
            BlockedPrefixEntity(
                id = obj.longOrDefault("id", 0L),
                prefixString = prefixStr.ifBlank { normalized },
                normalizedPrefix = normalized.ifBlank { prefixStr },
                label = obj.stringOrDefault("label", ""),
                isActive = obj.boolOrDefault("isActive", true),
                matchType = obj.stringOrDefault("matchType", BlockedMatchType.PREFIX),
                createdAt = obj.longOrDefault("createdAtMillis", System.currentTimeMillis())
            )
        }

        val callSettingsObj = root["callSettings"] as? Map<*, *>
        val callSettings = callSettingsObj?.let { obj ->
            val defaults = SpamBlockerSettings()
            SpamBlockerSettings(
                isSpamBlockerEnabled = obj.boolOrDefault("isSpamBlockerEnabled", defaults.isSpamBlockerEnabled),
                doNotDisturbEnabled = obj.boolOrDefault("doNotDisturbEnabled", defaults.doNotDisturbEnabled),
                blockBuiltInSpamSeries = obj.boolOrDefault("blockBuiltInSpamSeries", defaults.blockBuiltInSpamSeries),
                blockHiddenPrivateCalls = obj.boolOrDefault("blockHiddenPrivateCalls", defaults.blockHiddenPrivateCalls),
                blockSpoofedStirShakenCalls = obj.boolOrDefault("blockSpoofedStirShakenCalls", defaults.blockSpoofedStirShakenCalls),
                silenceOnlyMode = obj.boolOrDefault("silenceOnlyMode", defaults.silenceOnlyMode),
                ringtoneEnabled = obj.boolOrDefault("ringtoneEnabled", defaults.ringtoneEnabled),
                vibrationEnabled = obj.boolOrDefault("vibrationEnabled", defaults.vibrationEnabled),
                notificationsEnabled = obj.boolOrDefault("notificationsEnabled", defaults.notificationsEnabled),
                callRecordingEnabled = obj.boolOrDefault("callRecordingEnabled", defaults.callRecordingEnabled),
                autoRecordCalls = obj.boolOrDefault("autoRecordCalls", defaults.autoRecordCalls),
                batteryOptimizationPromptDismissed = obj.boolOrDefault(
                    "batteryOptimizationPromptDismissed",
                    defaults.batteryOptimizationPromptDismissed
                )
            )
        }

        val blockedHistory = root.arrayOrEmpty("blockedCallHistory").mapNotNull { elem ->
            val obj = elem as? Map<*, *> ?: return@mapNotNull null
            val phone = obj.stringOrDefault("phoneNumber", "")
            val normalized = obj.stringOrDefault("normalizedNumber", phone)
            if (phone.isBlank() && normalized.isBlank()) return@mapNotNull null
            BlockedCallLogEntity(
                id = obj.longOrDefault("id", 0L),
                phoneNumber = phone.ifBlank { normalized },
                normalizedNumber = normalized.ifBlank { phone },
                matchedPrefix = obj.stringOrDefault("matchedPrefix", ""),
                matchedLabel = obj.stringOrDefault("matchedLabel", ""),
                blockedAt = obj.longOrDefault("blockedAtMillis", System.currentTimeMillis()),
                spamProbability = obj.intOrDefault("spamProbability", 0),
                mlReason = obj.stringOrDefault("mlReason", "")
            )
        }

        val contacts = root.arrayOrEmpty("contacts").mapNotNull { elem ->
            val obj = elem as? Map<*, *> ?: return@mapNotNull null
            val phone = obj.stringOrDefault("phoneNumber", "")
            if (phone.isBlank()) return@mapNotNull null
            DeviceContactEntry(
                id = obj.longOrDefault("id", 0L),
                displayName = obj.stringOrDefault("displayName", phone),
                phoneNumber = phone,
                normalizedNumber = obj.stringOrDefault("normalizedNumber", phone)
            )
        }

        return CallGuardBackupPayload(
            schemaVersion = schemaVersion,
            appName = appName,
            exportedAtMillis = exportedAtMillis,
            blockedNumbers = blockedNumbers,
            callSettings = callSettings,
            blockedCallHistory = blockedHistory,
            contacts = contacts
        )
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

    private fun Map<*, *>.stringOrDefault(key: String, default: String): String {
        return (this[key] as? String) ?: default
    }

    private fun Map<*, *>.boolOrDefault(key: String, default: Boolean): Boolean {
        return (this[key] as? Boolean) ?: default
    }

    private fun Map<*, *>.intOrDefault(key: String, default: Int): Int {
        return (this[key] as? Number)?.toInt() ?: default
    }

    private fun Map<*, *>.longOrDefault(key: String, default: Long): Long {
        return (this[key] as? Number)?.toLong() ?: default
    }

    private fun Map<*, *>.arrayOrEmpty(key: String): List<*> {
        return (this[key] as? List<*>) ?: emptyList<Any?>()
    }

    /**
     * Minimal, strict RFC 8259 recursive-descent JSON parser with zero Android or reflection dependencies.
     */
    private class MiniJsonParser(private val input: String) {
        private var pos = 0

        fun parseRootObject(): Map<String, Any?> {
            skipWhitespace()
            if (pos >= input.length || input[pos] != '{') {
                throw IllegalArgumentException("Backup file must contain a valid JSON object.")
            }
            val result = parseObject()
            skipWhitespace()
            if (pos < input.length) {
                throw IllegalArgumentException("Unexpected trailing characters in JSON backup.")
            }
            return result
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= input.length) throw IllegalArgumentException("Unexpected end of JSON input.")
            return when (val ch = input[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected JSON token '$ch' at index $pos.")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            val map = LinkedHashMap<String, Any?>()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' in JSON object at index $pos.")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            val list = mutableListOf<Any?>()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' in JSON array at index $pos.")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < input.length) {
                val ch = input[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= input.length) {
                            throw IllegalArgumentException("Unterminated escape sequence in JSON string.")
                        }
                        when (val esc = input[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > input.length) {
                                    throw IllegalArgumentException("Invalid unicode escape in JSON string.")
                                }
                                val hex = input.substring(pos, pos + 4)
                                val codePoint = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Invalid hex '\\u$hex' in JSON string.")
                                sb.append(codePoint.toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Invalid escape '\\$esc' in JSON string.")
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string.")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (peek() == '-') pos++
            while (pos < input.length && input[pos] in '0'..'9') pos++
            var isFloating = false
            if (peek() == '.') {
                isFloating = true
                pos++
                while (pos < input.length && input[pos] in '0'..'9') pos++
            }
            if (peek() == 'e' || peek() == 'E') {
                isFloating = true
                pos++
                if (peek() == '+' || peek() == '-') pos++
                while (pos < input.length && input[pos] in '0'..'9') pos++
            }
            val token = input.substring(start, pos)
            return if (isFloating) {
                token.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid JSON number '$token'.")
            } else {
                token.toLongOrNull()
                    ?: token.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid JSON number '$token'.")
            }
        }

        private fun <T> parseLiteral(expected: String, value: T): T {
            if (!input.regionMatches(pos, expected, 0, expected.length)) {
                throw IllegalArgumentException("Expected '$expected' at index $pos.")
            }
            pos += expected.length
            return value
        }

        private fun expect(ch: Char) {
            if (pos >= input.length || input[pos] != ch) {
                throw IllegalArgumentException("Expected '$ch' at index $pos.")
            }
            pos++
        }

        private fun peek(): Char? = if (pos < input.length) input[pos] else null

        private fun skipWhitespace() {
            while (pos < input.length) {
                when (input[pos]) {
                    ' ', '\t', '\r', '\n' -> pos++
                    else -> return
                }
            }
        }
    }
}
