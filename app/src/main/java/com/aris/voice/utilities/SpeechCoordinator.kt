package com.aris.voice.utilities

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.aris.voice.api.GoogleTts
import com.aris.voice.api.TTSVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

class SpeechCoordinator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SpeechCoordinator"

        @Volatile private var INSTANCE: SpeechCoordinator? = null

        fun getInstance(context: Context): SpeechCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpeechCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val ttsManager = TTSManager.getInstance(context)
    private val sttManager = STTManager.getInstance(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    // Mutex to ensure only one speech operation at a time
    private val speechMutex = Mutex()
    private var ttsPlaybackJob: Job? = null

    // State tracking using thread-safe AtomicBoolean
    private val isSpeaking = AtomicBoolean(false)
    private val isListening = AtomicBoolean(false)

    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || 
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            Log.d(TAG, "Audio focus lost. Stopping playback and listening.")
                            stop()
                            stopListening()
                        }
                    }
                    .build()
                audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || 
                            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            Log.d(TAG, "Audio focus lost. Stopping playback and listening.")
                            stop()
                            stopListening()
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus", e)
            false
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon audio focus", e)
        }
    }

    /**
     * Speak text using TTS, ensuring STT is not listening
     * @param text The text to speak
     */
    suspend fun speakText(text: String) {
        // Avoid duplicate implementation - delegate to speakToUser
        speakToUser(text)
    }

    /**
     * Speak text to user, ensuring STT is not listening
     * @param text The text to speak to the user
     */
    suspend fun speakToUser(text: String) {
        val cleanedText = text.replace("*", "")
        
        // Interrupt previous speech job and stop playback immediately (BUG-3 & BUG-9)
        ttsPlaybackJob?.cancel(CancellationException("New speech command requested."))
        ttsManager.stop()
        
        delay(100) // Brief delay for cancellation cleanup

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                requestAudioFocus()
                if (isListening.get()) {
                    Log.d(TAG, "Stopping STT before speaking to user: $cleanedText")
                    sttManager.stopListening()
                    isListening.set(false)
                    delay(250) // Brief pause
                }

                isSpeaking.set(true)
                Log.d(TAG, "Starting TTS to user: $cleanedText")

                ttsManager.speakToUser(cleanedText)

                Log.d(TAG, "TTS to user completed: $cleanedText")

            } catch (e: CancellationException) {
                Log.d(TAG, "Speech job cancelled: $cleanedText")
                throw e
            } finally {
                isSpeaking.set(false)
                abandonAudioFocus()
            }
        }
        ttsPlaybackJob = job
        job.join()
    }

    /**
     * Plays raw audio data directly using TTSManager, bypassing synthesis.
     */
    suspend fun playAudioData(data: ByteArray) {
        ttsPlaybackJob?.cancel(CancellationException("New audio data request received"))
        ttsPlaybackJob = CoroutineScope(Dispatchers.IO).launch {
            speechMutex.withLock {
                try {
                    requestAudioFocus()
                    if (isListening.get()) {
                        sttManager.stopListening()
                        isListening.set(false)
                        delay(200)
                    }
                    ttsManager.playAudioData(data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during audio data playback", e)
                } finally {
                    abandonAudioFocus()
                }
            }
        }
    }

    /**
     * Synthesize and test a voice.
     */
    suspend fun testVoice(text: String, voice: TTSVoice) {
        ttsPlaybackJob?.cancel(CancellationException("New voice test request received"))
        ttsPlaybackJob = CoroutineScope(Dispatchers.IO).launch {
            speechMutex.withLock {
                try {
                    requestAudioFocus()
                    if (isListening.get()) {
                        sttManager.stopListening()
                        isListening.set(false)
                        delay(200)
                    }
                    ttsManager.speakToUser(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error during voice test", e)
                } finally {
                    abandonAudioFocus()
                }
            }
        }
    }

    fun stop() {
        ttsPlaybackJob?.cancel(CancellationException("Playback stopped by user action"))
        ttsManager.stop()
        isSpeaking.set(false)
        abandonAudioFocus()
        Log.d(TAG, "All TTS playback stopped by coordinator.")
    }

    suspend fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit,
        onPartialResult: (String) -> Unit
    ) {
        stop() // Stop TTS before listening
        speechMutex.withLock {
            try {
                if (isSpeaking.get()) {
                    Log.d(TAG, "Waiting for TTS to complete before starting STT")
                    while (isSpeaking.get()) {
                        delay(100)
                    }
                    delay(250)
                }

                isListening.set(true)
                sttManager.startListening(
                    onResult = { result -> onResult(result) },
                    onError = { error -> onError(error) },
                    onListeningStateChange = { listening ->
                        isListening.set(listening)
                        onListeningStateChange(listening)
                    },
                    onPartialResult = { partialText -> onPartialResult(partialText) }
                )

            } catch (e: Exception) {
                isListening.set(false)
                onError("Failed to start speech recognition: ${e.message}")
            }
        }
    }

    fun stopListening() {
        if (isListening.get()) {
            sttManager.stopListening()
            isListening.set(false)
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
        isSpeaking.set(false)
        abandonAudioFocus()
        Log.d(TAG, "Speaking explicitly stopped.")
    }

    fun isCurrentlySpeaking(): Boolean = isSpeaking.get()

    fun isCurrentlyListening(): Boolean = isListening.get()

    fun isSpeechActive(): Boolean = isSpeaking.get() || isListening.get()

    suspend fun waitForSpeechCompletion() {
        while (isSpeechActive()) {
            delay(100)
        }
    }

    fun shutdown() {
        stopListening()
        sttManager.shutdown()
        INSTANCE = null // Clear the volatile local reference so a clean one can be instantiated next time
    }
}
