package com.aris.voice.domain

enum class StrategyType {
    OPEN_APPLICATION,
    SEND_MESSAGE,
    NAVIGATE_UI,
    CHANGE_SETTINGS,
    SEARCH_INFORMATION,
    MEDIA_PLAYBACK,
    MULTI_APP_WORKFLOW,
    PERMISSION_RECOVERY,
    ERROR_RECOVERY,
    MATH_CALCULATION,
    SET_ALARM,
    COMMUNICATION_CALL,
    FALLBACK
}

data class Strategy(
    val type: StrategyType,
    val description: String
)
