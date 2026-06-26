package com.aris.voice.domain

data class KnowledgeQueryResult(
    val query: String,
    val nodes: List<KnowledgeNode>,
    val graph: KnowledgeGraph
)
