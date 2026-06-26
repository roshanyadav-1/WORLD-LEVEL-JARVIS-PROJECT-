package com.aris.voice.triggers

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ArisNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    companion object {
        @Volatile
        var instance: ArisNotificationListenerService? = null
            private set
    }

    private val TAG = "ArisNotification"
    private lateinit var triggerManager: TriggerManager
    
    private val notificationDebounce = android.util.LruCache<String, Long>(100)
    private val NOTIF_DEBOUNCE_MS = 5_000L  // 5 seconds

    override fun onCreate() {
        super.onCreate()
        instance = this
        triggerManager = TriggerManager.getInstance(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "Notification listener destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        Log.d(TAG, "Notification posted from package: $packageName")

        if (packageName == this.packageName) {
            Log.d(TAG, "Ignoring notification from own package.")
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        
        val key = "${packageName}_${title}"
        val lastTime = notificationDebounce.get(key) ?: 0L
        if (System.currentTimeMillis() - lastTime <= NOTIF_DEBOUNCE_MS) {
            Log.d(TAG, "Debouncing duplicate notification for: $key")
            return
        }
        notificationDebounce.put(key, System.currentTimeMillis())

        serviceScope.launch {
            val notificationTriggers = triggerManager.getTriggers()
                .filter { it.type == TriggerType.NOTIFICATION && it.isEnabled }

            // Check for specific or all-apps trigger
            var matchingTrigger = notificationTriggers.find { it.packageName == "*" }
            if (matchingTrigger == null) {
                matchingTrigger = notificationTriggers.find { it.packageName == packageName }
            }

            if (matchingTrigger != null) {
                val notificationContent = "Notification Content: $title - $text"
                val finalInstruction = "${matchingTrigger.instruction}\n\n$notificationContent"

                Log.d(TAG, "Found matching trigger for package: $packageName. Executing instruction: $finalInstruction")
                // Use the TriggerReceiver to start the agent service
                val intent = android.content.Intent(this@ArisNotificationListenerService, TriggerReceiver::class.java).apply {
                    action = TriggerReceiver.ACTION_EXECUTE_TASK
                    putExtra(TriggerReceiver.EXTRA_TASK_INSTRUCTION, finalInstruction)
                }
                sendBroadcast(intent)
            }
        }
    }
}
