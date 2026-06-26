package com.aris.voice.audio

import com.aris.voice.core.ArisResult
import kotlinx.coroutines.flow.Flow

/**
 * Microphone audio capturing interface to feed speech recognition.
 */
interface IAudioStreamProvider {
    /**
     * Start capturing continuous microphone stream chunk signals.
     */
    fun startRecording(): ArisResult<Flow<ByteArray>>

    /**
     * Stop capturing continuous microphone stream.
     */
    fun stopRecording()
}

/**
 * Detects the key-activation phrase triggers locally.
 */
interface IWakeWordDetector {
    /**
     * Feeds audio chunks into the wake-word engine.
     * Returns true if wake-word trigger is recognized.
     */
    fun processAudioChunk(audioData: ShortArray): Boolean
}

/**
 * Transcribes real-time user speech streams to raw text strings.
 */
interface ISpeechRecognizer {
    /**
     * Begins transcription. Emits partial and final recognized text blocks.
     */
    fun startTranscription(): ArisResult<Flow<String>>

    /**
     * Ends the active speech recognition stream.
     */
    fun stopTranscription()
}
