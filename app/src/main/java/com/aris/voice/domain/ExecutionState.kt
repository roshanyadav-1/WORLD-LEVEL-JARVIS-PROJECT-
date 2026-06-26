package com.aris.voice.domain

enum class ExecutionState {
    IDLE,
    STARTING,
    EXECUTING,
    VERIFYING,
    RETRYING,
    RECOVERING,
    PAUSED,
    CANCELLED,
    COMPLETED,
    FAILED
}
