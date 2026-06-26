package com.aris.voice.domain

/**
 * Status of an execution plan step.
 */
enum class StepStatus {
    PENDING,
    EXECUTING,
    SUCCESS,
    FAILED,
    SKIPPED
}
