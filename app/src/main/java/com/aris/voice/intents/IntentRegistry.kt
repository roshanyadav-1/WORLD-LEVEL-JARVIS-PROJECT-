package com.aris.voice.intents

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import com.aris.voice.intents.impl.DialIntent
import com.aris.voice.intents.impl.EmailComposeIntent
import com.aris.voice.intents.impl.ShareTextIntent
import com.aris.voice.intents.impl.ViewUrlIntent
import com.aris.voice.intents.impl.SendSMSIntent
import com.aris.voice.intents.impl.SetAlarmIntent
import com.aris.voice.intents.impl.OpenMapsIntent
import com.aris.voice.intents.impl.WebSearchIntent
import com.aris.voice.intents.impl.OpenAppSettingsIntent
import com.aris.voice.intents.impl.YouTubeSearchIntent
import com.aris.voice.intents.impl.SpotifySearchIntent
import com.aris.voice.intents.impl.SystemSettingsIntent
import com.aris.voice.intents.impl.PlayStoreIntent
import com.aris.voice.intents.impl.WhatsAppIntent
import com.aris.voice.intents.impl.AddCalendarEventIntent
import com.aris.voice.intents.impl.CameraIntent

/**
 * Discovers and manages AppIntent implementations.
 * Convention: Put intent implementations under package com.aris.voice.intents.impl
 */
object IntentRegistry {
    private const val TAG = "IntentRegistry"

    private val discovered: ConcurrentHashMap<String, AppIntent> = ConcurrentHashMap()
    @Volatile private var initialized = false

    @Synchronized
    @Suppress("UNUSED_PARAMETER")
    fun init(context: Context) {
        if (initialized) return

        val intentsToRegister = listOf(
            DialIntent(),
            ViewUrlIntent(),
            ShareTextIntent(),
            EmailComposeIntent(),
            SendSMSIntent(),
            SetAlarmIntent(),
            OpenMapsIntent(),
            WebSearchIntent(),
            OpenAppSettingsIntent(),
            YouTubeSearchIntent(),
            SpotifySearchIntent(),
            SystemSettingsIntent(),
            PlayStoreIntent(),
            WhatsAppIntent(),
            AddCalendarEventIntent(),
            CameraIntent()
        )

        for (intent in intentsToRegister) {
            try {
                register(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register intent: ${intent.name}", e)
            }
        }
        
        initialized = true
        Log.i(TAG, "Initialized IntentRegistry with ${discovered.size} intents")
    }

    fun register(intent: AppIntent) {
        val key = intent.name.trim()
        if (key.isBlank()) {
            Log.e(TAG, "Attempted to register an intent with a blank name")
            return
        }
        
        if (discovered.containsKey(key)) {
            Log.w(TAG, "Duplicate intent registration for name: $key; overriding")
        }
        discovered[key] = intent
    }

    fun listIntents(context: Context): List<AppIntent> {
        if (!initialized) init(context)
        return discovered.values.toList()
    }
    
    fun getPromptString(context: Context): String {
        return listIntents(context).joinToString("\n") { it.toPromptFormat() }
    }

    fun findByName(context: Context, name: String): AppIntent? {
        if (!initialized) init(context)
        
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return null
        
        // Exact match first
        discovered[trimmedName]?.let { return it }
        
        // Case-insensitive fallback
        return discovered.entries.firstOrNull { it.key.equals(trimmedName, ignoreCase = true) }?.value
    }
    
    /**
     * Clears all registered intents. Useful for testing or hot-reloading.
     */
    @Synchronized
    fun clear() {
        discovered.clear()
        initialized = false
        Log.i(TAG, "IntentRegistry cleared")
    }
}

