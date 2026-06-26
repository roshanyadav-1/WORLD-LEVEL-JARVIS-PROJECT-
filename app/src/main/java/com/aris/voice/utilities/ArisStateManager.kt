package com.aris.voice.utilities

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aris.voice.ConversationalAgentService
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages the state of the Aris app and provides callbacks for state changes.
 * This class monitors various service components to determine the current app state.
 */
class ArisStateManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ArisStateManager"
        
        @Volatile private var INSTANCE: ArisStateManager? = null

        fun getInstance(context: Context): ArisStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArisStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(context) }
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(context) }
    
    // State management
    private var currentState: ArisState = ArisState.IDLE
    private var hasRecentError: Boolean = false
    private var errorClearRunnable: Runnable? = null
    
    // Listeners for state changes
    private val stateChangeListeners = CopyOnWriteArrayList<(ArisState) -> Unit>()
    
    // Monitoring flags
    private var isMonitoring = false
    private var monitoringRunnable: Runnable? = null
    
    /**
         * Add a listener for state changes
         */
    fun addStateChangeListener(listener: (ArisState) -> Unit) {
        stateChangeListeners.add(listener)
    }
    
    /**
         * Remove a state change listener
         */
    fun removeStateChangeListener(listener: (ArisState) -> Unit) {
        stateChangeListeners.remove(listener)
    }
    
    /**
         * Get the current state
         */
    fun getCurrentState(): ArisState = currentState
    
    /**
         * Manually set the Aris state (called from ConversationalAgentService)
         */
    fun setState(newState: ArisState) {
        Log.d(TAG, "State manually set to: $newState")
        updateState(newState)
    }
    
    /**
         * Start monitoring service states and updating the current state
         */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring, skipping start")
            return
        }
        
        isMonitoring = true
        Log.d(TAG, "Starting state monitoring")
        
        // Set initial state to IDLE when monitoring starts
        setState(ArisState.IDLE)
    }
    
    /**
         * Stop monitoring service states
         */
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }
        
        isMonitoring = false
        Log.d(TAG, "Stopping state monitoring")
        
        // Cancel scheduled updates
        monitoringRunnable?.let { mainHandler.removeCallbacks(it) }
        errorClearRunnable?.let { mainHandler.removeCallbacks(it) }
        
        // Reset to idle state
        setState(ArisState.IDLE)
    }
    
    /**
         * Manually trigger an error state (called from service error callbacks)
         */
    fun triggerErrorState() {
        Log.d(TAG, "Error state triggered")
        setState(ArisState.ERROR)
        
        // Clear error state after 3 seconds and return to idle
        errorClearRunnable?.let { mainHandler.removeCallbacks(it) }
        errorClearRunnable = Runnable {
            Log.d(TAG, "Error state cleared, returning to idle")
            setState(ArisState.IDLE)
        }
        mainHandler.postDelayed(errorClearRunnable!!, 3000)
    }
    

    
    /**
         * Update the current state and notify listeners
         */
    private fun updateState(newState: ArisState) {
        val previousState = currentState
        currentState = newState
        
        Log.d(TAG, "State updated: $previousState -> $newState")
        
        // Notify all listeners on the main thread
        mainHandler.post {
            stateChangeListeners.forEach { listener ->
                try {
                    listener(newState)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying state change listener", e)
                }
            }
        }
    }
    
    /**
         * Get a human-readable status text for the current state
         */
    fun getStatusText(): String {
        return when (currentState) {
            ArisState.IDLE -> "Ready"
            ArisState.LISTENING -> "Listening..."
            ArisState.PROCESSING -> "Processing..."
            ArisState.SPEAKING -> "Speaking..."
            ArisState.ERROR -> "Error"
            ArisState.DIZZY -> "Ouch! Axel is dizzy"
        }
    }
    
    /**
         * Get the color associated with the current state
         */
    fun getStateColor(): Int {
        return DeltaStateColorMapper.getColor(context, currentState)
    }
    
    /**
         * Get the complete visual state information for the current state
         */
    fun getDeltaVisualState(): DeltaStateColorMapper.DeltaVisualState {
        return DeltaStateColorMapper.getDeltaVisualState(context, currentState)
    }
}
