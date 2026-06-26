package com.aris.voice.domain

enum class SkillState {
    ACTIVE,
    DEGRADED,
    DEPRECATED,
    ARCHIVED
}

data class SkillMetadata(
    val createdTime: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val compatibleAppVersions: List<String> = emptyList(),
    val compatibleAndroidVersions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val estimatedComplexity: Int = 1
)

data class Skill(
    val skillId: String,
    val name: String,
    val version: Int = 1,
    val description: String,
    val category: String,
    val triggerIntent: String,
    val strategyType: StrategyType? = null,
    val targetApplication: String? = null,
    val requiredCapabilities: List<String> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
    val workflow: Plan,
    val alternateWorkflow: Plan? = null,
    val successRate: Float = 0.0f,
    val failureRate: Float = 0.0f,
    val averageExecutionTimeMs: Long = 0L,
    val confidence: Float = 1.0f,
    val usageCount: Int = 0,
    val lastSuccessfulExecution: Long? = null,
    val lastFailedExecution: Long? = null,
    val state: SkillState = SkillState.ACTIVE,
    val compositeSkillIds: List<String> = emptyList(),
    val metadata: SkillMetadata = SkillMetadata(),
    val skillScore: Float = 0.0f
)
