package com.aris.voice.api

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.aris.voice.utilities.ApiKeyManager
import com.aris.voice.utilities.NetworkNotifier
import java.nio.ByteBuffer

/**
 * Service for generating embeddings using Gemini API
 */
object EmbeddingService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val VALID_TASK_TYPES = setOf("RETRIEVAL_QUERY", "RETRIEVAL_DOCUMENT", "SEMANTIC_SIMILARITY", "CLASSIFICATION", "CLUSTERING")
    
    fun floatListToBytes(floats: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun bytesToFloatList(bytes: ByteArray): List<Float> {
        val buffer = ByteBuffer.wrap(bytes)
        return (0 until bytes.size / 4).map { buffer.getFloat() }
    }

    /**
     * Generate embedding for a single text
     */
    suspend fun generateEmbedding(
        text: String,
        taskType: String = "RETRIEVAL_DOCUMENT",
        maxRetries: Int = 3,
        model: String = "models/text-embedding-004"
    ): List<Float>? {
        val validTaskType = if (VALID_TASK_TYPES.contains(taskType)) taskType else "RETRIEVAL_DOCUMENT"
        
        // Network check
        try {
            val isOnline = com.aris.voice.utilities.NetworkConnectivityManager(com.aris.voice.MyApplication.appContext).isNetworkAvailable()
            if (!isOnline) {
                Log.e("EmbeddingService", "No internet connection. Skipping embedding call.")
                try { NetworkNotifier.notifyOffline() } catch (e: Exception) {}
                return null
            }
        } catch (e: Exception) {
            Log.e("EmbeddingService", "Network check failed, assuming offline. ${e.message}")
            return null
        }
        var attempts = 0
        while (attempts < maxRetries) {
            val currentApiKey = ApiKeyManager.getNextKey()
            
            try {
                val payload = JSONObject().apply {
                    put("model", model)
                    put("content", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", text))
                        })
                    })
                    put("taskType", validTaskType)
                }
                
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/$model:embedContent?key=$currentApiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    
                    if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                        Log.e("EmbeddingService", "API call failed with HTTP ${response.code}. Response: $responseBody")
                        throw Exception("API Error ${response.code}: $responseBody")
                    }
                    
                    val embedding = parseEmbeddingResponse(responseBody)
                    return embedding
                }
                
            } catch (e: Exception) {
                Log.e("EmbeddingService", "=== EMBEDDING API ERROR (Attempt ${attempts + 1}) ===", e)
                attempts++
                if (attempts < maxRetries) {
                    val delayTime = 1000L * (1 shl attempts) // Exponential backoff
                    delay(delayTime)
                } else {
                    return null
                }
            }
        }
        return null
    }
    
    /**
     * Generate embeddings for multiple texts using the Batch API
     */
    suspend fun generateEmbeddings(
        texts: List<String>,
        taskType: String = "RETRIEVAL_DOCUMENT",
        maxRetries: Int = 3,
        model: String = "models/text-embedding-004"
    ): List<List<Float>>? {
        val validTaskType = if (VALID_TASK_TYPES.contains(taskType)) taskType else "RETRIEVAL_DOCUMENT"
        
        // Network check
        try {
            val isOnline = com.aris.voice.utilities.NetworkConnectivityManager(com.aris.voice.MyApplication.appContext).isNetworkAvailable()
            if (!isOnline) {
                Log.e("EmbeddingService", "No internet connection. Skipping batch embedding.")
                try { NetworkNotifier.notifyOffline() } catch (e: Exception) {}
                return null
            }
        } catch (e: Exception) {
            return null
        }
        
        var attempts = 0
        while (attempts < maxRetries) {
            val currentApiKey = ApiKeyManager.getNextKey()
            
            try {
                val payload = JSONObject().apply {
                    put("requests", JSONArray(texts.map { text ->
                        JSONObject().apply {
                            put("model", model)
                            put("content", JSONObject().apply {
                                put("parts", JSONArray().put(JSONObject().put("text", text)))
                            })
                            put("taskType", validTaskType)
                        }
                    }))
                }
                
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/$model:batchEmbedContents?key=$currentApiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    
                    if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                        throw Exception("Batch API Error ${response.code}: $responseBody")
                    }
                    
                    val json = JSONObject(responseBody)
                    val embeddingsArray = json.getJSONArray("embeddings")
                    
                    val result = mutableListOf<List<Float>>()
                    for (i in 0 until embeddingsArray.length()) {
                        val values = embeddingsArray.getJSONObject(i).getJSONArray("values")
                        val list = (0 until values.length()).map { values.getDouble(it).toFloat() }
                        result.add(list)
                    }
                    return result
                }
            } catch (e: Exception) {
                attempts++
                if (attempts < maxRetries) {
                    val delayTime = 1000L * (1 shl attempts)
                    delay(delayTime)
                } else {
                    return null
                }
            }
        }
        
        return null
    }
    
    private fun parseEmbeddingResponse(responseBody: String): List<Float> {
        val json = JSONObject(responseBody)
        val embedding = json.getJSONObject("embedding")
        val values = embedding.getJSONArray("values")
        
        return (0 until values.length()).map { i ->
            values.getDouble(i).toFloat()
        }
    }
} 