package com.example

import com.example.data.backup.BackupJsonSerializer
import com.example.data.backup.CallGuardBackupPayload
import com.example.data.local.BlockedCallLogEntity
import com.example.data.repository.DeviceContactEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonSerializerTest {

    @Test
    fun serialize_outputsCompleteStructuredJsonWithBlockedCallsAndContacts() {
        val payload = CallGuardBackupPayload(
            exportedAtMillis = 1_764_133_200_000L, // 2025-11-26T05:00:00Z
            blockedCallHistory = listOf(
                BlockedCallLogEntity(
                    id = 101L,
                    phoneNumber = "+911408829911",
                    normalizedNumber = "+911408829911",
                    matchedPrefix = "+91140",
                    matchedLabel = "Telemarketer",
                    blockedAt = 1_764_130_000_000L
                ),
                BlockedCallLogEntity(
                    id = 102L,
                    phoneNumber = "+18005550199",
                    normalizedNumber = "+18005550199",
                    matchedPrefix = "+1800",
                    matchedLabel = "Toll-Free Spam",
                    blockedAt = 1_764_129_000_000L
                )
            ),
            contacts = listOf(
                DeviceContactEntry(
                    id = 1L,
                    displayName = "Alice Mercer",
                    phoneNumber = "+1 (415) 555-0192",
                    normalizedNumber = "+14155550192"
                )
            )
        )

        val json = BackupJsonSerializer.serialize(payload)

        assertTrue(json.startsWith("{\n"))
        assertTrue(json.trimEnd().endsWith("}"))
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"appName\": \"CallGuard\""))
        assertTrue(json.contains("\"blockedCallCount\": 2"))
        assertTrue(json.contains("\"contactCount\": 1"))
        assertTrue(json.contains("\"phoneNumber\": \"+911408829911\""))
        assertTrue(json.contains("\"matchedLabel\": \"Telemarketer\""))
        assertTrue(json.contains("\"displayName\": \"Alice Mercer\""))
        assertTrue(json.contains("\"normalizedNumber\": \"+14155550192\""))
    }

    @Test
    fun serialize_handlesEmptyListsCleanly() {
        val payload = CallGuardBackupPayload(
            exportedAtMillis = 0L,
            blockedCallHistory = emptyList(),
            contacts = emptyList()
        )

        val json = BackupJsonSerializer.serialize(payload)

        assertTrue(json.contains("\"exportedAtIso\": \"1970-01-01T00:00:00Z\""))
        assertTrue(json.contains("\"blockedCallCount\": 0"))
        assertTrue(json.contains("\"contactCount\": 0"))
        assertTrue(json.contains("\"blockedCallHistory\": []"))
        assertTrue(json.contains("\"contacts\": []"))
    }

    @Test
    fun quote_escapesControlCharactersQuotesAndBackslashes() {
        val raw = "Dr. \"Bob\" \\ Clinic\nLine2\tTab\u0007"
        val escaped = BackupJsonSerializer.quote(raw)

        assertEquals("\"Dr. \\\"Bob\\\" \\\\ Clinic\\nLine2\\tTab\\u0007\"", escaped)
        assertFalse(escaped.contains('\n'))
    }

    @Test
    fun createDefaultFileName_usesTimestampedJsonExtension() {
        val fileName = BackupJsonSerializer.createDefaultFileName(1_764_133_200_000L)
        assertTrue(fileName.startsWith("callguard_backup_"))
        assertTrue(fileName.endsWith(".json"))
        assertFalse(fileName.contains(" "))
    }
}
