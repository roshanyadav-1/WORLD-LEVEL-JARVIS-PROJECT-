package com.aris.voice.domain

import java.util.UUID

data class LlmRequest(
    val requestId: String = "llmreq_${UUID.randomUUID()}",
    val prompt: String,
    val systemPrompt: String = "",
    val conversationContext: List<String> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1000,
    val preferredProvider: LlmProvider? = null,
    val preferredModel: String? = null,
    val timeoutMs: Long = 30000L,
    val budgetLevel: LlmBudgetLevel = LlmBudgetLevel.MEDIUM,
    val priority: Int = 1,
    val metadata: Map<String, String> = emptyMap(),
    val classification: LlmRequestClassification? = null
)
