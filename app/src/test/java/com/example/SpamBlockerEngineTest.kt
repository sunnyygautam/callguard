package com.example

import com.example.data.local.BlockedPrefixEntity
import com.example.data.repository.SpamBlockerSettings
import com.example.telecom.SpamBlockerEngine
import com.example.telecom.SpamScreeningDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamBlockerEngineTest {

    private val customRule = BlockedPrefixEntity(
        id = 1L,
        prefixString = "0114",
        normalizedPrefix = "0114",
        label = "Custom Promo",
        isActive = true
    )

    @Test
    fun `master switch disabled allows all calls even if matching spam criteria`() {
        val decision = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = "+911408889999",
            isHiddenOrPrivateCaller = true,
            isStirShakenVerificationFailed = true,
            activeManualPrefixes = listOf(customRule),
            settings = SpamBlockerSettings(isSpamBlockerEnabled = false)
        )
        assertEquals(SpamScreeningDecision.Allow, decision)
    }

    @Test
    fun `blocks hidden private caller when setting enabled`() {
        val decision = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = null,
            isHiddenOrPrivateCaller = true,
            isStirShakenVerificationFailed = false,
            activeManualPrefixes = emptyList(),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = true,
                blockHiddenPrivateCalls = true,
                silenceOnlyMode = false
            )
        )
        assertTrue(decision is SpamScreeningDecision.Block)
        val block = decision as SpamScreeningDecision.Block
        assertEquals("Private", block.matchedPrefix)
        assertFalse(block.silenceOnly)
    }

    @Test
    fun `blocks STIR SHAKEN failed verification call when spoofed protection enabled`() {
        val decision = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = "+14155550199",
            isHiddenOrPrivateCaller = false,
            isStirShakenVerificationFailed = true,
            activeManualPrefixes = emptyList(),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = true,
                blockSpoofedStirShakenCalls = true,
                silenceOnlyMode = true
            )
        )
        assertTrue(decision is SpamScreeningDecision.Block)
        val block = decision as SpamScreeningDecision.Block
        assertEquals("STIR/SHAKEN", block.matchedPrefix)
        assertTrue(block.silenceOnly)
    }

    @Test
    fun `blocks built-in telemarketer series when enabled and allows when disabled`() {
        val blocked = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = "+1 (800) 555-0199",
            isHiddenOrPrivateCaller = false,
            isStirShakenVerificationFailed = false,
            activeManualPrefixes = emptyList(),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = true,
                blockBuiltInSpamSeries = true
            )
        )
        assertTrue(blocked is SpamScreeningDecision.Block)
        assertEquals("+1800", (blocked as SpamScreeningDecision.Block).matchedPrefix)

        val allowed = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = "+1 (800) 555-0199",
            isHiddenOrPrivateCaller = false,
            isStirShakenVerificationFailed = false,
            activeManualPrefixes = emptyList(),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = true,
                blockBuiltInSpamSeries = false
            )
        )
        assertEquals(SpamScreeningDecision.Allow, allowed)
    }

    @Test
    fun `manual prefix rules take priority over built-in series`() {
        val customTelemarketer = BlockedPrefixEntity(
            id = 9L,
            prefixString = "+91140",
            normalizedPrefix = "+91140",
            label = "My Custom Label",
            isActive = true
        )
        val decision = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = "+91 140-999-1234",
            isHiddenOrPrivateCaller = false,
            isStirShakenVerificationFailed = false,
            activeManualPrefixes = listOf(customTelemarketer),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = true,
                blockBuiltInSpamSeries = true
            )
        )
        assertTrue(decision is SpamScreeningDecision.Block)
        assertEquals("My Custom Label", (decision as SpamScreeningDecision.Block).reasonLabel)
    }

    @Test
    fun `do not disturb rejects non-contact callers even when regular spam blocker is disabled`() {
        val decision = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = "+14155550192",
            isHiddenOrPrivateCaller = false,
            isStirShakenVerificationFailed = false,
            activeManualPrefixes = emptyList(),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = false,
                doNotDisturbEnabled = true,
                silenceOnlyMode = true
            ),
            isCallerInContacts = false
        )
        assertTrue(decision is SpamScreeningDecision.Block)
        val block = decision as SpamScreeningDecision.Block
        assertEquals("DND", block.matchedPrefix)
        assertFalse(block.silenceOnly)
    }

    @Test
    fun `do not disturb allows callers found in contacts`() {
        val decision = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = "+14155550192",
            isHiddenOrPrivateCaller = false,
            isStirShakenVerificationFailed = false,
            activeManualPrefixes = emptyList(),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = true,
                doNotDisturbEnabled = true,
                blockBuiltInSpamSeries = true
            ),
            isCallerInContacts = true
        )
        assertEquals(SpamScreeningDecision.Allow, decision)
    }

    @Test
    fun `do not disturb rejects hidden private callers`() {
        val decision = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = null,
            isHiddenOrPrivateCaller = true,
            isStirShakenVerificationFailed = false,
            activeManualPrefixes = emptyList(),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = false,
                doNotDisturbEnabled = true
            ),
            isCallerInContacts = false
        )
        assertTrue(decision is SpamScreeningDecision.Block)
        assertEquals("DND", (decision as SpamScreeningDecision.Block).matchedPrefix)
    }

    @Test
    fun `exact phone number in blocked list rejects matching incoming call`() {
        val exactRule = BlockedPrefixEntity(
            id = 12L,
            prefixString = "+1 (415) 555-0199",
            normalizedPrefix = "+14155550199",
            label = "",
            isActive = true,
            matchType = com.example.data.local.BlockedMatchType.EXACT_NUMBER
        )
        val blocked = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = "415-555-0199",
            isHiddenOrPrivateCaller = false,
            isStirShakenVerificationFailed = false,
            activeManualPrefixes = listOf(exactRule),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = true,
                blockBuiltInSpamSeries = false
            )
        )
        assertTrue(blocked is SpamScreeningDecision.Block)
        val block = blocked as SpamScreeningDecision.Block
        assertEquals("+1 (415) 555-0199", block.matchedPrefix)
        assertEquals("Blocked Number", block.reasonLabel)

        val otherCaller = SpamBlockerEngine.evaluateIncomingCall(
            rawIncomingNumber = "+14155550198",
            isHiddenOrPrivateCaller = false,
            isStirShakenVerificationFailed = false,
            activeManualPrefixes = listOf(exactRule),
            settings = SpamBlockerSettings(
                isSpamBlockerEnabled = true,
                blockBuiltInSpamSeries = false
            )
        )
        assertEquals(SpamScreeningDecision.Allow, otherCaller)
    }
}
