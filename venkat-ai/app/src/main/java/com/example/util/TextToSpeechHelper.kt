package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "TextToSpeechHelper"

class TextToSpeechHelper(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentlySpeakingMessageId = MutableStateFlow<String?>(null)
    val currentlySpeakingMessageId: StateFlow<String?> = _currentlySpeakingMessageId.asStateFlow()

    var speechRate: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var pitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(pitch)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpeakingMessageId.value = null
                    val cb = activeOnDoneCallback
                    activeOnDoneCallback = null
                    cb?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpeakingMessageId.value = null
                    activeOnDoneCallback = null
                }
            })
            Log.d(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed: $status")
        }
    }

    private var activeOnDoneCallback: (() -> Unit)? = null

    fun speak(text: String, messageId: String? = null, onDone: (() -> Unit)? = null) {
        activeOnDoneCallback = onDone
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not initialized yet")
            return
        }

        // Clean markdown symbols for cleaner speech
        val cleanedText = cleanMarkdownForSpeech(text)
        if (cleanedText.isBlank()) return

        // Auto-detect Tamil (\u0B80..\u0BFF) and Hindi (\u0900..\u097F)
        val hasTamil = cleanedText.any { it.code in 0x0B80..0x0BFF }
        val hasHindi = cleanedText.any { it.code in 0x0900..0x097F }
        val targetLocale = when {
            hasTamil -> Locale("ta", "IN")
            hasHindi -> Locale("hi", "IN")
            else -> Locale.US
        }

        val result = tts?.setLanguage(targetLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Target language $targetLocale not supported, using default locale")
            tts?.setLanguage(Locale.getDefault())
        }

        _currentlySpeakingMessageId.value = messageId
        _isSpeaking.value = true

        tts?.speak(
            cleanedText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            messageId ?: "venkat_tts_${System.currentTimeMillis()}"
        )
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        _currentlySpeakingMessageId.value = null
    }

    fun toggleSpeak(messageId: String, text: String) {
        if (_isSpeaking.value && _currentlySpeakingMessageId.value == messageId) {
            stop()
        } else {
            speak(text, messageId)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun cleanMarkdownForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), "Code snippet omitted.")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("[#*_~>]"), "")
            .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
            .trim()
    }
}
