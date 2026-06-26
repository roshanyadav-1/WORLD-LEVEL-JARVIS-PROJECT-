package com.aris.voice.domain

/**
 * A single atomic logical action within an execution plan.
 */
data class PlanStep(
    val stepId: String,
    val description: String,
    val requiredCapability: String,
    val arguments: Map<String, String> = emptyMap(),
    val expectedResult: String? = null,
    val retryPolicy: RetryPolicy = RetryPolicy.NONE,
    val failurePolicy: FailurePolicy = FailurePolicy.ABORT,
    val dependencies: List<String> = emptyList(),
    val status: StepStatus = StepStatus.PENDING,
    val errorMessage: String? = null
)
