package com.example.calling

data class CallCommandParseResult(
    val isCallIntent: Boolean,
    val targetNameOrNumber: String? = null,
    val isDirectNumber: Boolean = false,
    val detectedLanguage: String = "EN"
)

object VoiceCommandParser {

    /**
     * Determines if a spoken phrase expresses intent to place a phone call.
     */
    fun parseCallCommand(rawText: String): CallCommandParseResult {
        val text = rawText.trim()
        val lower = text.lowercase()

        val isTamil = text.any { it in '\u0B80'..'\u0BFF' }
        val isHindi = text.any { it in '\u0900'..'\u097F' }
        val lang = if (isTamil) "TA" else if (isHindi) "HI" else "EN"

        // Check Tamil calling phrases
        if (isTamil || lower.contains("கால்") || lower.contains("போன்")) {
            val isCall = lower.contains("கால் செய்") ||
                    lower.contains("கால் பண்ணு") ||
                    lower.contains("போன் போடு") ||
                    lower.contains("அழைக்கவும்") ||
                    lower.contains("கால்")

            if (isCall) {
                val target = extractTarget(text, listOf("கால் செய்", "கால் பண்ணு", "போன் போடு", "அழைக்கவும்", "கால்", "போன்"))
                return CallCommandParseResult(
                    isCallIntent = true,
                    targetNameOrNumber = target,
                    isDirectNumber = isDigitsOnly(target),
                    detectedLanguage = "TA"
                )
            }
        }

        // Check Hindi calling phrases
        if (isHindi || lower.contains("कॉल") || lower.contains("फोन")) {
            val isCall = lower.contains("कॉल करो") ||
                    lower.contains("फोन लगाओ") ||
                    lower.contains("फोन करो") ||
                    lower.contains("कॉल लगाओ") ||
                    lower.contains("कॉल") ||
                    lower.contains("फोन")

            if (isCall) {
                val target = extractTarget(text, listOf("कॉल करो", "फोन लगाओ", "फोन करो", "कॉल लगाओ", "कॉल", "फोन"))
                return CallCommandParseResult(
                    isCallIntent = true,
                    targetNameOrNumber = target,
                    isDirectNumber = isDigitsOnly(target),
                    detectedLanguage = "HI"
                )
            }
        }

        // Check English calling phrases
        if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ") || lower.contains("make a call to") || lower.contains("place a call to")) {
            val target = lower
                .removePrefix("make a call to ")
                .removePrefix("place a call to ")
                .removePrefix("please call ")
                .removePrefix("call ")
                .removePrefix("dial ")
                .removePrefix("phone ")
                .replace("hi kitty", "")
                .trim()

            return CallCommandParseResult(
                isCallIntent = true,
                targetNameOrNumber = target,
                isDirectNumber = isDigitsOnly(target),
                detectedLanguage = "EN"
            )
        }

        return CallCommandParseResult(isCallIntent = false, detectedLanguage = lang)
    }

    /**
     * Checks if the user confirmed placing the call.
     */
    fun isConfirmationAffirmative(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower in listOf(
            "yes", "yeah", "yep", "sure", "call", "please call", "confirm", "do it", "call now",
            "ஆமாம்", "ஆம்", "சரி", "கால் செய்", "போடு",
            "हाँ", "हाँ लगाओ", "कॉल करो", "लगाओ", "करो"
        ) || lower.startsWith("yes") || lower.startsWith("ஆம்") || lower.startsWith("हाँ")
    }

    /**
     * Checks if the user cancelled placing the call.
     */
    fun isConfirmationCancellation(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower in listOf(
            "cancel", "no", "nope", "stop", "don't", "dont call",
            "வேண்டாம்", "இல்லை", "நிறுத்து", "ரத்து செய்",
            "नहीं", "मत करो", "रद्द करो", "रोको"
        ) || lower.startsWith("no") || lower.startsWith("வேண்டாம்") || lower.startsWith("नहीं")
    }

    private fun extractTarget(original: String, commandTokens: List<String>): String {
        var clean = original
            .replace("Hi Kitty", "", ignoreCase = true)
            .replace("ஹாய் கிட்டி", "")
            .replace("हाय किट्टी", "")

        for (token in commandTokens) {
            clean = clean.replace(token, "", ignoreCase = true)
        }

        return clean.trim()
    }

    private fun isDigitsOnly(str: String?): Boolean {
        if (str.isNullOrBlank()) return false
        val digitsOnly = str.replace(Regex("[^0-9+]"), "")
        return digitsOnly.length >= 3 && digitsOnly.length == str.replace(" ", "").length
    }
}
