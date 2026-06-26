package com.aris.voice.core

import kotlinx.coroutines.flow.SharedFlow

/**
 * Interface representing any event flowing through the ARIS system.
 */
interface ArisEvent {
    val timestamp: Long
    val origin: String
}

/**
 * Event Listener contract for handling broadcasted events.
 */
interface ArisEventListener {
    /**
     * Called when an event matching the subscription criteria is published.
     */
    suspend fun onEvent(event: ArisEvent)
}

/**
 * Central event bus contract allowing decoupled modules to broadcast
 * and observe events asynchronously without forming direct dependencies.
 */
interface ArisEventBus {
    /**
     * Broadcast an event to all subscribed listeners.
     */
    suspend fun publish(event: ArisEvent)

    /**
     * Get a flow of all events matching a specific type.
     */
    fun <T : ArisEvent> subscribe(eventType: Class<T>): SharedFlow<T>
}
