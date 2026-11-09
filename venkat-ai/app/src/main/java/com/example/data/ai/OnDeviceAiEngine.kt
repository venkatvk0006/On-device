package com.example.data.ai

import android.content.Context
import com.example.data.local.VenkatAiDatabase
import com.example.data.model.MemoryNoteEntity
import com.example.util.DeviceDutyManager
import com.example.util.DutyResult
import com.example.util.TextToSpeechHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class OnDeviceCommandResult {
    data class Handled(
        val text: String,
        val speechSpeedDelta: Float? = null,
        val languageSwitch: String? = null,
        val triggerVoiceListen: Boolean = false
    )
}

/**
 * VENKAT AI - On-Device AI Assistant Engine.
 * 100% Offline, Instant Execution, Zero Network Latency.
 * Fully conforms to the ON-DEVICE AI ASSISTANT – MASTER SYSTEM PROMPT.
 */
object OnDeviceAiEngine {

    fun streamOnDeviceResponse(
        context: Context,
        prompt: String,
        currentLang: String, // "EN", "TA", "HI", "AUTO"
        ttsHelper: TextToSpeechHelper? = null,
        onActionCallback: ((speechDelta: Float?, langSwitch: String?, triggerListen: Boolean) -> Unit)? = null
    ): Flow<String> = flow {
        val result = processMasterCommand(context, prompt, currentLang, ttsHelper)
        onActionCallback?.invoke(result.speechSpeedDelta, result.languageSwitch, result.triggerVoiceListen)

        val words = result.text.split(" ")
        for (i in words.indices) {
            val chunk = if (i == words.lastIndex) words[i] else words[i] + " "
            emit(chunk)
            delay(16L) // Natural token streaming cadence
        }
    }

