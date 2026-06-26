package com.aris.voice.core

/**
 * Clean domain result wrapper used across all layers and modules in Project ARIS.
 * Encourages functional error handling and avoids throwing unchecked exceptions through the pipeline.
 */
sealed class ArisResult<out T> {

    data class Success<out T>(val value: T) : ArisResult<T>()

    data class Failure(val error: ArisError) : ArisResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ArisResult<R> {
        return when (this) {
            is Success -> Success(transform(value))
            is Failure -> Failure(error)
        }
    }

    inline fun <R> flatMap(transform: (T) -> ArisResult<R>): ArisResult<R> {
        return when (this) {
            is Success -> transform(value)
            is Failure -> Failure(error)
        }
    }

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error
    }
}
