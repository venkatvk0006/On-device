package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiRepository
import com.example.data.local.VenkatAiDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ConversationEntity
import com.example.ui.theme.ThemeMode
import com.example.util.ImageUtil
import com.example.util.TextToSpeechHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ChatViewModel"

sealed interface AiChatState {
    data object Idle : AiChatState
    data object Thinking : AiChatState
    data class Streaming(val accumulatedText: String) : AiChatState
    data class Error(val errorMessage: String) : AiChatState
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = VenkatAiDatabase.getDatabase(application)
    private val dao = db.chatDao()
    private val aiRepository = AiRepository()
    val ttsHelper = TextToSpeechHelper(application)

    // Conversations
    val conversations: StateFlow<List<ConversationEntity>> = dao.getAllConversations()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentConversation = MutableStateFlow<ConversationEntity?>(null)
    val currentConversation: StateFlow<ConversationEntity?> = _currentConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _messages.asStateFlow()

    // Input state
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _selectedImageBitmap = MutableStateFlow<Bitmap?>(null)

    // AI Status
    private val _aiState = MutableStateFlow<AiChatState>(AiChatState.Idle)
    val aiState: StateFlow<AiChatState> = _aiState.asStateFlow()

    // Settings
    private val _languageMode = MutableStateFlow("AUTO") // AUTO, EN, TA
    val languageMode: StateFlow<String> = _languageMode.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Full On-Device AI Mode (Enabled by default for instant offline execution)
    private val _useOnDeviceAi = MutableStateFlow(true)
    val useOnDeviceAi: StateFlow<Boolean> = _useOnDeviceAi.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var messageCollectionJob: Job? = null

    init {
        // Observe conversations and auto-select latest or create first
        viewModelScope.launch {
            conversations.collectLatest { list ->
                if (_currentConversation.value == null && list.isNotEmpty()) {
                    selectConversation(list.first())
                }
            }
        }
    }

    fun onInputTextChanged(text: String) {
        _inputText.value = text
    }

    fun onImageSelected(uri: Uri?) {
        _selectedImageUri.value = uri
        if (uri != null) {
            viewModelScope.launch {
                val bitmap = ImageUtil.loadScaledBitmap(getApplication(), uri)
                _selectedImageBitmap.value = bitmap
            }
        } else {
            _selectedImageBitmap.value = null
        }
    }

    fun clearSelectedImage() {
        _selectedImageUri.value = null
        _selectedImageBitmap.value = null
    }

