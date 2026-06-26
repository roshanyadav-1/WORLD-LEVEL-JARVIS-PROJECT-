package com.aris.voice.data

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents an item in the task history execution log.
 * Provides safe formatting helpers for UI display.
 */
data class TaskHistoryItem(
    val task: String = "",
    val status: String = "unknown",
    val startedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val success: Boolean? = null,
    val errorMessage: String? = null
) {
    /**
     * Returns an appropriate emoji to represent the current status of the task.
     */
    fun getStatusEmoji(): String {
        return when (status.lowercase(Locale.getDefault())) {
            "started", "in_progress", "running" -> "🔄"
            "completed", "done", "success" -> if (success == true) "✅" else "❌"
            "failed", "error" -> "❌"
            else -> "⏳"
        }
    }
    
    /**
     * Formats the startedAt timestamp into a readable string safely.
     */
    fun getFormattedStartTime(): String {
        return try {
            startedAt?.toDate()?.let { date ->
                val formatter = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                formatter.format(date)
            } ?: "Unknown time"
        } catch (e: Exception) {
            "Unknown time"
        }
    }
    
    /**
     * Formats the completedAt timestamp into a readable string safely.
     */
    fun getFormattedCompletionTime(): String {
        return try {
            completedAt?.toDate()?.let { date: Date ->
                val formatter = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
                formatter.format(date)
            } ?: "Not completed"
        } catch (e: Exception) {
            "Not completed"
        }
    }
    
    /**
     * Checks if this task represents a failed state.
     */
    fun isFailed(): Boolean {
        return status.equals("failed", ignoreCase = true) || success == false
    }
    
    /**
     * Returns the execution duration in seconds, or null if incomplete.
     */
    fun getDurationSeconds(): Long? {
        val start = startedAt?.seconds ?: return null
        val end = completedAt?.seconds ?: return null
        return (end - start).coerceAtLeast(0)
    }
}
