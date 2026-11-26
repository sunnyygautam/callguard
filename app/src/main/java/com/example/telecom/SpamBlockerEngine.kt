package com.example.telecom

import com.example.data.local.BlockedPrefixEntity
import com.example.data.repository.SpamBlockerSettings

/**
 * Screening decision produced by [SpamBlockerEngine] for an incoming call.
 */
sealed interface SpamScreeningDecision {
    data object Allow : SpamScreeningDecision

    data class Block(
        val matchedPrefix: String,
        val reasonLabel: String,
        val silenceOnly: Boolean
    ) : SpamScreeningDecision
}

/**
 * Pure, testable decision engine evaluating an incoming call against the user's
 * [SpamBlockerSettings], custom manual prefix rules, carrier STIR/SHAKEN verification,
 * hidden-caller presentation flags, and built-in telemarketer spam series.
 */
object SpamBlockerEngine {

    /**
     * Curated built-in telemarketer and promotional robocall series enabled when
     * [SpamBlockerSettings.blockBuiltInSpamSeries] is active.
     */
    val BUILT_IN_SPAM_SERIES: List<BlockedPrefixEntity> = listOf(
        BlockedPrefixEntity(
            id = -1L,
            prefixString = "+91140",
            normalizedPrefix = "+91140",
            label = "Telemarketer Series",
            isActive = true
        ),
        BlockedPrefixEntity(
            id = -2L,
            prefixString = "+1800",
            normalizedPrefix = "+1800",
            label = "Toll-Free Spam",
            isActive = true
        ),
        BlockedPrefixEntity(
            id = -3L,
            prefixString = "+1888",
            normalizedPrefix = "+1888",
            label = "Toll-Free Spam",
            isActive = true
        ),
        BlockedPrefixEntity(
            id = -4L,
            prefixString = "+1877",
            normalizedPrefix = "+1877",
            label = "Toll-Free Spam",
            isActive = true
        ),
        BlockedPrefixEntity(
            id = -5L,
            prefixString = "+1866",
            normalizedPrefix = "+1866",
            label = "Toll-Free Spam",
            isActive = true
        ),
        BlockedPrefixEntity(
            id = -6L,
            prefixString = "+1855",
            normalizedPrefix = "+1855",
            label = "Toll-Free Spam",
            isActive = true
        ),
        BlockedPrefixEntity(
            id = -7L,
            prefixString = "+1844",
            normalizedPrefix = "+1844",
            label = "Toll-Free Spam",
            isActive = true
        ),
        BlockedPrefixEntity(
            id = -8L,
            prefixString = "+1833",
            normalizedPrefix = "+1833",
            label = "Toll-Free Spam",
            isActive = true
        )
    )

    fun evaluateIncomingCall(
        rawIncomingNumber: String?,
        isHiddenOrPrivateCaller: Boolean,
        isStirShakenVerificationFailed: Boolean,
        activeManualPrefixes: List<BlockedPrefixEntity>,
        settings: SpamBlockerSettings,
        isCallerInContacts: Boolean = false
    ): SpamScreeningDecision {
        // 0. Do Not Disturb (Contacts Only) mode: automatically reject all calls not in Contacts
        if (settings.doNotDisturbEnabled) {
            val isVerifiedContact = isCallerInContacts &&
                !isHiddenOrPrivateCaller &&
                !rawIncomingNumber.isNullOrBlank()
            if (!isVerifiedContact) {
                return SpamScreeningDecision.Block(
                    matchedPrefix = "DND",
                    reasonLabel = "Do Not Disturb (Not in Contacts)",
                    silenceOnly = false
                )
            }
        }

        // 1. Master spam blocker switch check
        if (!settings.isSpamBlockerEnabled) {
            return SpamScreeningDecision.Allow
        }

        // 2. Private / Hidden / Restricted caller check
        if (settings.blockHiddenPrivateCalls &&
            (isHiddenOrPrivateCaller || rawIncomingNumber.isNullOrBlank())
        ) {
            return SpamScreeningDecision.Block(
                matchedPrefix = "Private",
                reasonLabel = "Hidden / Unknown Caller",
                silenceOnly = settings.silenceOnlyMode
            )
        }

        // 3. Carrier STIR/SHAKEN spoofed caller-ID check (Android 11+)
        if (settings.blockSpoofedStirShakenCalls && isStirShakenVerificationFailed) {
            return SpamScreeningDecision.Block(
                matchedPrefix = "STIR/SHAKEN",
                reasonLabel = "Suspected Spoofed Spam",
                silenceOnly = settings.silenceOnlyMode
            )
        }

        // 4. User's active Blocked List (specific phone numbers & series/prefix rules)
        val matchedManualRule = PhoneNumberMatcher.findMatchingPrefix(
            incomingRawNumber = rawIncomingNumber,
            activePrefixes = activeManualPrefixes
        )
        if (matchedManualRule != null) {
            val defaultReason = if (matchedManualRule.isExactNumber) {
                "Blocked Number"
            } else {
                "Blocked Prefix"
            }
            return SpamScreeningDecision.Block(
                matchedPrefix = matchedManualRule.prefixString,
                reasonLabel = matchedManualRule.label.ifBlank { defaultReason },
                silenceOnly = settings.silenceOnlyMode
            )
        }

        // 5. Built-in telemarketer & toll-free spam series check
        if (settings.blockBuiltInSpamSeries) {
            val matchedBuiltInRule = PhoneNumberMatcher.findMatchingPrefix(
                incomingRawNumber = rawIncomingNumber,
                activePrefixes = BUILT_IN_SPAM_SERIES
            )
            if (matchedBuiltInRule != null) {
                return SpamScreeningDecision.Block(
                    matchedPrefix = matchedBuiltInRule.prefixString,
                    reasonLabel = matchedBuiltInRule.label,
                    silenceOnly = settings.silenceOnlyMode
                )
            }
        }

        return SpamScreeningDecision.Allow
    }
}