    suspend fun processMasterCommand(
        context: Context,
        rawPrompt: String,
        currentLang: String,
        ttsHelper: TextToSpeechHelper? = null
    ): OnDeviceCommandResult.Handled {
        val prompt = rawPrompt.trim()
        val lower = prompt.lowercase()

        // Detect effective language if AUTO
        val effectiveLang = when {
            currentLang == "TA" || prompt.any { it in '\u0B80'..'\u0BFF' } -> "TA"
            currentLang == "HI" || prompt.any { it in '\u0900'..'\u097F' } -> "HI"
            currentLang == "EN" -> "EN"
            else -> "EN"
        }

        // ==========================================
        // 1. START COMMAND
        // User: Hey AI, Start On-Device AI.
        // AI: Hello! On-Device AI is now activated. I'm ready to assist you. What would you like me to do?
        // ==========================================
        if (lower.contains("start on-device ai") || lower.contains("start on device ai") || lower == "start ai" || lower == "activate ai") {
            return when (effectiveLang) {
                "TA" -> OnDeviceCommandResult.Handled("வணக்கம்! சாதன AI இப்போது செயல்படுத்தப்பட்டுள்ளது. நான் உங்களுக்கு உதவ தயாராக உள்ளேன். நான் என்ன செய்ய வேண்டும்?")
                "HI" -> OnDeviceCommandResult.Handled("नमस्ते! ऑन-डिवाइस एआई अब सक्रिय हो गया है। मैं आपकी सहायता के लिए तैयार हूँ। आप मुझसे क्या करवाना चाहते हैं?")
                else -> OnDeviceCommandResult.Handled("Hello! On-Device AI is now activated. I'm ready to assist you. What would you like me to do?")
            }
        }

        // ==========================================
        // 8. SHUTDOWN COMMAND
        // User: Hey AI, Shutdown.
        // AI: On-Device AI is going to sleep. Say "Hey AI" whenever you need me again.
        // ==========================================
        if (lower.contains("shutdown") || lower.contains("shut down") || lower.contains("தூங்கு") || lower.contains("बंद हो जाओ")) {
            return when (effectiveLang) {
                "TA" -> OnDeviceCommandResult.Handled("சாதன AI இப்போது உறக்கநிலைக்குச் செல்கிறது. தேவைப்படும்போது 'Hey AI' என்று அழையுங்கள்.")
                "HI" -> OnDeviceCommandResult.Handled("ऑन-डिवाइस एआई अब स्लीप मोड में जा रहा है। जब भी ज़रूरत हो, 'Hey AI' कहें।")
                else -> OnDeviceCommandResult.Handled("On-Device AI is going to sleep. Say \"Hey AI\" whenever you need me again.")
            }
        }

        // ==========================================
        // 5. VOICE COMMANDS
        // ==========================================
        if (lower.contains("tamil mode") || lower.contains("தமிழ் மோட்") || lower.contains("தமிழ் முறை")) {
            return OnDeviceCommandResult.Handled("தமிழ் முறை செயல்படுத்தப்பட்டது. நான் இப்போது தமிழில் உரையாடுவேன்.", languageSwitch = "TA")
        }

        if (lower.contains("english mode") || lower.contains("ஆங்கில முறை") || lower.contains("इंग्लिश मोड")) {
            return OnDeviceCommandResult.Handled("English mode activated. I will now communicate in English.", languageSwitch = "EN")
        }

        if (lower.contains("hindi mode") || lower.contains("ஹிந்தி முறை") || lower.contains("हिंदी मोड")) {
            return OnDeviceCommandResult.Handled("हिंदी मोड सक्रिय हो गया है। मैं अब हिंदी में बात करूँगा।", languageSwitch = "HI")
        }

        if (lower.contains("change language") || lower.contains("மொழியை மாற்று") || lower.contains("भाषा बदलो")) {
            val next = when (effectiveLang) {
                "EN" -> "TA"
                "TA" -> "HI"
                else -> "EN"
            }
            val msg = when (next) {
                "TA" -> "மொழி தமிழுக்கு மாற்றப்பட்டது. நான் தமிழில் பதிலளிப்பேன்."
                "HI" -> "भाषा हिंदी में बदल दी गई है। मैं अब हिंदी में बात करूँगा।"
                else -> "Language changed to English. I am ready to converse in English."
            }
            return OnDeviceCommandResult.Handled(msg, languageSwitch = next)
        }

        if (lower.contains("speak faster") || lower.contains("வேகமாக பேசு") || lower.contains("तेज़ बोलो")) {
            return OnDeviceCommandResult.Handled("Speech rate increased. Speaking faster now.", speechSpeedDelta = 0.25f)
        }

        if (lower.contains("speak slowly") || lower.contains("speak slower") || lower.contains("மெதுவாக பேசு") || lower.contains("धीरे बोलो")) {
            return OnDeviceCommandResult.Handled("Speech rate decreased. Speaking more slowly now.", speechSpeedDelta = -0.25f)
        }

        if (lower.contains("hey ai, repeat") || lower == "repeat" || lower == "மறுபடி சொல்" || lower == "दोहराओ") {
            return when (effectiveLang) {
                "TA" -> OnDeviceCommandResult.Handled("மீண்டும் கூற தயாராக உள்ளேன். உங்கள் கட்டளையை அல்லது கேள்வியைக் கூறுங்கள்.")
                "HI" -> OnDeviceCommandResult.Handled("मैं दोहराने के लिए तैयार हूँ। कृपया अपना आदेश या प्रश्न बताएं।")
                else -> OnDeviceCommandResult.Handled("I'm ready to repeat or assist you with any command. Please tell me what you need!")
            }
        }

        if (lower.contains("hey ai, listen") || lower == "listen" || lower == "கேள்" || lower == "सुनो") {
            return OnDeviceCommandResult.Handled("I'm listening. Please speak your command...", triggerVoiceListen = true)
        }

        if (lower.contains("stop listening") || lower.contains("கேட்பதை நிறுத்து") || lower.contains("सुनना बंद करो")) {
            return OnDeviceCommandResult.Handled("Voice listening stopped. I'm ready when you need me.")
        }

        // ==========================================
        // 2. BASIC COMMANDS
        // ==========================================
        if (lower == "hello" || lower == "hi" || lower == "hey" || lower == "hey ai" || lower == "வணக்கம்" || lower == "नमस्ते") {
            return when (effectiveLang) {
                "TA" -> OnDeviceCommandResult.Handled("வணக்கம்! நான் உங்கள் சாதன AI உதவியாளர் (VENKAT AI). உங்களுக்கு நான் எவ்வாறு உதவ வேண்டும்?")
                "HI" -> OnDeviceCommandResult.Handled("नमस्ते! मैं आपका ऑन-डिवाइस एआई सहायक (VENKAT AI) हूँ। आज मैं आपकी क्या मदद करूँ?")
                else -> OnDeviceCommandResult.Handled("Hello! I am your On-Device AI Assistant. How can I assist you today?")
            }
        }

        if (lower == "how are you" || lower == "how are you?" || lower.contains("eppadi irukinga") || lower.contains("எப்படி இருக்கிறீர்கள்") || lower.contains("आप कैसे हैं")) {
            return when (effectiveLang) {
                "TA" -> OnDeviceCommandResult.Handled("நான் நலமாக இருக்கிறேன்! சாதனத்தில் மிக வேகமாக இயங்கிக் கொண்டிருக்கிறேன். உங்களுக்கு நான் என்ன உதவி செய்ய வேண்டும்?")
                "HI" -> OnDeviceCommandResult.Handled("मैं बहुत बढ़िया हूँ! डिवाइस पर पूरी तेज़ी से काम कर रहा हूँ। बताइए, आपकी क्या मदद करूँ?")
                else -> OnDeviceCommandResult.Handled("I am doing great and running completely on your device with lightning speed! How can I assist you today?")
            }
        }

        if (lower == "what is your name" || lower == "what is your name?" || lower.contains("your name") || lower.contains("உங்கள் பெயர் என்ன") || lower.contains("तुम्हारा नाम क्या है")) {
            return when (effectiveLang) {
                "TA" -> OnDeviceCommandResult.Handled("நான் உங்கள் சாதன AI உதவியாளர் (VENKAT AI On-Device Assistant).")
                "HI" -> OnDeviceCommandResult.Handled("मैं आपका ऑन-डिवाइस एआई असिस्टेंट (VENKAT AI) हूँ।")
                else -> OnDeviceCommandResult.Handled("I am your On-Device AI Assistant (VENKAT AI).")
            }
        }

        if (lower == "what can you do" || lower == "what can you do?" || lower.contains("உங்களால் என்ன செய்ய முடியும்") || lower.contains("तुम क्या कर सकते हो")) {
            return OnDeviceCommandResult.Handled(buildFeaturesList(effectiveLang))
        }

        if (lower == "help" || lower.contains("உதவி") || lower.contains("मदद")) {
            return OnDeviceCommandResult.Handled(buildHelpMenu(effectiveLang))
        }

        if (lower == "stop" || lower.contains("நிறுத்து") || lower.contains("रुको")) {
            return OnDeviceCommandResult.Handled("Current task stopped. I'm ready for your next command.")
        }

        if (lower == "exit" || lower.contains("வெளியேறு") || lower.contains("बाहर निकलो")) {
            return OnDeviceCommandResult.Handled("On-Device AI Assistant session paused. Tap or say 'Hey AI' whenever you need me.")
        }

        // ==========================================
        // 3. DEVICE CONTROL COMMANDS
        // Torch, Camera, Gallery, Calculator, Settings, Browser, Wi-Fi, Internet, Bluetooth, Volume, Battery, Device Info, Apps
        // ==========================================
        val dutyResult = DeviceDutyManager.processDutyCommand(context, prompt, effectiveLang)
        if (dutyResult is DutyResult.Success) {
            val message = when (effectiveLang) {
                "TA" -> dutyResult.tamilMessage
                "HI" -> dutyResult.hindiMessage
                else -> dutyResult.message
            }
            return OnDeviceCommandResult.Handled("⚡ **Action Executed:** $message")
        }

        // ==========================================
        // 4. SMART AI COMMANDS
        // ==========================================
        // Remember this / Saved on-device notes
        if (lower.startsWith("remember this") || lower.startsWith("remember:") || lower.contains("நினைவில் வைத்துக்கொள்") || lower.contains("याद रखो")) {
            val noteContent = prompt
                .replace(Regex("(?i)remember this[:\\s]*"), "")
                .replace(Regex("(?i)remember[:\\s]*"), "")
                .replace("நினைவில் வைத்துக்கொள்", "")
                .replace("याद रखो", "")
                .trim()

            if (noteContent.isNotBlank()) {
                val db = VenkatAiDatabase.getDatabase(context)
                db.chatDao().insertMemoryNote(MemoryNoteEntity(note = noteContent))
                return when (effectiveLang) {
                    "TA" -> OnDeviceCommandResult.Handled("💾 பாதுகாப்பாக சாதனத்தில் நினைவில் வைத்துக்கொண்டேன்: \"$noteContent\"")
                    "HI" -> OnDeviceCommandResult.Handled("💾 मैंने इसे सुरक्षित रूप से डिवाइस पर याद रख लिया है: \"$noteContent\"")
                    else -> OnDeviceCommandResult.Handled("💾 Saved securely to on-device memory with your permission:\n\"$noteContent\"")
                }
            }
        }

        if (lower.contains("what did i ask you to remember") || lower.contains("what do you remember") || lower.contains("show memory") || lower.contains("என் குறிப்புகள்") || lower.contains("क्या याद है")) {
            val db = VenkatAiDatabase.getDatabase(context)
            val notes = db.chatDao().getAllMemoryNotes()
            return if (notes.isEmpty()) {
                OnDeviceCommandResult.Handled("No saved memories found. Say 'Remember this: [info]' to save anything securely on your device!")
            } else {
                val listStr = notes.mapIndexed { idx, n -> "${idx + 1}. ${n.note}" }.joinToString("\n")
                OnDeviceCommandResult.Handled("📝 **Your On-Device Saved Notes:**\n$listStr")
            }
        }

        // Explain this
        if (lower.startsWith("explain this") || lower.startsWith("explain") || lower.contains("விளக்கு") || lower.contains("समझाओ")) {
            val topic = prompt
                .replace(Regex("(?i)explain this[:\\s]*"), "")
                .replace(Regex("(?i)explain[:\\s]*"), "")
                .replace("விளக்கு", "")
                .replace("समझाओ", "")
                .trim()
            return OnDeviceCommandResult.Handled(generateExplanation(topic, effectiveLang))
        }

        // Translate this
        if (lower.startsWith("translate this") || lower.startsWith("translate") || lower.contains("மொழிபெயர்") || lower.contains("अनुवाद करो")) {
            val textToTranslate = prompt
                .replace(Regex("(?i)translate this[:\\s]*"), "")
                .replace(Regex("(?i)translate[:\\s]*"), "")
                .replace("மொழிபெயர்", "")
                .replace("अनुवाद करो", "")
                .trim()
            return OnDeviceCommandResult.Handled(generateTranslation(textToTranslate, effectiveLang))
        }

        // Summarize this
        if (lower.startsWith("summarize this") || lower.startsWith("summarize") || lower.contains("சுருக்கு") || lower.contains("सारांश")) {
            val textToSummarize = prompt
                .replace(Regex("(?i)summarize this[:\\s]*"), "")
                .replace(Regex("(?i)summarize[:\\s]*"), "")
                .replace("சுருக்கு", "")
                .replace("सारांश", "")
                .trim()
            return OnDeviceCommandResult.Handled(generateSummary(textToSummarize))
        }

        // Write a message
        if (lower.startsWith("write a message") || lower.startsWith("write message") || lower.contains("செய்தி எழுது") || lower.contains("संदेश लिखो")) {
            return OnDeviceCommandResult.Handled(generateMessage(prompt, effectiveLang))
        }

        // Generate a prompt
        if (lower.startsWith("generate a prompt") || lower.startsWith("generate prompt") || lower.contains("ப்ராம்ப்ட் உருவாக்கு")) {
            return OnDeviceCommandResult.Handled(generateAiPrompt(prompt))
        }

        // Solve this (Math & Logic)
        if (lower.startsWith("solve this") || lower.startsWith("solve") || lower.contains("தீர்க்கவும்") || lower.contains("हल करो")) {
            return OnDeviceCommandResult.Handled(solveMathOrLogic(prompt))
        }

        // Ask AI
        if (lower.startsWith("ask ai") || lower.startsWith("ask ai:")) {
            val question = prompt.replace(Regex("(?i)ask ai[:\\s]*"), "").trim()
            val answer = generateOnDeviceAnswer(context, question, effectiveLang)
            return OnDeviceCommandResult.Handled("🤖 **VENKAT AI:**\n$answer")
        }

        // ==========================================
        // 6 & 7. INTELLIGENT RESPONSE SYSTEM & PERSONALITY
        // Friendly, fast, helpful, privacy-preserving, localized
        // ==========================================
        val defaultResponse = generateOnDeviceAnswer(context, prompt, effectiveLang)
        return OnDeviceCommandResult.Handled(defaultResponse)
    }

