package com.aris.voice.api

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineManagerErrorCallback
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PorcupineWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onApiFailure: () -> Unit
) {
    private var porcupineManager: PorcupineManager? = null
    private var isListening = false
    private val keyManager = PicovoiceKeyManager(context)
    private var coroutineScope: CoroutineScope? = null

    companion object {
        private const val TAG = "PorcupineWakeWordDetector"
    }

    fun start() {
        if (isListening) {
            Log.d(TAG, "Already started.")
            return
        }

        // Cancel any existing coroutine scope first to prevent leaks (BUG-3)
        coroutineScope?.cancel()
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // Start the key fetching process asynchronously
        coroutineScope?.launch {
            try {
                // Fetch the access key from PicovoiceKeyManager on IO context to prevent Main thread network call (BUG-12)
                val accessKey = withContext(Dispatchers.IO) { keyManager.getAccessKey() }
                if (accessKey != null) {
                    Log.d(TAG, "Successfully obtained Picovoice access key")
                    startPorcupineWithKey(accessKey)
                } else {
                    Log.e(TAG, "Failed to obtain Picovoice access key. Triggering API failure callback.")
                    onApiFailure()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting access key: ${e.message}")
                onApiFailure()
            }
        }
    }

    private suspend fun startPorcupineWithKey(accessKey: String) = withContext(Dispatchers.Main) {
        try {
            var lastWakeWordTime = 0L
            val wakeWordCooldownMs = 2000L

            // Create the wake word callback with cooldown protection (IMP-5)
            val wakeWordCallback = PorcupineManagerCallback { keywordIndex ->
                val now = System.currentTimeMillis()
                if (now - lastWakeWordTime > wakeWordCooldownMs) {
                    lastWakeWordTime = now
                    Log.d(TAG, "Wake word detected! Keyword index: $keywordIndex")
                    // Use Handler/Coroutine to avoid blocking the audio callback thread
                    coroutineScope?.launch { onWakeWordDetected() }
                } else {
                    Log.d(TAG, "Wake word detection ignored within cooldown window")
                }
            }

            // Create error callback for debugging and resource deletion to prevent memory leak (BUG-4 & IMP-13)
            val errorCallback = PorcupineManagerErrorCallback { error ->
                Log.e(TAG, "Porcupine error: ${error.message}")
                if (isListening) {
                    Log.d(TAG, "Porcupine error occurred, triggering API failure callback")
                    stop() // Use proper stop method to avoid duplicated cleanup code
                    onApiFailure()
                }
            }

            // Retrieve user customizable sensitivity from Settings preferences (BUG-5 & IMP-3)
            val sharedPrefs = context.getSharedPreferences("ArisSettings", Context.MODE_PRIVATE)
            val sensitivity = sharedPrefs.getFloat("wake_word_sensitivity", 0.5f).coerceIn(0.0f, 1.0f)

            // Build and start PorcupineManager
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPaths(arrayOf("Aris_en_android_v3_0_0.ppn"))
                .setSensitivity(sensitivity) // Set user-adjustable sensitivity
                .setErrorCallback(errorCallback)
                .build(context, wakeWordCallback)

            porcupineManager?.start()
            isListening = true
            Log.d(TAG, "Porcupine wake word detection started successfully with sensitivity $sensitivity.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Porcupine: ${e.message}")
            try {
                porcupineManager?.stop()
                porcupineManager?.delete()
            } catch (ignored: Exception) {}
            porcupineManager = null
            isListening = false
            // Trigger API failure callback if Porcupine fails
            Log.d(TAG, "Porcupine failed to start, triggering API failure callback")
            onApiFailure()
        }
    }

    fun stop() {
        if (!isListening) {
            Log.d(TAG, "Already stopped.")
            return
        }

        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
            porcupineManager = null
            isListening = false
            Log.d(TAG, "Porcupine wake word detection stopped.")
            
            // Cancel the coroutine scope
            coroutineScope?.cancel()
            coroutineScope = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping wake word detection: ${e.message}")
        }
    }
} 