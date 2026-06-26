package com.aris.voice.di

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe Service Registry implementing the simple Service Locator pattern for Project ARIS.
 * This establishes an extensible dependency injector without circular compile-time package chains,
 * allowing clean decoupling of modules (e.g. Brain, Memory, actions, Perception, conversation).
 */
object ArisServiceRegistry {
    private val services = ConcurrentHashMap<Class<*>, Any>()

    /**
     * Registers a service implementation instance for a specified contract type.
     */
    fun <T : Any> register(serviceContract: Class<T>, implementation: T) {
        services[serviceContract] = implementation
    }

    /**
     * Retrieves the registered service implementation instance for a contract type.
     * Throws an exception if the service is not found in the registry.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(serviceContract: Class<T>): T {
        val instance = services[serviceContract]
            ?: throw IllegalStateException("Service Contract: ${serviceContract.name} is not registered in ARIS Service Registry.")
        return instance as T
    }

    /**
     * Retrieves the registered service implementation instance for a contract type, or null if unregistered.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(serviceContract: Class<T>): T? {
        val instance = services[serviceContract] ?: return null
        return instance as T
    }

    /**
     * Clears all registered services from the registry. Useful for teardown and testing.
     */
    fun clear() {
        services.clear()
    }
}
