package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "DeviceDutyManager"

sealed class DutyResult {
    data class Success(
        val message: String,
        val tamilMessage: String,
        val hindiMessage: String,
        val actionType: ActionType
    ) : DutyResult()

    data object NotADuty : DutyResult()
}

enum class ActionType {
    TORCH_ON, TORCH_OFF,
    OPEN_SETTINGS, OPEN_CAMERA, OPEN_GALLERY, OPEN_CALCULATOR, OPEN_BROWSER,
    OPEN_WIFI, OPEN_BLUETOOTH, OPEN_INTERNET,
    INCREASE_VOLUME, DECREASE_VOLUME,
    BATTERY_STATUS, DEVICE_INFO,
    LAUNCH_APP, HI_KITTY, CALL_CONTACT
}

object DeviceDutyManager {

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    /**
     * Executes supported Android device control duties and returns localized status messages.
     */
    fun processDutyCommand(context: Context, rawPrompt: String, lang: String): DutyResult {
        val prompt = rawPrompt.lowercase().trim()

        // 1. "Hi Kitty" Wake phrase
        if (prompt.contains("hi kitty") || prompt.contains("kitty") || prompt.contains("ஹாய் கிட்டி") || prompt.contains("हाय किट्टी")) {
            val commandAfterWake = prompt
                .replace("hi kitty", "")
                .replace("kitty", "")
                .replace("ஹாய் கிட்டி", "")
                .replace("हाय किट्टी", "")
                .trim()

            if (commandAfterWake.isNotBlank()) {
                val subResult = processDutyCommand(context, commandAfterWake, lang)
                if (subResult !is DutyResult.NotADuty) {
                    return subResult
                }
            }

            return DutyResult.Success(
                message = "Meow! Hi there, I'm listening. How can I assist you?",
                tamilMessage = "வணக்கம்! நான் கேட்டுக்கொண்டிருக்கிறேன். என்ன உதவி செய்ய வேண்டும்?",
                hindiMessage = "नमस्ते! मैं सुन रहा हूँ। मैं आपकी क्या मदद कर सकता हूँ?",
                actionType = ActionType.HI_KITTY
            )
        }

        // 1.5 Calling Commands (Tamil, English, Hindi)
        val callParse = com.example.calling.VoiceCommandParser.parseCallCommand(rawPrompt)
        if (callParse.isCallIntent && !callParse.targetNameOrNumber.isNullOrBlank()) {
            val target = callParse.targetNameOrNumber
            return DutyResult.Success(
                message = "Calling $target... Requesting confirmation.",
                tamilMessage = "$target-க்கு அழைப்பு விடுக்கப்படுகிறது...",
                hindiMessage = "$target को कॉल करने का अनुरोध...",
                actionType = ActionType.CALL_CONTACT
            )
        }

        // 2. Torch / Flashlight Commands
        if (isTorchOnCommand(prompt)) {
            val success = setTorchState(context, true)
            return if (success) {
                DutyResult.Success(
                    message = "Flashlight turned on.",
                    tamilMessage = "டார்ச் ஆன் செய்யப்பட்டது.",
                    hindiMessage = "टॉर्च चालू कर दी गई है।",
                    actionType = ActionType.TORCH_ON
                )
            } else {
                DutyResult.Success(
                    message = "Flashlight unavailable or permission required.",
                    tamilMessage = "டார்ச் வசதி கிடைக்கவில்லை அல்லது அனுமதி தேவைப்படுகிறது.",
                    hindiMessage = "टॉर्च उपलब्ध नहीं है या अनुमति आवश्यक है।",
                    actionType = ActionType.TORCH_ON
                )
            }
        }

        if (isTorchOffCommand(prompt)) {
            val success = setTorchState(context, false)
            return if (success) {
                DutyResult.Success(
                    message = "Flashlight turned off.",
                    tamilMessage = "டார்ச் ஆஃப் செய்யப்பட்டது.",
                    hindiMessage = "टॉर्च बंद कर दी गई है।",
                    actionType = ActionType.TORCH_OFF
                )
            } else {
                DutyResult.Success(
                    message = "Flashlight could not be turned off.",
                    tamilMessage = "டார்ச்சை அணைக்க முடியவில்லை.",
                    hindiMessage = "टॉर्च बंद नहीं की जा सकी।",
                    actionType = ActionType.TORCH_OFF
                )
            }
        }

        // 3. Open Settings
        if (prompt.contains("open settings") || prompt.contains("device settings") || prompt.contains("அமைப்புகளை திற") || prompt.contains("செட்டிங்ஸ்") || prompt.contains("सेटिंग्स खोलो")) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return DutyResult.Success(
                message = "Opening device settings.",
                tamilMessage = "சாதன அமைப்புகளைத் திறக்கிறேன்.",
                hindiMessage = "डिवाइस सेटिंग्स खोली जा रही हैं।",
                actionType = ActionType.OPEN_SETTINGS
            )
        }