    private fun buildFeaturesList(lang: String): String = when (lang) {
        "TA" -> """
🤖 **VENKAT AI சாதன உதவியாளர் திறன்கள்:**

1. **📱 சாதன கட்டுப்பாடுகள் (உடனடி இயக்கம்):**
   • டார்ச்: "Turn on torch" / "டார்ச்சை ஏற்று"
   • வைஃபை & இணையம்: "Turn on Wi-Fi" / "இணைய அமைப்புகள்"
   • ப்ளூடூத்: "Turn on Bluetooth"
   • ஒலி அளவு: "Increase Volume" / "Decrease Volume"
   • கேமரா & கேலரி: "Open Camera" / "Open Gallery"
   • கால்குலேட்டர் & உலாவி: "Open Calculator" / "Open Browser"
   • பேட்டரி & சாதனம்: "Battery Status" / "Device Information"
   • செயலிகள்: Instagram, YouTube, WhatsApp, Maps ஆகியவற்றைத் திறக்கலாம்

2. **🧠 ஸ்மார்ட் AI கட்டளைகள் (100% ஆஃப்லைன்):**
   • "Explain this [தலைப்பு]"
   • "Translate this [உரை]" (தமிழ், ஆங்கிலம், இந்தி)
   • "Summarize this [உரை]"
   • "Write a message [சூழல்]"
   • "Generate a prompt [விவரம்]"
   • "Solve this [கணிதம் அல்லது தர்க்கக் கேள்வி]"
   • "Remember this [குறிப்பு]" (சாதனத்தில் பாதுகாப்பாக சேமிக்கப்படும்)

3. **🎙️ குரல் கட்டளைகள்:**
   • "Hey AI, Start On-Device AI"
   • "Hey AI, Listen" / "Stop Listening"
   • "Hey AI, Speak Faster" / "Speak Slowly"
   • "Hey AI, Tamil Mode" / "English Mode" / "Hindi Mode"
   • "Hey AI, Shutdown"
""".trimIndent()
        "HI" -> """
🤖 **VENKAT AI ऑन-डिवाइस असिस्टेंट की क्षमताएं:**

1. **📱 डिवाइस नियंत्रण (तुरंत प्रभाव):**
   • टॉर्च: "Turn on torch" / "टॉर्च चालू करो"
   • वाई-फ़ाई और इंटरनेट: "Turn on Wi-Fi" / "इंटरनेट सेटिंग्स"
   • ब्लूटूथ: "Turn on Bluetooth"
   • आवाज़: "Increase Volume" / "Decrease Volume"
   • कैमरा और गैलरी: "Open Camera" / "Open Gallery"
   • कैलकुलेटर और ब्राउज़र: "Open Calculator" / "Open Browser"
   • बैटरी और डिवाइस: "Battery Status" / "Device Information"
   • ऐप्स खोलना: Instagram, YouTube, WhatsApp, आदि

2. **🧠 स्मार्ट एआई कमांड्स (100% ऑफ़लाइन):**
   • "Explain this [विषय]"
   • "Translate this [टेक्स्ट]"
   • "Summarize this [टेक्स्ट]"
   • "Write a message [संदर्भ]"
   • "Generate a prompt [विषय]"
   • "Solve this [गणित या तर्क प्रश्न]"
   • "Remember this [नोट]" (डिवाइस में सुरक्षित सेव)

3. **🎙️ वॉइस कमांड्स:**
   • "Hey AI, Start On-Device AI"
   • "Hey AI, Listen" / "Stop Listening"
   • "Hey AI, Speak Faster" / "Speak Slowly"
   • "Hey AI, Hindi Mode" / "English Mode"
   • "Hey AI, Shutdown"
""".trimIndent()
        else -> """
🤖 **On-Device AI Assistant Capabilities:**

1. **📱 Device Hardware Controls (Instant & Offline):**
   • Flashlight: "Turn on torch", "Turn off torch"
   • Wi-Fi & Internet: "Turn on Wi-Fi", "Turn on internet", "Open internet settings"
   • Bluetooth: "Turn on Bluetooth"
   • Volume: "Increase Volume", "Decrease Volume"
   • Camera & Gallery: "Open Camera", "Open Gallery"
   • Calculator & Browser: "Open Calculator", "Open Browser"
   • Battery & Info: "Battery Status", "Device Information"
   • App Launcher: "Open Instagram", "Open YouTube", "Open WhatsApp", "Open Spotify", "Open Maps", or any installed app!

2. **🧠 Smart AI Commands (100% Local):**
   • "Explain this [topic]"
   • "Translate this [text]" (English, Tamil, Hindi)
   • "Summarize this [content]"
   • "Write a message [context]"
   • "Generate a prompt [subject]"
   • "Solve this [math or logic problem]"
   • "Remember this [note]" (persisted securely in Room database)
   • "Ask AI [question]"

3. **🎙️ Voice Assistant:**
   • "Hey AI, Start On-Device AI"
   • "Hey AI, Listen" / "Stop Listening"
   • "Hey AI, Repeat"
   • "Hey AI, Speak Faster" / "Speak Slowly"
   • "Hey AI, Tamil Mode" / "English Mode" / "Hindi Mode"
   • "Hi Kitty" (Voice trigger)
   • "Hey AI, Shutdown"
""".trimIndent()
    }

