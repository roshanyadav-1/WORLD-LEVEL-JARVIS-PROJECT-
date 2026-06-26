package com.aris.voice.utilities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class STTManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: STTManager? = null

        fun getInstance(context: Context): STTManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: STTManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private val isListening = AtomicBoolean(false)
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onListeningStateChange: ((Boolean) -> Unit)? = null
    private var onPartialResultCallback: ((String) -> Unit)? = null
    private var isInitialized = false
    private val visualizerManager = STTVisualizer(context)
    
    // Silence/VAD Timers Configuration
    private var silenceTimeoutJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private fun isComponentAvailableAndActive(component: android.content.ComponentName): Boolean {
        return try {
            val pm = context.packageManager
            val serviceInfo = pm.getServiceInfo(component, android.content.pm.PackageManager.MATCH_ALL)
            serviceInfo != null && serviceInfo.enabled
        } catch (e: Exception) {
            false
        }
    }

    private fun initializeSpeechRecognizer() {
        if (isInitialized) return
        
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val googleComponentName = android.content.ComponentName(
                "com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.service.SpeechRecognitionService"
            )
            
            if (isComponentAvailableAndActive(googleComponentName)) {
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context, googleComponentName)
                    speechRecognizer?.setRecognitionListener(createRecognitionListener())
                    isInitialized = true
                    Log.d("STTManager", "Speech recognizer initialized successfully with Google service")
                    return
                } catch (e: Exception) {
                    Log.w("STTManager", "Failed to initialize with Google service component name, falling back", e)
                }
            } else {
                Log.d("STTManager", "Google Quick Search Box SpeechRecognitionService is not available/active. Using default creator.")
            }

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                isInitialized = true
                Log.d("STTManager", "Default speech recognizer initialized successfully")
            } catch (ex: Exception) {
                Log.e("STTManager", "Failed to initialize default speech recognizer", ex)
            }
        } else {
            Log.e("STTManager", "Speech recognition not available on this device")
        }
    }
    
    private fun startSilenceTimer(seconds: Long = 8) {
        silenceTimeoutJob?.cancel()
        silenceTimeoutJob = scope.launch {
            delay(seconds * 1000L)
            Log.w("STTManager", "Voice activity detection (VAD) silence timeout reached.")
            isListening.set(false)
            onListeningStateChange?.invoke(false)
            visualizerManager.hide()
            onErrorCallback?.invoke("No speech match")
            
            // Destroy and recreate recognizer to prevent stuck states
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                isInitialized = false
            } catch (e: Exception) {
                Log.e("STTManager", "Error clearing recognizer on VAD timeout", e)
            }
        }
    }

    private fun resetSilenceTimer() {
        silenceTimeoutJob?.cancel()
    }
    
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("STTManager", "Ready for speech")
                isListening.set(true)
                onListeningStateChange?.invoke(true)
                startSilenceTimer(seconds = 8) // Start 8 second silence monitor
            }
            
            override fun onBeginningOfSpeech() {
                Log.d("STTManager", "Beginning of speech")
                resetSilenceTimer() // Reset silence monitor as user started speaking
            }

            override fun onRmsChanged(rmsdB: Float) {
                visualizerManager.onRmsChanged(rmsdB)
                if (rmsdB > 2.0f) {
                    resetSilenceTimer() // User is actively making sound
                }
            }
            
            override fun onBufferReceived(buffer: ByteArray?) {
            }
            
            override fun onEndOfSpeech() {
                Log.d("STTManager", "End of speech")
                isListening.set(false)
                onListeningStateChange?.invoke(false)
                onPartialResultCallback = null
                resetSilenceTimer()
            }
            
            override fun onError(error: Int) {
                isListening.set(false)
                onListeningStateChange?.invoke(false)
                visualizerManager.hide()
                resetSilenceTimer()

                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error: $error"
                }
                
                Log.e("STTManager", "Speech recognition error: $errorMessage")
                
                // Destroy and recreate the recognizer to prevent it from getting stuck in a bad state
                try {
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                    isInitialized = false
                } catch (e: Exception) {
                    Log.e("STTManager", "Error clearing recognizer on error", e)
                }

                onErrorCallback?.invoke(errorMessage)
                onPartialResultCallback = null
            }
            
            override fun onResults(results: Bundle?) {
                isListening.set(false)
                onListeningStateChange?.invoke(false)
                visualizerManager.hide()
                resetSilenceTimer()

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    Log.d("STTManager", "Recognized text: $recognizedText")
                    onResultCallback?.invoke(recognizedText)
                } else {
                    Log.w("STTManager", "No results from speech recognition")
                    onErrorCallback?.invoke("No speech match")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                resetSilenceTimer() // Continuously reset as new voice partials arrive
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val partialText = matches[0]
                    Log.d("STTManager", "Partial result: $partialText")
                    onPartialResultCallback?.invoke(partialText)
                }
            }
            
            override fun onEvent(eventType: Int, params: Bundle?) {
            }
        }
    }
    
    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningStateChange: (Boolean) -> Unit,
        onPartialResult: (String) -> Unit
    ) {
        if (isListening.get()) {
            Log.w("STTManager", "Already listening")
            return
        }
        
        this.onResultCallback = onResult
        this.onErrorCallback = onError
        this.onListeningStateChange = onListeningStateChange
        this.onPartialResultCallback = onPartialResult

        scope.launch {
            initializeSpeechRecognizer()
            
            if (speechRecognizer == null) {
                onError("Speech recognition not available")
                return@launch
            }
            visualizerManager.show()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                // Set secondary and preferred languages for fluent Mix Language support (English-Hindi)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("en-IN", "hi-IN", "en-US"))
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                
                // Make mic smarter: Wait longer (3.5 seconds) if the user pauses/stops talking to prevent premature trigger
                putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 3500L)
                putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 3500L)
                putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 2000L)
            }
            
            try {
                speechRecognizer?.startListening(intent)
                Log.d("STTManager", "Started listening")
            } catch (e: Exception) {
                Log.e("STTManager", "Error starting speech recognition", e)
                onError("Failed to start speech recognition: ${e.message}")
            }
        }
    }
    
    fun stopListening() {
        resetSilenceTimer()
        scope.launch {
            if (isListening.get() && speechRecognizer != null) {
                try {
                    speechRecognizer?.stopListening()
                    isListening.set(false)
                    onListeningStateChange?.invoke(false)
                    Log.d("STTManager", "Stopped listening")
                } catch (e: Exception) {
                    Log.e("STTManager", "Error stopping speech recognition", e)
                }
            }
        }
    }
    
    fun isCurrentlyListening(): Boolean = isListening.get()
    
    fun shutdown() {
        resetSilenceTimer()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("STTManager", "Error destroying speech recognizer", e)
        }
        visualizerManager.hide()

        speechRecognizer = null
        isListening.set(false)
        isInitialized = false
        Log.d("STTManager", "STT Manager shutdown")
    }
}
