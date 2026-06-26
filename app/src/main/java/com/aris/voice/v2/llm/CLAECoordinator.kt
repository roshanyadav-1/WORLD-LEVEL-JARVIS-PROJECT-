package com.aris.voice.v2.llm

import android.content.Context
import android.util.Log
import com.aris.voice.utilities.ApiKeyManager
import kotlinx.coroutines.*

/**
 * C-LAE v2 Master Coordinator
 * Entry point for all AI queries in ARIS.
 *
 * Usage:
 *   val coordinator = CLAECoordinator.getInstance(this)
 *   coordinator.initialize()
 *
 *   val response = coordinator.processQuery(
 *       userInput = "Set alarm for 7am and play lofi music"
 *   )
 */
class CLAECoordinator private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CLAECoordinator"
        @Volatile
        private var INSTANCE: CLAECoordinator? = null

        fun getInstance(ctx: Context): CLAECoordinator =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CLAECoordinator(ctx.applicationContext).also { INSTANCE = it }
            }
    }

    private val engine = CLAEEngine.getInstance(context)
    private val executor = ParallelExecutor(context, engine)
    private var conversationTurns = 0
    private var isInitialized = false

    private val cloudApi = GeminiApi(
        modelName = "gemini-2.5-flash",
        apiKeyManager = ApiKeyManager,
        context = context,
        maxRetry = 10
    )

    suspend fun initialize() {
        if (isInitialized) return
        engine.initialize()
        isInitialized = true
        Log.i(TAG, "CLAECoordinator ready")
    }

    suspend fun processQuery(
        userInput: String,
        isOnline: Boolean = true,
        onStreamToken: ((String) -> Unit)? = null
    ): String {
        if (!isInitialized) initialize()

        val start = System.currentTimeMillis()
        conversationTurns++

        // If strictly offline, run local core intelligence
        if (!isOnline) {
            Log.i(TAG, "→ Offline mode: Running direct local blocking inference")
            val offlineResponse = engine.generateBlocking(CLAEEngine.ModelTier.TIER1, userInput)
            onStreamToken?.invoke(offlineResponse)
            return offlineResponse
        }

        // ── 1. Route ──────────────────────────────────────────────────
        val decision = SemanticRouter.route(userInput, conversationTurns, isOnline)
        Log.i(TAG, "Route Decision: ${decision.reasoning}")

        // ── 2. Cloud path (if applicable) ─────────────────────────────
        if (decision.useCloud && !decision.privacySensitive) {
            Log.i(TAG, "→ Routing to Cloud (Gemini)")
            val cloudResponse = callGeminiCloud(userInput)
            val elapsed = System.currentTimeMillis() - start
            Log.i(TAG, "✅ Cloud query completed in ${elapsed}ms")
            onStreamToken?.invoke(cloudResponse)
            return cloudResponse
        }

        // ── 3. Local parallel execution ────────────────────────────────
        Log.i(TAG, "→ Cloud routing bypassed, running direct local blocking inference")
        val localResponse = engine.generateBlocking(decision.primaryTier, userInput)
        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "✅ Completed locally in ${elapsed}ms | Primary Tier=${decision.primaryTier}")
        onStreamToken?.invoke(localResponse)

        // ── 4. Release Tier3 after use (save RAM) ──────────────────────
        if (decision.primaryTier == CLAEEngine.ModelTier.TIER3) {
            engine.releaseTier3()
        }

        return localResponse
    }

    private suspend fun callGeminiCloud(prompt: String): String {
        return try {
            cloudApi.generateRawText(prompt) ?: "Cloud generated empty response."
        } catch (e: Exception) {
            Log.w(TAG, "Cloud failed, falling back to Tier3 local reasoning.", e)
            engine.generateBlocking(CLAEEngine.ModelTier.TIER3, prompt)
        }
    }

    fun resetConversation() {
        conversationTurns = 0
    }

    fun shutdown() {
        engine.shutdown()
    }
}
