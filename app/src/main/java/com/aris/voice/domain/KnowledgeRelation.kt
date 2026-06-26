package com.aris.voice.domain

data class KnowledgeRelation(
    val relationId: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val relationType: RelationType,
    val confidence: Float
)