        // 4. Open Camera
        if (prompt.contains("open camera") || prompt.contains("launch camera") || prompt.contains("கேமராவை திற") || prompt.contains("கேமரா") || prompt.contains("कैमरा खोलो")) {
            val launched = launchCamera(context)
            return if (launched) {
                DutyResult.Success(
                    message = "Opening camera.",
                    tamilMessage = "கேமராவைத் திறக்கிறேன்.",
                    hindiMessage = "कैमरा खोला जा रहा है।",
                    actionType = ActionType.OPEN_CAMERA
                )
            } else {
                DutyResult.Success(
                    message = "Camera application could not be opened.",
                    tamilMessage = "கேமரா செயலியைத் திறக்க முடியவில்லை.",
                    hindiMessage = "कैमरा ऐप नहीं खोला जा सका।",
                    actionType = ActionType.OPEN_CAMERA
                )
            }
        }

        // 5. Open Gallery
        if (prompt.contains("open gallery") || prompt.contains("open photos") || prompt.contains("கேலரி திற") || prompt.contains("புகைப்படங்கள்") || prompt.contains("गैलरी खोलो")) {
            val launched = launchGallery(context)
            return if (launched) {
                DutyResult.Success(
                    message = "Opening gallery.",
                    tamilMessage = "புகைப்பட கேலரியைத் திறக்கிறேன்.",
                    hindiMessage = "गैलरी खोली जा रही है।",
                    actionType = ActionType.OPEN_GALLERY
                )
            } else {
                DutyResult.Success(
                    message = "Gallery application could not be found.",
                    tamilMessage = "கேலரி செயலியைத் திறக்க முடியவில்லை.",
                    hindiMessage = "गैलरी ऐप नहीं मिला।",
                    actionType = ActionType.OPEN_GALLERY
                )
            }
        }

        // 6. Open Calculator
        if (prompt.contains("open calculator") || prompt.contains("கணக்கீட்டு கருவி") || prompt.contains("கால்குலேட்டர்") || prompt.contains("कैलकुलेटर खोलो")) {
            val launched = launchCalculator(context)
            return if (launched) {
                DutyResult.Success(
                    message = "Opening calculator.",
                    tamilMessage = "கால்குலேட்டரைத் திறக்கிறேன்.",
                    hindiMessage = "कैलकुलेटर खोला जा रहा है।",
                    actionType = ActionType.OPEN_CALCULATOR
                )
            } else {
                DutyResult.Success(
                    message = "Calculator app could not be opened.",
                    tamilMessage = "கால்குலேட்டர் செயலியைத் திறக்க முடியவில்லை.",
                    hindiMessage = "कैलकुलेटर ऐप नहीं खोला जा सका।",
                    actionType = ActionType.OPEN_CALCULATOR
                )
            }
        }

