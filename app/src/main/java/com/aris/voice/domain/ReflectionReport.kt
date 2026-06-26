package com.aris.voice.domain

data class ReflectionReport(
    val reflectionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val intent: String,
    val strategy: Strategy,
    val confidence: Float,
    val overallAssessment: String,
    val findings: List<ReflectionFinding>,
    val recommendations: List<String>,
    val suggestedSkillUpdates: List<String> = emptyList(),
    val suggestedMemoryUpdates: List<String> = emptyList(),
    val suggestedLearningTargets: List<String> = emptyList(),
    val relatedExperienceIds: List<String>
)
