package com.aris.voice.runtime

import com.aris.voice.core.ArisResult
import com.aris.voice.core.ArisError
import com.aris.voice.domain.AudioState
import com.aris.voice.domain.RuntimeConversationContext
import com.aris.voice.domain.RuntimeConversationState
import com.aris.voice.domain.SpeechInputData
import com.aris.voice.domain.SpeechOutputData
import com.aris.voice.domain.VoiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class AudioRuntimeManagerImpl(
    private val audioProvider: IAudioProvider
) : IAudioRuntimeManager {
    
    private val _audioState = MutableStateFlow(AudioState.STOPPED)
    override val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    override suspend fun startRecording(): ArisResult<Unit> {
        val result = audioProvider.startAudioSession()
        if (result is ArisResult.Success) {
            _audioState.value = AudioState.RECORDING
        }
        return result
    }

    override suspend fun stopRecording(): ArisResult<Unit> {
        val result = audioProvider.stopAudioSession()
        if (result is ArisResult.Success) {
            _audioState.value = AudioState.STOPPED
        }
        return result
    }

    override suspend fun startPlayback(): ArisResult<Unit> {
        _audioState.value = AudioState.PLAYING
        return ArisResult.Success(Unit)
    }

    override suspend fun stopPlayback(): ArisResult<Unit> {
        _audioState.value = AudioState.STOPPED
        return ArisResult.Success(Unit)
    }

    override suspend fun requestAudioFocus(): ArisResult<Unit> {
        return ArisResult.Success(Unit)
    }

    override suspend fun releaseAudioFocus(): ArisResult<Unit> {
        return ArisResult.Success(Unit)
    }
}

