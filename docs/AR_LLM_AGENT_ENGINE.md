# 🧠 SOFTWARE ARCHITECTURE DOCUMENT: ARIS CO-ORDINATED LLM AGENT ENGINE (C-LAE)

This document formalizes the software architecture, design patterns, and routing strategies for the **ARIS Coordinated LLM Agent Engine (C-LAE)**. 

C-LAE is an on-device, offline-first cognitive routing and multi-agent execution pipeline. It enables A.R.I.S. (Automated Robust Interaction System) to execute complex, multi-modal, and routine Android system automation tasks efficiently, securely, and with zero-latency overhead. It achieves this under a strict **6GB RAM budget** on consumer Android devices.

---

## 🗺️ 1. Architecture Philosophy (The Master-Sub-Model Paradigm)

Traditional mobile LLM execution relies on a single model. This approach represents a massive architectural mismatch:
* Large models (e.g., 3B–7B parameters) handle logical reasoning well but are slow, resource-heavy, and risk Out-Of-Memory (OOM) failures.
* Ultra-small models (e.g., 0.5B–1.5B parameters) are lightning-fast with minimal RAM usage but lack the logical capacity for complex, multi-step agent actions.

**C-LAE resolves this mismatch by introducing a Hierarchical Coordinated Multi-Agent Routing (CMAR) layout:**

```
                                [ Incoming Prompt / Task ]
                                            │
                                            ▼
                             ┌──────────────────────────────┐
                             │    Central Orchestrator      │
                             │ (LLaMA 3.2 3B or Gemini API) │
                             └──────────────┬───────────────┘
                                            │
               ┌────────────────────────────┼───────────────────────────┐
               │ [Complexity: Trivial]      │ [Complexity: Moderate]    │ [Complexity: Complex]
               ▼                            ▼                           ▼
      ┌─────────────────┐          ┌─────────────────┐         ┌─────────────────┐
      │   Task Tier 1   │          │   Task Tier 2   │         │   Task Tier 3   │
      │   (Ultra-Fast)  │          │  (Conversational)│         │ (Deep Reasoner)  │
      ├─────────────────┤          ├─────────────────┤         ├─────────────────┤
      │  Qwen 2.5 0.5B  │          │   LLaMA 3.2 1B  │         │   Gemma 2 2B /  │
      │  (Intent Extr.) │          │ (Dialogue Inter)│         │  LLaMA 3.2 3B   │
      └────────┬────────┘          └────────┬────────┘         └────────┬────────┘
               │                            │                           │
               └────────────────────────────┼───────────────────────────┘
                                            │
                                            ▼
                            ┌──────────────────────────────┐
                            │    Dynamic Tokenizer &       │
                            │   Native Intent Registry     │
                            └──────────────┬───────────────┘
                                            │
                                            ▼
                               [ Hybrid Automation Flow ]
```

---

## 📊 2. On-Device Offline LLM Model Matrix for Android (2024-2026)

To support real-time execution on physical devices containing as little as 6GB of total physical RAM, we select extremely specialized, highly-quantized (Int4 / AWQ) models. These models are fully compatible with **MediaPipe Tasks GenAI (LlmInference API)**:

| Model Tier | Model Name | Parameters | Format & Quantization | Memory Footprint (RAM) | Primary Cognitive Specialization |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Tier 1 (Trivial/Light)** | **Qwen 2.5 0.5B Instruct** | 500M | `.task` (Int4 quantized) | **~350 MB** | Ultra-fast single-sentence intent parsing, extraction of entities, simple parameter dictionary assembly. |
| **Tier 2 (Dialogue)** | **LLaMA 3.2 1B Instruct** | 1.2B | `.task` (Int4 quantized) | **~750 MB** | High-speed natural-language chatting, follow-on prompts, disambiguation, and user clarification logic. |
| **Tier 2+ (General)** | **Qwen 2.5 1.5B Instruct** | 1.5B | `.task` (Int4 quantized) | **~1.1 GB** | Direct API intent mapping, structured JSON response formatting, local file systems state parsing. |
| **Tier 3 (Reasoning)** | **Gemma 2 2B Instruct** | 2.6B | `.task` (Int4 quantized) | **~1.4 GB** | Deep logic steps, screen-sensing XML tree evaluation, hybrid automation planning. |
| **Tier 3+ (Expert)** | **LLaMA 3.2 3B Instruct** | 3.2B | `.task` (Int4 quantized) | **~2.2 GB** | Local fallback Orchestrator, complex multi-step error recoveries, code block/prompt restructuring. |
| **Cloud (Fail-Safe)** | **Gemini 2.5 Flash / Pro** | Large | Web API (JSON mode) | **0 MB (Static Client)** | Heavy multi-modal, deep visual screen coordinate calculations, and fallback when local RAM limits are exhausted. |

---

## 🧠 3. The Central "Orchestrator" Routing Engine

The Orchestrator defines the entry-point of cognitive routing. When a task or prompt is supplied, the Orchestrator evaluates the query's complexity, state requirements, and systemic bounds to determine the minimal, most efficient model capable of handling the work.

