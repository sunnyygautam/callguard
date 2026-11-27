package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.telecom.PhoneNumberMatcher

/**
 * Result of matching a search query anywhere inside a candidate string (name or phone number),
 * preserving exact character ranges for visual highlighting and relevance ranking.
 */
data class SubstringMatch(
    val ranges: List<IntRange>,
    val rank: Int
) {
    val isMatch: Boolean
        get() = ranges.isNotEmpty()
}

/**
 * Enhanced substring search engine powering the Home and Keypad tabs.
 *
 * Uses [String.indexOf], compiled [Regex], and [Regex.find] to locate substrings anywhere
 * inside contact names, formatted phone numbers, normalized digit strings, and T9 name
 * representations (start, word boundary, middle, or end), and returns exact highlight spans.
 */
object SubstringSearchEngine {

    /**
     * Finds case-insensitive substring match ranges anywhere inside [text] for [query]:
     * 1. Direct substring match via [String.indexOf] (handles mid-word & exact multi-char spans).
     * 2. Regex-based multi-token match via [Regex.find] (e.g., "ali mer" matches inside "Alice Mercer").
     * 3. Flexible regex phone-digit match when [query] contains digits (matches across spaces/hyphens).
     */
    fun findTextMatches(text: String, query: String): SubstringMatch {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || text.isEmpty()) {
            return SubstringMatch(emptyList(), Int.MAX_VALUE)
        }

        // 1. Fast direct substring lookup anywhere inside text via indexOf
        val directIdx = text.indexOf(trimmed, ignoreCase = true)
        if (directIdx >= 0) {
            val range = directIdx until (directIdx + trimmed.length)
            return SubstringMatch(
                ranges = listOf(range),
                rank = computePositionRank(text, directIdx, isExactDirect = true)
            )
        }

