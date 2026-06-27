package com.aris.voice.utilities

data class CommandResult(
    val isHandled: Boolean,
    val feedbackSpeech: String? = null,
    val isGoalCompleted: Boolean = true
)
