package com.example.telecom

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Reactive playback state for the built-in call recordings audio player.
 */
data class RecordingPlaybackState(
    val activeRecordingId: Long? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Lightweight [MediaPlayer] controller for playing back saved local call recordings.
 */
class CallRecordingPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(RecordingPlaybackState())
    val playbackState: StateFlow<RecordingPlaybackState> = _playbackState.asStateFlow()

    fun togglePlayPause(recordingId: Long, filePath: String) {
        val current = _playbackState.value
        if (current.activeRecordingId == recordingId && mediaPlayer != null) {
            if (current.isPlaying) {
                pause()
            } else {
                resume()
            }
            return
        }
        startNewPlayback(recordingId, filePath)
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.toInt().coerceIn(0, player.duration.coerceAtLeast(0))
        runCatching {
            player.seekTo(clamped)
            _playbackState.update { it.copy(currentPositionMs = clamped.toLong()) }
        }
    }

    fun stopIfPlaying(recordingId: Long) {
        if (_playbackState.value.activeRecordingId == recordingId) {
            stop()
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.release() }
        }
        mediaPlayer = null
        _playbackState.value = RecordingPlaybackState()
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun pause() {
        val player = mediaPlayer ?: return
        runCatching {
            if (player.isPlaying) {
                player.pause()
            }
        }
        progressJob?.cancel()
        _playbackState.update { it.copy(isPlaying = false) }
    }

    private fun resume() {
        val player = mediaPlayer ?: return
        runCatching {
            player.start()
            _playbackState.update { it.copy(isPlaying = true, errorMessage = null) }
            startProgressTicker()
        }.onFailure { err ->
            _playbackState.update {
                it.copy(isPlaying = false, errorMessage = err.localizedMessage)
            }
        }
    }

    private fun startNewPlayback(recordingId: Long, filePath: String) {
        stop()
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            _playbackState.value = RecordingPlaybackState(
                activeRecordingId = recordingId,
                errorMessage = "Audio file not found on device."
            )
            return
        }

        val player = MediaPlayer()
        mediaPlayer = player
        _playbackState.value = RecordingPlaybackState(
            activeRecordingId = recordingId,
            isPlaying = false,
            currentPositionMs = 0L,
            durationMs = 0L,
            errorMessage = null
        )

        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { preparedPlayer ->
                // Guard against stop() or another recording taking over while preparing asynchronously
                if (mediaPlayer !== preparedPlayer) {
                    runCatching { preparedPlayer.release() }
                    return@setOnPreparedListener
                }
                runCatching {
                    preparedPlayer.start()
                    _playbackState.value = RecordingPlaybackState(
                        activeRecordingId = recordingId,
                        isPlaying = true,
                        currentPositionMs = 0L,
                        durationMs = preparedPlayer.duration.toLong().coerceAtLeast(1L),
                        errorMessage = null
                    )
                    startProgressTicker()
                }.onFailure { err ->
                    stop()
                    _playbackState.value = RecordingPlaybackState(
                        activeRecordingId = recordingId,
                        errorMessage = err.localizedMessage ?: "Unable to play this recording."
                    )
                }
            }
            player.setOnCompletionListener {
                if (mediaPlayer !== player) return@setOnCompletionListener
                progressJob?.cancel()
                _playbackState.update { state ->
                    state.copy(
                        isPlaying = false,
                        currentPositionMs = 0L
                    )
                }
                runCatching { player.seekTo(0) }
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                if (mediaPlayer === failedPlayer) {
                    stop()
                    _playbackState.value = RecordingPlaybackState(
                        activeRecordingId = recordingId,
                        errorMessage = "Unable to play this recording."
                    )
                } else {
                    runCatching { failedPlayer.release() }
                }
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            if (mediaPlayer === player) {
                mediaPlayer = null
            }
            runCatching { player.release() }
            _playbackState.value = RecordingPlaybackState(
                activeRecordingId = recordingId,
                errorMessage = e.localizedMessage ?: "Unable to play this recording."
            )
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer ?: break
                val playing = runCatching { player.isPlaying }.getOrDefault(false)
                if (!playing) break
                val pos = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
                val dur = runCatching { player.duration.toLong() }.getOrDefault(0L)
                _playbackState.update {
                    it.copy(
                        currentPositionMs = pos,
                        durationMs = dur.coerceAtLeast(it.durationMs)
                    )
                }
                delay(200L)
            }
        }
    }
}
