package com.aris.voice.domain

data class ExecutionResult(
    val planId: String,
    val isSuccess: Boolean,
    val isCancelled: Boolean = false,
    val totalExecutionTimeMs: Long,
    val stepResults: List<StepExecutionResult>,
    val failureReason: String? = null
)
