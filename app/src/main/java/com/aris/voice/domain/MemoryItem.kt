package com.aris.voice.domain

data class MemoryItem(
    val memoryId: String,
    val memoryType: MemoryType,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val importanceScore: Float,
    val confidence: Float,
    val source: String,
    val tags: List<String> = emptyList(),
    val relatedMemoryIds: List<String> = emptyList(),
    val expiresAt: Long? = null,
    val isArchived: Boolean = false
)
