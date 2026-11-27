package com.example.ml

import com.example.telecom.CallAudioRecorder
import java.util.Locale

/**
 * Acoustic voice-activity metrics extracted locally from a decoded PCM audio stream.
 */
data class CallAudioAcousticMetrics(
    val durationMillis: Long,
    val activeSpeechMillis: Long,
    val speechRatioPercent: Int,
    val estimatedTurns: Int,
    val sampleRateHz: Int
)

/**
 * Structured text-based conversation summary produced entirely on-device.
 */
data class CallConversationSummary(
    val summaryText: String,
    val keyPoints: List<String>,
    val topics: List<String>,
    val transcript: String,
    val processingMode: String
)

/**
 * Pure Kotlin, 100% on-device extractive NLP summarizer for recorded phone calls.
 *
 * Uses local sentence segmentation, term-frequency importance scoring, structural position
 * weighting, action-item/commitment detection, and domain topic classification—requiring
 * zero network calls or cloud APIs.
 */
object ConversationTextSummarizer {

    private val STOP_WORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are",
        "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but",
        "by", "can", "could", "did", "do", "does", "doing", "down", "during", "each", "few", "for",
        "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers", "herself",
        "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its", "itself",
        "just", "let", "me", "more", "most", "my", "myself", "no", "nor", "not", "now", "of", "off",
        "oh", "ok", "okay", "on", "once", "only", "or", "other", "our", "ours", "out", "over",
        "own", "really", "right", "same", "she", "should", "so", "some", "such", "than", "that",
        "the", "their", "theirs", "them", "themselves", "then", "there", "these", "they", "this",
        "those", "through", "to", "too", "um", "uh", "under", "until", "up", "very", "was", "we",
        "were", "what", "when", "where", "which", "while", "who", "whom", "why", "will", "with",
        "would", "yeah", "yes", "you", "your", "yours", "hello", "hey", "hi", "bye", "thanks", "thank"
    )

    private val ACTION_CUE_PATTERNS = listOf(
        Regex("\\b(call back|follow up|send|email|text|message|schedule|reschedule|confirm|book|cancel|deliver|pay|payment|invoice|transfer|meet|meeting|appointment|tomorrow|today|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|deadline|remind|reminder|update|check|review|share|need to|will|won't|please|make sure)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d{1,2}(:\\d{2})?\\s?(am|pm)\\b", RegexOption.IGNORE_CASE),
        Regex("(\\$|₹|€|£)\\s?\\d+([.,]\\d+)?")
    )

    private val TOPIC_LEXICON: Map<String, Set<String>> = mapOf(
        "Schedule" to setOf("meeting", "appointment", "schedule", "reschedule", "calendar", "tomorrow", "time", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "morning", "afternoon", "evening"),
        "Follow-Up" to setOf("callback", "call", "follow", "remind", "reminder", "later", "soon", "update", "confirm", "check"),
        "Billing" to setOf("payment", "pay", "invoice", "bill", "refund", "charge", "account", "bank", "card", "price", "cost", "amount", "fee", "quote"),
        "Delivery" to setOf("delivery", "package", "order", "shipping", "address", "courier", "arrive", "pickup", "tracking", "drop"),
        "Support" to setOf("issue", "problem", "error", "help", "support", "fix", "ticket", "repair", "service", "broken", "reset"),
        "Work" to setOf("project", "client", "report", "team", "office", "deadline", "contract", "proposal", "document", "email")
    )

    private val NEWLINE_REGEX = Regex("[\\r\\n]+")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val DOUBLE_DOT_REGEX = Regex("\\.\\s*\\.")
    private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?])\\s+")
    private val WORD_TOKEN_REGEX = Regex("[a-zA-Z0-9']+")

    /**
     * Generates a concise text summary, bulleted key points, and topic tags from a recorded
     * call's on-device transcript (or falls back to an acoustic speech-activity summary when
     * no transcript words were recognized).
     */
    fun summarizeConversation(
        transcript: String,
        callerLabel: String,
        isIncoming: Boolean,
        durationMillis: Long,
        acousticMetrics: CallAudioAcousticMetrics? = null,
        processingMode: String = "On-Device Speech + NLP"
    ): CallConversationSummary {
        val cleanedTranscript = normalizeTranscript(transcript)
        if (cleanedTranscript.isBlank()) {
            return buildAcousticOnlySummary(
                callerLabel = callerLabel,
                isIncoming = isIncoming,
                durationMillis = durationMillis,
                acousticMetrics = acousticMetrics
            )
        }

        val sentences = splitIntoSentences(cleanedTranscript)
        if (sentences.isEmpty()) {
            return buildAcousticOnlySummary(
                callerLabel = callerLabel,
                isIncoming = isIncoming,
                durationMillis = durationMillis,
                acousticMetrics = acousticMetrics
            )
        }

        // Compute content word frequencies across the entire conversation
        val wordFrequencies = mutableMapOf<String, Int>()
        val sentenceTokens = sentences.map { sentence ->
            tokenizeContentWords(sentence).also { tokens ->
                tokens.forEach { token ->
                    wordFrequencies[token] = (wordFrequencies[token] ?: 0) + 1
                }
            }
        }
        val maxFreq = (wordFrequencies.values.maxOrNull() ?: 1).toDouble()

        // Score every sentence using normalized TF + structural position + action/entity cues
        data class ScoredSentence(
            val index: Int,
            val text: String,
            val score: Double,
            val isActionCue: Boolean
        )

        val scored = sentences.mapIndexed { idx, sentence ->
            val tokens = sentenceTokens[idx]
            val tfScore = if (tokens.isEmpty()) {
                0.0
            } else {
                tokens.sumOf { (wordFrequencies[it] ?: 0) / maxFreq } /
                    kotlin.math.sqrt(tokens.size.toDouble())
            }
            val positionBoost = when {
                idx == 0 -> 0.25
                idx == sentences.lastIndex -> 0.20
                else -> 0.0
            }
            val hasActionCue = ACTION_CUE_PATTERNS.any { it.containsMatchIn(sentence) }
            val actionBoost = if (hasActionCue) 0.45 else 0.0
            val lengthPenalty = if (tokens.size < 3) -0.25 else 0.0

            ScoredSentence(
                index = idx,
                text = sentence,
                score = tfScore + positionBoost + actionBoost + lengthPenalty,
                isActionCue = hasActionCue
            )
        }

        // Pick top 2-3 sentences in chronological order for the executive summary paragraph
        val summarySentenceCount = when {
            sentences.size <= 2 -> sentences.size
            sentences.size <= 5 -> 2
            else -> 3
        }
        val selectedSummary = scored
            .sortedByDescending { it.score }
            .take(summarySentenceCount)
            .sortedBy { it.index }
            .joinToString(" ") { ensureSentencePunctuation(it.text) }

        val directionLabel = if (isIncoming) "Incoming" else "Outgoing"
        val formattedDuration = CallAudioRecorder.formatDurationMillis(durationMillis)
        val summaryParagraph = "$directionLabel call with $callerLabel ($formattedDuration): $selectedSummary"

        // Extract up to 4 concise key points (prioritizing action items / commitments first)
        val actionPoints = scored
            .filter { it.isActionCue }
            .sortedByDescending { it.score }
            .take(3)
        val topRemaining = scored
            .filter { candidate -> actionPoints.none { it.index == candidate.index } }
            .sortedByDescending { it.score }
            .take((4 - actionPoints.size).coerceAtLeast(0))

        val keyPoints = (actionPoints + topRemaining)
            .sortedBy { it.index }
            .map { point ->
                val prefix = if (point.isActionCue) "Action: " else "Note: "
                prefix + ensureSentencePunctuation(point.text)
            }
            .ifEmpty { listOf("Note: ${ensureSentencePunctuation(sentences.first())}") }

        // Append acoustic summary note if available
        val enrichedKeyPoints = buildList {
            addAll(keyPoints)
            if (acousticMetrics != null && acousticMetrics.estimatedTurns > 0) {
                add(
                    "Audio: ${acousticMetrics.speechRatioPercent}% active speech " +
                        "across ~${acousticMetrics.estimatedTurns} speaking segments."
                )
            }
        }

        val topics = extractTopics(wordFrequencies)

        return CallConversationSummary(
            summaryText = summaryParagraph,
            keyPoints = enrichedKeyPoints,
            topics = topics,
            transcript = cleanedTranscript,
            processingMode = processingMode
        )
    }

    private fun buildAcousticOnlySummary(
        callerLabel: String,
        isIncoming: Boolean,
        durationMillis: Long,
        acousticMetrics: CallAudioAcousticMetrics?
    ): CallConversationSummary {
        val directionLabel = if (isIncoming) "Incoming" else "Outgoing"
        val formattedDuration = CallAudioRecorder.formatDurationMillis(durationMillis)

        if (acousticMetrics == null) {
            return CallConversationSummary(
                summaryText = "$directionLabel call with $callerLabel lasting $formattedDuration. " +
                    "On-device speech recognition did not detect clear spoken words in this recording; " +
                    "you can add or edit transcript notes below to generate an on-device text summary.",
                keyPoints = listOf(
                    "Direction: $directionLabel call ($formattedDuration).",
                    "On-device audio checked — no transcribable speech stream detected."
                ),
                topics = listOf(directionLabel, "Voice Call"),
                transcript = "",
                processingMode = "On-Device Audio Analysis"
            )
        }

        val activeFormatted = CallAudioRecorder.formatDurationMillis(acousticMetrics.activeSpeechMillis)
        val paceDescriptor = when {
            acousticMetrics.speechRatioPercent < 15 -> "mostly quiet or low-volume audio"
            acousticMetrics.estimatedTurns >= 8 -> "an active back-and-forth conversation"
            acousticMetrics.estimatedTurns >= 3 -> "a steady multi-turn discussion"
            else -> "a brief voice exchange"
        }

        val summaryText = "$directionLabel call with $callerLabel ($formattedDuration) analyzed on-device: " +
            "detected $paceDescriptor with $activeFormatted of active voice activity " +
            "(${acousticMetrics.speechRatioPercent}% speech across ~${acousticMetrics.estimatedTurns} speaking segments). " +
            "Offline speech-to-text could not transcribe words on this device; add transcript notes below anytime to summarize key points locally."

        return CallConversationSummary(
            summaryText = summaryText,
            keyPoints = listOf(
                "Active voice detected: $activeFormatted (${acousticMetrics.speechRatioPercent}% of call).",
                "Estimated conversational segments: ${acousticMetrics.estimatedTurns}.",
                "Privacy: Processed 100% locally without uploading call audio."
            ),
            topics = buildList {
                add(directionLabel)
                if (acousticMetrics.estimatedTurns >= 6) add("Discussion") else add("Quick Call")
                add("On-Device")
            },
            transcript = "",
            processingMode = "On-Device Audio Analysis"
        )
    }

    private fun normalizeTranscript(raw: String): String {
        return raw
            .replace(NEWLINE_REGEX, ". ")
            .replace(WHITESPACE_REGEX, " ")
            .replace(DOUBLE_DOT_REGEX, ".")
            .trim()
    }

    private fun splitIntoSentences(text: String): List<String> {
        val rawSplits = text
            .split(SENTENCE_SPLIT_REGEX)
            .map { it.trim().trimEnd('.', '!', '?').trim() }
            .filter { it.length >= 4 }

        if (rawSplits.size > 1) return rawSplits

        // On-device ASR sometimes returns unpunctuated continuous speech: chunk cleanly into ~16-word clauses
        val single = rawSplits.firstOrNull() ?: return emptyList()
        val words = single.split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (words.size <= 20) return listOf(single)

        return words.chunked(16).map { it.joinToString(" ") }
    }

    private fun tokenizeContentWords(sentence: String): List<String> {
        return WORD_TOKEN_REGEX.findAll(sentence.lowercase(Locale.US))
            .map { it.value.trim('\'') }
            .filter { it.length >= 3 && it !in STOP_WORDS && !it.all(Char::isDigit) }
            .toList()
    }

    private fun extractTopics(wordFrequencies: Map<String, Int>): List<String> {
        val matchedTopics = mutableListOf<Pair<String, Int>>()
        for ((topic, keywords) in TOPIC_LEXICON) {
            val hits = keywords.sumOf { keyword -> wordFrequencies[keyword] ?: 0 }
            if (hits > 0) {
                matchedTopics.add(topic to hits)
            }
        }
        val topDomainTopics = matchedTopics
            .sortedByDescending { it.second }
            .map { it.first }
            .take(3)

        if (topDomainTopics.size >= 2) return topDomainTopics

        val frequentKeywords = wordFrequencies.entries
            .filter { it.key.length >= 4 }
            .sortedByDescending { it.value }
            .map { entry ->
                entry.key.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                }
            }
            .filter { keyword -> topDomainTopics.none { it.equals(keyword, ignoreCase = true) } }
            .take(3 - topDomainTopics.size)

        return (topDomainTopics + frequentKeywords).ifEmpty { listOf("Conversation") }
    }

    private fun ensureSentencePunctuation(sentence: String): String {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return trimmed
        val capitalized = trimmed.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
        }
        return if (capitalized.last() in listOf('.', '!', '?')) {
            capitalized
        } else {
            "$capitalized."
        }
    }
}
