package com.aris.voice.domain

enum class DecisionType {
    EXECUTE_PLAN,
    ASK_FOR_CLARIFICATION,
    REQUEST_PERMISSION,
    REQUIRE_USER_CONFIRMATION,
    WAIT,
    RETRY,
    ABORT,
    USE_LOCAL_LLM,
    USE_CLOUD_LLM,
    REPLAN,
    ERROR
}
