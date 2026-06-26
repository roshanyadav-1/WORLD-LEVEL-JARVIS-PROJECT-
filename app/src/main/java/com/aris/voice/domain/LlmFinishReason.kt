package com.aris.voice.domain

enum class LlmFinishReason {
    STOP,
    MAX_TOKENS,
    TIMEOUT,
    CANCELLED,
    ERROR,
    UNKNOWN
}
