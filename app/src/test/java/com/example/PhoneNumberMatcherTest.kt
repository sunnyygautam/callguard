package com.example

import com.example.data.local.BlockedPrefixEntity
import com.example.telecom.PhoneNumberMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberMatcherTest {

    @Test
    fun `normalize strips spaces dashes parentheses and keeps leading plus`() {
        assertEquals("+911408829911", PhoneNumberMatcher.normalize("+91 140-882-9911"))
        assertEquals("+18005551212", PhoneNumberMatcher.normalize("  +1 (800) 555.1212 "))
        assertEquals("0114223344", PhoneNumberMatcher.normalize("(0114) 223-344"))
        assertEquals("", PhoneNumberMatcher.normalize("   ---  "))
    }

    @Test
    fun `isValidPrefix requires at least two digits`() {
        assertTrue(PhoneNumberMatcher.isValidPrefix("+91140"))
        assertTrue(PhoneNumberMatcher.isValidPrefix("01"))
        assertFalse(PhoneNumberMatcher.isValidPrefix("+9"))
        assertFalse(PhoneNumberMatcher.isValidPrefix("+"))
        assertFalse(PhoneNumberMatcher.isValidPrefix("   "))
    }

    @Test
    fun `findMatchingPrefix blocks active matching rules and ignores inactive ones`() {
        val rules = listOf(
            BlockedPrefixEntity(
                id = 1L,
                prefixString = "+91140",
                normalizedPrefix = "+91140",
                label = "Telemarketer",
                isActive = true
            ),
            BlockedPrefixEntity(
                id = 2L,
                prefixString = "+1800",
                normalizedPrefix = "+1800",
                label = "Toll-Free",
                isActive = false
            ),
            BlockedPrefixEntity(
                id = 3L,
                prefixString = "0114",
                normalizedPrefix = "0114",
                label = "Promotional",
                isActive = true
            )
        )

        // Matches formatted international incoming call
        val matchedTelemarketer = PhoneNumberMatcher.findMatchingPrefix("+91 140-555-0199", rules)
        assertNotNull(matchedTelemarketer)
        assertEquals("+91140", matchedTelemarketer?.prefixString)

        // Matches carrier-stripped leading plus when stored rule has '+'
        val matchedWithoutPlus = PhoneNumberMatcher.findMatchingPrefix("911409876543", rules)
        assertNotNull(matchedWithoutPlus)
        assertEquals("+91140", matchedWithoutPlus?.prefixString)

        // Does not block paused/inactive rule
        assertNull(PhoneNumberMatcher.findMatchingPrefix("+1-800-555-0100", rules))

        // Matches local prefix cleanly
        val matchedLocal = PhoneNumberMatcher.findMatchingPrefix("0114-998877", rules)
        assertNotNull(matchedLocal)
        assertEquals("0114", matchedLocal?.prefixString)

        // Allows unrelated number
        assertNull(PhoneNumberMatcher.findMatchingPrefix("+1-415-555-0199", rules))
    }

    @Test
    fun `exact phone number rules match exact or local 10-digit subscriber without false prefix hits`() {
        assertTrue(PhoneNumberMatcher.isValidExactNumber("+1 (415) 555-0192"))
        assertTrue(PhoneNumberMatcher.isValidExactNumber("5550192"))
        assertFalse(PhoneNumberMatcher.isValidExactNumber("+1800"))

        val rules = listOf(
            BlockedPrefixEntity(
                id = 10L,
                prefixString = "+14155550192",
                normalizedPrefix = "+14155550192",
                label = "Harassing Caller",
                isActive = true,
                matchType = com.example.data.local.BlockedMatchType.EXACT_NUMBER
            )
        )

        // Exact formatted E.164 match
        val exact = PhoneNumberMatcher.findMatchingPrefix("+1 (415) 555-0192", rules)
        assertNotNull(exact)
        assertEquals("Harassing Caller", exact?.label)

        // Carrier-delivered 10-digit local format matches the E.164 exact entry
        val localMatch = PhoneNumberMatcher.findMatchingPrefix("415-555-0192", rules)
        assertNotNull(localMatch)

        // Different number sharing prefix must NOT be blocked by an EXACT_NUMBER rule
        assertNull(PhoneNumberMatcher.findMatchingPrefix("+14155550193", rules))
        assertNull(PhoneNumberMatcher.findMatchingPrefix("+141555501929", rules))
    }
}
