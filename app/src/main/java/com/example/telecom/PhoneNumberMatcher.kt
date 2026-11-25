package com.example.telecom

import com.example.data.local.BlockedPrefixEntity

/**
 * Pure utility responsible for normalizing phone numbers/prefixes and matching
 * incoming caller IDs against active blocked series rules.
 */
object PhoneNumberMatcher {

    private const val MIN_PREFIX_DIGITS = 2

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
     * Validates that the input contains at least [MIN_PREFIX_DIGITS] digits after normalization.
     */
    fun isValidPrefix(raw: String): Boolean {
        val normalized = normalize(raw)
        val digitCount = normalized.count { it.isDigit() }
        return digitCount >= MIN_PREFIX_DIGITS
    }

    /**
     * Checks whether an incoming phone number starts with any active stored prefix.
     *
     * Matching behavior:
     * - If a stored prefix starts with '+' (e.g., "+91140"), it matches incoming numbers
     *   starting with "+91140", as well as carrier-delivered numbers omitting '+' ("91140...").
     * - If a stored prefix does NOT start with '+' (e.g., "0114"), it matches incoming numbers
     *   starting strictly with "0114" to avoid accidental collisions with international country codes.
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

        return activePrefixes.firstOrNull { rule ->
            if (!rule.isActive) return@firstOrNull false
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
}
