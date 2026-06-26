package com.aris.voice.runtime.providers

import android.content.Context
import com.aris.voice.core.ArisError
import com.aris.voice.core.ArisResult
import com.aris.voice.runtime.ITextToSpeechProvider
import com.aris.voice.utilities.TTSManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LegacyTTSProviderAdapter(private val context: Context) : ITextToSpeechProvider {
    private val ttsManager = TTSManager.getInstance(context)

    override suspend fun speak(text: String, onComplete: () -> Unit, onError: (String) -> Unit): ArisResult<Unit> {
        return try {
            ttsManager.speakText(text)
            onComplete()
            ArisResult.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e.message ?: "Unknown TTS Error")
            ArisResult.Failure(ArisError.AudioError("TTS_ERROR", e.message ?: "Unknown TTS Error"))
        }
    }

    override suspend fun stop(): ArisResult<Unit> {
        ttsManager.stop()
        return ArisResult.Success(Unit)
    }
}
