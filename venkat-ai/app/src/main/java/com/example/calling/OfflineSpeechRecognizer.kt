package com.example.calling

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "OfflineSpeechRecognizer"

enum class SpeechRecognitionState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}

/**
 * On-device Speech Recognizer configured with EXTRA_PREFER_OFFLINE = true.
 */
class OfflineSpeechRecognizer(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(SpeechRecognitionState.IDLE)
    val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _isOfflineAvailable = MutableStateFlow(true)
    val isOfflineAvailable: StateFlow<Boolean> = _isOfflineAvailable.asStateFlow()

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(
        language: String = "AUTO",
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isRecognitionAvailable()) {
            onError("Speech recognition not available on this device.")
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = SpeechRecognitionState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _state.value = SpeechRecognitionState.PROCESSING
                    }

                    override fun onError(error: Int) {
                        _state.value = SpeechRecognitionState.ERROR
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please speak clearly."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                            else -> "Voice recognition stopped (Code $error)."
                        }
                        onError(errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        _state.value = SpeechRecognitionState.IDLE
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        _lastRecognizedText.value = text
                        onResult(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Critical: Explicit offline preference flag
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

                val targetLocale = when (language) {
                    "TA" -> "ta-IN"
                    "HI" -> "hi-IN"
                    else -> "en-US"
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLocale)
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer", e)
            _state.value = SpeechRecognitionState.ERROR
            onError(e.localizedMessage ?: "Failed to start voice recognition.")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up speech recognizer", e)
        } finally {
            speechRecognizer = null
            _state.value = SpeechRecognitionState.IDLE
        }
    }
}
