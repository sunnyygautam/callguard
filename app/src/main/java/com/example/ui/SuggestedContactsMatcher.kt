package com.example.ui

import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceContactEntry
import com.example.telecom.PhoneNumberMatcher

/**
 * Suggested contact/caller item displayed in the Keypad tab's top Suggested section.
 */
data class KeypadSuggestion(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val isFromRecentCall: Boolean
)

/**
 * Pure filtering and smart T9/digit suggestion utilities for the Home Contacts search
 * and Keypad top Suggested section.
 */
object SuggestedContactsMatcher {

    /**
     * Filters device contacts by case-insensitive display name OR raw/normalized phone digits.
     */
    fun filterContacts(
        contacts: List<DeviceContactEntry>,
        query: String
    ): List<DeviceContactEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return contacts

        val normalizedQueryDigits = PhoneNumberMatcher.normalize(trimmed).removePrefix("+")
        return contacts.filter { contact ->
            val matchesName = contact.displayName.contains(trimmed, ignoreCase = true)
            val matchesRaw = contact.phoneNumber.contains(trimmed, ignoreCase = true)
            val matchesDigits = normalizedQueryDigits.isNotEmpty() &&
                contact.normalizedNumber.removePrefix("+").contains(normalizedQueryDigits)
            matchesName || matchesRaw || matchesDigits
        }
    }

    /**
     * Builds smart caller suggestions for the Keypad tab's top Suggested section:
     * - When [dialerInput] is blank: prioritizes unique recent callers, then fills remaining slots
     *   with device contacts.
     * - When [dialerInput] has digits: matches contacts and recent callers by phone number digits
     *   OR classic T9 keypad letters (e.g., "254" -> "Alice").
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

        val normalizedDigits = PhoneNumberMatcher.normalize(trimmedInput).removePrefix("+")
        val rawDigitsOnly = trimmedInput.filter { it.isDigit() }

        return candidates.filter { item ->
            val matchesRawNumber = item.phoneNumber.contains(trimmedInput, ignoreCase = true)
            val matchesNormalized = normalizedDigits.isNotEmpty() &&
                item.normalizedNumber.removePrefix("+").contains(normalizedDigits)
            val matchesT9 = rawDigitsOnly.isNotEmpty() &&
                toT9Digits(item.displayName).contains(rawDigitsOnly)
            matchesRawNumber || matchesNormalized || matchesT9
        }.take(maxResults)
    }

    /**
     * Converts alphabetic characters in a name to their standard T9 telephone keypad digits.
     */
    internal fun toT9Digits(text: String): String = buildString(text.length) {
        for (ch in text.lowercase()) {
            when (ch) {
                'a', 'b', 'c' -> append('2')
                'd', 'e', 'f' -> append('3')
                'g', 'h', 'i' -> append('4')
                'j', 'k', 'l' -> append('5')
                'm', 'n', 'o' -> append('6')
                'p', 'q', 'r', 's' -> append('7')
                't', 'u', 'v' -> append('8')
                'w', 'x', 'y', 'z' -> append('9')
                in '0'..'9' -> append(ch)
            }
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
