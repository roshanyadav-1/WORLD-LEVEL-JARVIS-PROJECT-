package com.aris.voice.learning

import com.aris.voice.core.ArisResult
import com.aris.voice.domain.Decision

class LearningImpl : IReflectionEngine, IPatternLearner {

    override suspend fun reflectOnExperience(
        experiences: List<com.aris.voice.domain.ExperienceRecord>,
        summary: com.aris.voice.domain.ExperienceSummary
    ): com.aris.voice.core.ArisResult<com.aris.voice.domain.ReflectionReport> {
        val findings = mutableListOf<com.aris.voice.domain.ReflectionFinding>()
        val recommendations = mutableListOf<String>()
        val skillUpdates = mutableListOf<String>()
        val learningTargets = mutableListOf<String>()
        
        var confidence = summary.overallConfidence
        var assessment = "Neutral execution pattern detected."
        var strategy = com.aris.voice.domain.Strategy(type = com.aris.voice.domain.StrategyType.FALLBACK, description = "Default Strategy")
        
        if (experiences.isNotEmpty()) {
            strategy = experiences.first().strategy
            
            val recentFailures = experiences.take(3).count { !it.executionResult.isSuccess }
            if (recentFailures >= 2) {
                findings.add(com.aris.voice.domain.ReflectionFinding(com.aris.voice.domain.ReflectionCategory.FAILURE_PATTERN, "High rate of recent failures detected."))
                recommendations.add("Review strategy for intent ${summary.intent}.")
                learningTargets.add(summary.intent)
                confidence *= 0.8f
                assessment = "Deteriorating reliability requires attention."
            }
            
            val retries = experiences.sumOf { it.retryCount }
            if (retries > experiences.size) {
                findings.add(com.aris.voice.domain.ReflectionFinding(com.aris.voice.domain.ReflectionCategory.RELIABILITY, "Frequent retries observed during execution."))
                recommendations.add("Optimize execution steps to reduce flakiness.")
                assessment = "Executions are succeeding but with high retry overhead."
            }
            
            if (summary.averageSuccessScore > 0.8f) {
                findings.add(com.aris.voice.domain.ReflectionFinding(com.aris.voice.domain.ReflectionCategory.SUCCESS_PATTERN, "Consistent success pattern established."))
                assessment = "Highly reliable execution pattern."
            }
            
            if (summary.commonFailureReasons.isNotEmpty()) {
                findings.add(com.aris.voice.domain.ReflectionFinding(com.aris.voice.domain.ReflectionCategory.KNOWLEDGE_GAP, "Common failure reason: ${summary.commonFailureReasons.first()}"))
                recommendations.add("Update skill to handle common failure: ${summary.commonFailureReasons.first()}")
            }
        } else {
            findings.add(com.aris.voice.domain.ReflectionFinding(com.aris.voice.domain.ReflectionCategory.KNOWLEDGE_GAP, "No experiences recorded for this intent."))
            recommendations.add("Gather more data before making concrete recommendations.")
        }
        
        val report = com.aris.voice.domain.ReflectionReport(
            reflectionId = "ref_" + java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            intent = summary.intent,
            strategy = strategy,
            confidence = confidence,
            overallAssessment = assessment,
            findings = findings,
            recommendations = recommendations,
            suggestedSkillUpdates = skillUpdates,
            suggestedMemoryUpdates = emptyList(),
            suggestedLearningTargets = learningTargets,
            relatedExperienceIds = experiences.map { it.experienceId }
        )
        
        return com.aris.voice.core.ArisResult.Success(report)
    }

    override suspend fun extractRecurringWorkflow(historyLogsJson: String): ArisResult<String?> {
        // Return null or simulated repeating pattern if history log triggers matching keys
        if (historyLogsJson.contains("click", ignoreCase = true) && historyLogsJson.contains("type", ignoreCase = true)) {
            return ArisResult.Success("Pattern detected: Click coordinates followed by type text input.")
        }
        return ArisResult.Success(null)
    }
}

class LearningEngineImpl : ILearningEngine {
    override suspend fun evaluateLearningOpportunity(
        report: com.aris.voice.domain.ReflectionReport,
        summary: com.aris.voice.domain.ExperienceSummary
    ): com.aris.voice.core.ArisResult<com.aris.voice.domain.LearningDecision> {
        val evidenceCount = summary.totalExecutions
        
        var confidence = com.aris.voice.domain.LearningConfidence.LOW
        var isApproved = false
        var reason = "Insufficient evidence."
        var proposalType = com.aris.voice.domain.LearningProposalType.NO_ACTION
        
        if (evidenceCount >= 5 && summary.averageSuccessScore > 0.8f) {
            confidence = com.aris.voice.domain.LearningConfidence.HIGH
            isApproved = true
            reason = "High success rate with sufficient evidence. Safe to learn."
            proposalType = com.aris.voice.domain.LearningProposalType.UPDATE_SKILL
        } else if (evidenceCount >= 3 && summary.averageSuccessScore > 0.6f) {
            confidence = com.aris.voice.domain.LearningConfidence.MEDIUM
            isApproved = false
            reason = "Moderate success rate. Waiting for more evidence."
            proposalType = com.aris.voice.domain.LearningProposalType.UPDATE_SKILL
        } else if (evidenceCount >= 3 && summary.averageSuccessScore < 0.3f) {
            confidence = com.aris.voice.domain.LearningConfidence.HIGH
            isApproved = true
            reason = "Consistent failure detected. Action required."
            proposalType = com.aris.voice.domain.LearningProposalType.KNOWLEDGE_GAP
        } else {
            confidence = com.aris.voice.domain.LearningConfidence.LOW
            isApproved = false
            reason = "Not enough data to draw conclusions."
            proposalType = com.aris.voice.domain.LearningProposalType.NO_ACTION
        }

        val proposal = com.aris.voice.domain.LearningProposal(
            proposalType = proposalType,
            confidence = confidence,
            evidenceCount = evidenceCount,
            relatedReflectionIds = listOf(report.reflectionId),
            relatedExperienceIds = report.relatedExperienceIds,
            suggestedMemoryChanges = report.suggestedMemoryUpdates,
            suggestedSkillChanges = report.suggestedSkillUpdates,
            suggestedKnowledgeChanges = report.suggestedLearningTargets,
            reason = reason,
            riskLevel = com.aris.voice.domain.RiskLevel.LOW
        )

        val decision = com.aris.voice.domain.LearningDecision(
            proposal = proposal,
            isApproved = isApproved,
            reason = reason
        )

        return com.aris.voice.core.ArisResult.Success(decision)
    }
}
