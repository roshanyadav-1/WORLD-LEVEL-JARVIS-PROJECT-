package com.aris.voice.domain

enum class SpeechOutputState {
    IDLE,
    STARTED,
    SPEAKING,
    COMPLETED,
    CANCELLED,
    FAILED
}
