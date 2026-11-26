package com.example.ml

import com.example.telecom.PhoneNumberMatcher

/**
 * Local definition of a known malicious or high-risk calling pattern used by [SpamMlAnalysisService].
 */
data class MaliciousPatternRule(
    val id: String,
    val name: String,
    val category: String,
    val severityWeight: Double, // 0.0 .. 1.0
    val prefixes: List<String> = emptyList(),
    val regex: Regex? = null
)

/**
 * Matched pattern entry returned in an explainable ML analysis report.
 */
data class MatchedMaliciousPattern(
    val ruleId: String,
    val name: String,
    val category: String,
    val severityWeight: Double
)

/**
 * Curated local catalog of known malicious, telemarketing, robocall, Wangiri/premium-rate,
 * and spoofed caller number patterns evaluated completely on-device with zero network latency.
 */
object MaliciousPatternCatalog {

    val KNOWN_PATTERNS: List<MaliciousPatternRule> = listOf(
        MaliciousPatternRule(
            id = "telemarketer_in_140",
            name = "Registered Telemarketer Series (+91140 / 140)",
            category = "Telemarketer",
            severityWeight = 0.95,
            prefixes = listOf("+91140", "91140", "0140", "140")
        ),
        MaliciousPatternRule(
            id = "promotional_in_160",
            name = "Bulk Promotional Series (+91160 / 0114)",
            category = "Promotional",
            severityWeight = 0.80,
            prefixes = listOf("+91160", "91160", "0114", "+91114")
        ),
        MaliciousPatternRule(
            id = "toll_free_robocall_us",
            name = "Toll-Free Robocall Series (800/888/877/866/855/844/833)",
            category = "Toll-Free Spam",
            severityWeight = 0.78,
            prefixes = listOf(
                "+1800", "1800", "800",
                "+1888", "1888", "888",
                "+1877", "1877", "877",
                "+1866", "1866", "866",
                "+1855", "1855", "855",
                "+1844", "1844", "844",
                "+1833", "1833", "833"
            )
        ),
        MaliciousPatternRule(
            id = "premium_rate_na",
            name = "Premium-Rate Scam Prefix (900 / 976)",
            category = "Premium Scam",
            severityWeight = 0.96,
            prefixes = listOf("+1900", "1900", "900", "+1976", "1976")
        ),
        MaliciousPatternRule(
            id = "premium_rate_uk",
            name = "High-Charge Premium Prefix (+4484 / +4487 / +4490)",
            category = "Premium Scam",
            severityWeight = 0.88,
            prefixes = listOf("+4484", "+4487", "+4490", "+4470")
        ),
        MaliciousPatternRule(
            id = "wangiri_high_risk_intl",
            name = "Known Wangiri One-Ring Fraud Country Code",
            category = "Wangiri Fraud",
            severityWeight = 0.92,
            prefixes = listOf("+222", "+247", "+252", "+257", "+676", "+685", "+881", "+882")
        ),
        MaliciousPatternRule(
            id = "voip_synthetic_repeating",
            name = "Synthetic Repeating-Digit Robodialer Pattern",
            category = "Spoofed Pattern",
            severityWeight = 0.72,
            regex = Regex("(\\d)\\1{4,}")
        ),
        MaliciousPatternRule(
            id = "voip_sequential_run",
            name = "Sequential Digit Spoof Signature (e.g. 12345 / 98765)",
            category = "Spoofed Pattern",
            severityWeight = 0.75,
            regex = Regex("(01234|12345|23456|34567|45678|56789|98765|87654|76543|65432|54321)")
        ),
        MaliciousPatternRule(
            id = "voip_trailing_quad_zero",
            name = "Bulk Auto-Dialer Block Suffix (0000)",
            category = "Bulk Dialer",
            severityWeight = 0.58,
            regex = Regex("0000$")
        )
    )

    /**
     * Evaluates [rawNumber] against all known local malicious prefix & regex signatures.
     */
    fun findMatches(rawNumber: String?): List<MatchedMaliciousPattern> {
        val normalized = PhoneNumberMatcher.normalize(rawNumber.orEmpty())
        val digitsOnly = normalized.removePrefix("+")
        if (digitsOnly.isEmpty()) return emptyList()

        val matches = mutableListOf<MatchedMaliciousPattern>()
        for (rule in KNOWN_PATTERNS) {
            val prefixHit = rule.prefixes.any { rawPrefix ->
                val normPrefix = PhoneNumberMatcher.normalize(rawPrefix)
                val prefixDigits = normPrefix.removePrefix("+")
                when {
                    normPrefix.startsWith("+") ->
                        normalized.startsWith(normPrefix) || digitsOnly.startsWith(prefixDigits)
                    else ->
                        digitsOnly.startsWith(prefixDigits)
                }
            }
            val regexHit = rule.regex?.containsMatchIn(digitsOnly) == true

            if (prefixHit || regexHit) {
                matches.add(
                    MatchedMaliciousPattern(
                        ruleId = rule.id,
                        name = rule.name,
                        category = rule.category,
                        severityWeight = rule.severityWeight
                    )
                )
            }
        }
        return matches.sortedByDescending { it.severityWeight }
    }
}
