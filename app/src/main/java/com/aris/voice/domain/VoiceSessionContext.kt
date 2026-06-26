package com.aris.voice.domain

data class VoiceSessionContext(
    val sessionId: String,
    val startTimestamp: Long = System.currentTimeMillis(),
    var lastActivityTime: Long = System.currentTimeMillis(),
    var isCancelled: Boolean = false,
    var state: VoiceSessionState = VoiceSessionState.IDLE
) {
    fun isInactive(timeoutMs: Long): Boolean {
        return System.currentTimeMillis() - lastActivityTime > timeoutMs
    }
}
