package com.aris.voice.core

/**
 * Categorized error hierarchy representing system errors within ARIS.
 * This ensures that errors are strongly-typed, predictable, and fully translatable
 * across different layers of the AI Operating System.
 */
sealed class ArisError(
    val code: String,
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    class BrainError(code: String, message: String, cause: Throwable? = null) :
        ArisError("BRAIN_$code", message, cause)

    class MemoryError(code: String, message: String, cause: Throwable? = null) :
        ArisError("MEMORY_$code", message, cause)

    class PerceptionError(code: String, message: String, cause: Throwable? = null) :
        ArisError("PERCEPTION_$code", message, cause)

    class ExecutionError(code: String, message: String, cause: Throwable? = null) :
        ArisError("EXECUTION_$code", message, cause)

    class ToolError(code: String, message: String, cause: Throwable? = null) :
        ArisError("TOOL_$code", message, cause)

    class ConversationError(code: String, message: String, cause: Throwable? = null) :
        ArisError("CONVERSATION_$code", message, cause)

    class AudioError(code: String, message: String, cause: Throwable? = null) :
        ArisError("AUDIO_$code", message, cause)

    class ConfigurationError(code: String, message: String, cause: Throwable? = null) :
        ArisError("CONFIG_$code", message, cause)

    class UnknownError(message: String, cause: Throwable? = null) :
        ArisError("UNKNOWN", message, cause)
}
