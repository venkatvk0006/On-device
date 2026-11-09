package com.example.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.venkat.ai.R

private const val TAG = "WakeWordService"
private const val CHANNEL_ID = "venkat_ai_wake_word_channel"
private const val NOTIFICATION_ID = 2048

/**
 * Foreground Service for background "Hi Kitty" offline wake-word detection.
 * Conforms strictly to Android 14+ foreground service microphone policy.
 */
class WakeWordService : Service() {

    private lateinit var wakeWordEngine: WakeWordEngine
    private lateinit var settings: WakeWordSettings
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "WakeWordService created")
        settings = WakeWordSettings.getInstance(this)
        wakeWordEngine = WakeWordEngine(this)
        createNotificationChannel()

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator could not be created", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            Log.i(TAG, "Stop action received in WakeWordService")
            settings.setWakeWordEnabled(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Start Foreground Notification
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Begin offline acoustic listening
        wakeWordEngine.startListening {
            onWakeWordTriggered()
        }

        return START_STICKY
    }

    private fun onWakeWordTriggered() {
        Log.i(TAG, "Wake word 'Hi Kitty' triggered from WakeWordService!")

        // 1. Chime tone
        if (settings.playChimeSound.value) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
            } catch (e: Exception) {
                Log.w(TAG, "Error playing chime tone", e)
            }
        }

        // 2. Vibration feedback
        if (settings.vibrationFeedback.value) {
            vibrateDevice()
        }

        // 3. Launch or bring MainActivity to the foreground
        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WAKE_WORD_TRIGGERED, true)
            putExtra(EXTRA_WAKE_TIMESTAMP, System.currentTimeMillis())
        }

        try {
            startActivity(openIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct activity start restricted by OS. Posting heads-up wake notification", e)
        }

        // 4. Update notification with high-priority heads-up wake alert for Android 10+ background restriction
        notifyWakeAlert(openIntent)
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrator error", e)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VENKAT AI • Offline Listening")
            .setContentText("Say \"Hi Kitty\" to activate assistant")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Listening",
                stopPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                "Open App",
                openAppPendingIntent
            )
            .build()
    }

    private fun notifyWakeAlert(targetIntent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wakeNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🐱 \"Hi Kitty\" Activated!")
            .setContentText("VENKAT AI is listening. Tap to talk!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, wakeNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VENKAT AI Wake Word"
            val descriptionText = "Persistent microphone notification for offline 'Hi Kitty' detection"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "WakeWordService destroyed")
        wakeWordEngine.stopListening()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing tone generator", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.wakeword.ACTION_START"
        const val ACTION_STOP = "com.example.wakeword.ACTION_STOP"
        const val EXTRA_WAKE_WORD_TRIGGERED = "extra_wake_word_triggered"
        const val EXTRA_WAKE_TIMESTAMP = "extra_wake_timestamp"

        fun startService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
