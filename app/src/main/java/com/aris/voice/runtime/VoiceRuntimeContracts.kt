package com.aris.voice.runtime

import com.aris.voice.core.ArisResult
import com.aris.voice.domain.AudioState
import com.aris.voice.domain.RuntimeConversationContext
import com.aris.voice.domain.RuntimeConversationState
import com.aris.voice.domain.SpeechInputData
import com.aris.voice.domain.SpeechOutputData
import com.aris.voice.domain.VoiceState
import kotlinx.coroutines.flow.StateFlow

interface IAudioRuntimeManager {
    val audioState: StateFlow<AudioState>
    suspend fun startRecording(): ArisResult<Unit>
    suspend fun stopRecording(): ArisResult<Unit>
    suspend fun startPlayback(): ArisResult<Unit>
    suspend fun stopPlayback(): ArisResult<Unit>
    suspend fun requestAudioFocus(): ArisResult<Unit>
    suspend fun releaseAudioFocus(): ArisResult<Unit>
}

interface ISpeechInputProcessor {
    suspend fun startListening(
        onPartialResult: (SpeechInputData) -> Unit,
        onFinalResult: (SpeechInputData) -> Unit,
        onError: (com.aris.voice.core.ArisError) -> Unit
    ): ArisResult<Unit>
    suspend fun stopListening(): ArisResult<Unit>
    suspend fun processSpeech(rawText: String, isFinal: Boolean): ArisResult<SpeechInputData>
}

interface IVoiceConversationManager {
    val conversationState: StateFlow<RuntimeConversationState>
    val currentContext: RuntimeConversationContext?
    
    suspend fun handleInput(input: SpeechInputData): ArisResult<String>
    suspend fun handleBrainResponse(response: String, requiresConfirmation: Boolean = false, expectFollowUp: Boolean = false): ArisResult<Unit>
    suspend fun interrupt(): ArisResult<Unit>
    suspend fun setSpeaking(): ArisResult<Unit>
    suspend fun setWaitingForBrain(): ArisResult<Unit>
    suspend fun setListening(): ArisResult<Unit>
    suspend fun reset(): ArisResult<Unit>
}

interface ISpeechOutputProcessor {
    val outputState: StateFlow<com.aris.voice.domain.SpeechOutputState>
    suspend fun speak(outputData: SpeechOutputData): ArisResult<Unit>
    suspend fun stop(): ArisResult<Unit>
}

interface IVoiceOrchestrator {
    val voiceState: StateFlow<VoiceState>
    val currentDecision: com.aris.voice.domain.Decision?
    suspend fun startInteraction(): ArisResult<Unit>
    suspend fun processInput(rawText: String): ArisResult<Unit>
    suspend fun cancelInteraction(): ArisResult<Unit>
}

interface IVoiceSessionManager {
    val sessionState: StateFlow<com.aris.voice.domain.VoiceSessionState>
    val currentSession: com.aris.voice.domain.VoiceSessionContext?
    
    suspend fun startSession(): ArisResult<Unit>
    suspend fun processInput(rawText: String): ArisResult<Unit>
    suspend fun endSession(): ArisResult<Unit>
    suspend fun cancelSession(): ArisResult<Unit>
    suspend fun interruptSession(): ArisResult<Unit>
    suspend fun tick(): ArisResult<Unit> // for timeout checking
}

// Provider Abstractions
interface IAudioProvider {
    suspend fun startAudioSession(): ArisResult<Unit>
    suspend fun stopAudioSession(): ArisResult<Unit>
}

interface ISpeechRecognitionProvider {
    suspend fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (com.aris.voice.core.ArisError) -> Unit
    ): ArisResult<Unit>
    suspend fun stopListening(): ArisResult<Unit>
}

interface ITextToSpeechProvider {
    suspend fun speak(text: String, onComplete: () -> Unit, onError: (String) -> Unit): ArisResult<Unit>
    suspend fun stop(): ArisResult<Unit>
}
