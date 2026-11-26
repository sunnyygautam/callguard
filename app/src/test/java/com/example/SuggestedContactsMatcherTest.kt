package com.example

import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.data.repository.DeviceContactEntry
import com.example.ui.SuggestedContactsMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestedContactsMatcherTest {

    private val sampleContacts = listOf(
        DeviceContactEntry(
            id = 1L,
            displayName = "Alice Mercer",
            phoneNumber = "+1 (415) 555-0192",
            normalizedNumber = "+14155550192"
        ),
        DeviceContactEntry(
            id = 2L,
            displayName = "Brian Patel",
            phoneNumber = "+91 98765-43210",
            normalizedNumber = "+919876543210"
        ),
        DeviceContactEntry(
            id = 3L,
            displayName = "Zara Clinic",
            phoneNumber = "800-555-2400",
            normalizedNumber = "8005552400"
        )
    )

    private val sampleRecentCalls = listOf(
        DeviceCallLogEntry(
            id = 10L,
            number = "+1 (415) 555-0192",
            cachedName = "Alice Mercer",
            type = DeviceCallType.INCOMING,
            dateMillis = 2_000L,
            durationSeconds = 95L
        ),
        DeviceCallLogEntry(
            id = 11L,
            number = "+1 (415) 555-0192",
            cachedName = "Alice Mercer",
            type = DeviceCallType.OUTGOING,
            dateMillis = 1_900L,
            durationSeconds = 40L
        ),
        DeviceCallLogEntry(
            id = 12L,
            number = "+44 20 7946 0101",
            cachedName = "London Office",
            type = DeviceCallType.MISSED,
            dateMillis = 1_800L,
            durationSeconds = 0L
        )
    )

    @Test
    fun filterContacts_matchesNameCaseInsensitiveAndNormalizedDigits() {
        val byName = SuggestedContactsMatcher.filterContacts(sampleContacts, "mercer")
        assertEquals(listOf(1L), byName.map { it.id })

        val byFormattedNumber = SuggestedContactsMatcher.filterContacts(sampleContacts, "98765 432")
        assertEquals(listOf(2L), byFormattedNumber.map { it.id })

        val blankQuery = SuggestedContactsMatcher.filterContacts(sampleContacts, "   ")
        assertEquals(3, blankQuery.size)
    }

    @Test
    fun toT9Digits_mapsLettersAccurately() {
        assertEquals("25423", SuggestedContactsMatcher.toT9Digits("Alice"))
        assertEquals("2742672835", SuggestedContactsMatcher.toT9Digits("Brian Patel"))
    }

    @Test
    fun buildKeypadSuggestions_whenIdle_returnsDeduplicatedRecentsThenContacts() {
        val suggestions = SuggestedContactsMatcher.buildKeypadSuggestions(
            dialerInput = "",
            contacts = sampleContacts,
            recentCalls = sampleRecentCalls,
            maxResults = 6
        )

        // Alice should appear once (deduplicated from recents + contacts), followed by London Office,
        // then remaining contacts Brian Patel and Zara Clinic.
        assertEquals(4, suggestions.size)
        assertEquals("Alice Mercer", suggestions[0].displayName)
        assertTrue(suggestions[0].isFromRecentCall)
        assertEquals("London Office", suggestions[1].displayName)
        assertTrue(suggestions[1].isFromRecentCall)
        assertEquals("Brian Patel", suggestions[2].displayName)
        assertFalse(suggestions[2].isFromRecentCall)
        assertEquals("Zara Clinic", suggestions[3].displayName)
        assertFalse(suggestions[3].isFromRecentCall)
    }

    @Test
    fun buildKeypadSuggestions_matchesByDigitsAndT9Name() {
        // T9 "254" -> A(2) L(5) I(4) matches "Alice Mercer"
        val t9Matches = SuggestedContactsMatcher.buildKeypadSuggestions(
            dialerInput = "254",
            contacts = sampleContacts,
            recentCalls = sampleRecentCalls,
            maxResults = 5
        )
        assertTrue(t9Matches.any { it.displayName == "Alice Mercer" })

        // Digit match across formatting "+44 20 7946"
        val digitMatches = SuggestedContactsMatcher.buildKeypadSuggestions(
            dialerInput = "7946",
            contacts = sampleContacts,
            recentCalls = sampleRecentCalls,
            maxResults = 5
        )
        assertEquals(listOf("London Office"), digitMatches.map { it.displayName })
    }
}
