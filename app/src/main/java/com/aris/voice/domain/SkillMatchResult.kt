package com.aris.voice.domain

enum class SkillMatchType {
    EXACT_MATCH,
    PARTIAL_MATCH,
    NO_MATCH
}

data class SkillMatchResult(
    val matchType: SkillMatchType,
    val matchedSkill: Skill? = null,
    val confidence: Float = 0.0f
)
