package com.aris.voice.utilities

import android.util.Log
import com.aris.voice.BuildConfig

object Logger {
    private const val DEFAULT_TAG = "ArisVoice"
    
    // Enable logging based on build configuration
    private val isLoggingEnabled = BuildConfig.ENABLE_LOGGING
    
    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.d(tag, message)
        }
    }
    
    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.i(tag, message)
        }
    }
    
    fun w(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.w(tag, message)
        }
    }
    
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isLoggingEnabled) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
        
        // Track diagnostic errors inside ErrorTracker
        val module = when {
            tag.contains("TTS", ignoreCase = true) || tag.contains("Speech", ignoreCase = true) && message.contains("speak", ignoreCase = true) -> SystemModule.TTS
            tag.contains("STT", ignoreCase = true) || tag.contains("Recognizer", ignoreCase = true) -> SystemModule.STT
            tag.contains("Gemini", ignoreCase = true) || tag.contains("LLM", ignoreCase = true) -> SystemModule.GEMINI
            tag.contains("Accessibility", ignoreCase = true) || tag.contains("Screen", ignoreCase = true) -> SystemModule.ACCESSIBILITY
            tag.contains("Wake", ignoreCase = true) || tag.contains("Porcupine", ignoreCase = true) || tag.contains("Picovoice", ignoreCase = true) -> SystemModule.WAKE_WORD
            tag.contains("Billing", ignoreCase = true) || tag.contains("Purchase", ignoreCase = true) -> SystemModule.BILLING
            tag.contains("Permission", ignoreCase = true) -> SystemModule.PERMISSIONS
            else -> SystemModule.GEMINI // Default to main brain or general
        }
        ErrorTracker.logError(
            module, 
            message, 
            ErrorSeverity.ERROR, 
            throwable?.stackTraceToString() ?: "No technical details available."
        )
    }
    
    fun v(tag: String = DEFAULT_TAG, message: String) {
        if (isLoggingEnabled) {
            Log.v(tag, message)
        }
    }
    
    fun wtf(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (isLoggingEnabled) {
            if (throwable != null) {
                Log.wtf(tag, message, throwable)
            } else {
                Log.wtf(tag, message)
            }
        }
    }
}