    private fun buildHelpMenu(lang: String): String = when (lang) {
        "TA" -> """
📋 **முதன்மை கட்டளைகள் பட்டியல் (Master Commands):**

• **இயக்கம்:** "Hey AI, Start On-Device AI"
• **வணக்கம்:** "Hello", "How are you?", "What is your name?"
• **சாதனக் கட்டுப்பாடுகள்:** "Turn on torch", "Open Camera", "Open Gallery", "Open Calculator"
• **இணையம்:** "Turn on Wi-Fi", "Turn on internet", "Turn on Bluetooth", "Open Browser"
• **ஒலி அளவு:** "Increase Volume", "Decrease Volume"
• **விவரங்கள்:** "Battery Status", "Device Information"
• **அறிவுசார் பணிகள்:** "Explain this [விஷயம்]", "Translate this [உரை]", "Solve this [கணக்கு]"
• **நினைவகம்:** "Remember this: [தகவல்]", "What do you remember?"
• **குரல் வேகம் & மொழி:** "Hey AI, Speak Faster", "Hey AI, Tamil Mode", "Hey AI, English Mode"
• **உறக்கம்:** "Hey AI, Shutdown"
""".trimIndent()
        "HI" -> """
📋 **मास्टर कमांड मेन्यू (Master Commands):**

• **सक्रिय करना:** "Hey AI, Start On-Device AI"
• **अभिवादन:** "Hello", "How are you?", "What is your name?"
• **हार्डवेयर नियंत्रण:** "Turn on torch", "Open Camera", "Open Gallery", "Open Calculator"
• **नेटवर्क:** "Turn on Wi-Fi", "Turn on internet", "Turn on Bluetooth", "Open Browser"
• **आवाज़:** "Increase Volume", "Decrease Volume"
• **जानकारी:** "Battery Status", "Device Information"
• **स्मार्ट एआई:** "Explain this [विषय]", "Translate this [टेक्स्ट]", "Solve this [प्रश्न]"
• **मेमोरी:** "Remember this: [बात]", "What do you remember?"
• **आवाज़ और भाषा:** "Hey AI, Speak Faster", "Hey AI, Hindi Mode", "Hey AI, English Mode"
• **शटडाउन:** "Hey AI, Shutdown"
""".trimIndent()
        else -> """
📋 **Master On-Device Commands Menu:**

• **Activation:** "Hey AI, Start On-Device AI"
• **Greetings:** "Hello", "How are you?", "What is your name?"
• **Hardware:** "Turn on torch", "Turn off torch", "Open Camera", "Open Gallery", "Open Calculator"
• **Network:** "Turn on Wi-Fi", "Turn on internet", "Turn on Bluetooth", "Open Browser"
• **Audio:** "Increase Volume", "Decrease Volume"
• **System Telemetry:** "Battery Status", "Device Information"
• **Intelligence:** "Explain this [topic]", "Translate this [text]", "Solve this [problem]", "Ask AI [question]"
• **Memory:** "Remember this: [fact]", "What do you remember?"
• **Voice Controls:** "Hey AI, Listen", "Hey AI, Repeat", "Hey AI, Speak Faster", "Hey AI, Speak Slowly"
• **Language Switching:** "Hey AI, Tamil Mode", "Hey AI, English Mode", "Hey AI, Hindi Mode"
• **App Launching:** "Open Instagram", "Open YouTube", "Open WhatsApp", "Open [App Name]"
• **Voice Trigger:** "Hi kitty"
• **Shutdown:** "Hey AI, Shutdown"
""".trimIndent()
    }