### A. Routing & Classification Rules
1. **Keyword-Syntactic Triviality Checklist**: If the request matches direct command templates (e.g., *"Open Youtube"*, *"Play artist X on Spotify"*, *"Toggle WiFi"*), it requires zero deep thinking. Map immediately to **Tier 1 (Qwen 0.5B)** for direct registry parameters extraction.
2. **Clarification/Dialogue State Check**: If the system is in a dialogue-stale state or waiting for user confirmation (e.g., *"Did you want to open the profile page?"*), route to **Tier 2 (LLaMA 1B)**.
3. **Structured Form Automation & XML Parse**: If the agent must inspect a complex XML/JSON hierarchical snapshot to decide click actions, route to **Tier 3 (Gemma 2B / LLaMA 3B)**.
4. **Cloud/API Fallback Boundary**:
   * If the local device detects it is connected to a power supply/WiFi AND Gemini API key quota limits are active, route highly complex tasks to **Gemini API** Web fallback for enhanced speed and execution accuracy.
   * If offline or API-key absent, route strictly to local models.

```kotlin
enum class TaskComplexityTier {
    TIER1_TRIVIAL,     // Qwen 2.5 0.5B (Speed-optimized)
    TIER2_DIALOGUE,    // LLaMA 3.2 1B (Clarification & conversation)
    TIER3_REASONING,   // Gemma 2B / LLaMA 3B (Heuristic task planning)
    CLOUD_FALLBACK     // Gemini 2.5 API (Deep multi-modal integration)
}
```

---

## 🗄️ 4. Dynamic Model Downloader & Registry System

To prevent bloated APK files, ARIS ships with a modular **Model Registry Dashboard** allowing users to download, verify, register, and assign models directly from HuggingFace, Google Drive, or any secure custom URL.

### A. Room Database Schema (`ModelRegistryDao`)
We model and record which local model files are stored on device, their verification status, and their current assigned tasks.

```kotlin
@Entity(tableName = "local_llm_models")
data class LocalLlmModel(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val identifier: String, // e.g. "qwen_0.5b_int4", "llama_3.2_1b"
    val localFilePath: String, // absolute path on internal/SD Card storage
    val modelUrl: String, // URL it was downloaded from
    val fileSizeMax: Long,
    val sha256Checksum: String,
    val isDownloaded: Boolean = false,
    val complexityTier: TaskComplexityTier,
    val isCurrentlyActive: Boolean = false
)
```

### B. Download Manager Architecture
Using Android's native `DownloadManager` ensures background downloading is resilient, respect network metered restrictions (e.g., download *only* on unmetered Wi-Fi), and displays progress bars inside the dashboard.

---

## ⚡ 5. Integration with MediaPipe Tasks GenAI API on Android

The core engine utilizes `com.google.mediapipe.tasks.genai.llminference.LlmInference`. Because loading multiple LLMs simultaneously into memory will trigger a fatal **Out-Of-Memory (OOM)** error, **the framework enforces a strict "One-Active-Model" Singleton swapping pattern**.

### A. Thread-Safe Swappable Inference Wrapper (`SpeechLlmEngine`)
```kotlin
package com.blurr.voice.v2.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Thread-safe engine that manages the active offline LLM instance.
 * Ensures that only ONE local LLM model is loaded in RAM at any given moment.
 */
class SpeechLlmEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CLLMEngine"
        @Volatile
        private var instance: SpeechLlmEngine? = null

        fun getInstance(context: Context): SpeechLlmEngine {
            return instance ?: synchronized(this) {
                instance ?: SpeechLlmEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val swapMutex = Mutex()
    private var currentModelId: String? = null
    private var activeInference: LlmInference? = null


    /**
     * Swaps the active model in memory with a fast clean tear-down of the previous model.
     */
    suspend fun loadAndSwapModel(modelFile: File, modelId: String): Boolean = swapMutex.withLock {
        if (currentModelId == modelId && activeInference != null) {
            return@withLock true // Model already loaded in memory
        }

        try {
            Log.i(TAG, "Releasing existing model from RAM: $currentModelId")
            activeInference?.close()
            activeInference = null
            System.gc() // Actively trigger GC to free up immediate RAM of 400MB-2GB prior to loading

            Log.i(TAG, "Loading path: ${modelFile.absolutePath} into MediaPipe LlmInference")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(512)
                .setTemperature(0.2f)
                .build()

            activeInference = LlmInference.createFromOptions(context, options)
            currentModelId = modelId
            Log.d(TAG, "Successfully swapped and loaded offline LLM model: $modelId")
            return@withLock true
        } catch (e: Exception) {
            Log.e(TAG, "OOM or initialization failed while loading LLM: $modelId", e)
            activeInference = null
            currentModelId = null
            return@withLock false
        }
    }

    /**
     * Generates a streaming or blocking completion from the loaded model.
     */
    suspend fun generateCompletion(prompt: String): String = swapMutex.withLock {
        val inference = activeInference ?: return@withLock "Error: No offline local LLM loaded."
        return@withLock try {
            inference.generateResponse(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Inference processing error on dynamic active model", e)
            "Error: Inference Failed: ${e.localizedMessage}"
        }
    }
}
```

