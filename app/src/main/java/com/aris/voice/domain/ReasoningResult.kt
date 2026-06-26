package com.aris.voice.domain

data class ReasoningResult(
    val reasoningId: String,
    val type: ReasoningType,
    val updatedStrategy: Strategy? = null,
    val updatedPlan: Plan? = null,
    val requiresReplanning: Boolean = false,
    val requiresLlm: Boolean = false,
    val preferredLlmType: LlmType = LlmType.NONE,
    val recoveryActions: List<String> = emptyList(),
    val confidence: Float = 1.0f,
    val explanation: String
)
