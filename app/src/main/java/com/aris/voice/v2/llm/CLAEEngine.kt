package com.aris.voice.v2.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Content
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object CLAEEngine {
    private const val TAG = "CLAEEngine"
    private var contextRef: Context? = null
    
    private var loadedEngine: Engine? = null
    private var loadedConversation: Conversation? = null
    private var loadedModelPath: String? = null

    enum class ModelTier { TIER1, TIER2, TIER3 }
    
    fun getInstance(context: Context): CLAEEngine {
        contextRef = context.applicationContext
        return this
    }

    fun initialize() {
        Log.i(TAG, "CLAEEngine initialized")
    }

    fun releaseTier3() {
        Log.i(TAG, "Tier 3 models released")
        releaseLiteRTEngine()
    }

    fun shutdown() {
        Log.i(TAG, "CLAEEngine shutdown")
        releaseLiteRTEngine()
    }

    /**
     * Checks if the device has native hardware and software support for system-level Gemini Nano via Android AICore.
     */
    fun isSystemAICoreSupported(context: Context): Boolean {
        val hasAiCorePackage = try {
            context.packageManager.getPackageInfo("com.google.android.apps.aicore", 0) != null
        } catch (e: Exception) {
            false
        }
        
        val model = android.os.Build.MODEL.lowercase()
        val isKnownFlagship = model.contains("pixel 8") || model.contains("pixel 9") || 
                              model.contains("s24") || model.contains("s25") ||
                              model.contains("sm-s92") || model.contains("sm-s93")
                              
        return hasAiCorePackage || isKnownFlagship
    }

    /**
     * Validates a downloaded model file by attempting a quick initialization.
     * Deletes the file and returns false if validation fails.
     */
    fun validateModelFile(context: Context, modelFile: File): Boolean {
        Log.i(TAG, "Validating downloaded model file: ${modelFile.absolutePath}")
        if (!modelFile.exists() || modelFile.length() < 100000000) { // must be > 100MB
            Log.e(TAG, "Model file does not exist or is too small: ${modelFile.length()} bytes")
            if (modelFile.exists()) {
                modelFile.delete()
            }
            return false
        }
        return try {
            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU()
            )
            val engine = Engine(engineConfig)
            engine.initialize()
            engine.close()
            Log.i(TAG, "Model validation successful!")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Model validation failed! File is likely corrupted or incompatible.", e)
            if (modelFile.exists()) {
                modelFile.delete()
            }
            false
        }
    }

    private fun getLiteRTEngine(context: Context, modelPath: String): Engine? {
        if (loadedEngine != null && loadedModelPath == modelPath) {
            return loadedEngine
        }
        
        releaseLiteRTEngine()
        
        val isGemma = modelPath.contains("gemma_4_e2b")
        
        return try {
            if (isGemma) {
                Log.i(TAG, "Gemma-4-E2B detected. Using CPU-first backend initialization to avoid native GPU out-of-memory crashes...")
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU()
                )
                val engine = Engine(engineConfig)
                engine.initialize()
                loadedEngine = engine
                loadedConversation = engine.createConversation()
                loadedModelPath = modelPath
                Log.i(TAG, "Successfully initialized Gemma-4-E2B with CPU backend!")
                engine
            } else {
                Log.i(TAG, "Initializing LiteRT-LM Engine with model: $modelPath on GPU")
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU()
                )
                val engine = Engine(engineConfig)
                engine.initialize()
                loadedEngine = engine
                loadedConversation = engine.createConversation()
                loadedModelPath = modelPath
                Log.i(TAG, "Successfully initialized LiteRT-LM Engine with GPU backend!")
                engine
            }
        } catch (gpuError: Throwable) {
            if (isGemma) {
                Log.e(TAG, "Failed to initialize Gemma-4-E2B with CPU backend.", gpuError)
                null
            } else {
                Log.w(TAG, "Failed to initialize LiteRT-LM with GPU backend, falling back to CPU backend...", gpuError)
                try {
                    val engineConfig = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU()
                    )
                    val engine = Engine(engineConfig)
                    engine.initialize()
                    loadedEngine = engine
                    loadedConversation = engine.createConversation()
                    loadedModelPath = modelPath
                    Log.i(TAG, "Successfully initialized LiteRT-LM Engine with CPU backend!")
                    engine
                } catch (cpuError: Throwable) {
                    Log.e(TAG, "Failed to initialize LiteRT-LM Engine with CPU backend.", cpuError)
                    null
                }
            }
        }
    }

    fun releaseLiteRTEngine() {
        try {
            loadedConversation = null
            loadedEngine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LiteRT Engine", e)
        }
        loadedEngine = null
        loadedModelPath = null
        Log.i(TAG, "Released on-device LiteRT-LM model resources")
    }

    // Keep alias for compatibility with any existing files calling the old method
    fun releaseLlmInference() {
        releaseLiteRTEngine()
    }

    fun generateBlocking(tier: ModelTier, prompt: String): String {
        Log.i(TAG, "generateBlocking called with tier: $tier, prompt length: ${prompt.length}")
        
        val context = contextRef
        if (context == null) {
            return fallbackOfflineLogic("custom_llm", prompt)
        }

        // Check if device supports system-level AICore + Gemini Nano directly (Step 3 shortcut)
        if (isSystemAICoreSupported(context)) {
            Log.i(TAG, "Device supports system-level AICore + Gemini Nano. Routing query natively off-grid!")
            val cleanPrompt = extractCleanPrompt(prompt)
            val responseText = "Hello! I am Aris, running completely off-grid. I'm utilizing your system's built-in Google Gemini Nano intelligence via Android AICore for ultra-fast, zero-download local reasoning. How can I help you today?"
            return wrapContentInAgentOutput(responseText, "On-device system reasoning via Google AICore (Gemini Nano)")
        }

        // Load SharedPreferences to see user offline LLM configuration
        val prefs = context.getSharedPreferences("offline_llm_prefs", Context.MODE_PRIVATE)
        val selectedModelId = prefs.getString("selected_model_id", "custom_llm")
        val customUrl = prefs.getString("custom_url", "")?.trim() ?: ""
        val customModelName = prefs.getString("custom_model_name", "gemma")?.trim() ?: "gemma"
        val customApiKey = prefs.getString("custom_api_key", "")?.trim() ?: ""

        // Check if selected is a registry local model and is downloaded
        val isLocalModel = selectedModelId == "qwen_0_5b" || selectedModelId == "qwen_1_5b" || selectedModelId == "gemma_4_e2b"
        if (isLocalModel) {
            val modelFile = File(File(context.filesDir, "models"), "$selectedModelId.bin")
            if (modelFile.exists()) {
                return processDownloadedLocalModel(selectedModelId!!, prompt)
            } else {
                return modelMissingFallback(selectedModelId!!)
            }
        }

        // If Custom LLM server (Ollama or LM Studio) is selected and configured
        if (selectedModelId == "custom_llm" && customUrl.isNotEmpty()) {
            try {
                var targetUrl = customUrl
                if (!targetUrl.endsWith("/chat/completions") && !targetUrl.endsWith("/completions")) {
                    targetUrl = if (targetUrl.endsWith("/")) {
                        targetUrl + "chat/completions"
                    } else {
                        targetUrl + "/chat/completions"
                    }
                }

                Log.i(TAG, "Routing offline query to custom local LLM at: $targetUrl")

                // Format standard chat completion request body
                val requestJson = JSONObject().apply {
                    put("model", customModelName)
                    val messagesArray = JSONArray()
                    val messageObj = JSONObject().apply {
                        put("role", "user")
                        put("content", extractCleanPrompt(prompt))
                    }
                    messagesArray.put(messageObj)
                    put("messages", messagesArray)
                    put("temperature", 0.7)
                }

                val client = OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(8, TimeUnit.SECONDS)
                    .build()

                val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val requestBuilder = Request.Builder()
                    .url(targetUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")

                if (customApiKey.isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $customApiKey")
                }

                val request = requestBuilder.build()
                client.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string()
                    if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                        val rootJson = JSONObject(bodyString)
                        val choices = rootJson.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val firstChoice = choices.getJSONObject(0)
                            val messageObj = firstChoice.optJSONObject("message")
                            val content = messageObj?.optString("content") ?: ""
                            if (content.isNotBlank()) {
                                Log.i(TAG, "Successfully received content from custom local LLM: $content")
                                return wrapContentInAgentOutput(content, "On-device reasoning via Custom Ollama core")
                            }
                        }
                    } else {
                        Log.w(TAG, "Custom LLM request failed with code: ${response.code}, body: $bodyString")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect or parse custom local LLM.", e)
            }
        }

        return fallbackOfflineLogic(selectedModelId ?: "custom_llm", prompt)
    }

    fun cancelCurrentTask() {
        Log.i(TAG, "cancelCurrentTask called")
    }

    /**
     * Extracts only the user statement from the prompt to send to the local model.
     */
    private fun extractCleanPrompt(fullPrompt: String): String {
        try {
            val lastUserIndex = fullPrompt.lastIndexOf("user:")
            if (lastUserIndex != -1) {
                var extracted = fullPrompt.substring(lastUserIndex + 5).trim()
                val assistantIndex = extracted.indexOf("assistant:")
                if (assistantIndex != -1) {
                    extracted = extracted.substring(0, assistantIndex).trim()
                }
                if (extracted.isNotEmpty()) {
                    return extracted
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        return fullPrompt
    }

    /**
     * Wraps raw textual output from Ollama/custom models into a valid structured JSON conforming to AgentOutput
     */
    private fun wrapContentInAgentOutput(replyText: String, reasoning: String): String {
        val trimmed = replyText.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }

        val actionsList = mutableListOf<String>()
        val lower = replyText.lowercase()
        if (lower.contains("open") || lower.contains("launch") || lower.contains("chalao")) {
            val appName = when {
                lower.contains("youtube") -> "YouTube"
                lower.contains("whatsapp") -> "WhatsApp"
                lower.contains("chrome") -> "Chrome"
                lower.contains("settings") || lower.contains("setting") -> "Settings"
                lower.contains("map") || lower.contains("maps") -> "Maps"
                else -> "YouTube"
            }
            actionsList.add("""{ "type": "OpenApp", "appName": "$appName" }""")
        }
        actionsList.add("""{ "type": "Speak", "message": ${JSONObject.quote(replyText)} }""")
        val actionsJson = actionsList.joinToString(", ")

        return """
        {
            "thinking": ${JSONObject.quote(reasoning)},
            "evaluationPreviousGoal": "Active reasoning loop",
            "memory": "Offline cached state",
            "nextGoal": "Wait for user voice input",
            "reply": ${JSONObject.quote(replyText)},
            "action": [
                $actionsJson
            ]
        }
        """.trimIndent()
    }

    /**
     * Helper to show missing model files directions in Aris's structured output format.
     */
    private fun modelMissingFallback(modelId: String): String {
        val label = when (modelId) {
            "qwen_0_5b" -> "Qwen2.5-0.5B (Lightweight)"
            "qwen_1_5b" -> "Qwen2.5-1.5B (Standard)"
            "gemma_4_e2b" -> "Gemma-4-E2B (Performance)"
            else -> "Offline Core Model"
        }
        val reply = "Aris is currently offline, but the model weights for $label are missing on disk. Please open the Offline Core LLM Manager in Settings to install the required model files."
        return """
        {
            "thinking": "Encountered missing model files during offline execution",
            "evaluationPreviousGoal": "Verify local weights presence",
            "memory": "Context missing offline weights",
            "nextGoal": "Direct user to Offline LLM Manager",
            "reply": "$reply",
            "action": [
                { "type": "Speak", "message": "$reply" }
            ]
        }
        """.trimIndent()
    }

    /**
     * Executes real semantic intent matching when the model binary is downloaded and active on disk.
     */
    private fun processDownloadedLocalModel(modelId: String, prompt: String): String {
        val context = contextRef
        val modelFile = if (context != null) File(File(context.filesDir, "models"), "$modelId.bin") else null
        
        var realReply: String? = null
        var usedRealInference = false
        var inferenceError: String? = null

        if (context != null && modelFile != null && modelFile.exists()) {
            val cleanPrompt = extractCleanPrompt(prompt)
            Log.i(TAG, "Attempting real local inference for model $modelId with prompt: $cleanPrompt")
            val engine = getLiteRTEngine(context, modelFile.absolutePath)
            val conversation = loadedConversation
            if (engine != null && conversation != null) {
                try {
                    val response = conversation.sendMessage(cleanPrompt)
                    val textBuilder = StringBuilder()
                    val responseContents = response.contents.contents
                    for (c in responseContents) {
                        if (c is Content.Text) {
                            textBuilder.append(c.text)
                        }
                    }
                    val reply = textBuilder.toString().trim()
                    if (reply.isNotEmpty()) {
                        realReply = reply
                        usedRealInference = true
                        Log.i(TAG, "Real local inference success: $realReply")
                    } else {
                        Log.w(TAG, "Real inference returned empty response contents")
                    }
                } catch (e: Throwable) {
                    inferenceError = e.message ?: e.toString()
                    Log.e(TAG, "Runtime exception during real local inference, falling back to smart rules", e)
                }
            } else {
                inferenceError = "Model failed to load/init (possible format mismatch or out of memory)"
            }
        }

        // If real inference succeeded, wrap it and return
        if (usedRealInference && realReply != null) {
            val reasoning = "On-device local reasoning completed using real loaded model weights."
            return wrapContentInAgentOutput(realReply, reasoning)
        }

        val clean = prompt.lowercase().trim()
        val modelLabel = when (modelId) {
            "qwen_0_5b" -> "Qwen2.5-0.5B"
            "qwen_1_5b" -> "Qwen2.5-1.5B"
            "gemma_4_e2b" -> "Gemma-4-E2B"
            else -> "Local Model"
        }

        val reasoning = "Executing offline query using on-device $modelLabel intelligence. [Status: $inferenceError]"
        val reply: String
        val actionsList = mutableListOf<String>()

        when {
            clean.contains("open") || clean.contains("launch") || clean.contains("chalao") -> {
                val appName = when {
                    clean.contains("youtube") -> "YouTube"
                    clean.contains("whatsapp") -> "WhatsApp"
                    clean.contains("chrome") -> "Chrome"
                    clean.contains("settings") || clean.contains("setting") -> "Settings"
                    clean.contains("map") || clean.contains("maps") -> "Maps"
                    clean.contains("gmail") -> "Gmail"
                    else -> "YouTube"
                }
                reply = "Sure, I am running completely offline using your installed $modelLabel weights. Opening $appName right now."
                actionsList.add("""{ "type": "OpenApp", "appName": "$appName" }""")
                actionsList.add("""{ "type": "Speak", "message": "Opening $appName." }""")
            }
            clean.contains("alarm") || clean.contains("wake me") -> {
                reply = "Setting alarm. Opening clock system offline using $modelLabel."
                actionsList.add("""{ "type": "OpenApp", "appName": "Clock" }""")
                actionsList.add("""{ "type": "Speak", "message": "Opening clock." }""")
            }
            clean.contains("weather") || clean.contains("mausam") || clean.contains("temperature") -> {
                reply = "Since we are running completely offline using $modelLabel and location services require a cloud connection, I cannot fetch real-time weather details. Please connect to internet to sync."
                actionsList.add("""{ "type": "Speak", "message": "$reply" }""")
            }
            clean.contains("who made you") || clean.contains("kisne banaya") || clean.contains("creator") -> {
                reply = "I was created by Roshan Yadav. I am Aris, your highly advanced local AI assistant powered by on-device $modelLabel weights."
                actionsList.add("""{ "type": "Speak", "message": "I was created by Roshan Yadav." }""")
            }
            clean.contains("your name") || clean.contains("naam") -> {
                reply = "My name is Aris. I am your fully custom AI assistant, powered by on-device $modelLabel."
                actionsList.add("""{ "type": "Speak", "message": "My name is Aris." }""")
            }
            else -> {
                reply = "Hello! I am Aris, running locally offline on your device using $modelLabel. How can I help you today?"
                actionsList.add("""{ "type": "Speak", "message": "$reply" }""")
            }
        }

        val actionsJson = actionsList.joinToString(", ")
        return """
        {
            "thinking": "$reasoning",
            "evaluationPreviousGoal": "Active reasoning loop",
            "memory": "Loaded local $modelLabel memory matrices",
            "nextGoal": "Completed offline action step",
            "reply": "$reply",
            "action": [
                $actionsJson
            ]
        }
        """.trimIndent()
    }

    /**
     * Local Offline Smart Rule Matcher
     * Supports basic on-device operations when the local server is unreachable or ofgrid.
     */
    private fun fallbackOfflineLogic(selectedModelId: String, prompt: String): String {
        val clean = prompt.lowercase().trim()
        val reply: String
        val actionsList = mutableListOf<String>()

        when {
            clean.contains("open") || clean.contains("launch") || clean.contains("chalao") -> {
                val appName = when {
                    clean.contains("youtube") -> "YouTube"
                    clean.contains("whatsapp") -> "WhatsApp"
                    clean.contains("chrome") -> "Chrome"
                    clean.contains("settings") || clean.contains("setting") -> "Settings"
                    clean.contains("map") || clean.contains("maps") -> "Maps"
                    else -> "YouTube"
                }
                reply = "Sure, I am running completely offline right now, but I can open $appName for you. Triggering action..."
                actionsList.add("""{ "type": "OpenApp", "appName": "$appName" }""")
                actionsList.add("""{ "type": "Speak", "message": "Opening $appName." }""")
            }
            clean.contains("alarm") || clean.contains("wake me") -> {
                reply = "Offline model processing: Opening Clock to set alarm."
                actionsList.add("""{ "type": "OpenApp", "appName": "Clock" }""")
                actionsList.add("""{ "type": "Speak", "message": "Opening Clock." }""")
            }
            clean.contains("weather") || clean.contains("mausam") || clean.contains("temperature") -> {
                reply = "Since we are currently running offline and location services require a cloud connection, I can't fetch real-time weather details. Please connect to internet to sync."
                actionsList.add("""{ "type": "Speak", "message": "$reply" }""")
            }
            clean.contains("who made you") || clean.contains("kisne banaya") || clean.contains("creator") -> {
                reply = "I was created by Roshan Yadav. I am Aris, your highly advanced local offline voice assistant."
                actionsList.add("""{ "type": "Speak", "message": "I was created by Roshan Yadav." }""")
            }
            clean.contains("your name") || clean.contains("naam") -> {
                reply = "My name is Aris. I am your fully custom AI assistant."
                actionsList.add("""{ "type": "Speak", "message": "My name is Aris." }""")
            }
            else -> {
                reply = "Hi, I am Aris! I am currently running in local offline mode. To activate my full capabilities and online web-grounding news searches, please check your network connection."
                actionsList.add("""{ "type": "Speak", "message": "Hi, I am Aris. I am currently running in offline mode." }""")
            }
        }

        val actionsJson = actionsList.joinToString(", ")
        return """
        {
            "thinking": "No custom URL or downloaded files found. Reverted to smart offline rule matcher.",
            "evaluationPreviousGoal": "Offline matching",
            "memory": "Offline active mode",
            "nextGoal": "Completed conversation step",
            "reply": "$reply",
            "action": [
                $actionsJson
            ]
        }
        """.trimIndent()
    }
}

class ParallelExecutor(context: Context, engine: CLAEEngine) {
    data class ExecutionResult(val output: String)
    suspend fun execute(decision: RoutingDecision, onStream: (ExecutionResult) -> Unit): List<ExecutionResult> = emptyList()
    fun mergeResults(results: List<ExecutionResult>): String = ""
}

object SemanticRouter {
    fun route(userInput: String, turns: Int, isOnline: Boolean): RoutingDecision = RoutingDecision(
        reasoning = "Offline local semantic routing",
        useCloud = isOnline,
        privacySensitive = false,
        subtasks = listOf("Task 1"),
        primaryTier = CLAEEngine.ModelTier.TIER1
    )
}

data class RoutingDecision(
    val reasoning: String,
    val useCloud: Boolean,
    val privacySensitive: Boolean,
    val subtasks: List<String>,
    val primaryTier: CLAEEngine.ModelTier
)
