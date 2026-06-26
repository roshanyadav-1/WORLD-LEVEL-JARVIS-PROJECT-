package com.aris.voice.utilities

import android.graphics.Bitmap
import com.aris.voice.api.GeminiApi
import com.google.ai.client.generativeai.type.ImagePart
import com.google.ai.client.generativeai.type.TextPart

fun addResponse(
    role: String,
    prompt: String,
    chatHistory: List<Pair<String, List<Any>>>,
    imageBitmap: Bitmap? = null // MODIFIED: Accepts a Bitmap directly
): List<Pair<String, List<Any>>> {
    val updatedChat = chatHistory.toMutableList()

    val messageParts = mutableListOf<Any>()
    messageParts.add(TextPart(prompt))

    if (imageBitmap != null) {
        messageParts.add(ImagePart(imageBitmap))
    }

    updatedChat.add(Pair(role, messageParts))
    return updatedChat
}

fun addResponsePrePost(
    role: String,
    prompt: String,
    chatHistory: List<Pair<String, List<Any>>>,
    imageBefore: Bitmap? = null, // MODIFIED: Accepts a Bitmap
    imageAfter: Bitmap? = null  // MODIFIED: Accepts a Bitmap
): List<Pair<String, List<Any>>> {
    val updatedChat = chatHistory.toMutableList()
    val messageParts = mutableListOf<Any>()

    messageParts.add(TextPart(prompt))

    // Attach "before" image directly if available
    imageBefore?.let {
        messageParts.add(ImagePart(it))
    }

    // Attach "after" image directly if available
    imageAfter?.let {
        messageParts.add(ImagePart(it))
    }

    updatedChat.add(Pair(role, messageParts))
    return updatedChat
}

suspend fun getReasoningModelApiResponse(
    chat: List<Pair<String, List<Any>>>,
    enableSearch: Boolean = false,
    context: android.content.Context? = null
): String? {
    try {
        val cloudResponse = GeminiApi.generateContent(chat, enableSearch = enableSearch)
        if (cloudResponse != null && !cloudResponse.contains("No API Key Available") && !cloudResponse.contains("error_message")) {
            return cloudResponse
        }
    } catch (e: Exception) {
        android.util.Log.e("LLMHelper", "Gemini cloud call failed, fallback to local LLM", e)
    }

    if (context != null) {
        try {
            android.util.Log.i("LLMHelper", "Attempting local CLAEEngine fallback...")
            val engine = com.aris.voice.v2.llm.CLAEEngine.getInstance(context)
            engine.initialize()
            
            // Format chat into a clean conversation prompt
            val promptBuilder = java.lang.StringBuilder()
            promptBuilder.append("You are ARIS, an offline voice-controlled personal assistant. Keep responses extremely concise (1-2 sentences) and conversational.\n\n")
            for (turn in chat) {
                val role = turn.first
                val parts = turn.second
                val text = parts.filterIsInstance<com.google.ai.client.generativeai.type.TextPart>()
                    .joinToString("\n") { it.text }
                if (text.isNotBlank() && !text.contains("System Search Context") && !text.contains("system_context")) {
                    promptBuilder.append("$role: $text\n")
                }
            }
            promptBuilder.append("assistant:")

            val localText = engine.generateBlocking(com.aris.voice.v2.llm.CLAEEngine.ModelTier.TIER1, promptBuilder.toString())
            if (localText.isNotBlank()) {
                val sanitized = localText.replace("\"", "\\\"").replace("\n", " ")
                android.util.Log.i("LLMHelper", "Local LLM generated response: $sanitized")
                return """{"Type": "Reply", "Reply": "$sanitized", "Instruction": "", "Should End": "Continue"}"""
            }
        } catch (e: Exception) {
            android.util.Log.e("LLMHelper", "Local model fallback failed", e)
        }
    }
    return null
}

