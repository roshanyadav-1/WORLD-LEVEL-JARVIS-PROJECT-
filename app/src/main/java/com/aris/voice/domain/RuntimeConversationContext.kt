package com.aris.voice.domain

data class RuntimeConversationContext(
    val conversationId: String,
    val pendingConfirmationRequest: String? = null,
    val previousBrainResponse: String? = null,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(timeoutMs: Long = 60000): Boolean {
        return System.currentTimeMillis() - lastUpdatedTimestamp > timeoutMs
    }
}
