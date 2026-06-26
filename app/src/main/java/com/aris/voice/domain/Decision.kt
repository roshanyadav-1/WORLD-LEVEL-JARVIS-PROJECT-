package com.aris.voice.domain

data class Decision(
    val decisionId: String,
    val type: DecisionType,
    val reason: String,
    val confidence: Float,
    val riskLevel: RiskLevel,
    val missingPermissions: List<String> = emptyList(),
    val missingCapabilities: List<String> = emptyList(),
    val clarificationQuestion: String? = null,
    val requiresUserConfirmation: Boolean = false,
    val canContinue: Boolean = true,
    val suggestedNextStep: String? = null,
    val plan: Plan? = null
)
