package com.aris.voice.core

/**
 * Interface defining the global system configuration for ARIS.
 * Provides a clean way to query system flags and user-defined variables
 * without exposing Android-specific context or storage APIs directly.
 */
interface ArisConfig {
    /**
     * Get a Boolean configuration flag by its key.
     */
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    /**
     * Get a String configuration property by its key.
     */
    fun getString(key: String, defaultValue: String): String

    /**
     * Get an Integer configuration property by its key.
     */
    fun getInt(key: String, defaultValue: Int): Int

    /**
     * Update a configuration value dynamically.
     */
    fun set(key: String, value: Any)
}
