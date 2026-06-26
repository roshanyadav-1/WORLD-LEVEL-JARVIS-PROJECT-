package com.aris.voice.domain

data class ExperienceSummary(
    val intent: String,
    val totalExecutions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val averageExecutionDurationMs: Long,
    val averageSuccessScore: Float,
    val commonFailureReasons: List<String>,
    val overallConfidence: Float
)
