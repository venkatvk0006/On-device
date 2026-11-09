package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.calling.CallManager
import com.example.calling.CallResult
import com.example.calling.ContactItem
import com.example.calling.ContactMatcher
import com.example.calling.ContactPhoneNumber
import com.example.calling.VoiceCommandParser
import com.example.wakeword.WakeWordEngine
import com.example.wakeword.WakeWordEngineStatus
import com.example.wakeword.WakeWordSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeWordAndContactCallingTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testVoiceCommandParserEnglish() {
        val result1 = VoiceCommandParser.parseCallCommand("Call Arun")
        assertTrue(result1.isCallIntent)
        assertEquals("arun", result1.targetNameOrNumber)
        assertFalse(result1.isDirectNumber)

        val result2 = VoiceCommandParser.parseCallCommand("Dial 9876543210")
        assertTrue(result2.isCallIntent)
        assertEquals("9876543210", result2.targetNameOrNumber)
        assertTrue(result2.isDirectNumber)
    }

    @Test
    fun testVoiceCommandParserTamil() {
        val result1 = VoiceCommandParser.parseCallCommand("அம்மாவுக்கு கால் செய்")
        assertTrue(result1.isCallIntent)
        assertEquals("TA", result1.detectedLanguage)
        assertEquals("அம்மாவுக்கு", result1.targetNameOrNumber)

        val result2 = VoiceCommandParser.parseCallCommand("ரமேஷுக்கு கால் பண்ணு")
        assertTrue(result2.isCallIntent)
        assertEquals("ரமேஷுக்கு", result2.targetNameOrNumber)
    }

    @Test
    fun testVoiceCommandParserHindi() {
        val result1 = VoiceCommandParser.parseCallCommand("माँ को कॉल करो")
        assertTrue(result1.isCallIntent)
        assertEquals("HI", result1.detectedLanguage)
        assertEquals("माँ को", result1.targetNameOrNumber)

        val result2 = VoiceCommandParser.parseCallCommand("अरुण को फोन लगाओ")
        assertTrue(result2.isCallIntent)
        assertEquals("अरुण को", result2.targetNameOrNumber)
    }

    @Test
    fun testVoiceConfirmationResponses() {
        // English
        assertTrue(VoiceCommandParser.isConfirmationAffirmative("yes"))
        assertTrue(VoiceCommandParser.isConfirmationAffirmative("call"))
        assertTrue(VoiceCommandParser.isConfirmationCancellation("cancel"))
        assertTrue(VoiceCommandParser.isConfirmationCancellation("no"))

        // Tamil
        assertTrue(VoiceCommandParser.isConfirmationAffirmative("ஆமாம்"))
        assertTrue(VoiceCommandParser.isConfirmationAffirmative("ஆம்"))
        assertTrue(VoiceCommandParser.isConfirmationCancellation("வேண்டாம்"))
        assertTrue(VoiceCommandParser.isConfirmationCancellation("இல்லை"))

        // Hindi
        assertTrue(VoiceCommandParser.isConfirmationAffirmative("हाँ"))
        assertTrue(VoiceCommandParser.isConfirmationAffirmative("कॉल करो"))
        assertTrue(VoiceCommandParser.isConfirmationCancellation("नहीं"))
        assertTrue(VoiceCommandParser.isConfirmationCancellation("रद्द करो"))
    }

    @Test
    fun testContactMatcherTamilSuffixStrippingAndSynonyms() {
        val contacts = listOf(
            ContactItem("1", "Amma (Mom / அம்மா)", listOf(ContactPhoneNumber("+919876543210", "Mobile", "+919876543210"))),
            ContactItem("2", "Arun Kumar (அருண்)", listOf(ContactPhoneNumber("+919444156789", "Mobile", "+919444156789"))),
            ContactItem("3", "Ramesh (ரமேஷ்)", listOf(ContactPhoneNumber("+919789012345", "Mobile", "+919789012345")))
        )

        // Query with Tamil dative suffix "-வுக்கு"
        val ammaMatches = ContactMatcher.findMatches("அம்மாவுக்கு", contacts)
        assertTrue(ammaMatches.isNotEmpty())
        assertEquals("Amma (Mom / அம்மா)", ammaMatches.first().contact.displayName)

        // Query with Tamil dative suffix "-க்கு"
        val rameshMatches = ContactMatcher.findMatches("ரமேஷுக்கு", contacts)
        assertTrue(rameshMatches.isNotEmpty())
        assertEquals("Ramesh (ரமேஷ்)", rameshMatches.first().contact.displayName)

        // Query with English name
        val arunMatches = ContactMatcher.findMatches("Arun", contacts)
        assertTrue(arunMatches.isNotEmpty())
        assertEquals("Arun Kumar (அருண்)", arunMatches.first().contact.displayName)
    }

    @Test
    fun testCallManagerEmergencyNumberProtection() {
        val settings = WakeWordSettings.getInstance(context)
        settings.setDirectCallingEnabled(false)

        // 911 must be blocked from automated call
        val blockedResult = CallManager.placeCall(context, "911")
        assertTrue(blockedResult is CallResult.Blocked)

        // 112 must be blocked
        val blocked112 = CallManager.placeCall(context, "112")
        assertTrue(blocked112 is CallResult.Blocked)

        // Standard number should open DIAL intent
        CallManager.resetDebounce()
        val successResult = CallManager.placeCall(context, "+91 98765 43210")
        assertTrue(successResult is CallResult.Success)
        assertEquals("ACTION_DIAL", (successResult as CallResult.Success).dialMethod)
    }

    @Test
    fun testWakeWordSettings() {
        val settings = WakeWordSettings.getInstance(context)
        settings.setWakeWordEnabled(true)
        assertTrue(settings.isWakeWordEnabled.value)

        settings.setSensitivity(0.75f)
        assertEquals(0.75f, settings.sensitivity.value, 0.01f)

        settings.setPlayChimeSound(false)
        assertFalse(settings.playChimeSound.value)

        settings.setDirectCallingEnabled(true)
        assertTrue(settings.directCallingEnabled.value)
    }

    @Test
    fun testWakeWordEngineSimulation() {
        val engine = WakeWordEngine(context)
        assertEquals(WakeWordEngineStatus.STOPPED, engine.status.value)

        var triggered = false
        engine.simulateDetection {
            triggered = true
        }

        assertTrue(triggered)
        assertTrue(engine.lastWakeTimestamp.value > 0)
        engine.stopListening()
        assertEquals(WakeWordEngineStatus.STOPPED, engine.status.value)
    }
}
