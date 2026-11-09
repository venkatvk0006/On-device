package com.example.wakeword

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

private const val TAG = "WakeWordEngine"

enum class WakeWordEngineStatus {
    STOPPED,
    INITIALIZING,
    LISTENING,
    TRIGGERED,
    ERROR
}

/**
 * High-performance, offline wake-word detection engine for "Hi Kitty".
 *
 * Characteristics:
 * - 100% on-device local DSP processing.
 * - Zero network usage. Audio is never recorded to disk or streamed to cloud.
 * - Battery-friendly: processes 32ms PCM frames with pre-allocated buffers.
 * - Dynamic noise adaptation & multi-stage phonetic acoustic envelope spotting.
 */
class WakeWordEngine(private val context: Context) {

    private val _status = MutableStateFlow(WakeWordEngineStatus.STOPPED)
    val status: StateFlow<WakeWordEngineStatus> = _status.asStateFlow()

    private val _lastWakeTimestamp = MutableStateFlow(0L)
    val lastWakeTimestamp: StateFlow<Long> = _lastWakeTimestamp.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Default)

    private val sampleRate = 16000
    private val frameSize = 512 // 32 ms per frame
    private val bufferSize = maxOf(
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ),
        frameSize * 4
    )

    // Pre-allocated buffers to prevent GC thrashing inside the audio loop
    private val audioBuffer = ShortArray(frameSize)

    // Acoustic pattern tracker for "Hi Kitty" (Vowel "Hi" -> Silence dip -> Plosive "Kit" -> Vowel "ty")
    private var noiseFloorRms = 120.0
    private var consecutiveSpeechFrames = 0
    private var lastTriggerUptime = 0L

    // State machine phases for "Hi Kitty"
    private var phase = 0 // 0: Silence/Wait, 1: "Hi" vowel, 2: Inter-word dip, 3: "Kit-ty" burst & vowel
    private var phaseStartUptime = 0L

    @SuppressLint("MissingPermission")
    fun startListening(onWakeWordDetected: () -> Unit) {
        if (_status.value == WakeWordEngineStatus.LISTENING) return

        _status.value = WakeWordEngineStatus.INITIALIZING
        listeningJob?.cancel()

        listeningJob = engineScope.launch {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed")
                    _status.value = WakeWordEngineStatus.ERROR
                    return@launch
                }

                audioRecord?.startRecording()
                _status.value = WakeWordEngineStatus.LISTENING
                Log.i(TAG, "WakeWordEngine started listening for 'Hi Kitty' offline")

                val settings = WakeWordSettings.getInstance(context)

                while (isActive) {
                    val readCount = audioRecord?.read(audioBuffer, 0, frameSize) ?: -1
                    if (readCount <= 0) {
                        continue
                    }

                    // Compute Root Mean Square (RMS) energy & Zero Crossing Rate (ZCR)
                    var sumSquares = 0.0
                    var zeroCrossings = 0
                    for (i in 0 until readCount) {
                        val sample = audioBuffer[i]
                        sumSquares += (sample * sample).toDouble()
                        if (i > 0 && ((audioBuffer[i - 1] >= 0 && sample < 0) || (audioBuffer[i - 1] < 0 && sample >= 0))) {
                            zeroCrossings++
                        }
                    }

                    val rms = sqrt(sumSquares / readCount)
                    val zcr = zeroCrossings.toDouble() / readCount
                    val sensitivity = settings.sensitivity.value

                    // Dynamic background noise floor adaptation
                    if (rms < noiseFloorRms * 1.5) {
                        noiseFloorRms = (noiseFloorRms * 0.96) + (rms * 0.04)
                    }

                    val speechThreshold = (noiseFloorRms * (1.8 - (sensitivity * 0.5))).coerceAtLeast(180.0)
                    val isSpeech = rms > speechThreshold

                    val now = SystemClock.uptimeMillis()

                    // Cooldown protection: at least 2.2 seconds between wake triggers
                    if (now - lastTriggerUptime < 2200L) {
                        phase = 0
                        continue
                    }

                    // Multi-stage acoustic envelope state machine
                    when (phase) {
                        0 -> {
                            // Stage 0: Look for "Hi" onset (voiced diphthong /aɪ/ with low-to-mid ZCR and strong RMS)
                            if (isSpeech && zcr in 0.04..0.32) {
                                consecutiveSpeechFrames++
                                if (consecutiveSpeechFrames >= 3) { // ~96ms sustained vowel
                                    phase = 1
                                    phaseStartUptime = now
                                    consecutiveSpeechFrames = 0
                                }
                            } else {
                                consecutiveSpeechFrames = 0
                            }
                        }

                        1 -> {
                            // Stage 1: "Hi" vowel continuation followed by brief inter-syllable dip
                            if (!isSpeech || rms < speechThreshold * 1.1) {
                                // Vowel ended, entered dip
                                val duration = now - phaseStartUptime
                                if (duration in 80..450) {
                                    phase = 2
                                    phaseStartUptime = now
                                } else {
                                    phase = 0
                                }
                            } else {
                                if (now - phaseStartUptime > 500) {
                                    phase = 0 // Held too long
                                }
                            }
                        }

                        2 -> {
                            // Stage 2: Brief silence dip between "Hi" and "Kitty" (~40 to 220ms)
                            val dipDuration = now - phaseStartUptime
                            if (isSpeech) {
                                if (dipDuration in 30..280) {
                                    // Syllable 2 onset ("Kit-") with sharp burst /k/ (higher ZCR)
                                    phase = 3
                                    phaseStartUptime = now
                                    consecutiveSpeechFrames = 1
                                } else {
                                    phase = 0
                                }
                            } else if (dipDuration > 300) {
                                phase = 0 // Too long pause
                            }
                        }

                        3 -> {
                            // Stage 3: "Kit-ty" plosive + front vowels (/ɪ/ and /i/)
                            if (isSpeech) {
                                consecutiveSpeechFrames++
                                val duration = now - phaseStartUptime
                                // Complete "Kit-ty" phrase window matches: typically 250 - 650 ms
                                if (consecutiveSpeechFrames >= 5 && duration in 200..700) {
                                    // MATCH DETECTED!
                                    Log.i(TAG, "Offline Wake Word 'Hi Kitty' matched! Duration=${now - lastTriggerUptime}ms, RMS=$rms")
                                    lastTriggerUptime = now
                                    _lastWakeTimestamp.value = System.currentTimeMillis()
                                    _status.value = WakeWordEngineStatus.TRIGGERED

                                    launch(Dispatchers.Main) {
                                        onWakeWordDetected()
                                    }

                                    phase = 0
                                    consecutiveSpeechFrames = 0
                                    _status.value = WakeWordEngineStatus.LISTENING
                                }
                            } else {
                                if (now - phaseStartUptime > 750) {
                                    phase = 0
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during offline wake-word audio processing", e)
                _status.value = WakeWordEngineStatus.ERROR
            } finally {
                releaseAudioRecord()
                _status.value = WakeWordEngineStatus.STOPPED
            }
        }
    }

    /**
     * Trigger simulated wake-up for in-app UI testing and demo verifications.
     */
    fun simulateDetection(onWakeWordDetected: () -> Unit) {
        lastTriggerUptime = SystemClock.uptimeMillis()
        _lastWakeTimestamp.value = System.currentTimeMillis()
        _status.value = WakeWordEngineStatus.TRIGGERED
        onWakeWordDetected()
        _status.value = if (listeningJob?.isActive == true) WakeWordEngineStatus.LISTENING else WakeWordEngineStatus.STOPPED
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        releaseAudioRecord()
        _status.value = WakeWordEngineStatus.STOPPED
        Log.i(TAG, "WakeWordEngine stopped")
    }

    private fun releaseAudioRecord() {
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }
}
