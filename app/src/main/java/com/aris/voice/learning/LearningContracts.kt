package com.aris.voice.learning

import com.aris.voice.core.ArisResult
import com.aris.voice.domain.Decision

/**
 * Reflects on execution outcomes to calculate performance optimizations.
 */
interface IReflectionEngine {
    /**
     * Evaluates the completed decision against the actual environmental result.
     */
    suspend fun reflect(decision: Decision, executionResult: String, success: Boolean): ArisResult<String>
}

/**
 * Learns repeatable user pattern workflows and structures them into reusable skills.
 */
interface IPatternLearner {
    /**
     * Analyzes execution logs over time to extract and learn highly repetitive task sequences.
     */
    suspend fun extractRecurringWorkflow(historyLogsJson: String): ArisResult<String?>
}
