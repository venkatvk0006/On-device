package com.example.calling

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.TextToSpeechHelper
import com.example.wakeword.WakeWordSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ContactCallUiState {
    data object Idle : ContactCallUiState
    data class Searching(val query: String) : ContactCallUiState
    data class MultipleMatches(val matches: List<ContactItem>, val query: String) : ContactCallUiState
    data class ConfirmingCall(
        val contact: ContactItem,
        val selectedNumber: String,
        val language: String,
        val isAwaitingVoiceConfirmation: Boolean = true
    ) : ContactCallUiState
    data class Calling(val contact: ContactItem, val number: String, val method: String) : ContactCallUiState
    data class Cancelled(val reason: String) : ContactCallUiState
    data class Error(val message: String) : ContactCallUiState
}

class ContactCallViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ContactRepository(application)
    val speechRecognizer = OfflineSpeechRecognizer(application)
    val settings = WakeWordSettings.getInstance(application)

    private val _uiState = MutableStateFlow<ContactCallUiState>(ContactCallUiState.Idle)
    val uiState: StateFlow<ContactCallUiState> = _uiState.asStateFlow()

    private val _isListeningForConfirmation = MutableStateFlow(false)
    val isListeningForConfirmation: StateFlow<Boolean> = _isListeningForConfirmation.asStateFlow()

    /**
     * Parses a spoken command and triggers contact searching and calling confirmation.
     */
    fun processVoiceQuery(
        rawSpeech: String,
        language: String,
        ttsHelper: TextToSpeechHelper? = null
    ) {
        val parsed = VoiceCommandParser.parseCallCommand(rawSpeech)
        val targetQuery = parsed.targetNameOrNumber ?: rawSpeech

        _uiState.value = ContactCallUiState.Searching(targetQuery)

        viewModelScope.launch {
            // Direct phone number dialing
            if (parsed.isDirectNumber) {
                val directContact = ContactItem(
                    id = "direct_dial",
                    displayName = targetQuery,
                    phoneNumbers = listOf(
                        ContactPhoneNumber(targetQuery, "Phone Number", targetQuery)
                    )
                )
                startCallConfirmation(directContact, targetQuery, language, ttsHelper)
                return@launch
            }

            // Search in local offline contacts
            val allContacts = repository.getContacts()
            val matches = ContactMatcher.findMatches(targetQuery, allContacts)

            when {
                matches.isEmpty() -> {
                    val notFoundMsg = when (language) {
                        "TA" -> "\"$targetQuery\" என்ற பெயர் தொடர்புகளில் காணப்படவில்லை."
                        "HI" -> "\"$targetQuery\" संपर्कों में नहीं मिला।"
                        else -> "No contact found matching \"$targetQuery\"."
                    }
                    _uiState.value = ContactCallUiState.Error(notFoundMsg)
                    ttsHelper?.speak(notFoundMsg)
                }

                matches.size == 1 || (matches.first().confidence >= 0.90f && matches.first().confidence - (matches.getOrNull(1)?.confidence ?: 0f) > 0.2f) -> {
                    // Single confident match
                    val topContact = matches.first().contact
                    val numberToUse = topContact.primaryNumber
                    if (numberToUse.isBlank()) {
                        val noNumberMsg = "${topContact.displayName} has no saved phone number."
                        _uiState.value = ContactCallUiState.Error(noNumberMsg)
                        ttsHelper?.speak(noNumberMsg)
                    } else {
                        startCallConfirmation(topContact, numberToUse, language, ttsHelper)
                    }
                }

                else -> {
                    // Multiple close matches: ask user to choose
                    val topCandidates = matches.take(4).map { it.contact }
                    _uiState.value = ContactCallUiState.MultipleMatches(topCandidates, targetQuery)

                    val prompt = when (language) {
                        "TA" -> "பல தொடர்புகள் கண்டறியப்பட்டுள்ளன. எவரை அழைக்க வேண்டும்?"
                        "HI" -> "कई संपर्क मिले हैं। आप किसे कॉल करना चाहते हैं?"
                        else -> "Found multiple matching contacts. Which one would you like to call?"
                    }
                    ttsHelper?.speak(prompt)
                }
            }
        }
    }

    fun selectCandidate(
        contact: ContactItem,
        number: String? = null,
        language: String = "EN",
        ttsHelper: TextToSpeechHelper? = null
    ) {
        val targetNumber = number ?: contact.primaryNumber
        startCallConfirmation(contact, targetNumber, language, ttsHelper)
    }

    private fun startCallConfirmation(
        contact: ContactItem,
        number: String,
        language: String,
        ttsHelper: TextToSpeechHelper? = null
    ) {
        _uiState.value = ContactCallUiState.ConfirmingCall(
            contact = contact,
            selectedNumber = number,
            language = language,
            isAwaitingVoiceConfirmation = true
        )

        val confirmPrompt = when (language) {
            "TA" -> "${contact.displayName}-க்கு கால் செய்யவா?"
            "HI" -> "क्या मैं ${contact.displayName} को कॉल करूँ?"
            else -> "Do you want me to call ${contact.displayName}?"
        }

        ttsHelper?.speak(confirmPrompt) {
            // After TTS finishes speaking prompt, open offline microphone for voice confirmation ("Yes" / "Cancel")
            listenForVoiceConfirmation(language, ttsHelper)
        }
    }

    private fun listenForVoiceConfirmation(
        language: String,
        ttsHelper: TextToSpeechHelper? = null
    ) {
        _isListeningForConfirmation.value = true
        speechRecognizer.startListening(
            language = language,
            onResult = { recognizedText ->
                _isListeningForConfirmation.value = false
                if (VoiceCommandParser.isConfirmationAffirmative(recognizedText)) {
                    confirmCall(getApplication())
                } else if (VoiceCommandParser.isConfirmationCancellation(recognizedText)) {
                    cancelCall(ttsHelper)
                }
            },
            onError = {
                _isListeningForConfirmation.value = false
            }
        )
    }

    fun confirmCall(context: Context) {
        val current = _uiState.value as? ContactCallUiState.ConfirmingCall ?: return
        speechRecognizer.stopListening()
        _isListeningForConfirmation.value = false

        val result = CallManager.placeCall(context, current.selectedNumber)
        when (result) {
            is CallResult.Success -> {
                _uiState.value = ContactCallUiState.Calling(
                    contact = current.contact,
                    number = current.selectedNumber,
                    method = result.dialMethod
                )
            }
            is CallResult.Blocked -> {
                _uiState.value = ContactCallUiState.Cancelled(result.reason)
            }
            is CallResult.Error -> {
                _uiState.value = ContactCallUiState.Error(result.message)
            }
        }
    }

    fun cancelCall(ttsHelper: TextToSpeechHelper? = null) {
        speechRecognizer.stopListening()
        _isListeningForConfirmation.value = false
        val currentState = _uiState.value
        val lang = (currentState as? ContactCallUiState.ConfirmingCall)?.language ?: "EN"

        val cancelMessage = when (lang) {
            "TA" -> "அழைப்பு ரத்து செய்யப்பட்டது."
            "HI" -> "कॉल रद्द कर दी गई।"
            else -> "Call cancelled."
        }

        _uiState.value = ContactCallUiState.Cancelled(cancelMessage)
        ttsHelper?.speak(cancelMessage)
    }

    fun reset() {
        speechRecognizer.stopListening()
        _isListeningForConfirmation.value = false
        _uiState.value = ContactCallUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer.stopListening()
    }
}
