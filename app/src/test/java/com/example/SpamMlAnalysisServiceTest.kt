package com.example

import com.example.data.local.BlockedPrefixEntity
import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.data.repository.DeviceContactEntry
import com.example.ml.MaliciousPatternCatalog
import com.example.ml.SpamMlAnalysisService
import com.example.ml.SpamRiskTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamMlAnalysisServiceTest {

    @Test
    fun analyzeNumber_scoresKnownTelemarketerAndPremiumPatternsHighOrCritical() {
        val telemarketer = SpamMlAnalysisService.analyzeNumber("+911408829911")
        assertTrue(
            "Expected >= 60% spam probability for +91140 telemarketer, got ${telemarketer.probabilityPercent}%",
            telemarketer.probabilityPercent >= 60
        )
        assertTrue(telemarketer.matchedPatterns.isNotEmpty())

        val premiumScam = SpamMlAnalysisService.analyzeNumber("+19005550000")
        assertTrue(
            "Expected CRITICAL risk for +1900 + 0000 suffix, got ${premiumScam.probabilityPercent}%",
            premiumScam.riskTier == SpamRiskTier.CRITICAL || premiumScam.riskTier == SpamRiskTier.HIGH
        )

        val wangiri = SpamMlAnalysisService.analyzeNumber("+22244991122")
        assertTrue(wangiri.probabilityPercent >= 60)
        assertTrue(wangiri.matchedPatterns.any { it.category == "Wangiri Fraud" })
    }

    @Test
    fun analyzeNumber_scoresTrustedSavedContactWithHealthyHistoryLow() {
        val trusted = SpamMlAnalysisService.analyzeNumber(
            rawNumber = "+14158293741",
            isKnownContact = true,
            hasHealthyCallHistory = true
        )
        assertEquals(SpamRiskTier.LOW, trusted.riskTier)
        assertTrue("Expected <= 10% for trusted contact, got ${trusted.probabilityPercent}%", trusted.probabilityPercent <= 10)
    }

    @Test
    fun analyzeNumber_elevatesScoreOnUserBlockedPrefixAndStirShakenFailure() {
        val manualRule = BlockedPrefixEntity(
            id = 1L,
            prefixString = "+44123",
            normalizedPrefix = "+44123",
            label = "Scam Boiler Room",
            isActive = true
        )
        val result = SpamMlAnalysisService.analyzeNumber(
            rawNumber = "+44123998877",
            activeManualPrefixes = listOf(manualRule),
            isStirShakenFailed = true
        )
        assertEquals(SpamRiskTier.CRITICAL, result.riskTier)
        assertTrue(result.probabilityPercent >= 90)
        assertTrue(result.signals.any { it.contains("STIR/SHAKEN") })
        assertTrue(result.signals.any { it.contains("+44123") })
    }

    @Test
    fun computeDigitEntropyDeficit_distinguishesSyntheticRepeatingFromOrganicNumbers() {
        val repetitiveDeficit = SpamMlAnalysisService.computeDigitEntropyDeficit("8888888888")
        val organicDeficit = SpamMlAnalysisService.computeDigitEntropyDeficit("4158293706")
        assertTrue(repetitiveDeficit > 0.95)
        assertTrue(organicDeficit < 0.15)
    }

    @Test
    fun analyzeCallHistoryBatch_detectsMissedBurstAndTrustSignalsAcrossLogs() {
        val contacts = listOf(
            DeviceContactEntry(
                id = 1L,
                displayName = "Alice Mercer",
                phoneNumber = "+1 (415) 829-3741",
                normalizedNumber = "+14158293741"
            )
        )
        val logs = listOf(
            DeviceCallLogEntry(
                id = 10L,
                number = "+1 (415) 829-3741",
                cachedName = "Alice Mercer",
                type = DeviceCallType.OUTGOING,
                dateMillis = 5_000L,
                durationSeconds = 180L
            ),
            DeviceCallLogEntry(
                id = 11L,
                number = "+18005550000",
                cachedName = null,
                type = DeviceCallType.MISSED,
                dateMillis = 4_000L,
                durationSeconds = 0L
            ),
            DeviceCallLogEntry(
                id = 12L,
                number = "+18005550000",
                cachedName = null,
                type = DeviceCallType.REJECTED,
                dateMillis = 3_000L,
                durationSeconds = 0L
            )
        )

        val scores = SpamMlAnalysisService.analyzeCallHistoryBatch(
            callLogs = logs,
            contacts = contacts,
            activeManualPrefixes = emptyList()
        )

        assertEquals(3, scores.size)
        val aliceScore = scores.getValue(10L)
        val spamRejectedScore = scores.getValue(12L)

        assertEquals(SpamRiskTier.LOW, aliceScore.riskTier)
        assertTrue(spamRejectedScore.probabilityPercent >= 85)
        assertTrue(spamRejectedScore.signals.any { it.contains("Repeated unanswered/missed call burst") })
        assertTrue(MaliciousPatternCatalog.findMatches("+18005550000").size >= 2)
    }
}
