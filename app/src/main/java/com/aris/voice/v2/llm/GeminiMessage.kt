package com.aris.voice.v2.llm

import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole {
    USER, MODEL, TOOL
}

@Serializable
sealed class MessagePart

@Serializable
data class TextPart(val text: String) : MessagePart()

@Serializable
data class GeminiMessage(
    val role: MessageRole = MessageRole.USER,
    val parts: List<MessagePart> = emptyList()
) {
    constructor(role: MessageRole = MessageRole.USER, text: String) : this(role, listOf(TextPart(text)))
}
