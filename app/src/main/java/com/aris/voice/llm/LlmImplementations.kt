package com.aris.voice.llm

import com.aris.voice.core.ArisError
import com.aris.voice.core.ArisResult
import com.aris.voice.domain.LlmBudgetLevel
import com.aris.voice.domain.LlmProvider
import com.aris.voice.domain.LlmRequest
import com.aris.voice.domain.LlmRequestClassification
import com.aris.voice.domain.LlmResponse

class LlmBridgeImpl : ILlmBridge {
    private val providers = mutableMapOf<LlmProvider, ILlmProvider>()
    private val cache = mutableMapOf<String, LlmResponse>()
    
    override fun classifyRequest(prompt: String): LlmRequestClassification {
        val lowerPrompt = prompt.lowercase()
        return when {
            lowerPrompt.contains("image") || lowerPrompt.contains("look at") || lowerPrompt.contains("vision") -> LlmRequestClassification.VISION
            lowerPrompt.contains("translate") -> LlmRequestClassification.TRANSLATION
            lowerPrompt.contains("summarize") || lowerPrompt.contains("tldr") -> LlmRequestClassification.SUMMARIZATION
            lowerPrompt.contains("code") || lowerPrompt.contains("fun ") || lowerPrompt.contains("def ") -> LlmRequestClassification.CODING
            lowerPrompt.contains("tool") || lowerPrompt.contains("execute") -> LlmRequestClassification.TOOL_SELECTION
            lowerPrompt.contains("plan") || lowerPrompt.contains("step by step") -> LlmRequestClassification.PLANNING
            lowerPrompt.contains("reason") || lowerPrompt.contains("why") || lowerPrompt.contains("how") -> LlmRequestClassification.REASONING
            lowerPrompt.contains("story") || lowerPrompt.contains("creative") || lowerPrompt.contains("poem") -> LlmRequestClassification.CREATIVE
            lowerPrompt.contains("what is") || lowerPrompt.contains("who is") -> LlmRequestClassification.KNOWLEDGE_LOOKUP
            else -> LlmRequestClassification.CHAT
        }
    }

    override suspend fun execute(request: LlmRequest): ArisResult<LlmResponse> {
        val cacheKey = buildCacheKey(request)
        if (cache.containsKey(cacheKey)) {
            val cachedResponse = cache[cacheKey]!!
            return ArisResult.Success(cachedResponse.copy(latencyMs = 0, estimatedCost = 0.0))
        }

        val classification = request.classification ?: classifyRequest(request.prompt)
        val selectedProvider = selectProvider(request, classification)
            ?: return ArisResult.Failure(ArisError.BrainError("NO_PROVIDER", "No suitable provider found for request"))

        val startTime = System.currentTimeMillis()
        val result = selectedProvider.generateResponse(request)
        
        if (result is ArisResult.Success) {
            val latency = System.currentTimeMillis() - startTime
            val finalResponse = result.value.copy(latencyMs = latency)
            cache[cacheKey] = finalResponse
            return ArisResult.Success(finalResponse)
        }
        
        return result
    }

    override fun registerProvider(provider: ILlmProvider) {
        providers[provider.providerType] = provider
    }

    override fun clearCache() {
        cache.clear()
    }
    
    private fun selectProvider(request: LlmRequest, classification: LlmRequestClassification): ILlmProvider? {
        if (request.preferredProvider != null && providers.containsKey(request.preferredProvider)) {
            return providers[request.preferredProvider]
        }
        
        val localProvider = providers[LlmProvider.LOCAL_LLM]
        val cloudProvider = providers[LlmProvider.GEMINI] ?: providers.values.firstOrNull { !it.isAvailableOffline }
        
        if (request.budgetLevel == LlmBudgetLevel.LOW) {
            return localProvider ?: cloudProvider
        }
        
        return when (classification) {
            LlmRequestClassification.CHAT, 
            LlmRequestClassification.TRANSLATION -> localProvider ?: cloudProvider
            LlmRequestClassification.VISION -> providers.values.firstOrNull { it.supportsVision } ?: cloudProvider
            LlmRequestClassification.REASONING,
            LlmRequestClassification.CODING,
            LlmRequestClassification.PLANNING -> cloudProvider ?: localProvider
            else -> localProvider ?: cloudProvider
        }
    }
    
    private fun buildCacheKey(request: LlmRequest): String {
        return "${request.systemPrompt}|${request.prompt}|${request.temperature}"
    }
}
