package com.aris.voice.triggers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON"
        )) {
            Log.d(TAG, "Device boot completed. Rescheduling alarms.")
            val triggerManager = TriggerManager.getInstance(context)

            // Start the TriggerMonitoringService
            val serviceIntent = Intent(context, TriggerMonitoringService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Started TriggerMonitoringService on boot.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start TriggerMonitoringService from boot receiver: ${e.message}", e)
            }

            CoroutineScope(Dispatchers.IO).launch {
                val triggers = triggerManager.getTriggers()
                val scheduledTriggers = triggers.filter { it.isEnabled && it.type == TriggerType.SCHEDULED_TIME }
                scheduledTriggers.forEach { trigger ->
                    triggerManager.updateTrigger(trigger)
                }
                Log.d(TAG, "Finished rescheduling ${scheduledTriggers.size} alarms.")
            }
            
            val prefs = context.getSharedPreferences("ArisSettings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("wake_word_enabled", false)) {
                val wakeWordIntent = Intent(context, com.aris.voice.services.EnhancedWakeWordService::class.java)
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(wakeWordIntent)
                    } else {
                        context.startService(wakeWordIntent)
                    }
                    Log.d(TAG, "Started EnhancedWakeWordService on boot.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start EnhancedWakeWordService from boot receiver: ${e.message}", e)
                }
            }
        }
    }
}
