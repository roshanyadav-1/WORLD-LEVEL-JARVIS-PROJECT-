package com.aris.voice.domain

data class ExecutionProgress(
    val state: ExecutionState,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val completedSteps: Int,
    val failedSteps: Int,
    val retryCount: Int,
    val recoveryCount: Int,
    val totalExecutionTimeMs: Long,
    val currentStepResult: StepExecutionResult? = null
)
