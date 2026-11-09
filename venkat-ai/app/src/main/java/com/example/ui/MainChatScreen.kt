package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venkat.ai.R
import com.example.data.model.ChatMessageEntity
import com.example.ui.components.AiLoadingIndicator
import com.example.ui.components.ChatInputBar
import com.example.ui.components.MessageBubble
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SidebarDrawer
import com.example.ui.components.WelcomeScreen
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiPurple
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    viewModel: ChatViewModel,
    contactCallViewModel: com.example.calling.ContactCallViewModel? = null,
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val conversations by viewModel.conversations.collectAsState()
    val currentConversation by viewModel.currentConversation.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val aiState by viewModel.aiState.collectAsState()
    val languageMode by viewModel.languageMode.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val useOnDeviceAi by viewModel.useOnDeviceAi.collectAsState()
    val isSpeaking by viewModel.ttsHelper.isSpeaking.collectAsState()
    val currentlySpeakingId by viewModel.ttsHelper.currentlySpeakingMessageId.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun handleSendPrompt(prompt: String) {
        val callParse = com.example.calling.VoiceCommandParser.parseCallCommand(prompt)
        if (callParse.isCallIntent && contactCallViewModel != null) {
            contactCallViewModel.processVoiceQuery(prompt, languageMode, viewModel.ttsHelper)
        } else {
            viewModel.sendMessage(prompt)
        }
    }

    // Auto-scroll to bottom when messages count changes or streaming text updates
    LaunchedEffect(messages.size, (aiState as? AiChatState.Streaming)?.accumulatedText?.length) {
        val totalItems = messages.size + if (aiState !is AiChatState.Idle) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarDrawer(
                conversations = conversations,
                currentConversationId = currentConversation?.id,
                languageMode = languageMode,
                onSelectConversation = { conv ->
                    viewModel.selectConversation(conv)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.createNewChat()
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                },
                onSelectLanguage = { mode ->
                    viewModel.setLanguageMode(mode)
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    showSettingsDialog = true
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .testTag("main_chat_scaffold"),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = 1.5.dp,
                                        brush = Brush.linearGradient(listOf(GeminiCyan, GeminiPurple)),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.venkat_ai_star_icon),
                                    contentDescription = "VENKAT AI",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentConversation?.title ?: "VENKAT AI",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (useOnDeviceAi) "⚡ On-Device AI Assistant • Offline" else if (languageMode == "TA") "தமிழ் மொழி • Cloud Gemini" else "Tamil & English • Cloud Gemini",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (useOnDeviceAi) GeminiCyan else GeminiPurple
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open sidebar navigation",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        // Quick Language Toggle Chip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clickable {
                                    val nextMode = when (languageMode) {
                                        "AUTO" -> "TA"
                                        "TA" -> "EN"
                                        else -> "AUTO"
                                    }
                                    viewModel.setLanguageMode(nextMode)
                                }
                                .padding(end = 4.dp)
                                .testTag("quick_lang_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = GeminiBlue,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (languageMode) {
                                        "TA" -> "தமிழ்"
                                        "EN" -> "EN"
                                        else -> "Auto"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Quick Call Contact Action
                        IconButton(
                            onClick = {
                                contactCallViewModel?.processVoiceQuery("call", languageMode, viewModel.ttsHelper)
                            },
                            modifier = Modifier.testTag("topbar_call_contact_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Offline Contact Calling",
                                tint = GeminiPurple
                            )
                        }

                        // New Chat Quick Action
                        IconButton(
                            onClick = { viewModel.createNewChat() },
                            modifier = Modifier.testTag("topbar_new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New chat",
                                tint = GeminiCyan
                            )
                        }

                        // Settings Button
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("topbar_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                val isGenerating = aiState is AiChatState.Thinking || aiState is AiChatState.Streaming
                ChatInputBar(
                    inputText = inputText,
                    onInputTextChanged = { viewModel.onInputTextChanged(it) },
                    selectedImageUri = selectedImageUri,
                    onImageSelected = { viewModel.onImageSelected(it) },
                    onClearImage = { viewModel.clearSelectedImage() },
                    isGenerating = isGenerating,
                    onSend = { handleSendPrompt(inputText) },
                    onStop = { viewModel.stopGeneration() },
                    languageMode = languageMode
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (messages.isEmpty() && aiState is AiChatState.Idle) {
                    WelcomeScreen(
                        onSelectPrompt = { prompt ->
                            handleSendPrompt(prompt)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp)
                    ) {
                        itemsIndexed(
                            items = messages,
                            key = { _, msg -> msg.id }
                        ) { index, message ->
                            val isLastModel = message.role == "model" &&
                                    (index == messages.lastIndex ||
                                            (index == messages.lastIndex - 1 && messages.last().role == "user"))

                            MessageBubble(
                                message = message,
                                isSpeaking = isSpeaking && currentlySpeakingId == message.id,
                                onToggleSpeak = {
                                    viewModel.ttsHelper.toggleSpeak(message.id, message.content)
                                },
                                onRegenerate = {
                                    viewModel.regenerateLastAnswer()
                                },
                                isLastModelMessage = isLastModel
                            )
                        }

                        // Live AI streaming state or Thinking indicator
                        item {
                            when (val state = aiState) {
                                is AiChatState.Thinking -> {
                                    AiLoadingIndicator()
                                }
                                is AiChatState.Streaming -> {
                                    val streamingMsg = remember(state.accumulatedText) {
                                        ChatMessageEntity(
                                            conversationId = currentConversation?.id.orEmpty(),
                                            role = "model",
                                            content = state.accumulatedText
                                        )
                                    }
                                    Column {
                                        MessageBubble(
                                            message = streamingMsg,
                                            isSpeaking = false,
                                            onToggleSpeak = {},
                                            onRegenerate = {},
                                            isLastModelMessage = false
                                        )
                                        AiLoadingIndicator(text = "VENKAT AI is streaming...")
                                    }
                                }
                                is AiChatState.Error -> {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                            .testTag("ai_error_card"),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Generation Error",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = state.errorMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = { viewModel.regenerateLastAnswer() },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Retry")
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        if (showSettingsDialog) {
            SettingsDialog(
                themeMode = themeMode,
                languageMode = languageMode,
                useOnDeviceAi = useOnDeviceAi,
                ttsHelper = viewModel.ttsHelper,
                onSetThemeMode = { viewModel.setThemeMode(it) },
                onSetLanguageMode = { viewModel.setLanguageMode(it) },
                onSetUseOnDeviceAi = { viewModel.setUseOnDeviceAi(it) },
                onClearAllHistory = { viewModel.clearAllHistory() },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}
