package com.aris.voice.domain

import java.util.UUID

data class LearningProposal(
    val proposalId: String = "prop_${UUID.randomUUID()}",
    val timestamp: Long = System.currentTimeMillis(),
    val proposalType: LearningProposalType,
    val confidence: LearningConfidence,
    val evidenceCount: Int,
    val relatedReflectionIds: List<String>,
    val relatedExperienceIds: List<String>,
    val suggestedMemoryChanges: List<String> = emptyList(),
    val suggestedSkillChanges: List<String> = emptyList(),
    val suggestedKnowledgeChanges: List<String> = emptyList(),
    val reason: String,
    val riskLevel: RiskLevel
)
