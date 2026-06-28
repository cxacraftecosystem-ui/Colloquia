package com.transcriptai.app.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

enum class RecState { IDLE, RECORDING, PAUSED }

/**
 * Singleton owner of the MediaRecorder so recording state is shared between the foreground service
 * (which keeps it alive in the background) and the Compose UI. Records AAC/m4a, supports
 * pause/resume/stop, and exposes elapsed time + amplitude for the live level meter.
 */
object RecorderController {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var segmentStartMs = 0L
    private var accumulatedMs = 0L

    private val _state = MutableStateFlow(RecState.IDLE)
    val state: StateFlow<RecState> = _state

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs

    val isActive: Boolean get() = _state.value != RecState.IDLE

    fun currentElapsedMs(): Long {
        val live = if (_state.value == RecState.RECORDING) System.currentTimeMillis() - segmentStartMs else 0L
        return accumulatedMs + live
    }

    fun amplitude(): Int = runCatching {
        if (_state.value == RecState.RECORDING) recorder?.maxAmplitude ?: 0 else 0
    }.getOrDefault(0)

    fun start(context: Context, noiseCancellation: Boolean = true, highQuality: Boolean = true): File {
        if (isActive) return outputFile!!
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${UUID.randomUUID().hex()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        rec.apply {
            // VOICE_RECOGNITION routes capture through the platform's voice pre-processing (noise
            // suppression / AGC) — cleaner audio + better transcription accuracy than raw MIC. Falls
            // back to MIC if the device doesn't support it. MIC = no processing (when NC is off).
            if (noiseCancellation) {
                runCatching { setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION) }
                    .onFailure { setAudioSource(MediaRecorder.AudioSource.MIC) }
            } else {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(if (highQuality) 128_000 else 64_000)
            setAudioSamplingRate(if (highQuality) 44_100 else 22_050)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
        outputFile = file
        accumulatedMs = 0L
        segmentStartMs = System.currentTimeMillis()
        _state.value = RecState.RECORDING
        _elapsedMs.value = 0L
        return file
    }

    fun pause() {
        if (_state.value != RecState.RECORDING) return
        runCatching { recorder?.pause() }
        accumulatedMs += System.currentTimeMillis() - segmentStartMs
        _state.value = RecState.PAUSED
    }

    fun resume() {
        if (_state.value != RecState.PAUSED) return
        runCatching { recorder?.resume() }
        segmentStartMs = System.currentTimeMillis()
        _state.value = RecState.RECORDING
    }

    /** Stop and finalize; returns the recorded file (or null on failure). */
    fun stop(): Pair<File, Int>? {
        val rec = recorder ?: return null
        val file = outputFile
        val durationMs = currentElapsedMs()
        runCatching {
            rec.stop()
        }
        runCatching { rec.release() }
        recorder = null
        outputFile = null
        _state.value = RecState.IDLE
        accumulatedMs = 0L
        _elapsedMs.value = 0L
        return if (file != null && file.exists()) file to (durationMs / 1000).toInt() else null
    }

    /** Stop and throw away the recording (no upload). Deletes the partial file. */
    fun discard() {
        val rec = recorder
        val file = outputFile
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        runCatching { file?.delete() }
        recorder = null
        outputFile = null
        _state.value = RecState.IDLE
        accumulatedMs = 0L
        _elapsedMs.value = 0L
    }

    fun tick() {
        _elapsedMs.value = currentElapsedMs()
    }

    private fun UUID.hex(): String = toString().replace("-", "")
}
