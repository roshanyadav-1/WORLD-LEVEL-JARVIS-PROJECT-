package com.aris.voice.utilities

import android.content.Context
import androidx.core.content.ContextCompat
import com.aris.voice.R

/**
 * Utility class for mapping ArisState values to their corresponding colors
 * and providing state-related information for the delta symbol.
 */
object DeltaStateColorMapper {

    /**
     * Data class representing the visual state of the delta symbol
     */
    data class DeltaVisualState(
        val state: ArisState,
        val color: Int,
        val statusText: String,
        val colorHex: String
    )

    /**
     * Get the color resource ID for a given ArisState
     */
    fun getColorResourceId(state: ArisState): Int {
        return when (state) {
            ArisState.IDLE -> R.color.delta_idle
            ArisState.LISTENING -> R.color.delta_listening
            ArisState.PROCESSING -> R.color.delta_processing
            ArisState.SPEAKING -> R.color.delta_speaking
            ArisState.ERROR -> R.color.delta_error
            ArisState.DIZZY -> R.color.delta_error
        }
    }

    /**
     * Get the resolved color value for a given ArisState
     */
    fun getColor(context: Context, state: ArisState): Int {
        val colorResId = getColorResourceId(state)
        return ContextCompat.getColor(context, colorResId)
    }

    /**
     * Get the status text for a given ArisState
     */
    fun getStatusText(state: ArisState): String {
        return when (state) {
            ArisState.IDLE -> "Ready, tap delta to wake me up!"
            ArisState.LISTENING -> "Listening..."
            ArisState.PROCESSING -> "Processing..."
            ArisState.SPEAKING -> "Speaking..."
            ArisState.ERROR -> "Error"
            ArisState.DIZZY -> "Ouch! Axel is dizzy..."
        }
    }

    /**
     * Get the hex color string for a given ArisState (for debugging/logging)
     */
    fun getColorHex(context: Context, state: ArisState): String {
        val color = getColor(context, state)
        return String.format("#%08X", color)
    }

    /**
     * Get complete visual state information for a given ArisState
     */
    fun getDeltaVisualState(context: Context, state: ArisState): DeltaVisualState {
        return DeltaVisualState(
            state = state,
            color = getColor(context, state),
            statusText = getStatusText(state),
            colorHex = getColorHex(context, state)
        )
    }

    /**
     * Get all available states with their visual information
     */
    fun getAllStates(context: Context): List<DeltaVisualState> {
        return ArisState.values().map { state ->
            getDeltaVisualState(context, state)
        }
    }

    /**
     * Check if a state represents an active operation (not idle or error)
     */
    fun isActiveState(state: ArisState): Boolean {
        return when (state) {
            ArisState.LISTENING, ArisState.PROCESSING, ArisState.SPEAKING -> true
            ArisState.IDLE, ArisState.ERROR, ArisState.DIZZY -> false
        }
    }

    /**
     * Check if a state represents an error condition
     */
    fun isErrorState(state: ArisState): Boolean {
        return state == ArisState.ERROR
    }

    /**
     * Get the priority of a state for determining which state to display
     * when multiple conditions might be true. Higher numbers = higher priority.
     */
    fun getStatePriority(state: ArisState): Int {
        return when (state) {
            ArisState.DIZZY -> 6      // Dizzy gets absolute priority
            ArisState.ERROR -> 5      // Highest priority
            ArisState.SPEAKING -> 4   // High priority
            ArisState.LISTENING -> 3  // Medium-high priority
            ArisState.PROCESSING -> 2 // Medium priority
            ArisState.IDLE -> 1       // Lowest priority
        }
    }
}
