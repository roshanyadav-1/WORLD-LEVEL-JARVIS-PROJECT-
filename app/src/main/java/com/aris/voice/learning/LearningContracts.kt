package com.aris.voice.learning

import com.aris.voice.core.ArisResult
import com.aris.voice.domain.Decision

/**
 * Reflects on execution outcomes to calculate performance optimizations.
 */
interface IReflectionEngine {
    /**
     * Evaluates the accumulated ExperienceRecords and identifies improvements.
     */
    suspend fun reflectOnExperience(
        experiences: List<com.aris.voice.domain.ExperienceRecord>,
        summary: com.aris.voice.domain.ExperienceSummary
    ): ArisResult<com.aris.voice.domain.ReflectionReport>
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

/**
 * Interface to evaluate reflection reports and experiences to generate learning proposals.
 */
interface ILearningEngine {
    suspend fun evaluateLearningOpportunity(
        report: com.aris.voice.domain.ReflectionReport,
        summary: com.aris.voice.domain.ExperienceSummary
    ): ArisResult<com.aris.voice.domain.LearningDecision>
}