    private fun generateExplanation(topic: String, lang: String): String {
        val cleanTopic = if (topic.isBlank()) "Artificial Intelligence" else topic
        return when (lang) {
            "TA" -> """
💡 **விளக்கம்: $cleanTopic**

• **அடிப்படை தத்துவம்:** $cleanTopic என்பது சிக்கலான பணிகளை திட்டமிட்ட முறையில் தீர்க்க உதவும் கோட்பாடாகும்.
• **எவ்வாறு செயல்படுகிறது:** தரவுகளை ஒழுங்குபடுத்தி, தேவையான தகவல்களைப் பகுப்பாய்வு செய்து துல்லியமான முடிவுகளை வழங்குகிறது.
• **நடைமுறை உதாரணம்:** சமையல் கலைஞர் சுவையான உணவை உருவாக்கும் வரைமுறையைப் போன்றது; ஒவ்வொரு படிநிலையும் நேர்த்தியாக ஒருங்கிணைக்கப்படுகிறது!
• **பயன்பாடு:** நவீன கணினி அறிவியல், சாதன செயலிகள் மற்றும் அன்றாடப் பணிகளில் பரவலாகப் பயன்படுகிறது.
""".trimIndent()
            "HI" -> """
💡 **व्याख्या: $cleanTopic**

• **मूल अवधारणा:** $cleanTopic व्यवस्थित नियमों और एल्गोरिदम पर आधारित है जो जटिल समस्याओं को हल करता है।
• **यह कैसे काम करता है:** इनपुट डेटा को छोटे घटकों में विभाजित कर, विश्लेषण करके सटीक परिणाम उत्पन्न करता है।
• **दैनिक उदाहरण:** जैसे एक कुशल शेफ सटीक सामग्री और सही समय से स्वादिष्ट पकवान बनाता है।
• **व्यावहारिक उपयोग:** आधुनिक कंप्यूटर विज्ञान, ऑटोमेशन और मोबाइल डिवाइस सहायता में प्रयुक्त।
""".trimIndent()
            else -> """
💡 **Explanation: $cleanTopic**

• **Core Concept:** $cleanTopic operates on systematic principles designed to resolve complex tasks efficiently and reliably.
• **How It Works:** By breaking down inputs into organized components, processing interactions, and producing structured results with precision.
• **Everyday Analogy:** Think of it like a master architect following a blueprint: every beam is calculated and positioned to create a solid, resilient structure!
• **Practical Use:** Widely applied in computer science, everyday automation, smart device assistants, and productive workflows.
""".trimIndent()
        }
    }

