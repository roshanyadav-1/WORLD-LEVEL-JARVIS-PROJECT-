package com.aris.voice.domain

data class ExperienceRecord(
    val experienceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val intent: UserIntent,
    val strategy: Strategy,
    val planId: String,
    val decisionId: String,
    val executionResult: ExecutionResult,
    val retryCount: Int,
    val recoveryCount: Int,
    val executionDurationMs: Long,
    val successScore: Float,
    val confidence: Float,
    val failureReason: String?,
    val environmentSnapshotReference: String,
    val memoryReferences: List<String> = emptyList(),
    val skillReference: String? = null
)
