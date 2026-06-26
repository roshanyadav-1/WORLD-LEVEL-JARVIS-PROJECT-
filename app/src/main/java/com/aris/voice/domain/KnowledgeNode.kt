package com.aris.voice.domain

data class KnowledgeNode(
    val knowledgeId: String,
    val knowledgeType: KnowledgeType,
    val name: String,
    val description: String,
    val confidence: Float,
    val source: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val relatedNodeIds: List<String> = emptyList(),
    val isArchived: Boolean = false
)
