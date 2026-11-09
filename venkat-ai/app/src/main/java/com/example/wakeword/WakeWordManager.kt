package com.example.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller singleton for interacting with WakeWordService and WakeWordEngine.
 */
class WakeWordManager private constructor(private val context: Context) {

    val settings: WakeWordSettings = WakeWordSettings.getInstance(context)

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canStartWakeWordService(): Boolean {
        return hasMicrophonePermission() && hasNotificationPermission()
    }

    fun enableWakeWord() {
        if (!canStartWakeWordService()) return
        settings.setWakeWordEnabled(true)
        WakeWordService.startService(context)
        _isServiceActive.value = true
    }

    fun disableWakeWord() {
        settings.setWakeWordEnabled(false)
        WakeWordService.stopService(context)
        _isServiceActive.value = false
    }

    fun toggleWakeWord(): Boolean {
        return if (_isServiceActive.value) {
            disableWakeWord()
            false
        } else {
            enableWakeWord()
            true
        }
    }

    companion object {
        @Volatile
        private var instance: WakeWordManager? = null

        fun getInstance(context: Context): WakeWordManager {
            return instance ?: synchronized(this) {
                instance ?: WakeWordManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
