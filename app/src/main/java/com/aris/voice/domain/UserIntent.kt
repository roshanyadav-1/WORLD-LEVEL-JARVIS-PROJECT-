package com.aris.voice.domain

/**
 * Represents the structured intent extracted from user input.
 */
data class UserIntent(
    val rawInput: String,
    val primaryIntent: String,
    val secondaryIntent: String? = null,
    val targetApplication: String? = null,
    val targetEntity: String? = null,
    val action: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val constraints: List<String> = emptyList(),
    val requiredCapabilities: List<String> = emptyList(),
    val confidenceScore: Float = 1.0f,
    val isClarificationRequired: Boolean = false,
    val clarificationReason: String? = null
)
