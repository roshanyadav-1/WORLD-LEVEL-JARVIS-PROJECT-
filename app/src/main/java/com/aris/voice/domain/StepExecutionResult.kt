package com.aris.voice.domain

data class StepExecutionResult(
    val stepId: String,
    val status: StepStatus,
    val executionTimeMs: Long,
    val errorMessage: String? = null,
    val output: String? = null
)
