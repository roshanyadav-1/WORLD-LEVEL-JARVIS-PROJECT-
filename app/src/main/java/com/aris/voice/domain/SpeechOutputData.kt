package com.aris.voice.domain

data class SpeechOutputData(
    val id: String,
    val text: String,
    val rawContent: String,
    val interruptible: Boolean = true,
    val priority: Int = 0
)
