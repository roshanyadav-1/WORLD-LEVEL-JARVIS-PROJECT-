package com.aris.voice.utilities

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SystemModule(val displayName: String, val affectedCapability: String) {
    STT("Voice Input (STT)", "Speech recognition is broken. Axel cannot hear you."),
    TTS("Voice Output (TTS)", "Speech synthesis is broken. Axel cannot speak out loud."),
    GEMINI("Brain Engine (Gemini API)", "Thinking is broken. Axel cannot form smart responses or process custom intents."),
    ACCESSIBILITY("Screen Action Service", "Automation is broken. Axel cannot scroll, tap or capture screen components."),
    WAKE_WORD("Wake Word (Picovoice)", "Voice wakeup is broken. Axel won't trigger automatically on screen off or home screen."),
    BILLING("Subscription System", "Premium features cannot be unlocked or verified."),
    PERMISSIONS("System Permissions", "Axel lacks hardware/system permissions to run properly.")
}

enum class ErrorSeverity {
    WARNING, ERROR, CRITICAL
}

data class SystemError(
    val module: SystemModule,
    val message: String,
    val timestamp: Long,
    val severity: ErrorSeverity,
    val technicalDetails: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(timestamp))
}

object ErrorTracker {
    private val errors = mutableListOf<SystemError>()
    private val maxErrors = 50
    private val listeners = mutableListOf<() -> Unit>()

    // Current operational states
    private val moduleStates = mutableMapOf<SystemModule, ModuleStatus>()

    enum class ModuleStatus(val label: String, val colorHex: String) {
        HEALTHY("HEALTHY", "#4CAF50"),
        DEGRADED("DEGRADED", "#FF9800"),
        CRITICAL("CRITICAL ERROR", "#F44336"),
        UNKNOWN("NOT TESTING", "#9E9E9E")
    }

    init {
        // Initialize default states
        SystemModule.values().forEach { moduleStates[it] = ModuleStatus.UNKNOWN }
    }

    @Synchronized
    fun logError(module: SystemModule, message: String, severity: ErrorSeverity = ErrorSeverity.ERROR, technicalDetails: String? = null) {
        val error = SystemError(module, message, System.currentTimeMillis(), severity, technicalDetails)
        errors.add(0, error)
        if (errors.size > maxErrors) {
            errors.removeAt(errors.lastIndex)
        }
        
        // Update status of that module
        val currentStatus = when (severity) {
            ErrorSeverity.WARNING -> ModuleStatus.DEGRADED
            ErrorSeverity.ERROR, ErrorSeverity.CRITICAL -> ModuleStatus.CRITICAL
        }
        moduleStates[module] = currentStatus
        
        notifyListeners()
    }

    @Synchronized
    fun updateModuleStatus(module: SystemModule, status: ModuleStatus) {
        moduleStates[module] = status
        notifyListeners()
    }

    @Synchronized
    fun getModuleStatus(module: SystemModule): ModuleStatus {
        return moduleStates[module] ?: ModuleStatus.UNKNOWN
    }

    @Synchronized
    fun getErrors(): List<SystemError> {
        return ArrayList(errors)
    }

    @Synchronized
    fun clearErrors() {
        errors.clear()
        SystemModule.values().forEach { moduleStates[it] = ModuleStatus.HEALTHY }
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners() {
        val targets = synchronized(listeners) { ArrayList(listeners) }
        targets.forEach { it() }
    }
}
