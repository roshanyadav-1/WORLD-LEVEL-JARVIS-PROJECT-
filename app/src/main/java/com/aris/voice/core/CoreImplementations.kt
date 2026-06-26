package com.aris.voice.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import java.util.concurrent.ConcurrentHashMap

class AndroidArisLogger : ArisLogger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val priority = when (level) {
            LogLevel.VERBOSE -> Log.VERBOSE
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
            LogLevel.ASSERT -> Log.ASSERT
        }
        if (throwable != null) {
            Log.println(priority, tag, "$message\n${Log.getStackTraceString(throwable)}")
        } else {
            Log.println(priority, tag, message)
        }
    }
}

class AndroidArisConfig(context: Context) : ArisConfig {
    private val prefs = context.getSharedPreferences("ArisCoreSettings", Context.MODE_PRIVATE)
    private val inMemoryCache = ConcurrentHashMap<String, Any>()

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val cached = inMemoryCache[key] as? Boolean
        if (cached != null) return cached
        return prefs.getBoolean(key, defaultValue).also { inMemoryCache[key] = it }
    }

    override fun getString(key: String, defaultValue: String): String {
        val cached = inMemoryCache[key] as? String
        if (cached != null) return cached
        return (prefs.getString(key, null) ?: defaultValue).also { inMemoryCache[key] = it }
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        val cached = inMemoryCache[key] as? Int
        if (cached != null) return cached
        return prefs.getInt(key, defaultValue).also { inMemoryCache[key] = it }
    }

    override fun set(key: String, value: Any) {
        inMemoryCache[key] = value
        val editor = prefs.edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Float -> editor.putFloat(key, value)
            is Long -> editor.putLong(key, value)
        }
        editor.apply()
    }
}

class DefaultArisEventBus : ArisEventBus {
    private val flows = ConcurrentHashMap<Class<*>, MutableSharedFlow<ArisEvent>>()

    override suspend fun publish(event: ArisEvent) {
        flows.forEach { (clazz, flow) ->
            if (clazz.isInstance(event)) {
                flow.emit(event)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ArisEvent> subscribe(eventType: Class<T>): SharedFlow<T> {
        return flows.getOrPut(eventType) {
            MutableSharedFlow(extraBufferCapacity = 128)
        } as SharedFlow<T>
    }
}