        // 7. Open Browser
        if (prompt.contains("open browser") || prompt.contains("open chrome") || prompt.contains("இணைய உலாவி") || prompt.contains("உலாவியை திற") || prompt.contains("ब्राउज़र खोलो")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return DutyResult.Success(
                message = "Opening web browser.",
                tamilMessage = "இணைய உலாவியைத் திறக்கிறேன்.",
                hindiMessage = "वेब ब्राउज़र खोला जा रहा है।",
                actionType = ActionType.OPEN_BROWSER
            )
        }

        // 8. Turn on Wi-Fi / Internet Settings
        if (isWifiOrInternetCommand(prompt)) {
            val isInternet = prompt.contains("internet") || prompt.contains("இணையம்") || prompt.contains("इंटरनेट") || prompt.contains("data")
            openWifiOrInternetSettings(context)
            return DutyResult.Success(
                message = if (isInternet) "Opening Internet & network connectivity settings." else "Opening Wi-Fi settings panel.",
                tamilMessage = if (isInternet) "இணைய இணைப்பு அமைப்புகளைத் திறக்கிறேன்." else "வைஃபை அமைப்புகளைத் திறக்கிறேன்.",
                hindiMessage = if (isInternet) "इंटरनेट और नेटवर्क सेटिंग्स खोली जा रही हैं।" else "वाई-फ़ाई सेटिंग्स खोली जा रही हैं।",
                actionType = if (isInternet) ActionType.OPEN_INTERNET else ActionType.OPEN_WIFI
            )
        }

