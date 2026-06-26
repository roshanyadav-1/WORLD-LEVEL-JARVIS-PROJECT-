package com.aris.voice.domain

data class KnowledgeGraph(
    val nodes: List<KnowledgeNode>,
    val relations: List<KnowledgeRelation>
)