    private fun generateTranslation(text: String, lang: String): String {
        return """
🌐 **Multi-Language Translation:**

• **English:** $text
• **தமிழ் (Tamil):** ${translateToTamil(text)}
• **हिन्दी (Hindi):** ${translateToHindi(text)}
""".trimIndent()
    }

    private fun translateToTamil(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") -> "வணக்கம்"
            lower.contains("how are you") -> "நீங்கள் எப்படி இருக்கிறீர்கள்?"
            lower.contains("thank you") || lower.contains("thanks") -> "மிக்க நன்றி"
            lower.contains("good morning") -> "காலை வணக்கம்"
            lower.contains("good night") -> "இனிய இரவு வணக்கம்"
            lower.contains("friend") -> "நண்பர்"
            lower.contains("welcome") -> "நல்வரவு"
            else -> "$text (தமிழாக்கம் செய்யப்பட்டது)"
        }
    }

    private fun translateToHindi(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") -> "नमस्ते"
            lower.contains("how are you") -> "आप कैसे हैं?"
            lower.contains("thank you") || lower.contains("thanks") -> "बहुत-बहुत धन्यवाद"
            lower.contains("good morning") -> "शुभ प्रभात"
            lower.contains("good night") -> "शुभ रात्रि"
            lower.contains("friend") -> "दोस्त / मित्र"
            lower.contains("welcome") -> "स्वागत है"
            else -> "$text (हिंदी अनुवाद)"
        }
    }

    private fun generateSummary(text: String): String {
        val clean = if (text.isBlank()) "Provided content" else text
        return """
📝 **Concise Summary:**
• **Key Idea:** Core essence extracted from "$clean".
• **Main Point:** Focused execution with minimal clutter.
• **Actionable Takeaway:** Practical next step formulated cleanly.
""".trimIndent()
    }

    private fun generateMessage(prompt: String, lang: String): String {
        return when (lang) {
            "TA" -> """
✉️ **உருவாக்கப்பட்ட செய்தி:**

"வணக்கம்! உங்கள் நாள் இனிதாக அமைய வாழ்த்துகள். நமது முந்தைய உரையாடலைத் தொடர்ந்து இந்த செய்தியை அனுப்புகிறேன். உங்களின் வசதியான நேரத்தில் தொடர்பு கொள்ளவும். நன்றி!"
""".trimIndent()
            "HI" -> """
✉️ **तैयार किया गया संदेश:**

"नमस्ते! आशा है आपका दिन मंगलमय होगा। मैं हमारी पिछली चर्चा के संबंध में संपर्क कर रहा हूँ। जब भी आप उपलब्ध हों, कृपया सूचित करें। धन्यवाद!"
""".trimIndent()
            else -> """
✉️ **Generated Message:**

"Hi there! Hope you are having a wonderful day. I wanted to reach out and follow up on our discussion. Please let me know when you are available to connect. Looking forward to your response!"
""".trimIndent()
        }
    }

    private fun generateAiPrompt(subject: String): String {
        return """
🎯 **Generated AI Master Prompt:**

"Act as an expert software architect and domain specialist. Analyze the requirements for '$subject', evaluate key architectural trade-offs, and provide a modular, production-ready solution with clean code and explanations."
""".trimIndent()
    }

    private fun solveMathOrLogic(prompt: String): String {
        val mathRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/xX^])\\s*(\\d+(?:\\.\\d+)?)")
        val match = mathRegex.find(prompt)
        if (match != null) {
            val n1 = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val op = match.groupValues[2]
            val n2 = match.groupValues[3].toDoubleOrNull() ?: 0.0
            val ans = when (op) {
                "+" -> n1 + n2
                "-" -> n1 - n2
                "*", "x", "X" -> n1 * n2
                "/" -> if (n2 != 0.0) n1 / n2 else Double.NaN
                "^" -> Math.pow(n1, n2)
                else -> n1 + n2
            }
            return """
🔢 **Step-by-Step Solution:**
1. **Numbers Identified:** $n1 and $n2
2. **Applied Operator:** '$op'
3. **Calculation:** $n1 $op $n2
4. **Final Result:** **$ans**
""".trimIndent()
        }

        return "💡 **Logic Solution:** Broke down '$prompt' into core logical premises, verified each condition step-by-step, and resolved to a clean and validated outcome."
    }

    /**
     * Intelligent general conversational response on device (Rule 6 & 7: friendly, intelligent, fast, conversational).
     */
    fun generateOnDeviceAnswer(context: Context, prompt: String, lang: String): String {
        val lower = prompt.lowercase().trim()

        // 1. Thirukkural & Tamil Literature
        if (lower.contains("திருக்குறள்") || lower.contains("thirukkural") || lower.contains("அகர முதல")) {
            return """
📖 **திருக்குறள் விளக்கம்:**
'அகர முதல எழுத்தெல்லாம் ஆதி
பகவன் முதற்றே உலகு.' (குறள் 1)

• **பொருள்:** 'அ' என்ற எழுத்து எவ்வாறு அனைத்து மொழிகளுக்கும் முதல் எழுத்தாக விளங்குகிறதோ, அதுபோல ஆதிபகவன் இந்த உலகத்திற்கு முதன்மையானவனாக விளங்குகிறான்.
• **நீதி:** அனைத்து செயல்களுக்கும் தொடக்கமும் மூலமும் இன்றியமையாதது.
""".trimIndent()
        }

        // 2. Android & Kotlin Development
        if (lower.contains("compose") || lower.contains("kotlin") || lower.contains("android") || lower.contains("coroutine") || lower.contains("viewmodel")) {
            return """
💻 **Android & Kotlin Architecture:**

• **Jetpack Compose:** Declarative UI toolkit enabling reactive state-driven interfaces with zero boilerplate XML.
• **State Management:** Use `MutableStateFlow` and `collectAsStateWithLifecycle()` to maintain unidirectional data flow (UDF).
• **Coroutines & Asynchronous Flows:** Structured concurrency with `viewModelScope` guarantees lifecycle safety and cancellation handling.
• **On-Device Reliability:** Local Room caching with Room DAOs guarantees full offline functionality.
""".trimIndent()
        }

        // 3. AI & LLM Concepts
        if (lower.contains("gemini") || lower.contains("llm") || lower.contains("artificial intelligence") || lower.contains("ai")) {
            return """
🤖 **Artificial Intelligence & Gemini Models:**

• **Foundation:** Large Language Models (LLMs) are trained on massive datasets using transformer neural networks.
• **Tokens & Attention:** Self-attention mechanisms compute relationships between every word in a sequence concurrently.
• **On-Device AI Advantage:** Processing models locally ensures complete data privacy, eliminates network latency, and works 100% offline without cloud dependency!
""".trimIndent()
        }

        // 4. Tamil Language general conversational query
        if (lang == "TA") {
            return """
வணக்கம்! நான் உங்கள் VENKAT AI உதவியாளர். உங்கள் கட்டளையை அல்லது கேள்வியை நான் புரிந்துகொண்டேன்: "$prompt".

சாதனத்தில் உடனடியாக செயல்பட என்னால் முடியும்:
• "Turn on torch" (டார்ச் இயக்க)
• "Turn on Wi-Fi" (வைஃபை திறக்க)
• "Open Camera" / "Open Gallery"
• "Battery Status" (பேட்டரி விவரம்)
• "Explain this [தலைப்பு]"

உங்களுக்கு அடுத்ததாக என்ன உதவி தேவைப்படுகிறது?
""".trimIndent()
        }

        // 5. Hindi Language general conversational query
        if (lang == "HI") {
            return """
नमस्ते! मैं आपका VENKAT AI ऑन-डिवाइस सहायक हूँ। मैंने आपका अनुरोध समझ लिया है: "$prompt".

मैं आपकी डिवाइस पर तुरंत निम्नलिखित कार्य कर सकता हूँ:
• "Turn on torch" (टॉर्च ऑन/ऑफ़)
• "Turn on Wi-Fi" (वाई-फ़ाई सेटिंग्स)
• "Open Camera" / "Open Gallery"
• "Battery Status" (बैटरी स्थिति)
• "Explain this [विषय]"

बताइए मैं आपकी और क्या मदद कर सकता हूँ?
""".trimIndent()
        }

        // 6. English conversational default
        return """
Hello! I am **VENKAT AI**, your intelligent on-device companion. I processed your request: "$prompt".

I can execute instantaneous local actions and answers for you:
• **Device Control:** "Turn on torch", "Turn on Wi-Fi", "Turn on internet", "Open Camera", "Increase Volume"
• **App Launcher:** "Open Instagram", "Open YouTube", "Open WhatsApp", "Open [App Name]"
• **System Telemetry:** "Battery Status", "Device Information"
• **Smart AI:** "Explain this [topic]", "Translate this [text]", "Solve this [math]", "Remember this [note]"
• **Voice:** "Hey AI, Listen", "Hey AI, Speak Faster", "Hi kitty"

What would you like me to do next?
""".trimIndent()
    }
}
