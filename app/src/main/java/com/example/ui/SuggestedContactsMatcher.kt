package com.example.ui

import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceContactEntry
import com.example.telecom.PhoneNumberMatcher

/**
 * Suggested contact/caller item displayed in the Keypad tab's top Suggested section,
 * including exact matched substring ranges for visual highlighting.
 */
data class KeypadSuggestion(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val isFromRecentCall: Boolean,
    val nameMatchRanges: List<IntRange> = emptyList(),
    val numberMatchRanges: List<IntRange> = emptyList()
)

/**
 * Pure filtering and smart substring/T9/regex suggestion utilities for the Home Contacts search
 * and Keypad top Suggested section.
 */
object SuggestedContactsMatcher {

    /**
     * Filters and ranks device contacts using [SubstringSearchEngine] (`indexOf`, `Regex.find`,
     * multi-token matching, and separator-agnostic phone regexes) to find substrings anywhere
     * inside contact names or phone numbers.
     */
    fun filterContacts(
        contacts: List<DeviceContactEntry>,
        query: String
    ): List<DeviceContactEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return contacts

        return contacts.mapNotNull { contact ->
            val nameMatch = SubstringSearchEngine.findTextMatches(contact.displayName, trimmed)
            val phoneMatch = SubstringSearchEngine.findPhoneNumberMatch(contact.phoneNumber, trimmed)
            val bestRank = minOf(nameMatch.rank, phoneMatch.rank)
            if (nameMatch.isMatch || phoneMatch.isMatch) {
                contact to bestRank
            } else {
                null
            }
        }
            .sortedBy { it.second }
            .map { it.first }
    }

    /**
     * Builds smart caller suggestions for the Keypad tab's top Suggested section:
     * - When [dialerInput] is blank: prioritizes unique recent callers, then fills remaining slots
     *   with device contacts.
     * - When [dialerInput] has digits: uses [SubstringSearchEngine] (`indexOf` & `Regex.find`) to
     *   find digit substrings anywhere inside formatted/normalized phone numbers AND T9 substrings
     *   anywhere inside contact names (e.g., `"372"` -> `"erc"` in `"Alice Mercer"`), returning
     *   relevance-ranked results with exact highlight spans.
     */
    fun buildKeypadSuggestions(
        dialerInput: String,
        contacts: List<DeviceContactEntry>,
        recentCalls: List<DeviceCallLogEntry>,
        maxResults: Int = 6
    ): List<KeypadSuggestion> {
        val candidates = buildDeduplicatedCandidates(contacts, recentCalls)
        if (candidates.isEmpty()) return emptyList()

        val trimmedInput = dialerInput.trim()
        if (trimmedInput.isEmpty()) {
            return candidates.take(maxResults)
        }

        return candidates.mapIndexedNotNull { originalIndex, item ->
            val phoneMatch = SubstringSearchEngine.findPhoneNumberMatch(item.phoneNumber, trimmedInput)
            val t9NameMatch = SubstringSearchEngine.findT9NameMatch(item.displayName, trimmedInput)
            val directNameMatch = SubstringSearchEngine.findTextMatches(item.displayName, trimmedInput)

            val resolvedNameRanges = when {
                directNameMatch.isMatch -> directNameMatch.ranges
                t9NameMatch.isMatch -> t9NameMatch.ranges
                else -> emptyList()
            }
            val resolvedNameRank = minOf(directNameMatch.rank, t9NameMatch.rank)

            if (phoneMatch.isMatch || resolvedNameRanges.isNotEmpty()) {
                val recencyBonus = if (item.isFromRecentCall) 0 else 4
                val totalRank = minOf(phoneMatch.rank, resolvedNameRank) + recencyBonus
                Triple(
                    item.copy(
                        nameMatchRanges = resolvedNameRanges,
                        numberMatchRanges = phoneMatch.ranges
                    ),
                    totalRank,
                    originalIndex
                )
            } else {
                null
            }
        }
            .sortedWith(compareBy<Triple<KeypadSuggestion, Int, Int>> { it.second }.thenBy { it.third })
            .take(maxResults)
            .map { it.first }
    }

    /**
     * Converts alphabetic characters in a name to their standard T9 telephone keypad digits.
     */
    internal fun toT9Digits(text: String): String = buildString(text.length) {
        for (ch in text) {
            SubstringSearchEngine.charToT9Digit(ch)?.let(::append)
        }
    }

    private fun buildDeduplicatedCandidates(
        contacts: List<DeviceContactEntry>,
        recentCalls: List<DeviceCallLogEntry>
    ): List<KeypadSuggestion> {
        val results = mutableListOf<KeypadSuggestion>()
        val seenNumbers = HashSet<String>()

        // 1. Prioritize unique recent callers first
        for (call in recentCalls) {
            if (call.number.isBlank()) continue
            val normalized = PhoneNumberMatcher.normalize(call.number)
            val key = normalized.ifEmpty { call.number }
            if (!seenNumbers.add(key)) continue

            val matchingContactName = contacts.firstOrNull {
                it.normalizedNumber.isNotEmpty() && it.normalizedNumber == normalized
            }?.displayName

            results.add(
                KeypadSuggestion(
                    id = "recent_${call.id}",
                    displayName = call.cachedName ?: matchingContactName ?: call.number,
                    phoneNumber = call.number,
                    normalizedNumber = normalized,
                    isFromRecentCall = true
                )
            )
        }

        // 2. Append remaining device contacts
        for (contact in contacts) {
            val key = contact.normalizedNumber.ifEmpty { contact.phoneNumber }
            if (!seenNumbers.add(key)) continue

            results.add(
                KeypadSuggestion(
                    id = "contact_${contact.id}",
                    displayName = contact.displayName,
                    phoneNumber = contact.phoneNumber,
                    normalizedNumber = contact.normalizedNumber,
                    isFromRecentCall = false
                )
            )
        }

        return results
    }
}
