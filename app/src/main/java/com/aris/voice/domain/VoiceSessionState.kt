package com.aris.voice.domain

enum class VoiceSessionState {
    IDLE,
    ACTIVATED,
    LISTENING,
    PROCESSING,
    WAITING_FOR_BRAIN,
    SPEAKING,
    COMPLETED,
    ERROR
}
