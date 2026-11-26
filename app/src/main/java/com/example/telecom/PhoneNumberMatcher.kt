package com.example.telecom

import com.example.data.local.BlockedPrefixEntity

/**
 * Pure utility responsible for normalizing phone numbers/prefixes and matching
 * incoming caller IDs against active blocked series rules.
 */
object PhoneNumberMatcher {

    private const val MIN_PREFIX_DIGITS = 2
    private const val MIN_EXACT_NUMBER_DIGITS = 7
    private const val SUBSCRIBER_SUFFIX_DIGITS = 10

    /**
     * Normalizes a raw phone number or prefix string by trimming whitespace and stripping
     * separators (spaces, dashes, parentheses, dots) while preserving a single leading '+'.
     *
     * Examples:
     * - "+91 140-882-9911" -> "+911408829911"
     * - "(0114) 223 4455"  -> "01142234455"
     * - "  +1 800 "        -> "+1800"
     */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val hasLeadingPlus = trimmed.startsWith("+")
        val digitsOnly = buildString(trimmed.length) {
            for (ch in trimmed) {
                if (ch.isDigit()) append(ch)
            }
        }

        if (digitsOnly.isEmpty()) return ""
        return if (hasLeadingPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Validates that a prefix input contains at least [MIN_PREFIX_DIGITS] digits after normalization.
     */
    fun isValidPrefix(raw: String): Boolean {
        val normalized = normalize(raw)
        val digitCount = normalized.count { it.isDigit() }
        return digitCount >= MIN_PREFIX_DIGITS
    }

    /**
     * Validates that a specific phone number input contains at least [MIN_EXACT_NUMBER_DIGITS] digits.
     */
    fun isValidExactNumber(raw: String): Boolean {
        val normalized = normalize(raw)
        val digitCount = normalized.count { it.isDigit() }
        return digitCount >= MIN_EXACT_NUMBER_DIGITS
    }

    /**
     * Checks whether an incoming phone number matches any active entry in the Room Blocked List
     * (evaluating specific exact phone number rules first, followed by prefix/series rules).
     *
     * Matching behavior:
     * - **Exact Number rules**: matches identical normalized numbers, identical digit sequences
     *   with or without leading '+', or identical trailing 10-digit subscriber numbers when
     *   one side includes a country code (e.g., "+14155550192" matches "4155550192").
     * - **Prefix rules**: if stored prefix starts with '+' (e.g., "+91140"), matches incoming
     *   numbers starting with "+91140" or carrier-delivered "91140..."; otherwise strictly matches
     *   numbers starting with the local prefix (e.g., "0114").
     *
     * @return The first matching active [BlockedPrefixEntity], or null if the call should be allowed.
     */
    fun findMatchingPrefix(
        incomingRawNumber: String?,
        activePrefixes: List<BlockedPrefixEntity>
    ): BlockedPrefixEntity? {
        if (incomingRawNumber.isNullOrBlank() || activePrefixes.isEmpty()) return null

        val normalizedIncoming = normalize(incomingRawNumber)
        if (normalizedIncoming.isEmpty()) return null
        val incomingDigits = normalizedIncoming.removePrefix("+")

        // 1. Check exact phone number entries first
        val exactMatch = activePrefixes.firstOrNull { rule ->
            if (!rule.isActive || !rule.isExactNumber) return@firstOrNull false
            matchesExactNumber(
                normalizedIncoming = normalizedIncoming,
                incomingDigits = incomingDigits,
                ruleNormalized = rule.normalizedPrefix
            )
        }
        if (exactMatch != null) return exactMatch

        // 2. Check prefix/series entries
        return activePrefixes.firstOrNull { rule ->
            if (!rule.isActive || rule.isExactNumber) return@firstOrNull false
            val rulePrefix = rule.normalizedPrefix
            if (rulePrefix.isEmpty()) return@firstOrNull false

            if (rulePrefix.startsWith("+")) {
                normalizedIncoming.startsWith(rulePrefix) ||
                    (!normalizedIncoming.startsWith("+") &&
                        normalizedIncoming.startsWith(rulePrefix.removePrefix("+")))
            } else {
                normalizedIncoming.startsWith(rulePrefix)
            }
        }
    }

    private fun matchesExactNumber(
        normalizedIncoming: String,
        incomingDigits: String,
        ruleNormalized: String
    ): Boolean {
        if (ruleNormalized.isEmpty()) return false
        if (normalizedIncoming == ruleNormalized) return true

        val ruleDigits = ruleNormalized.removePrefix("+")
        if (incomingDigits == ruleDigits) return true

        // Handle local 10-digit vs E.164 country-code formatting (e.g. 4155550192 vs +14155550192)
        if (incomingDigits.length >= SUBSCRIBER_SUFFIX_DIGITS &&
            ruleDigits.length >= SUBSCRIBER_SUFFIX_DIGITS &&
            (incomingDigits.length == SUBSCRIBER_SUFFIX_DIGITS || ruleDigits.length == SUBSCRIBER_SUFFIX_DIGITS)
        ) {
            return incomingDigits.takeLast(SUBSCRIBER_SUFFIX_DIGITS) ==
                ruleDigits.takeLast(SUBSCRIBER_SUFFIX_DIGITS)
        }
        return false
    }
}
