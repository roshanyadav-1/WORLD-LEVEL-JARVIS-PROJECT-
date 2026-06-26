package com.aris.voice.conversation

import android.content.Context
import android.util.Log
import com.aris.voice.core.ArisError
import com.aris.voice.core.ArisResult
import com.aris.voice.utilities.SpeechCoordinator

class ConversationImpl(private val context: Context) : 
    IDialogueManager, 
    ITextToSpeech, 
    IConversationContext {

    private val TAG = "ArisConversationImpl"
    private val speechCoordinator = SpeechCoordinator.getInstance(context)
    private val messages = mutableListOf<Pair<String, String>>()

    // ITextToSpeech
    override suspend fun speak(text: String): ArisResult<Unit> {
        return try {
            speechCoordinator.speakToUser(text)
            ArisResult.Success(Unit)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.AudioError("TTS_SPEECH_FAILED", "Failed speech synthesis for text", e))
        }
    }

    override fun stopSpeaking() {
        speechCoordinator.stopSpeaking()
    }

    override fun isSpeaking(): Boolean {
        return speechCoordinator.isCurrentlySpeaking()
    }

    // IConversationContext
    override fun getConversationHistory(): List<String> {
        return messages.map { "${it.first}: ${it.second}" }
    }

    override fun addMessage(speaker: String, text: String) {
        messages.add(speaker to text)
        if (messages.size > 50) {
            messages.removeAt(0)
        }
    }

    override fun clearHistory() {
        messages.clear()
    }

    // IDialogueManager
    override suspend fun processUserStatement(text: String): ArisResult<String> {
        Log.d(TAG, "Processing statement: $text")
        addMessage("User", text)
        val reply = "Processing command: $text"
        addMessage("A.R.I.S", reply)
        return ArisResult.Success(reply)
    }

    override suspend fun presentClarificationPrompt(prompt: String): ArisResult<String> {
        Log.d(TAG, "Presenting clarification prompt: $prompt")
        speak(prompt)
        addMessage("A.R.I.S", prompt)
        return ArisResult.Success("Clarification presented successfully")
    }
}
