package com.aris.voice.domain

data class SpeechInputData(
    val rawText: String,
    val normalizedText: String,
    val confidence: Float,
    val isFinal: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
