package com.aris.voice.core

/**
 * Severity level of the log message.
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ASSERT
}

/**
 * Permanent logging interface for the ARIS platform.
 * Modules must log strictly through this interface to maintain environment-agnostic clean layers.
 */
interface ArisLogger {
    /**
     * Log a message with the specified level, tag, and optional throwable.
     */
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)

    fun v(tag: String, message: String) = log(LogLevel.VERBOSE, tag, message)
    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)
}
