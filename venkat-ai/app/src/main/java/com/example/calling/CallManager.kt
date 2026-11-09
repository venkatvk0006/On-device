package com.example.calling

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.wakeword.WakeWordSettings

private const val TAG = "CallManager"

sealed class CallResult {
    data class Success(val dialMethod: String, val phoneNumber: String) : CallResult()
    data class Blocked(val reason: String) : CallResult()
    data class Error(val message: String) : CallResult()
}

/**
 * Handles placing phone calls safely using ACTION_DIAL (default) or ACTION_CALL (when authorized).
 */
object CallManager {

    private var lastCallTimestamp = 0L

    private val emergencyNumbers = setOf(
        "911", "112", "100", "101", "102", "108", "999", "000"
    )

    fun hasDirectCallPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun resetDebounce() {
        lastCallTimestamp = 0L
    }

    @SuppressLint("MissingPermission")
    fun placeCall(context: Context, rawNumber: String, ignoreDebounce: Boolean = false): CallResult {
        val cleanNumber = rawNumber.replace(Regex("[^0-9+]"), "")

        if (cleanNumber.isBlank()) {
            return CallResult.Error("Invalid phone number.")
        }

        // Safety Guard 1: Never automatically dial emergency services
        val digitsOnly = cleanNumber.replace("+", "")
        if (digitsOnly in emergencyNumbers) {
            Log.w(TAG, "Emergency number detected. Restricting to manual dialer.")
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(dialIntent)
            } catch (e: Exception) {
                Log.w(TAG, "No dialer found to handle emergency ACTION_DIAL", e)
            }
            return CallResult.Blocked("Emergency number opened in dialer for safety.")
        }

        // Safety Guard 2: Debounce against rapid successive calls
        val now = SystemClock.uptimeMillis()
        if (!ignoreDebounce && lastCallTimestamp > 0L && now - lastCallTimestamp < 2500L) {
            return CallResult.Blocked("Call already initiated. Please wait.")
        }
        lastCallTimestamp = now

        val settings = WakeWordSettings.getInstance(context)
        val canDirectCall = settings.directCallingEnabled.value && hasDirectCallPermission(context)

        return try {
            if (canDirectCall) {
                // Direct call placement with user consent and permission
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(callIntent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.w(TAG, "No dialer application found to handle ACTION_CALL", e)
                }
                CallResult.Success("ACTION_CALL", cleanNumber)
            } else {
                // Safe default: opens dialer with number pre-filled for user review
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(dialIntent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Log.w(TAG, "No dialer application found to handle ACTION_DIAL", e)
                }
                CallResult.Success("ACTION_DIAL", cleanNumber)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating call for number: $cleanNumber", e)
            CallResult.Error(e.localizedMessage ?: "Failed to open phone dialer.")
        }
    }
}
