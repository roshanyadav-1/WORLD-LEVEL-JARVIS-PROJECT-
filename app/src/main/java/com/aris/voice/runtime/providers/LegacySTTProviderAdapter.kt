package com.aris.voice.runtime.providers

import android.content.Context
import com.aris.voice.core.ArisError
import com.aris.voice.core.ArisResult
import com.aris.voice.runtime.ISpeechRecognitionProvider
import com.aris.voice.utilities.STTManager

class LegacySTTProviderAdapter(private val context: Context) : ISpeechRecognitionProvider {
    private val sttManager: STTManager = STTManager.getInstance(context)

    override suspend fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (ArisError) -> Unit
    ): ArisResult<Unit> {
        sttManager.startListening(
            onResult = { finalResult ->
                onFinalResult(finalResult)
            },
            onError = { errorString ->
                val error = when {
                    errorString.contains("No speech match") -> ArisError.AudioError("NO_SPEECH", "No speech detected")
                    errorString.contains("Network timeout") || errorString.contains("Speech timeout") -> ArisError.AudioError("TIMEOUT", "Speech recognition timed out")
                    errorString.contains("Insufficient permissions") -> ArisError.AudioError("PERMISSION_DENIED", "Microphone permission is unavailable")
                    else -> ArisError.AudioError("RECOGNITION_FAILED", errorString)
                }
                onError(error)
            },
            onListeningStateChange = { isListening ->
                // Do we need to bubble this up? The orchestrator manages state.
            },
            onPartialResult = { partialText ->
                onPartialResult(partialText)
            }
        )
        return ArisResult.Success(Unit)
    }

    override suspend fun stopListening(): ArisResult<Unit> {
        sttManager.stopListening()
        return ArisResult.Success(Unit)
    }
}
