package com.aris.voice.domain

/**
 * A sequence of execution steps created by the Task Planner.
 */
data class Plan(
    val planId: String,
    val steps: List<PlanStep>,
    val currentStepIndex: Int = 0,
    val isReversible: Boolean = true,
    val goalId: String? = null,
    val estimatedComplexity: Int? = null
) {
    fun isCompleted() = currentStepIndex >= steps.size
    fun currentStep() = steps.getOrNull(currentStepIndex)
}
