package com.example.ml

import com.example.data.local.BlockedPrefixEntity
import com.example.data.repository.DeviceCallLogEntry
import com.example.data.repository.DeviceCallType
import com.example.data.repository.DeviceContactEntry
import com.example.telecom.PhoneNumberMatcher
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Risk classification buckets derived from the ML model's calibrated spam probability.
 */
enum class SpamRiskTier {
    LOW,       // 0% - 29%
    MODERATE,  // 30% - 59%
    HIGH,      // 60% - 84%
    CRITICAL   // 85% - 100%
}

/**
 * Explainable output from [SpamMlAnalysisService] for a single phone number / call event.
 */
data class SpamMlAnalysisResult(
    val probability: Double,
    val probabilityPercent: Int,
    val riskTier: SpamRiskTier,
    val topReason: String,
    val signals: List<String>,
    val matchedPatterns: List<MatchedMaliciousPattern>
)

/**
 * On-device Machine Learning Spam Analysis Service using a calibrated Logistic Regression
 * classifier (`P(spam | x) = sigmoid(bias + Σ w_i * x_i)`) over local telecom, structural,
 * entropy, and call-behavior features.
 *
 * Designed for deterministic, sub-millisecond, 100% offline inference inside both Android's
 * [com.example.telecom.PrefixCallScreeningService] and the call history logs UI.
 */
object SpamMlAnalysisService {

    // Calibrated Logistic Regression weights trained on telecom spam signal priors
    private const val MODEL_BIAS = -1.65
    private const val W_USER_BLOCKED_PREFIX = 3.80
    private const val W_MALICIOUS_PATTERN_MAX = 3.25
    private const val W_MULTI_PATTERN_BONUS = 0.85
    private const val W_HIDDEN_OR_PRIVATE = 2.35
    private const val W_STIR_SHAKEN_FAILED = 2.95
    private const val W_ENTROPY_DEFICIT = 1.45
    private const val W_ABNORMAL_LENGTH = 1.15
    private const val W_BEHAVIOR_ANOMALY = 1.65
    private const val W_KNOWN_CONTACT = -2.85
    private const val W_HEALTHY_HISTORY = -1.75

