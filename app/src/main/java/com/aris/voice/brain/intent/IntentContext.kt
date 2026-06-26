package com.aris.voice.brain.intent

import com.aris.voice.domain.UserIntent

data class IntentContext(
    val rawInput: String,
    var normalizedInput: String = "",
    val tokens: MutableList<String> = mutableListOf(),
    var primaryIntent: String = "UNKNOWN",
    var secondaryIntent: String? = null,
    var targetApplication: String? = null,
    var targetEntity: String? = null,
    var action: String? = null,
    val parameters: MutableMap<String, String> = mutableMapOf(),
    val constraints: MutableList<String> = mutableListOf(),
    val requiredCapabilities: MutableList<String> = mutableListOf(),
    var confidenceScore: Float = 1.0f,
    var isClarificationRequired: Boolean = false,
    var clarificationReason: String? = null
) {
    fun toUserIntent(): UserIntent {
        return UserIntent(
            rawInput = rawInput,
            primaryIntent = primaryIntent,
            secondaryIntent = secondaryIntent,
            targetApplication = targetApplication,
            targetEntity = targetEntity,
            action = action,
            parameters = parameters.toMap(),
            constraints = constraints.toList(),
            requiredCapabilities = requiredCapabilities.toList(),
            confidenceScore = confidenceScore,
            isClarificationRequired = isClarificationRequired,
            clarificationReason = clarificationReason
        )
    }
}
