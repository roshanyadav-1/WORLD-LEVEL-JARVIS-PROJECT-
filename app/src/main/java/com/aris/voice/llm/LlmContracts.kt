package com.aris.voice.llm

import com.aris.voice.core.ArisResult
import com.aris.voice.domain.LlmRequest
import com.aris.voice.domain.LlmRequestClassification
import com.aris.voice.domain.LlmResponse

/**
 * Interface representing a specific LLM Provider (e.g., Local, Gemini, Claude).
 */
interface ILlmProvider {
    val providerType: com.aris.voice.domain.LlmProvider
    val isAvailableOffline: Boolean
    val supportsVision: Boolean
    
    suspend fun generateResponse(request: LlmRequest): ArisResult<LlmResponse>
}

/**
 * The single gateway between the ARIS Brain and every Large Language Model.
 */
interface ILlmBridge {
    /**
     * Classifies a request to determine the best provider.
     */
    fun classifyRequest(prompt: String): LlmRequestClassification
    
    /**
     * Executes the request through the best available provider.
     */
    suspend fun execute(request: LlmRequest): ArisResult<LlmResponse>
    
    /**
     * Registers a new provider to the bridge.
     */
    fun registerProvider(provider: ILlmProvider)
    
    /**
     * Clears the response cache.
     */
    fun clearCache()
}
