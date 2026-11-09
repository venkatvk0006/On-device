package com.example.wakeword

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted settings for the offline "Hi Kitty" wake-word detection engine and background service.
 */
class WakeWordSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isWakeWordEnabled = MutableStateFlow(prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false))
    val isWakeWordEnabled: StateFlow<Boolean> = _isWakeWordEnabled.asStateFlow()

    private val _playChimeSound = MutableStateFlow(prefs.getBoolean(KEY_PLAY_CHIME, true))
    val playChimeSound: StateFlow<Boolean> = _playChimeSound.asStateFlow()

    private val _vibrationFeedback = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION, true))
    val vibrationFeedback: StateFlow<Boolean> = _vibrationFeedback.asStateFlow()

    private val _sensitivity = MutableStateFlow(prefs.getFloat(KEY_SENSITIVITY, 0.6f))
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _directCallingEnabled = MutableStateFlow(prefs.getBoolean(KEY_DIRECT_CALLING, false))
    val directCallingEnabled: StateFlow<Boolean> = _directCallingEnabled.asStateFlow()

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
        _isWakeWordEnabled.value = enabled
    }

    fun setPlayChimeSound(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PLAY_CHIME, enabled).apply()
        _playChimeSound.value = enabled
    }

    fun setVibrationFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
        _vibrationFeedback.value = enabled
    }

    fun setSensitivity(value: Float) {
        val clamped = value.coerceIn(0.1f, 1.0f)
        prefs.edit().putFloat(KEY_SENSITIVITY, clamped).apply()
        _sensitivity.value = clamped
    }

    fun setDirectCallingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIRECT_CALLING, enabled).apply()
        _directCallingEnabled.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "venkat_ai_wake_word_prefs"
        private const val KEY_WAKE_WORD_ENABLED = "key_wake_word_enabled"
        private const val KEY_PLAY_CHIME = "key_play_chime"
        private const val KEY_VIBRATION = "key_vibration"
        private const val KEY_SENSITIVITY = "key_sensitivity"
        private const val KEY_DIRECT_CALLING = "key_direct_calling"

        @Volatile
        private var instance: WakeWordSettings? = null

        fun getInstance(context: Context): WakeWordSettings {
            return instance ?: synchronized(this) {
                instance ?: WakeWordSettings(context.applicationContext).also { instance = it }
            }
        }
    }
}
