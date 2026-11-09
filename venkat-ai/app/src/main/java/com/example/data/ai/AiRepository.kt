package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ChatMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val TAG = "VenkatAiRepository"

class AiRepository {

    // Lazy initialization mandated by Firebase AI Logic skill
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(SYSTEM_INSTRUCTION)
            }
        )
    }

    /**
     * Streams AI response for chat, supporting conversation history and optional image.
     * Enforces strict user->model alternating turns and provides unary fallback if streaming stalls.
     */
    fun streamChat(
        prompt: String,
        bitmap: Bitmap? = null,
        history: List<ChatMessageEntity> = emptyList(),
        languagePreference: String = "AUTO"
    ): Flow<String> = flow {
        val effectivePrompt = when (languagePreference) {
            "TA" -> "[Instruction: Respond strictly in Tamil / தமிழ்]\n$prompt"
            "EN" -> "[Instruction: Respond strictly in English]\n$prompt"
            else -> prompt
        }

        // Build valid alternating conversation history (strictly user -> model -> user -> model...)
        val validHistory = mutableListOf<ChatMessageEntity>()
        for (msg in history) {
            if (msg.content.isBlank()) continue
            if (validHistory.isEmpty()) {
                if (msg.role == "user") {
                    validHistory.add(msg)
                }
            } else {
                val lastRole = validHistory.last().role
                // Only accept strictly alternating turns
                if (msg.role != lastRole) {
                    validHistory.add(msg)
                }
            }
        }

        // History for startChat must strictly end with 'model', so the new prompt is the next 'user' turn
        if (validHistory.isNotEmpty() && validHistory.last().role == "user") {
            validHistory.removeAt(validHistory.size - 1)
        }

        // Convert to Firebase Content objects (limit to last 10 messages for performance)
        val historyContents = mutableListOf<Content>()
        for (msg in validHistory.takeLast(10)) {
            val role = if (msg.role == "user") "user" else "model"
            historyContents.add(
                content(role) {
                    text(msg.content)
                }
            )
        }

        val userContent = content("user") {
            if (bitmap != null) {
                image(bitmap)
            }
            text(effectivePrompt)
        }

        var receivedAnyChunk = false

        try {
            // Attempt streaming with realistic coroutine timeout
            withTimeout(45_000L) {
                val stream = if (historyContents.isEmpty()) {
                    model.generateContentStream(userContent)
                } else {
                    val chat = model.startChat(history = historyContents)
                    chat.sendMessageStream(userContent)
                }

                stream.collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        receivedAnyChunk = true
                        emit(chunkText)
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) {
                // Re-throw user-requested cancellations (e.g. stop button) immediately
                throw e
            }

            Log.w(TAG, "Streaming encountered issue (receivedAnyChunk=$receivedAnyChunk). Attempting unary fallback...", e)

            if (!receivedAnyChunk) {
                // If stream timed out or stalled without receiving any chunks, execute unary generation
                try {
                    val unaryResponse = withTimeout(30_000L) {
                        if (historyContents.isEmpty()) {
                            model.generateContent(userContent)
                        } else {
                            val chat = model.startChat(history = historyContents)
                            chat.sendMessage(userContent)
                        }
                    }
                    val text = unaryResponse.text.orEmpty().trim()
                    if (text.isNotEmpty()) {
                        emit(text)
                    } else {
                        throw e
                    }
                } catch (fallbackEx: Exception) {
                    if (fallbackEx is CancellationException && fallbackEx !is TimeoutCancellationException) {
                        throw fallbackEx
                    }
                    Log.e(TAG, "Unary fallback also failed", fallbackEx)
                    throw fallbackEx
                }
            } else {
                throw e
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Quick title generator for a conversation based on the initial query.
     */
    suspend fun generateTitle(firstPrompt: String): String {
        return try {
            withTimeout(12_000L) {
                val titlePrompt = "Summarize the following user prompt into a short conversation title of 3 to 5 words maximum. Do not include quotes, markdown or punctuation:\n$firstPrompt"
                val response = model.generateContent(titlePrompt)
                val text = response.text?.trim()
                if (!text.isNullOrBlank()) text.take(40) else firstPrompt.take(30)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate title with AI, using fallback", e)
            firstPrompt.take(30).trim()
        }
    }

    companion object {
        private const val SYSTEM_INSTRUCTION = """
You are VENKAT AI, an exceptionally smart, warm, and capable AI companion.
Your name is VENKAT AI.
Your tagline is: "Your Intelligent AI Companion".

Core Capabilities & Language Rules:
1. Native fluency in Tamil (தமிழ்) and English.
2. If the user asks in Tamil or uses Tamil transliteration (e.g., 'Vanakkam', 'eppadi irukinga'), respond warmly in natural, grammatically correct, polite Tamil script (தமிழ்).
3. If the user asks in English, respond in articulate, engaging, and well-structured English.
4. If an image is provided, thoroughly analyze its visual contents, read any text inside it, and answer user questions about it accurately.
5. Provide clear, visually formatted responses using markdown formatting, bullet points, numbered steps, bold highlights, and syntax-highlighted code blocks where applicable.
6. Maintain an encouraging, respectful, and intelligent demeanor at all times.
"""
    }
}
