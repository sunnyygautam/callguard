package com.example.ml

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.data.local.CallRecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Fully on-device call audio analysis and conversation summarization pipeline.
 *
 * 1. Decodes the local `.m4a` recording using Android's [MediaExtractor] + [MediaCodec]
 *    into 16-bit PCM frames to compute real Voice Activity Detection metrics (speech ratio,
 *    active speech duration, conversational turn count).
 * 2. On Android 13+ (API 33+) when [SpeechRecognizer.isOnDeviceRecognitionAvailable] is true,
 *    streams 16 kHz mono PCM via a [ParcelFileDescriptor] pipe directly into
 *    [SpeechRecognizer.createOnDeviceSpeechRecognizer] (`EXTRA_AUDIO_SOURCE`) to obtain
 *    an offline transcript.
 * 3. Runs [ConversationTextSummarizer] completely on-device to produce the final summary,
 *    action items, and topic tags.
 */
class OnDeviceCallSummarizer(
    private val context: Context
) {

    private data class DecodedAudioData(
        val metrics: CallAudioAcousticMetrics,
        val pcm16kMonoBytes: ByteArray
    )

    /**
     * Processes a recorded call audio file entirely on-device and produces a structured
     * [CallConversationSummary].
     */
    suspend fun summarizeRecording(recording: CallRecordingEntity): CallConversationSummary =
        withContext(Dispatchers.Default) {
            val callerLabel = recording.callerName.ifBlank { recording.phoneNumber }
            val audioFile = File(recording.filePath)

            // If we already have a non-empty user-edited or recognized transcript and the audio file is gone
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext ConversationTextSummarizer.summarizeConversation(
                    transcript = recording.transcript,
                    callerLabel = callerLabel,
                    isIncoming = recording.isIncoming,
                    durationMillis = recording.durationMillis,
                    acousticMetrics = null,
                    processingMode = if (recording.transcript.isNotBlank()) {
                        "On-Device NLP"
                    } else {
                        "On-Device Audio Analysis"
                    }
                )
            }

            // Stage 1: Decode local .m4a -> PCM & compute acoustic voice activity metrics
            val decoded = runCatching {
                decodeAudioToPcmAndMetrics(audioFile, recording.durationMillis)
            }.onFailure { err ->
                Log.w(TAG, "Audio decode failed for ${audioFile.name}: ${err.message}")
            }.getOrNull()

            // Stage 2: Attempt Android 13+ on-device SpeechRecognizer transcription from decoded PCM
            val recognizedTranscript = if (
                decoded != null &&
                decoded.pcm16kMonoBytes.isNotEmpty() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                runCatching {
                    transcribePcmOnDeviceApi33(decoded.pcm16kMonoBytes)
                }.onFailure { err ->
                    Log.w(TAG, "On-device SpeechRecognizer unavailable or failed: ${err.message}")
                }.getOrNull().orEmpty()
            } else {
                ""
            }

            val finalTranscript = recognizedTranscript.ifBlank { recording.transcript }
            val modeLabel = when {
                recognizedTranscript.isNotBlank() -> "On-Device Speech + NLP"
                finalTranscript.isNotBlank() -> "On-Device NLP"
                else -> "On-Device Audio Analysis"
            }

            // Stage 3: Run local extractive NLP summarizer
            ConversationTextSummarizer.summarizeConversation(
                transcript = finalTranscript,
                callerLabel = callerLabel,
                isIncoming = recording.isIncoming,
                durationMillis = decoded?.metrics?.durationMillis ?: recording.durationMillis,
                acousticMetrics = decoded?.metrics,
                processingMode = modeLabel
            )
        }

    /**
     * Re-runs the pure on-device NLP summarizer over a user-provided or refined transcript.
     */
    suspend fun summarizeTranscriptText(
        recording: CallRecordingEntity,
        transcriptText: String
    ): CallConversationSummary = withContext(Dispatchers.Default) {
        val callerLabel = recording.callerName.ifBlank { recording.phoneNumber }
        ConversationTextSummarizer.summarizeConversation(
            transcript = transcriptText,
            callerLabel = callerLabel,
            isIncoming = recording.isIncoming,
            durationMillis = recording.durationMillis,
            acousticMetrics = null,
            processingMode = "On-Device NLP"
        )
    }

    /**
     * Decodes a compressed `.m4a` audio file into 16-bit PCM using [MediaExtractor] and
     * [MediaCodec], computing frame-level RMS energy for voice activity detection and
     * downsampling up to the first 90 seconds to 16 kHz mono PCM for on-device ASR.
     */
    private fun decodeAudioToPcmAndMetrics(
        file: File,
        fallbackDurationMillis: Long
    ): DecodedAudioData {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                return DecodedAudioData(
                    metrics = CallAudioAcousticMetrics(
                        durationMillis = fallbackDurationMillis,
                        activeSpeechMillis = 0L,
                        speechRatioPercent = 0,
                        estimatedTurns = 0,
                        sampleRateHz = TARGET_ASR_SAMPLE_RATE
                    ),
                    pcm16kMonoBytes = ByteArray(0)
                )
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                fallbackDurationMillis * 1000L
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            // VAD tracking in 50ms windows
            val windowSamples = (sampleRate * 50) / 1000
            var currentWindowSqSum = 0.0
            var currentWindowCount = 0
            var totalWindows = 0
            var activeSpeechWindows = 0
            var inSpeechSegment = false
            var consecutiveSilenceWindows = 0
            var estimatedTurns = 0

            // Resampled 16 kHz mono PCM buffer (capped at ~60s = ~1.9 MB for fast on-device ASR)
            val pcm16kStream = ByteArrayOutputStream()
            val maxAsrBytes = TARGET_ASR_SAMPLE_RATE * 2 * MAX_ASR_SECONDS
            val resampleStep = sampleRate.toDouble() / TARGET_ASR_SAMPLE_RATE.toDouble()
            var resampleAccumulator = 0.0

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)

                        val shortBuf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frameCount = shortBuf.remaining() / channelCount
                        for (f in 0 until frameCount) {
                            var monoSum = 0
                            for (c in 0 until channelCount) {
                                monoSum += shortBuf.get().toInt()
                            }
                            val monoSample = (monoSum / channelCount).coerceIn(
                                Short.MIN_VALUE.toInt(),
                                Short.MAX_VALUE.toInt()
                            )

                            // VAD RMS accumulation
                            val norm = monoSample / 32768.0
                            currentWindowSqSum += norm * norm
                            currentWindowCount++
                            if (currentWindowCount >= windowSamples) {
                                val rms = sqrt(currentWindowSqSum / currentWindowCount)
                                totalWindows++
                                if (rms >= VAD_RMS_THRESHOLD) {
                                    activeSpeechWindows++
                                    if (!inSpeechSegment) {
                                        inSpeechSegment = true
                                        estimatedTurns++
                                    }
                                    consecutiveSilenceWindows = 0
                                } else if (inSpeechSegment) {
                                    consecutiveSilenceWindows++
                                    if (consecutiveSilenceWindows >= TURN_PAUSE_WINDOWS) {
                                        inSpeechSegment = false
                                    }
                                }
                                currentWindowSqSum = 0.0
                                currentWindowCount = 0
                            }

                            // Simple linear decimation to 16 kHz mono 16-bit LE PCM
                            if (pcm16kStream.size() < maxAsrBytes) {
                                resampleAccumulator += 1.0
                                if (resampleAccumulator >= resampleStep) {
                                    resampleAccumulator -= resampleStep
                                    pcm16kStream.write(monoSample and 0xFF)
                                    pcm16kStream.write((monoSample ushr 8) and 0xFF)
                                }
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && sawInputEos) {
                    break
                }
            }

            val totalDurationMs = (durationUs / 1000L).coerceAtLeast(fallbackDurationMillis)
            val speechRatio = if (totalWindows > 0) {
                ((activeSpeechWindows * 100.0) / totalWindows).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val activeSpeechMs = (activeSpeechWindows * 50L).coerceAtMost(totalDurationMs)

            return DecodedAudioData(
                metrics = CallAudioAcousticMetrics(
                    durationMillis = totalDurationMs,
                    activeSpeechMillis = activeSpeechMs,
                    speechRatioPercent = speechRatio,
                    estimatedTurns = estimatedTurns,
                    sampleRateHz = sampleRate
                ),
                pcm16kMonoBytes = pcm16kStream.toByteArray()
            )
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Streams decoded 16 kHz mono PCM into Android 13+'s on-device [SpeechRecognizer]
     * using `RecognizerIntent.EXTRA_AUDIO_SOURCE`.
     */
    private suspend fun transcribePcmOnDeviceApi33(pcmBytes: ByteArray): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return ""
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return ""

        val pipe = ParcelFileDescriptor.createPipe()
        val readFd = pipe[0]
        val writeFd = pipe[1]

        // Write PCM bytes on IO thread and close the write end so SpeechRecognizer sees EOF
        val writerThread = Thread {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(writeFd).use { out ->
                    var offset = 0
                    val chunkSize = 4096
                    while (offset < pcmBytes.size) {
                        val len = minOf(chunkSize, pcmBytes.size - offset)
                        out.write(pcmBytes, offset, len)
                        offset += len
                    }
                    out.flush()
                }
            } catch (_: Exception) {
                // Pipe closed by recognizer
            }
        }

        val transcript = withTimeoutOrNull(ASR_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<String> { continuation ->
                    val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    val accumulatedSegments = mutableListOf<String>()

                    fun finish(result: String) {
                        runCatching { recognizer.destroy() }
                        runCatching { readFd.close() }
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }

                    recognizer.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit

                        override fun onPartialResults(partialResults: Bundle?) {
                            // Keep latest partial in case final times out
                        }

                        override fun onResults(results: Bundle?) {
                            val best = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (best.isNotEmpty()) {
                                accumulatedSegments.add(best)
                            }
                            finish(accumulatedSegments.joinToString(". "))
                        }

                        override fun onError(error: Int) {
                            Log.d(TAG, "On-device SpeechRecognizer ended with code=$error")
                            finish(accumulatedSegments.joinToString(". "))
                        }
                    })

                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readFd)
                        putExtra(
                            RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                            AudioFormat.ENCODING_PCM_16BIT
                        )
                        putExtra(
                            RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
                            TARGET_ASR_SAMPLE_RATE
                        )
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                    }

                    continuation.invokeOnCancellation {
                        runCatching { recognizer.cancel() }
                        runCatching { recognizer.destroy() }
                        runCatching { readFd.close() }
                        runCatching { writeFd.close() }
                    }

                    writerThread.start()
                    recognizer.startListening(intent)
                }
            }
        }

        runCatching { readFd.close() }
        runCatching { writeFd.close() }
        return transcript.orEmpty()
    }

    companion object {
        private const val TAG = "OnDeviceCallSummarizer"
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val TARGET_ASR_SAMPLE_RATE = 16_000
        private const val MAX_ASR_SECONDS = 60
        private const val ASR_TIMEOUT_MS = 18_000L
        private const val VAD_RMS_THRESHOLD = 0.018
        private const val TURN_PAUSE_WINDOWS = 14 // ~700ms pause separates speaking turns
    }
}
