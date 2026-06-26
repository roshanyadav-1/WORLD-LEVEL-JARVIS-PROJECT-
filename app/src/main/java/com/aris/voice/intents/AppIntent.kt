package com.aris.voice.intents

import android.content.Context
import android.content.Intent

/**
 * Contract for pluggable Android Intents the agent can invoke.
 * Implementations must have a public no-arg constructor to allow reflective discovery.
 */
interface AppIntent {
    /**
     * Unique, human-readable name used by the LLM to refer to this intent.
     * Example: "Dial".
     */
    val name: String

    /** A short description to show in prompts. */
    fun description(): String

    /**
     * Returns the parameters this intent accepts in a stable order, for prompting.
     */
    fun parametersSpec(): List<ParameterSpec>

    /**
     * Builds the actual Android Intent to launch, based on provided params.
     * Should return null if required parameters are missing/invalid.
     */
    fun buildIntent(context: Context, params: Map<String, Any?>): Intent?

    /**
     * Safely checks if there is any application on the device that can handle this intent.
     * This prevents ActivityNotFoundExceptions at runtime.
     * By default, it builds a sample intent with empty params to check resolution,
     * but implementations can override this if building requires specific valid params.
     */
    fun isSupported(context: Context): Boolean {
        return try {
            val dummyIntent = buildIntent(context, emptyMap())
            if (dummyIntent != null) {
                dummyIntent.resolveActivity(context.packageManager) != null
            } else {
                // If it can't build a dummy intent, assume true and let the executor handle it
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Formats the intent and its parameters into a prompt-friendly string for the LLM.
     */
    fun toPromptFormat(): String {
        val params = parametersSpec().joinToString(", ") { 
            "${it.name}: ${it.type}${if (it.required) " (Required)" else " (Optional)"} - ${it.description}" 
        }
        return "- $name: ${description()}\n  Parameters: [${if (params.isEmpty()) "None" else params}]"
    }
}

/** Parameter specification for prompting and validation */
data class ParameterSpec(
    val name: String,
    val type: String = "String",
    val required: Boolean = true,
    val description: String = ""
) {
    init {
        require(name.isNotBlank()) { "Parameter name cannot be blank" }
        require(type.isNotBlank()) { "Parameter type cannot be blank" }
    }
}

