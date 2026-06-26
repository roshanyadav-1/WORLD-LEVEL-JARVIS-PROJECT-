package com.aris.voice.domain

enum class RuntimeConversationState {
    IDLE,
    LISTENING,
    WAITING_FOR_BRAIN,
    SPEAKING,
    AWAITING_CONFIRMATION,
    AWAITING_FOLLOW_UP,
    INTERRUPTED
}