        // 9. Turn on Bluetooth / Bluetooth Settings
        if (prompt.contains("bluetooth") || prompt.contains("ப்ளூடூத்") || prompt.contains("ब्लूटूथ")) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return DutyResult.Success(
                message = "Opening Bluetooth settings.",
                tamilMessage = "ப்ளூடூத் அமைப்புகளைத் திறக்கிறேன்.",
                hindiMessage = "ब्लूटूथ सेटिंग्स खोली जा रही हैं।",
                actionType = ActionType.OPEN_BLUETOOTH
            )
        }

        // 10. Volume Controls
        if (prompt.contains("increase volume") || prompt.contains("volume up") || prompt.contains("சத்தத்தை கூட்டு") || prompt.contains("ஆடியோ ஏற்று") || prompt.contains("आवाज़ बढ़ाओ")) {
            adjustVolume(context, AudioManager.ADJUST_RAISE)
            return DutyResult.Success(
                message = "Media volume increased.",
                tamilMessage = "ஒலி அளவு அதிகரிக்கப்பட்டது.",
                hindiMessage = "मीडिया आवाज़ बढ़ा दी गई है।",
                actionType = ActionType.INCREASE_VOLUME
            )
        }

        if (prompt.contains("decrease volume") || prompt.contains("volume down") || prompt.contains("சத்தத்தை குறை") || prompt.contains("ஆடியோ குறை") || prompt.contains("आवाज़ कम करो")) {
            adjustVolume(context, AudioManager.ADJUST_LOWER)
            return DutyResult.Success(
                message = "Media volume decreased.",
                tamilMessage = "ஒலி அளவு குறைக்கப்பட்டது.",
                hindiMessage = "मीडिया आवाज़ कम कर दी गई है।",
                actionType = ActionType.DECREASE_VOLUME
            )
        }

        // 11. Battery Status
        if (prompt.contains("battery") || prompt.contains("பேட்டரி") || prompt.contains("बैटरी")) {
            val info = getBatteryInfo(context)
            return DutyResult.Success(
                message = "Battery status: $info",
                tamilMessage = "பேட்டரி விவரம்: $info",
                hindiMessage = "बैटरी की स्थिति: $info",
                actionType = ActionType.BATTERY_STATUS
            )
        }

        // 12. Device Information
        if (prompt.contains("device information") || prompt.contains("device info") || prompt.contains("phone info") || prompt.contains("சாதன விவரம்") || prompt.contains("டிவைஸ் தகவல்") || prompt.contains("डिवाइस जानकारी")) {
            val info = getDeviceInfo(context)
            return DutyResult.Success(
                message = info,
                tamilMessage = info,
                hindiMessage = info,
                actionType = ActionType.DEVICE_INFO
            )
        }

        // 13. Other Apps (Instagram, YouTube, WhatsApp, Spotify, Maps, or any installed app)
        val app = detectAppLaunch(context, prompt)
        if (app != null) {
            val launched = launchApp(context, app)
            return if (launched) {
                DutyResult.Success(
                    message = "Opening ${app.displayName}...",
                    tamilMessage = "${app.displayName}-ஐ திறக்கிறேன்...",
                    hindiMessage = "${app.displayName} खोला जा रहा है...",
                    actionType = ActionType.LAUNCH_APP
                )
            } else {
                DutyResult.Success(
                    message = "Could not find ${app.displayName}. Searching in Play Store...",
                    tamilMessage = "${app.displayName}-ஐ திறக்க முடியவில்லை. Play Store-க்கு செல்கிறேன்...",
                    hindiMessage = "${app.displayName} नहीं मिला। प्ले स्टोर पर खोजा जा रहा है...",
                    actionType = ActionType.LAUNCH_APP
                )
            }
        }

        return DutyResult.NotADuty
    }

    private fun isTorchOnCommand(prompt: String): Boolean {
        return prompt.contains("turn on torch") ||
                prompt.contains("torch on") ||
                prompt.contains("turn on flashlight") ||
                prompt.contains("flashlight on") ||
                prompt.contains("switch on torch") ||
                prompt.contains("டார்ச் போடு") ||
                prompt.contains("டார்ச் ஆன்") ||
                prompt.contains("விளக்கை ஏற்று") ||
                prompt.contains("टॉर्च चालू")
    }

    private fun isTorchOffCommand(prompt: String): Boolean {
        return prompt.contains("turn off torch") ||
                prompt.contains("torch off") ||
                prompt.contains("turn off flashlight") ||
                prompt.contains("flashlight off") ||
                prompt.contains("switch off torch") ||
                prompt.contains("டார்ச் ஆப்") ||
                prompt.contains("டார்ச் அணை") ||
                prompt.contains("டார்ச்சை அணை") ||
                prompt.contains("टॉर्च बंद")
    }

    private fun isWifiOrInternetCommand(prompt: String): Boolean {
        return prompt.contains("turn on wifi") ||
                prompt.contains("open wifi") ||
                prompt.contains("wifi on") ||
                prompt.contains("wifi settings") ||
                prompt.contains("turn on internet") ||
                prompt.contains("internet on") ||
                prompt.contains("open internet") ||
                prompt.contains("mobile data") ||
                prompt.contains("data settings") ||
                prompt.contains("வைஃபை") ||
                prompt.contains("இணையம்") ||
                prompt.contains("वाई-फ़ाई") ||
                prompt.contains("इंटरनेट")
    }

    fun setTorchState(context: Context, state: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return false
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull() ?: return false

            cameraManager.setTorchMode(cameraId, state)
            _isTorchOn.value = state
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle torch: $state", e)
            false
        }
    }

    fun openWifiOrInternetSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    private fun adjustVolume(context: Context, direction: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to adjust volume", e)
        }
    }

    private fun launchCamera(context: Context): Boolean {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun launchGallery(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCalculator(context: Context): Boolean {
        val calcPackages = listOf(
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.sec.android.app.popupcalculator",
            "com.miui.calculator"
        )
        for (pkg in calcPackages) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALCULATOR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getBatteryInfo(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
        val isCharging = bm?.isCharging == true
        val statusText = if (isCharging) "Charging ⚡" else "Discharging (Battery power)"
        return "$level% ($statusText)"
    }

    private fun getDeviceInfo(context: Context): String {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024 * 1024 * 1024.0)
        val availRamGb = memInfo.availMem / (1024 * 1024 * 1024.0)

        return """
📱 **Device Information:**
• **Model:** ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}
• **Android OS:** Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
• **Device Hardware:** ${Build.HARDWARE}
• **RAM:** ${String.format("%.1f", availRamGb)} GB available / ${String.format("%.1f", totalRamGb)} GB total
• **AI Engine:** On-Device Neural Local Engine
• **System Mode:** Local Offline Processing
""".trimIndent()
    }

    data class AppTarget(
        val displayName: String,
        val packageName: String,
        val webFallback: String
    )

    private fun detectAppLaunch(context: Context, prompt: String): AppTarget? {
        val lower = prompt.lowercase().trim()
        val wellKnown = when {
            lower.contains("instagram") || lower.contains("இன்ஸ்டாகிராம்") || lower.contains("इंस्टाग्राम") -> AppTarget(
                displayName = "Instagram",
                packageName = "com.instagram.android",
                webFallback = "https://www.instagram.com"
            )
            lower.contains("youtube") || lower.contains("யூடியூப்") || lower.contains("यूट्यूब") -> AppTarget(
                displayName = "YouTube",
                packageName = "com.google.android.youtube",
                webFallback = "https://www.youtube.com"
            )
            lower.contains("whatsapp") || lower.contains("வாட்ஸ்அப்") || lower.contains("व्हाट्सएप") -> AppTarget(
                displayName = "WhatsApp",
                packageName = "com.whatsapp",
                webFallback = "https://web.whatsapp.com"
            )
            lower.contains("spotify") || lower.contains("ஸ்பாட்டிஃபை") || lower.contains("स्पॉटिफ़ाई") -> AppTarget(
                displayName = "Spotify",
                packageName = "com.spotify.music",
                webFallback = "https://open.spotify.com"
            )
            lower.contains("maps") || lower.contains("மேப்ஸ்") || lower.contains("गूगल मैप्स") -> AppTarget(
                displayName = "Google Maps",
                packageName = "com.google.android.apps.maps",
                webFallback = "https://maps.google.com"
            )
            else -> null
        }
        if (wellKnown != null) return wellKnown

        // Generic "open <app>" or "launch <app>" detection
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("திற ")) {
            val candidateName = lower
                .removePrefix("open ")
                .removePrefix("launch ")
                .removePrefix("திற ")
                .removeSuffix(" app")
                .removeSuffix(" செயலியை")
                .trim()

            // Skip built-in system duties already handled earlier
            val systemDuties = setOf(
                "settings", "camera", "gallery", "calculator", "browser", "chrome",
                "wifi", "wi-fi", "bluetooth", "torch", "flashlight", "battery", "info"
            )
            if (candidateName in systemDuties || candidateName.length < 2) {
                return null
            }

            // Check if installed on device
            try {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(0)
                val matched = apps.firstOrNull { appInfo ->
                    val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                    label == candidateName || label.contains(candidateName)
                }

                if (matched != null) {
                    val label = pm.getApplicationLabel(matched).toString()
                    return AppTarget(
                        displayName = label,
                        packageName = matched.packageName,
                        webFallback = "https://play.google.com/store/apps/details?id=${matched.packageName}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking installed applications", e)
            }

            // Fallback target for Play Store / Web
            val cleanDisplayName = candidateName.replaceFirstChar { it.uppercase() }
            return AppTarget(
                displayName = cleanDisplayName,
                packageName = "com.$candidateName.app",
                webFallback = "https://play.google.com/store/search?q=$candidateName"
            )
        }

        return null
    }

    fun launchApp(context: Context, app: AppTarget): Boolean {
        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }

            val installedApps = pm.getInstalledApplications(0)
            val matched = installedApps.firstOrNull { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                label == app.displayName.lowercase() || label.contains(app.displayName.lowercase())
            }

            if (matched != null) {
                val intent = pm.getLaunchIntentForPackage(matched.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return true
                }
            }

            val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (playStoreIntent.resolveActivity(pm) != null) {
                context.startActivity(playStoreIntent)
                return true
            }

            if (app.webFallback.isNotBlank()) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(app.webFallback)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app: ${app.displayName}", e)
        }
        return false
    }
}
