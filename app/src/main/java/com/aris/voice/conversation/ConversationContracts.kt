package com.aris.voice.conversation

import com.aris.voice.core.ArisResult

/**
 * Manages active user conversation loops, handling dialogue states, turn-taking, and clarification requests.
 */
interface IDialogueManager {
    suspend fun processUserStatement(text: String): ArisResult<String>
    suspend fun presentClarificationPrompt(prompt: String): ArisResult<String>
}

/**
 * Text-to-speech engine to speak synthesized output back to the user.
 */
interface ITextToSpeech {
    suspend fun speak(text: String): ArisResult<Unit>
    fun stopSpeaking()
    fun isSpeaking(): Boolean
}

/**
 * Stores local dialog history, active participant slots, and speech-to-text contexts.
 */
interface IConversationContext {
    fun getConversationHistory(): List<String>
    fun addMessage(speaker: String, text: String)
    fun clearHistory()
}