---

## 🛠️ 6. Code Scaffolding: Coordinated Router Interface Contract

This specifies the core interfaces and implementation classes that will link our `AgentSettings`, existing `GeminiApi` client, and the new locally-loaded media-pipe sub-models:

```kotlin
package com.blurr.voice.v2.llm

import android.content.Context
import com.blurr.voice.v2.AgentOutput
import java.io.File

/**
 * Main coordinator that evaluates prompt semantics and dispatches to 
 * specialized local or online execution layers.
 */
class CoordinatedRouter(
    private val context: Context,
    private val cloudApi: GeminiApi,
    private val speechLlmEngine: SpeechLlmEngine
) {
    private val TAG = "CoordinatedRouter"

    /**
     * Evaluates task prompt and dynamically delegates to the matching local/cloud model.
     */
    suspend fun executeTaskThroughRoute(prompt: String, taskHistoryJson: String): String {
        val calculatedTier = evaluateTaskComplexity(prompt)
        Log.i(TAG, "Prompt: '$prompt' routed to complexity: $calculatedTier")

        return when (calculatedTier) {
            TaskComplexityTier.TIER1_TRIVIAL -> {
                executeTrivialLocalTask(prompt)
            }
            TaskComplexityTier.TIER2_DIALOGUE -> {
                executeDialogueLocalTask(prompt, taskHistoryJson)
            }
            TaskComplexityTier.TIER3_REASONING -> {
                executeReasoningLocalTask(prompt, taskHistoryJson)
            }
            TaskComplexityTier.CLOUD_FALLBACK -> {
                // If cloud connectivity is broken, fall back elegantly to best local reasoning LLM (Tier 3)
                if (isNetworkAvailable(context)) {
                    val messages = listOf(GeminiMessage(prompt))
                    val cloudOutput = cloudApi.generateAgentOutput(messages)
                    cloudOutput?.thought ?: "Failed to generate structured action plan through cloud."
                } else {
                    Log.w(TAG, "Network unavailable! Falling back to on-device reasoning Tier-3 model.")
                    executeReasoningLocalTask(prompt, taskHistoryJson)
                }
            }
        }
    }

    private fun evaluateTaskComplexity(prompt: String): TaskComplexityTier {
        val query = prompt.lowercase().trim()
        
        // Tier 1 Direct Match Rule (Intent routing)
        if (query.startsWith("open ") || query.startsWith("launch ") || query.startsWith("toggle ")) {
            return TaskComplexityTier.TIER1_TRIVIAL
        }
        
        // Tier 2 Conversational Dialogue Match Rule
        if (query.contains("hello") || query.contains("who are you") || query.contains("thank you")) {
            return TaskComplexityTier.TIER2_DIALOGUE
        }

        // TIER 3 / CLOUD Fallback Rule for heavy multi-step planning or system sensory
        if (query.contains("crawl") || query.contains("order") || query.contains("buy") || query.contains("scrape")) {
            return TaskComplexityTier.CLOUD_FALLBACK
        }

        return TaskComplexityTier.TIER3_REASONING
    }

    private suspend fun executeTrivialLocalTask(prompt: String): String {
        val modelFile = getLocalModelFile("qwen_0.5b_int4")
        if (modelFile != null && modelFile.exists()) {
            val loaded = speechLlmEngine.loadAndSwapModel(modelFile, "qwen_2.5_0.5b")
            if (loaded) {
                return speechLlmEngine.generateCompletion(wrapPromptWithTrivialSystemRules(prompt))
            }
        }
        return "Offline Trivial Match Fallback: Launching app command directly."
    }

    // Remaining methods mapped out conceptually in full scope of interface
    private suspend fun executeDialogueLocalTask(prompt: String, history: String): String { ... }
    private suspend fun executeReasoningLocalTask(prompt: String, history: String): String { ... }
}
```

---

## 🎨 7. User Interaction Design: Model Management Dashboard

To provide a visual control system, a dedicated and polished **Model Management Dashboard** is specified inside settings. It consists of:

1. **Current System Memory Meter**: A clean progress bar displaying live native RAM consumption, demonstrating which model sizes can run concurrently without breaching the OOM safety boundary.
2. **Offline LLM Cards**: Individual model cards (e.g., Qwen 0.5B, LLaMA 1B) displaying download progress trackers, sizes, checksum validation states, and a toggle to "Set as Default Orchestrator" or "Assign to Custom Voice Agent".
3. **URL Direct Import Bar**: A Material 3 filled Text Field with clean action buttons, letting power users link to custom converted HuggingFace MediaPipe task downloads directly.
4. **Agent Profile Configurator**: Allows users to configure a custom system prompt and select which offline model coordinates their execution, empowering user-created Agent workflows.

---

## 🔐 8. Privacy & Data Integrity

Our design establishes localized security containment:
* **Zero Cloud Bleed**: High-privacy tasks (such as inspecting system contacts or editing passwords) are restricted from using Cloud translation. They route strictly to on-device models. This is critical for medical, bank, or authentication compliance.
* **Encrypted Quantization**: Optional on-device model encryption models decrypted locally in temporary secure memory buffers on loading.
