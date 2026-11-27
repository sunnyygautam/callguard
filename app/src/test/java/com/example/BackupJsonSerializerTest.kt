package com.example

import com.example.data.backup.BackupJsonSerializer
import com.example.data.backup.CallGuardBackupPayload
import com.example.data.local.BlockedCallLogEntity
import com.example.data.local.BlockedMatchType
import com.example.data.local.BlockedPrefixEntity
import com.example.data.repository.DeviceContactEntry
import com.example.data.repository.SpamBlockerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonSerializerTest {

    @Test
    fun serialize_outputsCompleteStructuredJsonWithBlockedNumbersSettingsHistoryAndContacts() {
        val payload = CallGuardBackupPayload(
            exportedAtMillis = 1_764_133_200_000L, // 2025-11-26T05:00:00Z
            blockedNumbers = listOf(
                BlockedPrefixEntity(
                    id = 1L,
                    prefixString = "+91140",
                    normalizedPrefix = "+91140",
                    label = "Telemarketer Series",
                    matchType = BlockedMatchType.PREFIX,
                    isActive = true,
                    createdAt = 1_764_100_000_000L
                ),
                BlockedPrefixEntity(
                    id = 2L,
                    prefixString = "+14155550199",
                    normalizedPrefix = "+14155550199",
                    label = "Spam Caller",
                    matchType = BlockedMatchType.EXACT_NUMBER,
                    isActive = false,
                    createdAt = 1_764_110_000_000L
                )
            ),
            callSettings = SpamBlockerSettings(
                isSpamBlockerEnabled = true,
                doNotDisturbEnabled = true,
                blockBuiltInSpamSeries = false,
                blockHiddenPrivateCalls = true,
                blockSpoofedStirShakenCalls = true,
                silenceOnlyMode = true,
                ringtoneEnabled = false,
                vibrationEnabled = true,
                notificationsEnabled = false,
                callRecordingEnabled = true,
                autoRecordCalls = true,
                batteryOptimizationPromptDismissed = true
            ),
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
        assertTrue(json.contains("\"schemaVersion\": 2"))
        assertTrue(json.contains("\"appName\": \"CallGuard\""))
        assertTrue(json.contains("\"blockedNumberCount\": 2"))
        assertTrue(json.contains("\"includesCallSettings\": true"))
        assertTrue(json.contains("\"blockedCallCount\": 2"))
        assertTrue(json.contains("\"contactCount\": 1"))
        assertTrue(json.contains("\"phoneNumber\": \"+911408829911\""))
        assertTrue(json.contains("\"matchedLabel\": \"Telemarketer\""))
        assertTrue(json.contains("\"displayName\": \"Alice Mercer\""))
        assertTrue(json.contains("\"normalizedNumber\": \"+14155550192\""))
    }

    @Test
    fun serializeAndDeserialize_roundTripsBlockedNumbersCallSettingsAndHistoryAccurately() {
        val original = CallGuardBackupPayload(
            exportedAtMillis = 1_764_133_200_000L,
            blockedNumbers = listOf(
                BlockedPrefixEntity(
                    id = 10L,
                    prefixString = "+1800",
                    normalizedPrefix = "+1800",
                    label = "Dr. \"Bob\" \\ Promo\nSeries",
                    matchType = BlockedMatchType.PREFIX,
                    isActive = true,
                    createdAt = 1_764_120_000_000L
                ),
                BlockedPrefixEntity(
                    id = 11L,
                    prefixString = "+14155550142",
                    normalizedPrefix = "+14155550142",
                    label = "Exact Spammer",
                    matchType = BlockedMatchType.EXACT_NUMBER,
                    isActive = false,
                    createdAt = 1_764_125_000_000L
                )
            ),
            callSettings = SpamBlockerSettings(
                isSpamBlockerEnabled = false,
                doNotDisturbEnabled = true,
                blockBuiltInSpamSeries = false,
                blockHiddenPrivateCalls = true,
                blockSpoofedStirShakenCalls = false,
                silenceOnlyMode = true,
                ringtoneEnabled = false,
                vibrationEnabled = false,
                notificationsEnabled = true,
                callRecordingEnabled = true,
                autoRecordCalls = true,
                batteryOptimizationPromptDismissed = true
            ),
            blockedCallHistory = listOf(
                BlockedCallLogEntity(
                    id = 55L,
                    phoneNumber = "+18005559999",
                    normalizedNumber = "+18005559999",
                    matchedPrefix = "+1800",
                    matchedLabel = "Promo Series",
                    blockedAt = 1_764_131_000_000L
                )
            ),
            contacts = listOf(
                DeviceContactEntry(
                    id = 7L,
                    displayName = "Alice Mercer",
                    phoneNumber = "+1 (415) 555-0192",
                    normalizedNumber = "+14155550192"
                )
            )
        )

        val json = BackupJsonSerializer.serialize(original)
        val restored = BackupJsonSerializer.deserialize(json)

        assertEquals(2, restored.schemaVersion)
        assertEquals(original.exportedAtMillis, restored.exportedAtMillis)

        assertEquals(2, restored.blockedNumbers.size)
        assertEquals("+1800", restored.blockedNumbers[0].prefixString)
        assertEquals("+1800", restored.blockedNumbers[0].normalizedPrefix)
        assertEquals("Dr. \"Bob\" \\ Promo\nSeries", restored.blockedNumbers[0].label)
        assertEquals(BlockedMatchType.PREFIX, restored.blockedNumbers[0].matchType)
        assertTrue(restored.blockedNumbers[0].isActive)
        assertEquals(1_764_120_000_000L, restored.blockedNumbers[0].createdAt)

        assertEquals("+14155550142", restored.blockedNumbers[1].prefixString)
        assertEquals(BlockedMatchType.EXACT_NUMBER, restored.blockedNumbers[1].matchType)
        assertFalse(restored.blockedNumbers[1].isActive)

        assertNotNull(restored.callSettings)
        assertEquals(original.callSettings, restored.callSettings)

        assertEquals(1, restored.blockedCallHistory.size)
        assertEquals("+18005559999", restored.blockedCallHistory[0].phoneNumber)
        assertEquals("Promo Series", restored.blockedCallHistory[0].matchedLabel)
        assertEquals(1_764_131_000_000L, restored.blockedCallHistory[0].blockedAt)

        assertEquals(1, restored.contacts.size)
        assertEquals("Alice Mercer", restored.contacts[0].displayName)
    }

    @Test
    fun deserialize_supportsSchemaVersion1BackupsWithoutBlockedNumbersOrSettings() {
        val legacyV1Json = """
            {
              "schemaVersion": 1,
              "appName": "CallGuard",
              "exportedAtMillis": 1764133200000,
              "blockedCallHistory": [
                {
                  "id": 101,
                  "phoneNumber": "+911408829911",
                  "normalizedNumber": "+911408829911",
                  "matchedPrefix": "+91140",
                  "matchedLabel": "Telemarketer",
                  "blockedAt": 1764130000000
                }
              ],
              "contacts": []
            }
        """.trimIndent()

        val restored = BackupJsonSerializer.deserialize(legacyV1Json)
        assertEquals(1, restored.schemaVersion)
        assertTrue(restored.blockedNumbers.isEmpty())
        assertNull(restored.callSettings)
        assertEquals(1, restored.blockedCallHistory.size)
        assertEquals("+911408829911", restored.blockedCallHistory[0].phoneNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun deserialize_rejectsMalformedJson() {
        BackupJsonSerializer.deserialize("{ not valid json")
    }

    @Test
    fun serialize_handlesEmptyListsCleanly() {
        val payload = CallGuardBackupPayload(
            exportedAtMillis = 0L,
            blockedNumbers = emptyList(),
            callSettings = null,
            blockedCallHistory = emptyList(),
            contacts = emptyList()
        )

        val json = BackupJsonSerializer.serialize(payload)

        assertTrue(json.contains("\"exportedAtIso\": \"1970-01-01T00:00:00Z\""))
        assertTrue(json.contains("\"blockedNumberCount\": 0"))
        assertTrue(json.contains("\"includesCallSettings\": false"))
        assertTrue(json.contains("\"blockedCallCount\": 0"))
        assertTrue(json.contains("\"contactCount\": 0"))
        assertTrue(json.contains("\"blockedNumbers\": []"))
        assertTrue(json.contains("\"callSettings\": null"))
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