class SpeechInputProcessorImpl(
    private val sttProvider: ISpeechRecognitionProvider
) : ISpeechInputProcessor {

    override suspend fun startListening(
        onPartialResult: (SpeechInputData) -> Unit,
        onFinalResult: (SpeechInputData) -> Unit,
        onError: (ArisError) -> Unit
    ): ArisResult<Unit> {
        return sttProvider.startListening(
            onPartialResult = { rawText ->
                val result = processSpeechInternal(rawText, isFinal = false)
                onPartialResult(result)
            },
            onFinalResult = { rawText ->
                val result = processSpeechInternal(rawText, isFinal = true)
                onFinalResult(result)
            },
            onError = onError
        )
    }

    override suspend fun stopListening(): ArisResult<Unit> {
        return sttProvider.stopListening()
    }

    override suspend fun processSpeech(rawText: String, isFinal: Boolean): ArisResult<SpeechInputData> {
        return ArisResult.Success(processSpeechInternal(rawText, isFinal))
    }

    private fun processSpeechInternal(rawText: String, isFinal: Boolean): SpeechInputData {
        val normalized = normalizeText(rawText)
        return SpeechInputData(
            rawText = rawText,
            normalizedText = normalized,
            confidence = 1.0f,
            isFinal = isFinal
        )
    }

    private fun normalizeText(text: String): String {
        var result = text.trim()
        
        // Remove filler words
        val fillerWords = listOf("umm", "hmm", "please", "actually")
        for (word in fillerWords) {
            // Regex to match the word as a standalone word (case insensitive)
            val regex = "(?i)\\b$word\\b".toRegex()
            result = result.replace(regex, "").trim()
        }
        
        // Remove multiple spaces left behind by filler word removal
        result = result.replace("\\s+".toRegex(), " ")

        // Capitalization correction (capitalize first letter, leave rest intact unless we have better rules)
        if (result.isNotEmpty()) {
            result = result.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        // Alias normalization
        val aliases = mapOf(
            "insta kholo" to "Open Instagram",
            "insta" to "Instagram",
            "yt" to "YouTube",
            "msg" to "Message"
        )
        
        // Full string matches for commands like "Insta kholo" -> "Open Instagram"
        aliases.forEach { (alias, normalized) ->
            if (result.lowercase() == alias) {
                return normalized
            }
        }
        
        // Word level replacement for generic aliases
        var words = result.split(" ").toMutableList()
        for (i in words.indices) {
            val lowerWord = words[i].lowercase()
            if (aliases.containsKey(lowerWord) && lowerWord != "insta kholo") {
                words[i] = aliases[lowerWord]!!
            }
        }
        
        return words.joinToString(" ").trim()
    }
}

class VoiceConversationManagerImpl : IVoiceConversationManager {
    private val _conversationState = MutableStateFlow(RuntimeConversationState.IDLE)
    override val conversationState: StateFlow<RuntimeConversationState> = _conversationState.asStateFlow()

    private var _currentContext: RuntimeConversationContext? = null
    override val currentContext: RuntimeConversationContext?
        get() = _currentContext

    override suspend fun handleInput(input: SpeechInputData): ArisResult<String> {
        val state = _conversationState.value
        
        // Clean context if expired
        if (_currentContext?.isExpired() == true) {
            _currentContext = null
        }
        
        // Handle input with current context
        val contextPrefix = when (state) {
            RuntimeConversationState.AWAITING_CONFIRMATION -> "Confirmation reply: "
            RuntimeConversationState.AWAITING_FOLLOW_UP -> "Follow up: "
            else -> ""
        }
        
        val contextSuffix = if (_currentContext?.previousBrainResponse != null) {
            " (Context: Previous response was '${_currentContext?.previousBrainResponse}')"
        } else {
            ""
        }
        
        val requestToBrain = "$contextPrefix${input.normalizedText}$contextSuffix"
        
        _conversationState.value = RuntimeConversationState.WAITING_FOR_BRAIN
        
        return ArisResult.Success(requestToBrain)
    }

    override suspend fun handleBrainResponse(response: String, requiresConfirmation: Boolean, expectFollowUp: Boolean): ArisResult<Unit> {
        _currentContext = RuntimeConversationContext(
            conversationId = _currentContext?.conversationId ?: UUID.randomUUID().toString(),
            pendingConfirmationRequest = if (requiresConfirmation) response else null,
            previousBrainResponse = response,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        
        if (requiresConfirmation) {
            _conversationState.value = RuntimeConversationState.AWAITING_CONFIRMATION
        } else if (expectFollowUp) {
            _conversationState.value = RuntimeConversationState.AWAITING_FOLLOW_UP
        } else {
            _conversationState.value = RuntimeConversationState.SPEAKING
        }
        
        return ArisResult.Success(Unit)
    }

    override suspend fun interrupt(): ArisResult<Unit> {
        _conversationState.value = RuntimeConversationState.INTERRUPTED
        _currentContext = null
        return ArisResult.Success(Unit)
    }

    override suspend fun setSpeaking(): ArisResult<Unit> {
        _conversationState.value = RuntimeConversationState.SPEAKING
        return ArisResult.Success(Unit)
    }

    override suspend fun setWaitingForBrain(): ArisResult<Unit> {
        _conversationState.value = RuntimeConversationState.WAITING_FOR_BRAIN
        return ArisResult.Success(Unit)
    }

    override suspend fun setListening(): ArisResult<Unit> {
        _conversationState.value = RuntimeConversationState.LISTENING
        return ArisResult.Success(Unit)
    }

    override suspend fun reset(): ArisResult<Unit> {
        _conversationState.value = RuntimeConversationState.IDLE
        _currentContext = null
        return ArisResult.Success(Unit)
    }
}

class SpeechOutputProcessorImpl(
    private val ttsProvider: ITextToSpeechProvider,
    private val responseFormatter: ResponseFormatter = ResponseFormatter()
) : ISpeechOutputProcessor {

    private val _outputState = MutableStateFlow(com.aris.voice.domain.SpeechOutputState.IDLE)
    override val outputState: StateFlow<com.aris.voice.domain.SpeechOutputState> = _outputState.asStateFlow()

    override suspend fun speak(outputData: SpeechOutputData): ArisResult<Unit> {
        _outputState.value = com.aris.voice.domain.SpeechOutputState.STARTED
        val formattedText = responseFormatter.format(outputData.rawContent)
        _outputState.value = com.aris.voice.domain.SpeechOutputState.SPEAKING
        
        val result = ttsProvider.speak(
            text = formattedText,
            onComplete = {
                _outputState.value = com.aris.voice.domain.SpeechOutputState.COMPLETED
            },
            onError = { _ ->
                _outputState.value = com.aris.voice.domain.SpeechOutputState.FAILED
            }
        )
        
        if (result is ArisResult.Failure) {
             _outputState.value = com.aris.voice.domain.SpeechOutputState.FAILED
        }
        return result
    }

    override suspend fun stop(): ArisResult<Unit> {
        val result = ttsProvider.stop()
        _outputState.value = com.aris.voice.domain.SpeechOutputState.CANCELLED
        return result
    }
}

class VoiceOrchestratorImpl(
    private val audioManager: IAudioRuntimeManager,
    private val inputProcessor: ISpeechInputProcessor,
    private val conversationManager: IVoiceConversationManager,
    private val outputProcessor: ISpeechOutputProcessor,
    private val brainOrchestrator: com.aris.voice.brain.IBrainOrchestrator,
    private val executionOrchestrator: com.aris.voice.brain.IExecutionOrchestrator
) : IVoiceOrchestrator {

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    override val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private var _currentDecision: com.aris.voice.domain.Decision? = null
    override val currentDecision: com.aris.voice.domain.Decision?
        get() = _currentDecision

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    override suspend fun startInteraction(): ArisResult<Unit> {
        _voiceState.value = VoiceState.LISTENING
        audioManager.requestAudioFocus()
        audioManager.startRecording()
        
        return inputProcessor.startListening(
            onPartialResult = { partial ->
                // Do nothing for partial results in orchestrator currently
            },
            onFinalResult = { final ->
                scope.launch {
                    processFinalInput(final)
                }
            },
            onError = { error ->
                _voiceState.value = VoiceState.ERROR
                scope.launch {
                    audioManager.stopRecording()
                    audioManager.releaseAudioFocus()
                }
            }
        )
    }

    private suspend fun processFinalInput(inputData: SpeechInputData): ArisResult<Unit> {
        _voiceState.value = VoiceState.PROCESSING
        inputProcessor.stopListening()
        audioManager.stopRecording()
        
        val convoResult = conversationManager.handleInput(inputData)
        if (convoResult is ArisResult.Failure) {
             _voiceState.value = VoiceState.ERROR
            return ArisResult.Failure(convoResult.error)
        }

        val processedText = (convoResult as ArisResult.Success).value
        
        // Pass to brain
        val brainResult = brainOrchestrator.think(processedText)
        if (brainResult is ArisResult.Failure) {
            _voiceState.value = VoiceState.ERROR
            return ArisResult.Failure(brainResult.error)
        }
        
        var decision = (brainResult as ArisResult.Success).value
        _currentDecision = decision // Preserve the complete decision object
        
        if (decision.type == com.aris.voice.domain.DecisionType.EXECUTE_PLAN && decision.plan != null) {
            val executionResult = executionOrchestrator.executeDecision(decision) { progress ->
                // Optionally handle progress
            }
            
            val followupBrainResult = brainOrchestrator.processExecutionResult(executionResult)
            if (followupBrainResult is ArisResult.Success) {
                decision = followupBrainResult.value
                _currentDecision = decision
            } else {
                 _voiceState.value = VoiceState.ERROR
                 return ArisResult.Failure((followupBrainResult as ArisResult.Failure).error)
            }
        }
        
        val responseText = decision.reason // Only forward the conversational response
        conversationManager.handleBrainResponse(responseText)
        
        _voiceState.value = VoiceState.SPEAKING
        audioManager.startPlayback()
        
        val outputData = SpeechOutputData(
            id = UUID.randomUUID().toString(),
            text = responseText,
            rawContent = responseText
        )
        val speakResult = outputProcessor.speak(outputData)
        if (speakResult is ArisResult.Failure) {
             _voiceState.value = VoiceState.ERROR
             return ArisResult.Failure(speakResult.error)
        }
        
        _voiceState.value = VoiceState.IDLE
        audioManager.stopPlayback()
        audioManager.releaseAudioFocus()
        return ArisResult.Success(Unit)
    }

    override suspend fun processInput(rawText: String): ArisResult<Unit> {
        val inputResult = inputProcessor.processSpeech(rawText, true)
        if (inputResult is ArisResult.Failure) {
            return ArisResult.Failure(inputResult.error)
        }
        return processFinalInput((inputResult as ArisResult.Success).value)
    }

    override suspend fun cancelInteraction(): ArisResult<Unit> {
        _voiceState.value = VoiceState.IDLE
        inputProcessor.stopListening()
        audioManager.stopRecording()
        outputProcessor.stop()
        audioManager.stopPlayback()
        audioManager.releaseAudioFocus()
        conversationManager.reset()
        return ArisResult.Success(Unit)
    }
}

class VoiceSessionManagerImpl(
    private val orchestrator: IVoiceOrchestrator,
    private val conversationManager: IVoiceConversationManager
) : IVoiceSessionManager {
    private val _sessionState = MutableStateFlow(com.aris.voice.domain.VoiceSessionState.IDLE)
    override val sessionState: StateFlow<com.aris.voice.domain.VoiceSessionState> = _sessionState.asStateFlow()
    
    private var _currentSession: com.aris.voice.domain.VoiceSessionContext? = null
    override val currentSession: com.aris.voice.domain.VoiceSessionContext?
        get() = _currentSession

    override suspend fun startSession(): ArisResult<Unit> {
        _currentSession = com.aris.voice.domain.VoiceSessionContext(
            sessionId = UUID.randomUUID().toString(),
            state = com.aris.voice.domain.VoiceSessionState.ACTIVATED
        )
        _sessionState.value = com.aris.voice.domain.VoiceSessionState.ACTIVATED
        
        // Synchronize with orchestrator
        val result = orchestrator.startInteraction()
        if (result is ArisResult.Success) {
            _currentSession?.state = com.aris.voice.domain.VoiceSessionState.LISTENING
            _sessionState.value = com.aris.voice.domain.VoiceSessionState.LISTENING
            _currentSession?.lastActivityTime = System.currentTimeMillis()
        } else {
            handleError()
        }
        return result
    }

    override suspend fun processInput(rawText: String): ArisResult<Unit> {
        if (_currentSession == null) {
            _currentSession = com.aris.voice.domain.VoiceSessionContext(
                sessionId = UUID.randomUUID().toString(),
                state = com.aris.voice.domain.VoiceSessionState.ACTIVATED
            )
            _sessionState.value = com.aris.voice.domain.VoiceSessionState.ACTIVATED
        }
        _currentSession?.state = com.aris.voice.domain.VoiceSessionState.PROCESSING
        _sessionState.value = com.aris.voice.domain.VoiceSessionState.PROCESSING
        
        val result = orchestrator.processInput(rawText)
        if (result is ArisResult.Failure) {
            handleError()
        } else {
             _currentSession?.state = com.aris.voice.domain.VoiceSessionState.IDLE
             _sessionState.value = com.aris.voice.domain.VoiceSessionState.IDLE
        }
        return result
    }

    override suspend fun endSession(): ArisResult<Unit> {
        _currentSession?.state = com.aris.voice.domain.VoiceSessionState.COMPLETED
        _sessionState.value = com.aris.voice.domain.VoiceSessionState.COMPLETED
        cleanup()
        return ArisResult.Success(Unit)
    }

    override suspend fun cancelSession(): ArisResult<Unit> {
        _currentSession?.isCancelled = true
        _currentSession?.state = com.aris.voice.domain.VoiceSessionState.IDLE
        _sessionState.value = com.aris.voice.domain.VoiceSessionState.IDLE
        cleanup()
        return ArisResult.Success(Unit)
    }

    override suspend fun interruptSession(): ArisResult<Unit> {
        conversationManager.interrupt()
        cancelSession()
        return ArisResult.Success(Unit)
    }

    override suspend fun tick(): ArisResult<Unit> {
        val session = _currentSession
        if (session != null && session.state != com.aris.voice.domain.VoiceSessionState.IDLE && session.state != com.aris.voice.domain.VoiceSessionState.COMPLETED) {
            if (session.isInactive(60000)) { // 60 seconds timeout
                cancelSession()
            }
        }
        return ArisResult.Success(Unit)
    }

    private suspend fun cleanup() {
        orchestrator.cancelInteraction()
        _currentSession = null
        _sessionState.value = com.aris.voice.domain.VoiceSessionState.IDLE
    }
    
    private suspend fun handleError() {
        _currentSession?.state = com.aris.voice.domain.VoiceSessionState.ERROR
        _sessionState.value = com.aris.voice.domain.VoiceSessionState.ERROR
        cleanup()
    }
}