        // 2. Multi-token regex search using Regex.find (all space-separated tokens must match anywhere)
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size > 1) {
            val matchedRanges = mutableListOf<IntRange>()
            var allMatched = true
            for (token in tokens) {
                val tokenRegex = Regex(Regex.escape(token), RegexOption.IGNORE_CASE)
                val match = tokenRegex.find(text)
                if (match != null) {
                    matchedRanges.add(match.range)
                } else {
                    allMatched = false
                    break
                }
            }
            if (allMatched && matchedRanges.isNotEmpty()) {
                val merged = mergeRanges(matchedRanges)
                return SubstringMatch(
                    ranges = merged,
                    rank = computePositionRank(text, merged.first().first, isExactDirect = false) + 5
                )
            }
        }

        // 3. Phone-digit flexible regex match via Regex.find (matches digits anywhere across punctuation)
        val phoneMatch = findPhoneNumberMatch(text, trimmed)
        if (phoneMatch.isMatch) {
            return phoneMatch
        }

        return SubstringMatch(emptyList(), Int.MAX_VALUE)
    }

    /**
     * Uses a compiled [Regex] with [Regex.find] to locate a sequence of query digits anywhere
     * inside a formatted phone number (ignoring spaces, dashes, parentheses, and '+' symbols).
     *
     * Example: query `"55501"` matches `"555-01"` inside `"+1 (415) 555-0192"`.
     */
    fun findPhoneNumberMatch(formattedPhone: String, query: String): SubstringMatch {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || formattedPhone.isEmpty()) {
            return SubstringMatch(emptyList(), Int.MAX_VALUE)
        }

        // Direct substring check via indexOf first (supports symbols like "+1", "(415)", "-01")
        val directIdx = formattedPhone.indexOf(trimmed, ignoreCase = true)
        if (directIdx >= 0) {
            val range = directIdx until (directIdx + trimmed.length)
            return SubstringMatch(
                ranges = listOf(range),
                rank = computePositionRank(formattedPhone, directIdx, isExactDirect = true)
            )
        }

        // Extract digits and match anywhere inside formattedPhone ignoring non-digit separators
        val digitsOnly = PhoneNumberMatcher.normalize(trimmed).removePrefix("+")
        if (digitsOnly.isEmpty()) {
            return SubstringMatch(emptyList(), Int.MAX_VALUE)
        }

        val flexiblePattern = digitsOnly.toCharArray().joinToString(separator = "[^0-9]*") { ch ->
            Regex.escape(ch.toString())
        }
        val matchResult = Regex(flexiblePattern).find(formattedPhone)
        if (matchResult != null) {
            return SubstringMatch(
                ranges = listOf(matchResult.range),
                rank = computePositionRank(formattedPhone, matchResult.range.first, isExactDirect = false) + 2
            )
        }

        return SubstringMatch(emptyList(), Int.MAX_VALUE)
    }

    /**
     * Maps [displayName] to T9 digits while tracking original character indices, then uses
     * [String.indexOf] to find [dialerDigits] anywhere inside the name (start, word start, or
     * mid-word) and maps the matched T9 substring back to its exact [IntRange] in [displayName].
     *
     * Example: dialing `"372"` (`ERC`) matches `"erc"` inside `"Alice Mercer"`.
     */
    fun findT9NameMatch(displayName: String, dialerDigits: String): SubstringMatch {
        val cleanDigits = dialerDigits.filter { it.isDigit() }
        if (cleanDigits.isEmpty() || displayName.isEmpty()) {
            return SubstringMatch(emptyList(), Int.MAX_VALUE)
        }

        val t9Builder = StringBuilder(displayName.length)
        val charIndices = IntArray(displayName.length)
        var t9Length = 0

        for (i in displayName.indices) {
            val digit = charToT9Digit(displayName[i])
            if (digit != null) {
                t9Builder.append(digit)
                charIndices[t9Length++] = i
            }
        }

        if (t9Length < cleanDigits.length) {
            return SubstringMatch(emptyList(), Int.MAX_VALUE)
        }

        val t9String = t9Builder.toString()
        val matchStartInT9 = t9String.indexOf(cleanDigits)
        if (matchStartInT9 < 0) {
            return SubstringMatch(emptyList(), Int.MAX_VALUE)
        }

        val startOriginalIdx = charIndices[matchStartInT9]
        val endOriginalIdx = charIndices[matchStartInT9 + cleanDigits.length - 1]
        val range = startOriginalIdx..endOriginalIdx

        return SubstringMatch(
            ranges = listOf(range),
            rank = computePositionRank(displayName, startOriginalIdx, isExactDirect = false) + 10
        )
    }

    /**
     * Evaluates whether a contact (name + phone number) matches [query] anywhere inside either field.
     */
    fun matchesContact(
        displayName: String,
        phoneNumber: String,
        query: String
    ): Boolean {
        return findTextMatches(displayName, query).isMatch ||
            findPhoneNumberMatch(phoneNumber, query).isMatch
    }

    /**
     * Builds a Jetpack Compose [AnnotatedString] highlighting all matched character [ranges]
     * inside [text] for clear visual feedback in Home and Keypad search results.
     */
    fun buildHighlightedText(
        text: String,
        ranges: List<IntRange>,
        highlightColor: Color,
        highlightBackground: Color
    ): AnnotatedString {
        if (ranges.isEmpty() || text.isEmpty()) {
            return AnnotatedString(text)
        }
        val merged = mergeRanges(ranges)
        return buildAnnotatedString {
            append(text)
            for (range in merged) {
                val safeStart = range.first.coerceIn(0, text.length)
                val safeEndExclusive = (range.last + 1).coerceIn(safeStart, text.length)
                if (safeStart < safeEndExclusive) {
                    addStyle(
                        style = SpanStyle(
                            color = highlightColor,
                            background = highlightBackground,
                            fontWeight = FontWeight.Bold
                        ),
                        start = safeStart,
                        end = safeEndExclusive
                    )
                }
            }
        }
    }

    internal fun charToT9Digit(ch: Char): Char? = when (ch.lowercaseChar()) {
        'a', 'b', 'c' -> '2'
        'd', 'e', 'f' -> '3'
        'g', 'h', 'i' -> '4'
        'j', 'k', 'l' -> '5'
        'm', 'n', 'o' -> '6'
        'p', 'q', 'r', 's' -> '7'
        't', 'u', 'v' -> '8'
        'w', 'x', 'y', 'z' -> '9'
        in '0'..'9' -> ch
        else -> null
    }

    private fun computePositionRank(text: String, startIndex: Int, isExactDirect: Boolean): Int {
        val base = when {
            startIndex == 0 -> 0
            startIndex > 0 && !text[startIndex - 1].isLetterOrDigit() -> 20
            else -> 40 + startIndex.coerceAtMost(50)
        }
        return if (isExactDirect) base else base + 3
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.size <= 1) return ranges
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val prev = merged.last()
            val curr = sorted[i]
            if (curr.first <= prev.last + 1) {
                merged[merged.lastIndex] = prev.first..maxOf(prev.last, curr.last)
            } else {
                merged.add(curr)
            }
        }
        return merged
    }
}