    /**
     * Evaluates a single incoming call or phone number in real time and returns a calibrated
     * [SpamMlAnalysisResult] containing the 0-100% Spam Probability score and explainable signals.
     */
    fun analyzeNumber(
        rawNumber: String?,
        activeManualPrefixes: List<BlockedPrefixEntity> = emptyList(),
        isHiddenOrPrivate: Boolean = false,
        isStirShakenFailed: Boolean = false,
        isKnownContact: Boolean = false,
        hasHealthyCallHistory: Boolean = false,
        behaviorAnomalyScore: Double = 0.0,
        behaviorReasons: List<String> = emptyList()
    ): SpamMlAnalysisResult {
        val normalized = PhoneNumberMatcher.normalize(rawNumber.orEmpty())
        val digitsOnly = normalized.removePrefix("+")
        val effectiveHidden = isHiddenOrPrivate || digitsOnly.isEmpty()

        val signals = mutableListOf<String>()
        var logit = MODEL_BIAS

        // 1. User's custom blocked prefix rule match
        val matchedManualPrefix = if (!effectiveHidden) {
            PhoneNumberMatcher.findMatchingPrefix(rawNumber, activeManualPrefixes)
        } else {
            null
        }
        if (matchedManualPrefix != null) {
            logit += W_USER_BLOCKED_PREFIX
            val labelPart = matchedManualPrefix.label.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
            signals.add("Matches your blocked prefix ${matchedManualPrefix.prefixString}$labelPart")
        }

        // 2. Local Malicious Pattern Catalog match (prefixes + regex signatures)
        val matchedPatterns = if (!effectiveHidden) {
            MaliciousPatternCatalog.findMatches(rawNumber)
        } else {
            emptyList()
        }
        if (matchedPatterns.isNotEmpty()) {
            val strongest = matchedPatterns.first()
            logit += W_MALICIOUS_PATTERN_MAX * strongest.severityWeight
            if (matchedPatterns.size > 1) {
                logit += W_MULTI_PATTERN_BONUS
            }
            matchedPatterns.take(2).forEach { pattern ->
                signals.add("Malicious pattern: ${pattern.name}")
            }
        }

        // 3. Hidden / Restricted / Unknown Caller ID
        if (effectiveHidden) {
            logit += W_HIDDEN_OR_PRIVATE
            signals.add("Hidden or restricted caller ID")
        }

        // 4. Carrier STIR/SHAKEN attestation failure
        if (isStirShakenFailed) {
            logit += W_STIR_SHAKEN_FAILED
            signals.add("Failed carrier STIR/SHAKEN caller verification")
        }

        // 5. Shannon Digit Entropy deficit (robocall & spoofed numbers have lower digit entropy)
        if (digitsOnly.length >= 6) {
            val entropyDeficit = computeDigitEntropyDeficit(digitsOnly)
            if (entropyDeficit >= 0.35) {
                logit += W_ENTROPY_DEFICIT * entropyDeficit
                signals.add("Low digit entropy (automated/synthetic number signature)")
            }
        }

        // 6. Non-standard E.164 subscriber length anomaly
        if (!effectiveHidden && (digitsOnly.length < 7 || digitsOnly.length > 14)) {
            logit += W_ABNORMAL_LENGTH
            signals.add("Unusual number length (${digitsOnly.length} digits)")
        }

        // 7. Behavioral call-history anomaly (burst missed/rejected calls, ultra-short hangup)
        val clampedBehavior = behaviorAnomalyScore.coerceIn(0.0, 1.0)
        if (clampedBehavior > 0.0) {
            logit += W_BEHAVIOR_ANOMALY * clampedBehavior
            signals.addAll(behaviorReasons)
        }

        // 8. Negative / Trust signals (saved contacts & healthy outgoing/sustained call history)
        if (isKnownContact) {
            logit += W_KNOWN_CONTACT
            signals.add("Saved contact (trusted caller signal)")
        }
        if (hasHealthyCallHistory) {
            logit += W_HEALTHY_HISTORY
            signals.add("Prior outgoing or sustained call history")
        }

        val probability = sigmoid(logit).coerceIn(0.01, 0.99)
        val probabilityPercent = (probability * 100.0).roundToInt().coerceIn(1, 99)
        val riskTier = tierForPercent(probabilityPercent)

        val topReason = signals.firstOrNull() ?: when (riskTier) {
            SpamRiskTier.LOW -> "No known malicious patterns detected"
            SpamRiskTier.MODERATE -> "Unverified number with mild risk indicators"
            SpamRiskTier.HIGH -> "Multiple spam risk indicators detected"
            SpamRiskTier.CRITICAL -> "High-confidence malicious call pattern"
        }

        return SpamMlAnalysisResult(
            probability = probability,
            probabilityPercent = probabilityPercent,
            riskTier = riskTier,
            topReason = topReason,
            signals = signals.ifEmpty { listOf(topReason) },
            matchedPatterns = matchedPatterns
        )
    }

