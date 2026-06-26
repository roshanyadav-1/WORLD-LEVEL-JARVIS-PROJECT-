package com.aris.voice.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aris.voice.v2.AgentService
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TriggerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXECUTE_TASK = "com.aris.voice.action.EXECUTE_TASK"
        const val EXTRA_TASK_INSTRUCTION = "com.aris.voice.EXTRA_TASK_INSTRUCTION"
        const val EXTRA_TRIGGER_ID = "com.aris.voice.EXTRA_TRIGGER_ID"
        private const val TAG = "TriggerReceiver"
        private const val DEBOUNCE_INTERVAL_MS = 60 * 1000 // 1 minute

        // Cache to store recent task instructions and their timestamps
        private val recentTasks = ConcurrentHashMap<String, Long>()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent, cannot proceed.")
            return
        }
        
        val prefs = context.getSharedPreferences("TriggerReceiverPrefs", Context.MODE_PRIVATE)

        if (intent.action == ACTION_EXECUTE_TASK) {
            val taskInstruction = intent.getStringExtra(EXTRA_TASK_INSTRUCTION)

            if (taskInstruction.isNullOrBlank()) {
                Log.e(TAG, "Received execute task action but instruction was null or empty.")
                return
            }

            val currentTime = System.currentTimeMillis()
            val lastExecutionTime = prefs.getLong("last_exec_$taskInstruction", recentTasks[taskInstruction] ?: 0L)

            if ((currentTime - lastExecutionTime) < DEBOUNCE_INTERVAL_MS) {
                Log.d(TAG, "Debouncing duplicate task: '$taskInstruction'")
                return
            }

            // Update the cache with the new execution time
            recentTasks[taskInstruction] = currentTime
            prefs.edit().putLong("last_exec_$taskInstruction", currentTime).apply()

            Log.d(TAG, "Received task to execute: '$taskInstruction'")

            // Directly start the v2 AgentService
            AgentService.start(context, taskInstruction)

            // Reschedule the alarm for the next day
            val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID)
            if (triggerId != null) {
                val pendingResult = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()).launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        TriggerManager.getInstance(context).rescheduleTrigger(triggerId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            // Clean up old entries from the cache
            cleanupRecentTasks(context, currentTime)
        }
    }

    private fun cleanupRecentTasks(context: Context, currentTime: Long) {
        val prefs = context.getSharedPreferences("TriggerReceiverPrefs", Context.MODE_PRIVATE)
        val iterator = recentTasks.entries.iterator()
        val editor = prefs.edit()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((currentTime - entry.value) > DEBOUNCE_INTERVAL_MS) {
                iterator.remove()
                editor.remove("last_exec_${entry.key}")
            }
        }
        editor.apply()
    }
}
