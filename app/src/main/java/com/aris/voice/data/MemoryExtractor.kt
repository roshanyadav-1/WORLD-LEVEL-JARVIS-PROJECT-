package com.aris.voice.data

import android.util.Log
import com.aris.voice.api.GeminiApi
import com.google.ai.client.generativeai.type.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Extracts and stores typed, structured memories from conversations using LLM structured extraction.
 * Production ready with network checks and robust parsing.
 */
object MemoryExtractor {
    
    private const val TAG = "MemoryExtractor"
    
    private val memoryExtractionPrompt = """
        You are an advanced memory extraction agent. Analyze the following conversation and extract new, significant, long-term facts, preferences, or relationships about the user.
        
        You MUST categorize each memory into one of these specific types:
        - FACT: Key facts (e.g., job, home town, age).
        - PREFERENCE: User likes, dislikes, tea/coffee choice, theme choices.
        - RELATIONSHIP: Wife/husband, child, friends, family names.
        - HABIT: Daily routines, regular habits, recurring activities.
        - GOAL: Immediate projects, ambitions, learning goals.
        - SCHEDULE: Meetings, tasks, schedules or events.
        - GENERAL: Anything else worth remembering.
        
        IMPORTANT: Do NOT extract memories that are semantically identical to these known memories:
        {used_memories}
        
        Conversation:
        {conversation}
        
        You MUST respond ONLY with a raw valid JSON array of objects. Do not wrap in ```json block or any other markdown text.
        Each object in the array MUST have:
        1. "text": The extracted memory as a precise, self-contained, user-observational sentence. E.g. "User preferred light theme", not "I prefer light theme".
        2. "type": The exact String matches of the category enum above (e.g. "PREFERENCE").
        3. "importance": An integer from 0 to 100 capturing the long-term utility (e.g. relationship = 85, preference = 80).
        
        If no memories are found, respond with an empty JSON array: []
    """.trimIndent()
    
    /**
     * Extracts memories from a conversation and stores them structured into categories
     */
    suspend fun extractAndStoreMemories(
        conversationHistory: List<Pair<String, List<Any>>>,
        memoryManager: MemoryManager,
        usedMemories: Set<String> = emptySet()
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting structural memory extraction from conversation")
                if (conversationHistory.isEmpty()) return@withContext

                // Network check
                val isOnline = try {
                    com.aris.voice.utilities.NetworkConnectivityManager(com.aris.voice.MyApplication.appContext).isNetworkAvailable()
                } catch (e: Exception) {
                    false
                }
                
                if (!isOnline) {
                    Log.w(TAG, "Device is offline. Skipping memory extraction to conserve local resources or avoid errors.")
                    return@withContext
                }

                val conversationText = formatConversationForExtraction(conversationHistory)
                val usedMemoriesText = if (usedMemories.isNotEmpty()) {
                    usedMemories.joinToString("\n") { "- $it" }
                } else {
                    "None"
                }

                val extractionPrompt = memoryExtractionPrompt
                    .replace("{conversation}", conversationText)
                    .replace("{used_memories}", usedMemoriesText)

                val extractionChat = listOf(
                    "user" to listOf(TextPart(extractionPrompt))
                )

                val extractionResponse = try { 
                    GeminiApi.generateContent(extractionChat) 
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate content from Gemini API", e)
                    null 
                }

                if (!extractionResponse.isNullOrBlank()) {
                    // Extract JSON array using regex to be resilient to LLM formatting quirks
                    val jsonMatch = Regex("\\[.*\\]", RegexOption.DOT_MATCHES_ALL).find(extractionResponse)
                    val cleanedResponse = jsonMatch?.value ?: extractionResponse.trim()

                    Log.d(TAG, "Memory JSON Response: $cleanedResponse")

                    val jsonArray = try {
                        JSONArray(cleanedResponse)
                    } catch (e: Exception) {
                        Log.e(TAG, "Response is not valid JSON array, attempting backup parse", e)
                        null
                    }

                    if (jsonArray != null && jsonArray.length() > 0) {
                        for (i in 0 until jsonArray.length()) {
                            try {
                                val obj = jsonArray.getJSONObject(i)
                                val text = obj.optString("text").trim()
                                val categoryStr = obj.optString("type", "GENERAL")
                                val importance = obj.optInt("importance", 50)

                                if (text.isNotBlank() && text.length > 3) {
                                    val memoryType = MemoryType.fromString(categoryStr)

                                    val success = memoryManager.addMemory(
                                        originalText = text,
                                        type = memoryType,
                                        customImportance = importance.coerceIn(0, 100),
                                        source = MemorySource.CONVERSATION,
                                        checkDuplicates = true
                                    )
                                    if (success) {
                                        Log.d(TAG, "Successfully stored category memory: $text ($memoryType)")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing object in array at index $i", e)
                            }
                        }
                    } else {
                        Log.d(TAG, "Empty array parsed. No memories extracted.")
                    }
                } else {
                    Log.w(TAG, "Failed to retrieve content response from LLM or response was blank")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during memory extraction flow", e)
            }
        }
    }

    /**
     * Incremental parsing method to run every few conversation turns (e.g. 5-6)
     */
    suspend fun extractIncrementally(
        conversationHistory: List<Pair<String, List<Any>>>,
        memoryManager: MemoryManager,
        usedMemories: Set<String> = emptySet(),
        turnThreshold: Int = 6
    ) {
        if (conversationHistory.size < turnThreshold) return
        
        val recentHistory = conversationHistory.takeLast(turnThreshold)
        extractAndStoreMemories(recentHistory, memoryManager, usedMemories)
    }

    private fun formatConversationForExtraction(conversationHistory: List<Pair<String, List<Any>>>): String {
        return conversationHistory.joinToString("\n") { (role, parts) ->
            val textParts = parts.filterIsInstance<TextPart>()
            val text = textParts.joinToString(" ") { it.text }
            "$role: $text"
        }
    }
}

