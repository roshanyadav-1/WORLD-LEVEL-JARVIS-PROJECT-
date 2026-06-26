package com.aris.voice.utilities

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aris.voice.BuildConfig
import java.util.concurrent.CopyOnWriteArrayList

enum class KeyStatus {
    ACTIVE,
    EXHAUSTED,
    COOLDOWN
}

data class KeyHealthInfo(
    val index: Int,
    val maskedKey: String,
    val status: KeyStatus,
    val remainingSeconds: Int
)

interface KeyStatusListener {
    fun onKeyExhausted(keyIndex: Int)
    fun onAllKeysExhausted()
    fun onKeyRecovered(keyIndex: Int)
}

/**
 * A thread-safe, singleton object to manage and rotate a list of API keys.
 */
object ApiKeyManager {

    private const val PREFS_NAME = "ArisSettingsEncrypted"
    private const val KEY_GEMINI_API_KEYS = "gemini_api_keys_list"

    private var apiKeys: List<String> = emptyList()
    private val keyUsageCounts = java.util.Collections.synchronizedMap(mutableMapOf<Int, Int>())
    // Keep track of the last returned key index, so we can increment usage correctly.
    @Volatile
    private var lastReturnedIndex: Int = -1

    private var applicationContext: Context? = null
    private val exhaustedKeysWithTime = java.util.Collections.synchronizedMap(mutableMapOf<Int, Long>())
    private const val COOLDOWN_DURATION_MS = 60000L // 60s cooldown as per bug-7

    private val listeners = CopyOnWriteArrayList<KeyStatusListener>()
    
    fun addKeyStatusListener(listener: KeyStatusListener) {
        listeners.addIfAbsent(listener)
    }
    
    fun removeKeyStatusListener(listener: KeyStatusListener) {
        listeners.remove(listener)
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // If the encrypted preferences get corrupted or there is a keystore issue, clear them and try again
            Logger.e("ApiKeyManager", "Error initializing EncryptedSharedPreferences. Clearing corrupted preferences.", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
            
            // Re-attempt after clearing
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun init(context: Context) {
        applicationContext = context.applicationContext
        val prefs = getEncryptedPrefs(context)
        val savedKeys = prefs.getString(KEY_GEMINI_API_KEYS, null)
        apiKeys = if (!savedKeys.isNullOrBlank()) {
            savedKeys.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        
        if (apiKeys.isEmpty()) {
            Logger.e("ApiKeyManager", "No Gemini API keys found. Please add them in Settings.")
        } else {
            Logger.d("ApiKeyManager", "Initialized with ${apiKeys.size} API keys.")
        }
    }

    private fun notifyKeyExhausted(index: Int) {
        listeners.forEach { it.onKeyExhausted(index) }
    }

    private fun notifyAllKeysExhausted() {
        listeners.forEach { it.onAllKeysExhausted() }
        val ctx = applicationContext
        if (ctx != null) {
            Handler(Looper.getMainLooper()).post {
                android.widget.Toast.makeText(ctx, "All API keys exhausted!", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun notifyKeyRecovered(index: Int) {
        listeners.forEach { it.onKeyRecovered(index) }
    }

    fun saveKeys(context: Context, keysString: String) {
        val prefs = getEncryptedPrefs(context)
        val formattedKeys = keysString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        prefs.edit().putString(KEY_GEMINI_API_KEYS, formattedKeys).apply()
        init(context)
    }

    fun getSavedKeysString(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        val savedKeys = prefs.getString(KEY_GEMINI_API_KEYS, null)
        return if (!savedKeys.isNullOrBlank()) {
            savedKeys
        } else {
            ""
        }
    }

    fun markCurrentKeyExhausted() {
        val exhaustedIndex = lastReturnedIndex
        if (exhaustedIndex < 0 || exhaustedIndex >= apiKeys.size) return
        
        val exhaustedKey = apiKeys[exhaustedIndex]
        exhaustedKeysWithTime[exhaustedIndex] = System.currentTimeMillis()
        
        // Clear old usage
        keyUsageCounts[exhaustedIndex] = 0
        
        // Clear the exhausted key from GeminiApi's model cache synchronously inside exception handler
        try {
            com.aris.voice.v2.llm.GeminiApi.evictFromCache(exhaustedKey)
        } catch (e: Exception) {
            Logger.e("ApiKeyManager", "Failed to clear GeminiApi model cache for exhausted key: ${e.message}")
        }

        Logger.w("ApiKeyManager", "Key exhausted and model cache cleared for key ending in ...${exhaustedKey.takeLast(4)}")
        notifyKeyExhausted(exhaustedIndex)
    }

    /**
     * Gets the next API key optimally preferring healthy keys with lowest usage.
     */
    @Synchronized
    fun getNextKey(): String {
        if (apiKeys.isEmpty()) {
            throw IllegalStateException("API key list is empty. Please add keys to Settings > Gemini API Keys.")
        }
        val currentTime = System.currentTimeMillis()
        
        // Clean up expired exhausted keys
        val expiredIndices = mutableListOf<Int>()
        synchronized(exhaustedKeysWithTime) {
            for ((idx, exhaustTime) in exhaustedKeysWithTime) {
                if (currentTime - exhaustTime >= COOLDOWN_DURATION_MS) {
                    expiredIndices.add(idx)
                }
            }
            for (idx in expiredIndices) {
                exhaustedKeysWithTime.remove(idx)
            }
        }
        expiredIndices.forEach { notifyKeyRecovered(it) }

        val healthyKeys = apiKeys.indices.filter { !exhaustedKeysWithTime.containsKey(it) }
        
        if (healthyKeys.isEmpty()) {
            // All exhausted
            notifyAllKeysExhausted()
            // Try to find the one that will cool down soonest or just pick the first
            val bestFallbackIndex = exhaustedKeysWithTime.minByOrNull { it.value }?.key ?: 0
            lastReturnedIndex = bestFallbackIndex
            return apiKeys[bestFallbackIndex]
        }
        
        // Prefer keys with lower usage
        val chosenIndex = healthyKeys.minByOrNull { idx -> keyUsageCounts[idx] ?: 0 } ?: healthyKeys.first()
        keyUsageCounts[chosenIndex] = (keyUsageCounts[chosenIndex] ?: 0) + 1
        lastReturnedIndex = chosenIndex
        return apiKeys[chosenIndex]
    }

    fun getKeyHealthList(): List<KeyHealthInfo> {
        val size = apiKeys.size
        if (size == 0) return emptyList()
        val currentTime = System.currentTimeMillis()
        
        // Safely capture current times
        val snapshots = synchronized(exhaustedKeysWithTime) {
            exhaustedKeysWithTime.toMap()
        }

        return apiKeys.mapIndexed { index, apiKey ->
            val masked = if (apiKey.length > 12) {
                "${apiKey.take(4)}${"*".repeat(apiKey.length - 8)}${apiKey.takeLast(4)}"
            } else {
                "Key ${index + 1}"
            }
            
            val exhaustTime = snapshots[index]
            val status: KeyStatus
            val remainingSec: Int
            if (exhaustTime != null) {
                val elapsed = currentTime - exhaustTime
                if (elapsed >= COOLDOWN_DURATION_MS) {
                    status = KeyStatus.ACTIVE
                    remainingSec = 0
                } else {
                    status = KeyStatus.COOLDOWN
                    remainingSec = ((COOLDOWN_DURATION_MS - elapsed) / 1000).toInt()
                }
            } else {
                status = KeyStatus.ACTIVE
                remainingSec = 0
            }
            
            KeyHealthInfo(index, masked, status, remainingSec)
        }
    }
}