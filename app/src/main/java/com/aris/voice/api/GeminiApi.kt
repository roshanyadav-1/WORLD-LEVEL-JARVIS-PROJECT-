package com.aris.voice.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.aris.voice.BuildConfig
import com.aris.voice.MyApplication
import com.aris.voice.utilities.ApiKeyManager
import com.google.ai.client.generativeai.type.ImagePart
import com.google.ai.client.generativeai.type.TextPart
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.aris.voice.utilities.NetworkConnectivityManager
import com.aris.voice.utilities.NetworkNotifier
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Refactored GeminiApi as a singleton object.
 * It now gets a rotated API key from ApiKeyManager for every request
 * and logs all requests and responses to a persistent file.
 */
object GeminiApi {
    private val proxyUrl: String = BuildConfig.GCLOUD_PROXY_URL
    private val proxyKey: String = BuildConfig.GCLOUD_PROXY_URL_KEY

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    val db = Firebase.firestore


    suspend fun generateContent(
        chat: List<Pair<String, List<Any>>>,
        images: List<Bitmap> = emptyList(),
        modelName: String = "gemini-2.5-flash", // Updated to a more standard model name
        maxRetry: Int = 4,
        context: Context? = null,
        enableSearch: Boolean = false
    ): String? {
        // Network check before making any calls
        try {
            val appCtx = context ?: MyApplication.appContext
            val isOnline = NetworkConnectivityManager(appCtx).isNetworkAvailable()
            if (!isOnline) {
                Log.e("GeminiApi", "No internet connection. Skipping generateContent call.")
                NetworkNotifier.notifyOffline()
                return null
            }
        } catch (e: Exception) {
            Log.e("GeminiApi", "Network check failed, assuming offline. ${e.message}")
            return null
        }
        // Extract the last user prompt text for logging purposes.
        val lastUserPrompt = chat.lastOrNull { it.first == "user" }
            ?.second
            ?.filterIsInstance<TextPart>()
            ?.joinToString(separator = "\n") { it.text } ?: "No text prompt found"

        val isProxyAvailable = !proxyUrl.isNullOrBlank() && !proxyKey.isNullOrBlank() && !enableSearch
        var attempts = 0
        while (attempts < maxRetry) {
            val currentApiKey = if (isProxyAvailable) "" else ApiKeyManager.getNextKey()
            Log.d("GeminiApi", "=== GEMINI API REQUEST (Attempt ${attempts + 1}) ===")
            if (!isProxyAvailable) {
                Log.d("GeminiApi", "Using API key ending in: ...${currentApiKey.takeLast(4)}")
            }
            Log.d("GeminiApi", "Model: $modelName")

            val attemptStartTime = System.currentTimeMillis()
            val requestUrl = if (isProxyAvailable) {
                proxyUrl
            } else {
                "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$currentApiKey"
            }

            val requestPayload = if (isProxyAvailable) {
                buildPayload(chat, modelName)
            } else {
                buildDirectPayload(chat, enableSearch)
            }
            val payload = requestPayload

            Log.d("GeminiApi", "=== GEMINI API REQUEST (Attempt ${attempts + 1}) ===")
            Log.d("GeminiApi", "Model: $modelName")
            Log.d("GeminiApi", "Url: $requestUrl")
            Log.d("GeminiApi", "Payload: ${payload.toString().take(500)}...")

            try {
                val requestBuilder = Request.Builder()
                    .url(requestUrl)
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")

                if (isProxyAvailable) {
                    requestBuilder.addHeader("X-API-Key", proxyKey)
                }

                val request = requestBuilder.build()

                val requestStartTime = System.currentTimeMillis()
                client.newCall(request).execute().use { response ->
                    val responseEndTime = System.currentTimeMillis()
                    val requestTime = responseEndTime - requestStartTime
                    val totalAttemptTime = responseEndTime - attemptStartTime
                    val responseBody = response.body?.string()

                    Log.d("GeminiApi", "=== GEMINI API RESPONSE (Attempt ${attempts + 1}) ===")
                    Log.d("GeminiApi", "HTTP Status: ${response.code}")
                    Log.d("GeminiApi", "Request time: ${requestTime}ms")

                    if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                        Log.e("GeminiApi", "API call failed with HTTP ${response.code}. Response: $responseBody")
                        throw Exception("API Error ${response.code}: $responseBody")
                    }

                    // Assuming the response returns the standard Gemini API response format
                    val parsedResponse = parseSuccessResponse(responseBody) ?: responseBody

                    val logEntry = createLogEntry(
                        attempt = attempts + 1,
                        modelName = modelName,
                        prompt = lastUserPrompt,
                        imagesCount = images.size,
                        payload = payload.toString(),
                        responseCode = response.code,
                        responseBody = responseBody,
                        responseTime = requestTime,
                        totalTime = totalAttemptTime
                    )
                    saveLogToFile(MyApplication.appContext, logEntry)
                    val logData = createLogDataMap(
                        attempt = attempts + 1,
                        modelName = modelName,
                        prompt = lastUserPrompt,
                        imagesCount = images.size,
                        responseCode = null, // Note: This was null, kept as is
                        responseTime = requestTime,
                        totalTime = totalAttemptTime,
                        responseBody = responseBody,
                        status = "pass",
                    )
                    logToFirestore(logData)


                    return parsedResponse
                }
            } catch (e: Exception) {
                val attemptEndTime = System.currentTimeMillis()
                val totalAttemptTime = attemptEndTime - attemptStartTime

                Log.e("GeminiApi", "=== GEMINI API ERROR (Attempt ${attempts + 1}) ===", e)
                
                if (e.message?.contains("429") == true || e.message?.contains("rate limit") == true) {
                    ApiKeyManager.markCurrentKeyExhausted()
                    val appCtx = context ?: MyApplication.appContext
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(appCtx, "API Rate limit hit. Key rotated, retrying...", Toast.LENGTH_SHORT).show()
                    }
                    attempts++
                    if (attempts < maxRetry) {
                        val delayTime = 1000L * attempts
                        delay(delayTime)
                        continue
                    } else {
                        throw Exception("ERROR_429")
                    }
                }

                // Save the error log entry to a file.
                val logEntry = createLogEntry(
                    attempt = attempts + 1,
                    modelName = modelName,
                    prompt = lastUserPrompt,
                    imagesCount = images.size,
                    payload = payload.toString(), // Log the payload that caused the error
                    responseCode = null,
                    responseBody = null,
                    responseTime = 0,
                    totalTime = totalAttemptTime,
                    error = e.message
                )
                saveLogToFile(MyApplication.appContext, logEntry)
                val logData = createLogDataMap(
                    attempt = attempts + 1,
                    modelName = modelName,
                    prompt = lastUserPrompt,
                    imagesCount = images.size,
                    responseCode = null,
                    responseTime = 0,
                    totalTime = totalAttemptTime,
                    status = "error",
                    responseBody = null,
                    error = e.message
                )
                logToFirestore(logData)

                attempts++
                if (attempts < maxRetry) {
                    val delayTime = 1000L * attempts
                    Log.d("GeminiApi", "Retrying in ${delayTime}ms...")
                    delay(delayTime)
                } else {
                    Log.e("GeminiApi", "Request failed after all ${maxRetry} retries.")
                    throw Exception("ERROR_API")
                }
            }
        }
        throw Exception("ERROR_API")
    }

    /**
     * MODIFIED: This function now builds the payload to match the structure required by the proxy in code-2.
     * The new structure is: { "modelName": "...", "messages": [ { "role": "...", "parts": [ { "text": "..." } ] } ] }
     * NOTE: This proxy structure does not support images. ImageParts will be ignored.
     */
    private fun buildPayload(chat: List<Pair<String, List<Any>>>, modelName: String): JSONObject {
        val rootObject = JSONObject()
        rootObject.put("modelName", modelName)

        val messagesArray = JSONArray()
        chat.forEach { (role, parts) ->
            val messageObject = JSONObject()
            val apiRole = if (role.equals("model", ignoreCase = true)) "model" else "user"
            messageObject.put("role", apiRole)

            val jsonParts = JSONArray()
            parts.forEach { part ->
                when (part) {
                    is TextPart -> {
                        // The structure for a part is {"text": "..."}
                        val partObject = JSONObject().put("text", part.text)
                        jsonParts.put(partObject)
                    }
                    is ImagePart -> {
                        // Log a warning that images are being skipped for the proxy call
                        Log.w("GeminiApi", "ImagePart found but skipped. The proxy payload format does not support images.")
                    }
                }
            }

            // Only add the message to the array if it contains text parts
            if (jsonParts.length() > 0) {
                messageObject.put("parts", jsonParts)
                messagesArray.put(messageObject)
            }
        }

        rootObject.put("messages", messagesArray)
        return rootObject
    }
    
    /**
     * Builds the standard payload for a direct call to the Gemini API endpoint.
     * Format: { "contents": [ { "role": "...", "parts": [ { "text": "..." } ] } ], "systemInstruction": { "parts": [ { "text": "..." } ] } }
     */
    private fun buildDirectPayload(chat: List<Pair<String, List<Any>>>, enableSearch: Boolean = false): JSONObject {
        val rootObject = JSONObject()
        val contentsArray = JSONArray()
        var systemInstructionObject: JSONObject? = null

        chat.forEach { (role, parts) ->
            if (role.equals("system", ignoreCase = true)) {
                val jsonParts = JSONArray()
                parts.forEach { part ->
                    if (part is TextPart) {
                        jsonParts.put(JSONObject().put("text", part.text))
                    }
                }
                if (jsonParts.length() > 0) {
                    systemInstructionObject = JSONObject().apply {
                        put("parts", jsonParts)
                    }
                }
            } else {
                val messageObject = JSONObject()
                // Convert roles appropriately
                val apiRole = if (role.equals("model", ignoreCase = true)) "model" else "user"
                messageObject.put("role", apiRole)

                val jsonParts = JSONArray()
                parts.forEach { part ->
                    if (part is TextPart) {
                        val partObject = JSONObject().put("text", part.text)
                        jsonParts.put(partObject)
                    } else if (part is ImagePart) {
                        val partObject = JSONObject().apply {
                            val inlineData = JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                // Compress and encode bitmap to base64
                                val outputStream = java.io.ByteArrayOutputStream()
                                part.image.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                                val base64Data = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
                                put("data", base64Data)
                            }
                            put("inlineData", inlineData)
                        }
                        jsonParts.put(partObject)
                    }
                }

                if (jsonParts.length() > 0) {
                    messageObject.put("parts", jsonParts)
                    contentsArray.put(messageObject)
                }
            }
        }
        rootObject.put("contents", contentsArray)
        if (systemInstructionObject != null) {
            rootObject.put("systemInstruction", systemInstructionObject)
        }
        if (enableSearch) {
            val toolsArray = JSONArray()
            val googleSearchTool = JSONObject().put("googleSearchRetrieval", JSONObject())
            toolsArray.put(googleSearchTool)
            rootObject.put("tools", toolsArray)
        }
        return rootObject
    }

    /**
     * This function parses the standard response from the Gemini API.
     * It is assumed the proxy forwards this response structure without modification.
     */
    private fun parseSuccessResponse(responseBody: String): String? {
        return try {
            val json = JSONObject(responseBody)
            // Handle cases where the proxy might return a simplified text response directly
            if (json.has("text")) {
                return json.getString("text")
            }
            // Standard Gemini API response parsing
            if (!json.has("candidates")) {
                Log.w("GeminiApi", "API response has no 'candidates'. It was likely blocked. Full response: $responseBody")
                // Check for proxy-specific error format
                if (json.has("error")) {
                    Log.e("GeminiApi", "Proxy returned an error: ${json.getString("error")}")
                }
                return null
            }
            val candidates = json.getJSONArray("candidates")
            if (candidates.length() == 0) {
                Log.w("GeminiApi", "API response has an empty 'candidates' array. Full response: $responseBody")
                return null
            }
            val firstCandidate = candidates.getJSONObject(0)
            if (!firstCandidate.has("content")) {
                Log.w("GeminiApi", "First candidate has no 'content' object. Full response: $responseBody")
                return null
            }
            val content = firstCandidate.getJSONObject("content")
            if (!content.has("parts")) {
                Log.w("GeminiApi", "Content object has no 'parts' array. Full response: $responseBody")
                return null
            }
            val parts = content.getJSONArray("parts")
            if (parts.length() == 0) {
                Log.w("GeminiApi", "Parts array is empty. Full response: $responseBody")
                return null
            }
            parts.getJSONObject(0).getString("text")
        } catch (e: Exception) {
            Log.e("GeminiApi", "Failed to parse successful response: $responseBody", e)
            // As a fallback, if parsing fails but there was a response, return the raw string.
            // The proxy might be configured to return plain text on success.
            responseBody
        }
    }


    private fun saveLogToFile(context: Context, logEntry: String) {
        // Logging to file disabled for privacy/storage reasons.
    }
    private fun logToFirestore(logData: Map<String, Any?>) {
        // Logging to Firestore disabled for GDPR privacy reasons.
    }
    private fun createLogEntry(
        attempt: Int,
        modelName: String,
        prompt: String,
        imagesCount: Int,
        payload: String,
        responseCode: Int?,
        responseBody: String?,
        responseTime: Long,
        totalTime: Long,
        error: String? = null
    ): String {
        return buildString {
            appendLine("=== GEMINI API DEBUG LOG ===")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())}")
            appendLine("Attempt: $attempt")
            appendLine("Model: $modelName")
            appendLine("Images count: $imagesCount")
            appendLine("Prompt length: ${prompt.length}")
            appendLine("Prompt: $prompt")
            appendLine("Payload: $payload")
            appendLine("Response code: $responseCode")
            appendLine("Response time: ${responseTime}ms")
            appendLine("Total time: ${totalTime}ms")
            if (error != null) {
                appendLine("Error: $error")
            } else {
                appendLine("Response body: $responseBody")
            }
            appendLine("=== END LOG ===")
        }
    }
    private fun createLogDataMap(
        attempt: Int,
        modelName: String,
        prompt: String,
        imagesCount: Int,
        responseCode: Int?,
        responseTime: Long,
        totalTime: Long,
        status: String,
        responseBody: String?,
        error: String? = null
    ): Map<String, Any?> {
        return mapOf(
            "timestamp" to FieldValue.serverTimestamp(), // Use server time
            "status" to status,
            "attempt" to attempt,
            "model" to modelName,
            "prompt" to prompt,
            "imagesCount" to imagesCount,
            "responseCode" to responseCode,
            "responseTimeMs" to responseTime,
            "totalTimeMs" to totalTime,
            "llmReply" to responseBody,
            "error" to error
        )
    }
}