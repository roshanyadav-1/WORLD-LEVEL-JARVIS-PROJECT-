package com.aris.voice.domain

import java.util.UUID

data class LlmResponse(
    val responseId: String = "llmres_${UUID.randomUUID()}",
    val provider: LlmProvider,
    val model: String,
    val content: String,
    val finishReason: LlmFinishReason,
    val latencyMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val estimatedCost: Double,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)
