package com.example

import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.data.repository.DeviceContactEntry
import com.example.ui.CallHistoryFilter
import com.example.ui.SubstringSearchEngine
import com.example.ui.SuggestedContactsMatcher
import com.example.ui.filterDeviceCallLogs
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
    fun filterContacts_matchesSubstringsAnywhereInsideNameAndNumber() {
        // Mid-word substring via indexOf ("erce" inside "Alice Mercer")
        val midWord = SuggestedContactsMatcher.filterContacts(sampleContacts, "erce")
        assertEquals(listOf(1L), midWord.map { it.id })

        // Multi-token Regex.find ("za lin" inside "Zara Clinic")
        val multiToken = SuggestedContactsMatcher.filterContacts(sampleContacts, "za lin")
        assertEquals(listOf(3L), multiToken.map { it.id })

        // Flexible Regex.find across punctuation ("55501" inside "+1 (415) 555-0192")
        val acrossDash = SuggestedContactsMatcher.filterContacts(sampleContacts, "55501")
        assertEquals(listOf(1L), acrossDash.map { it.id })
    }

    @Test
    fun substringSearchEngine_returnsExactHighlightRangesForTextPhoneAndT9() {
        val textMatch = SubstringSearchEngine.findTextMatches("Alice Mercer", "erce")
        assertTrue(textMatch.isMatch)
        assertEquals(listOf(7..10), textMatch.ranges)

        val multiTokenMatch = SubstringSearchEngine.findTextMatches("Alice Mercer", "ali mer")
        assertTrue(multiTokenMatch.isMatch)
        assertEquals(listOf(0..2, 6..8), multiTokenMatch.ranges)

        // "+1 (415) 555-0192" -> "555-01" spans indices 9..14
        val phoneMatch = SubstringSearchEngine.findPhoneNumberMatch("+1 (415) 555-0192", "55501")
        assertTrue(phoneMatch.isMatch)
        assertEquals(listOf(9..14), phoneMatch.ranges)

        // T9 "372" -> E(3) R(7) C(2) matches "erc" inside "Alice Mercer" at indices 7..9
        val t9Match = SubstringSearchEngine.findT9NameMatch("Alice Mercer", "372")
        assertTrue(t9Match.isMatch)
        assertEquals(listOf(7..9), t9Match.ranges)
    }

    @Test
    fun filterDeviceCallLogs_supportsMidWordMultiTokenAndFlexibleDigitSubstrings() {
        val byMidName = filterDeviceCallLogs(sampleRecentCalls, CallHistoryFilter.ALL, "ndon")
        assertEquals(listOf(12L), byMidName.map { it.id })

        val byMultiToken = filterDeviceCallLogs(sampleRecentCalls, CallHistoryFilter.ALL, "lon off")
        assertEquals(listOf(12L), byMultiToken.map { it.id })

        val byDigitsAcrossSpaces = filterDeviceCallLogs(sampleRecentCalls, CallHistoryFilter.ALL, "207946")
        assertEquals(listOf(12L), byDigitsAcrossSpaces.map { it.id })
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
    fun buildKeypadSuggestions_matchesByDigitsAndMidWordT9WithHighlightRanges() {
        // T9 "254" -> A(2) L(5) I(4) matches "Alice Mercer"
        val t9Matches = SuggestedContactsMatcher.buildKeypadSuggestions(
            dialerInput = "254",
            contacts = sampleContacts,
            recentCalls = sampleRecentCalls,
            maxResults = 5
        )
        val aliceMatch = t9Matches.firstOrNull { it.displayName == "Alice Mercer" }
        assertTrue(aliceMatch != null)
        assertEquals(listOf(0..2), aliceMatch!!.nameMatchRanges)

        // Mid-word T9 "372" -> E(3) R(7) C(2) matches "erc" inside "Alice Mercer"
        val midWordT9 = SuggestedContactsMatcher.buildKeypadSuggestions(
            dialerInput = "372",
            contacts = sampleContacts,
            recentCalls = sampleRecentCalls,
            maxResults = 5
        )
        assertEquals(listOf("Alice Mercer"), midWordT9.map { it.displayName })
        assertEquals(listOf(7..9), midWordT9.first().nameMatchRanges)

        // Digit match across formatting "+44 20 7946 0101" with range highlighted
        val digitMatches = SuggestedContactsMatcher.buildKeypadSuggestions(
            dialerInput = "794601",
            contacts = sampleContacts,
            recentCalls = sampleRecentCalls,
            maxResults = 5
        )
        assertEquals(listOf("London Office"), digitMatches.map { it.displayName })
        assertTrue(digitMatches.first().numberMatchRanges.isNotEmpty())
    }
}
