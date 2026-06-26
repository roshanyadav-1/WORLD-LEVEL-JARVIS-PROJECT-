package com.aris.voice.domain

data class MemoryQuery(
    val query: String? = null,
    val type: MemoryType? = null,
    val minImportance: Float? = null,
    val minConfidence: Float? = null,
    val tags: List<String> = emptyList(),
    val source: String? = null,
    val includeArchived: Boolean = false,
    val limit: Int = 100
)