    /**
     * Runs batch ML scoring over the device's recent call history, incorporating cross-call
     * behavioral features (repeated missed/rejected call bursts, outgoing trust history, and
     * saved contact matching) so every call history log displays an accurate Spam Probability score.
     */
    fun analyzeCallHistoryBatch(
        callLogs: List<DeviceCallLogEntry>,
        contacts: List<DeviceContactEntry>,
        activeManualPrefixes: List<BlockedPrefixEntity>
    ): Map<Long, SpamMlAnalysisResult> {
        if (callLogs.isEmpty()) return emptyMap()

        val contactNumbers = contacts.mapNotNull { entry ->
            entry.normalizedNumber.takeIf { it.isNotEmpty() }
        }.toHashSet()

        // Aggregate per-number behavioral statistics across the history window
        val callsByNormalized = callLogs.groupBy {
            PhoneNumberMatcher.normalize(it.number)
        }

        val results = LinkedHashMap<Long, SpamMlAnalysisResult>(callLogs.size)
        for (entry in callLogs) {
            val normalized = PhoneNumberMatcher.normalize(entry.number)
            val isHidden = entry.number.isBlank() || normalized.isEmpty()
            val isKnownContact = !entry.cachedName.isNullOrBlank() ||
                (normalized.isNotEmpty() && contactNumbers.contains(normalized))

            val peerCalls = if (normalized.isNotEmpty()) {
                callsByNormalized[normalized].orEmpty()
            } else {
                emptyList()
            }

            val hasHealthyHistory = peerCalls.any { peer ->
                peer.type == DeviceCallType.OUTGOING || peer.durationSeconds >= 45L
            }

            // Compute behavioral risk features
            var behaviorScore = 0.0
            val behaviorReasons = mutableListOf<String>()

            if (entry.type == DeviceCallType.REJECTED) {
                behaviorScore += 0.65
                behaviorReasons.add("Call was rejected or blocked")
            }

            val missedOrRejectedCount = peerCalls.count {
                it.type == DeviceCallType.MISSED || it.type == DeviceCallType.REJECTED
            }
            if (missedOrRejectedCount >= 2 && !hasHealthyHistory && !isKnownContact) {
                behaviorScore += 0.45
                behaviorReasons.add("Repeated unanswered/missed call burst ($missedOrRejectedCount calls)")
            }

            if (entry.type == DeviceCallType.INCOMING &&
                entry.durationSeconds in 1L..8L &&
                !isKnownContact
            ) {
                behaviorScore += 0.25
                behaviorReasons.add("Very short incoming call (${entry.durationSeconds}s)")
            }

            results[entry.id] = analyzeNumber(
                rawNumber = entry.number,
                activeManualPrefixes = activeManualPrefixes,
                isHiddenOrPrivate = isHidden,
                isStirShakenFailed = false,
                isKnownContact = isKnownContact,
                hasHealthyCallHistory = hasHealthyHistory,
                behaviorAnomalyScore = behaviorScore.coerceIn(0.0, 1.0),
                behaviorReasons = behaviorReasons
            )
        }

        return results
    }

    /**
     * Computes normalized Shannon entropy deficit (`0.0` = high diversity like organic numbers,
     * `1.0` = zero diversity like `0000000000`).
     */
    internal fun computeDigitEntropyDeficit(digitsOnly: String): Double {
        if (digitsOnly.isEmpty()) return 1.0
        val counts = IntArray(10)
        var total = 0
        for (ch in digitsOnly) {
            if (ch in '0'..'9') {
                counts[ch - '0']++
                total++
            }
        }
        if (total == 0) return 1.0

        var entropy = 0.0
        for (count in counts) {
            if (count > 0) {
                val p = count.toDouble() / total.toDouble()
                entropy -= p * (ln(p) / ln(2.0))
            }
        }
        // Max Shannon entropy for base-10 digits is log2(10) ≈ 3.3219 bits
        val maxEntropy = ln(10.0) / ln(2.0)
        val normalizedEntropy = (entropy / maxEntropy).coerceIn(0.0, 1.0)
        return (1.0 - normalizedEntropy).coerceIn(0.0, 1.0)
    }

    internal fun tierForPercent(percent: Int): SpamRiskTier = when {
        percent >= 85 -> SpamRiskTier.CRITICAL
        percent >= 60 -> SpamRiskTier.HIGH
        percent >= 30 -> SpamRiskTier.MODERATE
        else -> SpamRiskTier.LOW
    }

    private fun sigmoid(z: Double): Double = 1.0 / (1.0 + exp(-z))
}
