package com.example

import com.example.ml.CallAudioAcousticMetrics
import com.example.ml.ConversationTextSummarizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTextSummarizerTest {

    @Test
    fun `summarizes transcript extracting action items and topics on-device`() {
        val transcript = """
            Hi Alex, thanks for picking up quickly today.
            We need to reschedule the client project meeting to Friday at 3:30 PM.
            Please email the updated invoice for $450 before tomorrow afternoon.
            I will send a calendar invite right after this call and confirm with the team.
        """.trimIndent()

        val summary = ConversationTextSummarizer.summarizeConversation(
            transcript = transcript,
            callerLabel = "Alex Morgan",
            isIncoming = true,
            durationMillis = 125_000L,
            acousticMetrics = CallAudioAcousticMetrics(
                durationMillis = 125_000L,
                activeSpeechMillis = 82_000L,
                speechRatioPercent = 66,
                estimatedTurns = 7,
                sampleRateHz = 44_100
            )
        )

        assertTrue(summary.summaryText.contains("Incoming call with Alex Morgan"))
        assertTrue(summary.summaryText.contains("02:05"))
        assertTrue(summary.keyPoints.any { it.startsWith("Action:") })
        assertTrue(summary.keyPoints.any { it.contains("450") || it.contains("Friday") })
        assertTrue(summary.topics.contains("Schedule") || summary.topics.contains("Billing"))
        assertFalse(summary.transcript.isBlank())
    }

    @Test
    fun `falls back gracefully to acoustic summary when transcript is empty`() {
        val summary = ConversationTextSummarizer.summarizeConversation(
            transcript = "",
            callerLabel = "+1 415-555-0192",
            isIncoming = false,
            durationMillis = 60_000L,
            acousticMetrics = CallAudioAcousticMetrics(
                durationMillis = 60_000L,
                activeSpeechMillis = 36_000L,
                speechRatioPercent = 60,
                estimatedTurns = 5,
                sampleRateHz = 44_100
            )
        )

        assertTrue(summary.summaryText.contains("Outgoing call with +1 415-555-0192"))
        assertTrue(summary.summaryText.contains("60% speech"))
        assertTrue(summary.keyPoints.any { it.contains("100% locally") })
        assertTrue(summary.processingMode == "On-Device Audio Analysis")
    }
}