    fun setLanguageMode(mode: String) {
        _languageMode.value = mode
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setUseOnDeviceAi(value: Boolean) {
        _useOnDeviceAi.value = value
    }

    fun createNewChat() {
        activeGenerationJob?.cancel()
        _aiState.value = AiChatState.Idle
        _inputText.value = ""
        clearSelectedImage()
        _currentConversation.value = null
        _messages.value = emptyList()
    }

    fun selectConversation(conversation: ConversationEntity) {
        if (_currentConversation.value?.id == conversation.id) return
        activeGenerationJob?.cancel()
        _aiState.value = AiChatState.Idle
        _currentConversation.value = conversation
        clearSelectedImage()
        _inputText.value = ""

        messageCollectionJob?.cancel()
        messageCollectionJob = viewModelScope.launch {
            dao.getMessagesForConversation(conversation.id).collectLatest { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            dao.deleteConversationById(id)
            if (_currentConversation.value?.id == id) {
                createNewChat()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.deleteAllConversations()
            createNewChat()
        }
    }

    fun sendMessage(promptOverride: String? = null) {
        val prompt = (promptOverride ?: _inputText.value).trim()
        val bitmap = _selectedImageBitmap.value
        val imageUri = _selectedImageUri.value

        if (prompt.isEmpty() && bitmap == null) return
        if (_aiState.value is AiChatState.Thinking || _aiState.value is AiChatState.Streaming) return

        // Capture existing past messages BEFORE adding new user message
        val pastHistory = _messages.value.toList()

        // Clear input field and image
        _inputText.value = ""
        clearSelectedImage()

        activeGenerationJob = viewModelScope.launch {
            try {
                // Ensure active conversation
                var conversation = _currentConversation.value
                val isNewConversation = conversation == null

                if (conversation == null) {
                    val newConv = ConversationEntity(
                        title = prompt.take(30).ifEmpty { "Assistant Action" },
                        languageMode = _languageMode.value
                    )
                    dao.insertConversation(newConv)
                    _currentConversation.value = newConv
                    conversation = newConv

                    // Bind message collection for new conversation
                    messageCollectionJob?.cancel()
                    messageCollectionJob = viewModelScope.launch {
                        dao.getMessagesForConversation(newConv.id).collectLatest { msgs ->
                            _messages.value = msgs
                        }
                    }
                } else {
                    // Update conversation timestamp
                    dao.updateConversation(conversation.copy(updatedAt = System.currentTimeMillis()))
                }

                // Save local image path if image attached
                var localImagePath: String? = null
                if (imageUri != null) {
                    localImagePath = ImageUtil.saveImageLocally(getApplication(), imageUri)
                }

                // Insert User message
                val userMessage = ChatMessageEntity(
                    conversationId = conversation.id,
                    role = "user",
                    content = prompt,
                    imageUri = localImagePath ?: imageUri?.toString()
                )
                dao.insertMessage(userMessage)

                // Set AI state to Thinking
                _aiState.value = AiChatState.Thinking

                val accumulated = StringBuilder()

                if (_useOnDeviceAi.value) {
                    // Full on-device AI stream (Instant, 100% offline, zero network lag)
                    com.example.data.ai.OnDeviceAiEngine.streamOnDeviceResponse(
                        context = getApplication(),
                        prompt = prompt,
                        currentLang = _languageMode.value,
                        ttsHelper = ttsHelper,
                        onActionCallback = { delta, langSwitch, triggerListen ->
                            if (delta != null) {
                                ttsHelper.speechRate = (ttsHelper.speechRate + delta).coerceIn(0.5f, 2.0f)
                            }
                            if (langSwitch != null) {
                                _languageMode.value = langSwitch
                            }
                        }
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _aiState.value = AiChatState.Streaming(accumulated.toString())
                    }
                } else {
                    // Cloud Hybrid Stream with automatic on-device fallback on timeout
                    try {
                        aiRepository.streamChat(
                            prompt = prompt,
                            bitmap = bitmap,
                            history = pastHistory,
                            languagePreference = _languageMode.value
                        ).collect { chunk ->
                            accumulated.append(chunk)
                            _aiState.value = AiChatState.Streaming(accumulated.toString())
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException && e !is TimeoutCancellationException) throw e
                        Log.w(TAG, "Cloud generation failed, falling back to on-device engine", e)
                        if (accumulated.isEmpty()) {
                            com.example.data.ai.OnDeviceAiEngine.streamOnDeviceResponse(
                                context = getApplication(),
                                prompt = prompt,
                                currentLang = _languageMode.value,
                                ttsHelper = ttsHelper
                            ).collect { chunk ->
                                accumulated.append(chunk)
                                _aiState.value = AiChatState.Streaming(accumulated.toString())
                            }
                        } else {
                            throw e
                        }
                    }
                }

                val finalAiResponse = accumulated.toString().trim()
                if (finalAiResponse.isNotEmpty()) {
                    val aiMessage = ChatMessageEntity(
                        conversationId = conversation.id,
                        role = "model",
                        content = finalAiResponse
                    )
                    dao.insertMessage(aiMessage)
                    _aiState.value = AiChatState.Idle

                    // If it was the first turn, generate title
                    if (isNewConversation && prompt.isNotEmpty()) {
                        launch {
                            val smartTitle = if (_useOnDeviceAi.value) {
                                prompt.take(28).replaceFirstChar { it.uppercase() }
                            } else {
                                aiRepository.generateTitle(prompt)
                            }
                            dao.updateConversation(conversation.copy(title = smartTitle))
                            _currentConversation.value = conversation.copy(title = smartTitle)
                        }
                    }
                } else {
                    _aiState.value = AiChatState.Error("VENKAT AI received an empty response. Please try again.")
                }

            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI streaming timed out", e)
                _aiState.value = AiChatState.Error("Generation timed out. Switched to on-device assistant mode.")
            } catch (e: CancellationException) {
                // Cooperative cancellation re-throw
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error generating response", e)
                val errorMessage = e.localizedMessage ?: "Failed to connect to VENKAT AI."
                _aiState.value = AiChatState.Error(errorMessage)
            }
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        val currentState = _aiState.value
        if (currentState is AiChatState.Streaming && currentState.accumulatedText.isNotBlank()) {
            val convId = _currentConversation.value?.id
            if (convId != null) {
                viewModelScope.launch {
                    val aiMessage = ChatMessageEntity(
                        conversationId = convId,
                        role = "model",
                        content = currentState.accumulatedText
                    )
                    dao.insertMessage(aiMessage)
                }
            }
        }
        _aiState.value = AiChatState.Idle
    }

    fun regenerateLastAnswer() {
        val conv = _currentConversation.value ?: return
        val currentMsgs = _messages.value
        if (currentMsgs.isEmpty()) return

        val lastUserMsg = currentMsgs.lastOrNull { it.role == "user" } ?: return
        val historyBeforeLastUser = currentMsgs.takeWhile { it.id != lastUserMsg.id }

        activeGenerationJob?.cancel()
        _aiState.value = AiChatState.Thinking

        activeGenerationJob = viewModelScope.launch {
            try {
                // Delete previous model response if it exists
                dao.deleteLastModelMessage(conv.id)

                val accumulated = StringBuilder()

                if (_useOnDeviceAi.value) {
                    com.example.data.ai.OnDeviceAiEngine.streamOnDeviceResponse(
                        context = getApplication(),
                        prompt = lastUserMsg.content,
                        currentLang = _languageMode.value,
                        ttsHelper = ttsHelper
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _aiState.value = AiChatState.Streaming(accumulated.toString())
                    }
                } else {
                    try {
                        aiRepository.streamChat(
                            prompt = lastUserMsg.content,
                            bitmap = null,
                            history = historyBeforeLastUser,
                            languagePreference = _languageMode.value
                        ).collect { chunk ->
                            accumulated.append(chunk)
                            _aiState.value = AiChatState.Streaming(accumulated.toString())
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException && e !is TimeoutCancellationException) throw e
                        if (accumulated.isEmpty()) {
                            com.example.data.ai.OnDeviceAiEngine.streamOnDeviceResponse(
                                context = getApplication(),
                                prompt = lastUserMsg.content,
                                currentLang = _languageMode.value,
                                ttsHelper = ttsHelper
                            ).collect { chunk ->
                                accumulated.append(chunk)
                                _aiState.value = AiChatState.Streaming(accumulated.toString())
                            }
                        } else {
                            throw e
                        }
                    }
                }

                val finalAiResponse = accumulated.toString().trim()
                if (finalAiResponse.isNotEmpty()) {
                    val aiMessage = ChatMessageEntity(
                        conversationId = conv.id,
                        role = "model",
                        content = finalAiResponse
                    )
                    dao.insertMessage(aiMessage)
                    _aiState.value = AiChatState.Idle
                } else {
                    _aiState.value = AiChatState.Error("VENKAT AI received an empty response. Please try again.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI regeneration timed out", e)
                _aiState.value = AiChatState.Error("Generation timed out. Please tap retry to try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error regenerating response", e)
                _aiState.value = AiChatState.Error(e.localizedMessage ?: "Failed to regenerate response.")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        activeGenerationJob?.cancel()
        messageCollectionJob?.cancel()
    }
}
