package com.aris.voice.v2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.aris.voice.R
import com.aris.voice.utilities.ApiKeyManager
import com.aris.voice.api.Eyes
import com.aris.voice.api.Finger
import com.aris.voice.overlay.OverlayDispatcher
import com.aris.voice.utilities.VisualFeedbackManager
import com.aris.voice.overlay.OverlayManager
import com.aris.voice.v2.actions.ActionExecutor
import com.aris.voice.v2.fs.FileSystem
import com.aris.voice.v2.llm.GeminiApi
import com.aris.voice.v2.message_manager.MemoryManager
import com.aris.voice.v2.perception.Perception
import com.aris.voice.v2.perception.SemanticParser
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A Foreground Service responsible for hosting and running the AI Agent.
 *
 * This service manages the entire lifecycle of the agent, from initializing its components
 * to running its main loop in a background coroutine. It starts as a foreground service
 * to ensure the OS does not kill it while it's performing a long-running task.
 */
class AgentService : Service() {

    private val TAG = "AgentService"

    // A dedicated coroutine scope tied to the service's lifecycle.
    // Using a SupervisorJob ensures that if one child coroutine fails, it doesn't cancel the whole scope.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }

    // Declare agent and its dependencies. They will be initialized in onCreate.
    private val taskQueue: Queue<String> = ConcurrentLinkedQueue()
    private lateinit var agent: Agent
    private lateinit var settings: AgentSettings
    private lateinit var fileSystem: FileSystem
    private lateinit var memoryManager: MemoryManager
    private lateinit var perception: Perception
    private lateinit var llmApi: GeminiApi
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var overlayManager: OverlayManager

    // Firebase instances for task tracking
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "AgentServiceChannelV2"
        private const val NOTIFICATION_ID = 14
        private const val EXTRA_TASK = "com.aris.voice.v2.EXTRA_TASK"
        private const val ACTION_STOP_SERVICE = "com.aris.voice.v2.ACTION_STOP_SERVICE"

        private val _isRunning = java.util.concurrent.atomic.AtomicBoolean(false)
        private val _currentTask = java.util.concurrent.atomic.AtomicReference<String>("")
        private val _currentTaskStartTime = java.util.concurrent.atomic.AtomicLong(0L)
        private val _isPaused = java.util.concurrent.atomic.AtomicBoolean(false)

        var isRunning: Boolean
            get() = _isRunning.get()
            set(v) = _isRunning.set(v)

        var isPaused: Boolean
            get() = _isPaused.get()
            set(v) = _isPaused.set(v)

        var currentTask: String?
            get() = _currentTask.get()
            set(v) = _currentTask.set(v ?: "")

        var currentTaskStartTime: Long
            get() = _currentTaskStartTime.get()
            set(v) = _currentTaskStartTime.set(v)

        /**
         * A public method to request the service to stop from outside.
         */
        fun stop(context: Context) {
            Log.d("AgentService", "External stop request received.")
            val intent = Intent(context, AgentService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun start(context: Context, task: String) {
            Log.d("AgentService", "Starting service with task: $task")
            if (isRunning) {
                Log.w("AgentService", "Task already running: $currentTask")
                return
            }
            val dangerous = setOf("delete all", "factory reset", "format", "uninstall all")
            if (dangerous.any { task.lowercase().contains(it) }) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try { com.aris.voice.utilities.TTSManager.getInstance(context).speakText("I will not perform dangerous actions. Please try another task.") } catch (e: Exception) {}
                }
                return
            }
            if (com.aris.voice.ConversationalAgentService.isRunning) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        com.aris.voice.utilities.TTSManager.getInstance(context).speakText("Starting task while talking")
                    } catch (e: Exception) {}
                }
            }
            val intent = Intent(context, AgentService::class.java).apply {
                putExtra(EXTRA_TASK, task)
            }
            try {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        com.aris.voice.utilities.FreemiumManager().decrementTaskCount()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            context.startService(intent)
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service is being created.")
        overlayManager = OverlayManager.getInstance(this)
        overlayManager.startObserving()

        // visualFeedbackManager.showTtsWave() // removed to replace with vibration
        createNotificationChannel()

        settings = AgentSettings(
            maxHistoryItems = 8,
            maxActionsPerStep = 5
        )
        fileSystem = FileSystem(this,)
        memoryManager = MemoryManager(this, "", fileSystem, settings)
        perception = Perception(Eyes(this), SemanticParser())
        llmApi = GeminiApi(
            "gemini-2.5-flash",
            apiKeyManager = ApiKeyManager,
            context = this,
            maxRetry = 10
        )
        actionExecutor = ActionExecutor(Finger(this))
        agent = Agent(
            settings,
            memoryManager,
            perception,
            llmApi,
            actionExecutor,
            fileSystem,
            this
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received.")

        // Handle stop action
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Received stop action. Stopping service.")
            if (::agent.isInitialized) {
                // If agent has a pause/stop method, we could call it here, but typically changing isRunning will stop process loop?
                isRunning = false
            }
            stopSelf() // onDestroy will handle cleanup
            return START_NOT_STICKY
        }

        // Add new task to the queue
        intent?.getStringExtra(EXTRA_TASK)?.let {
            if (it.isNotBlank()) {
                Log.d(TAG, "Adding task to queue: $it")
                taskQueue.add(it)
            }
        }

        // If the agent is not already processing tasks, start the loop.
        if (!isRunning && taskQueue.isNotEmpty()) {
            Log.i(TAG, "Agent not running, starting processing loop.")
            val batteryLevel = getBatteryLevel(this)
            if (batteryLevel > 0 && batteryLevel <= 10) {
                Log.w(TAG, "Battery level too low ($batteryLevel%) to start AgentService")
                serviceScope.launch {
                    try { com.aris.voice.utilities.TTSManager.getInstance(this@AgentService).speakText("Battery too low to execute tasks. Please charge your device.") } catch (e: Exception) {}
                }
                stopSelf()
                return START_NOT_STICKY
            }
            serviceScope.launch {
                processTaskQueue()
            }
        } else {
            if(isRunning) Log.d(TAG, "Task added to queue. Processor is already running.")
            else Log.d(TAG, "Service started with no task, waiting for tasks.")
        }

        // Use START_STICKY to ensure the service stays running in the background
        // until we explicitly stop it. This is crucial for a queue-based system.
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun processTaskQueue() {
        if (isRunning) {
            Log.d(TAG, "processTaskQueue called but already running.")
            return
        }
        isRunning = true

        Log.i(TAG, "Starting task processing loop.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("Agent is starting..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification("Agent is starting..."),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification("Agent is starting..."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AgentService in foreground: ${e.message}", e)
        }

        // Start vibration heartbeat
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        val vibrationJob = serviceScope.launch {
            while (isRunning) {
                // Check if the agent is under "load" (queued tasks, many steps, or long execution)
                val isUnderLoad = taskQueue.isNotEmpty() ||
                        (::agent.isInitialized && agent.state.nSteps > 2) ||
                        (currentTaskStartTime > 0 && (System.currentTimeMillis() - currentTaskStartTime > 15000L))

                // Heartbeat rhythm settings
                val beatCycle = if (isUnderLoad) 500L else 833L // ~120 bpm under load, ~72 bpm normally
                val lubDuration = if (isUnderLoad) 70L else 50L
                val dubDuration = if (isUnderLoad) 50L else 30L
                val lubAmplitude = if (isUnderLoad) 255 else 180
                val dubAmplitude = if (isUnderLoad) 150 else 80
                val pauseBetween = 150L

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (vibrator.hasAmplitudeControl()) {
                        val timings = longArrayOf(0, lubDuration, pauseBetween, dubDuration)
                        val amplitudes = intArrayOf(0, lubAmplitude, 0, dubAmplitude)
                        vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        val timings = longArrayOf(0, lubDuration, pauseBetween, dubDuration)
                        vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, -1))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val timings = longArrayOf(0, lubDuration, pauseBetween, dubDuration)
                    vibrator.vibrate(timings, -1)
                }
                kotlinx.coroutines.delay(beatCycle)
            }
        }

        while (taskQueue.isNotEmpty()) {
            val task = taskQueue.poll() ?: continue // Dequeue task, continue if null
            
            // Reinitialize core components to provide a fresh, unpolluted state for each queued task
            fileSystem = FileSystem(this)
            memoryManager = MemoryManager(this, "", fileSystem, settings)
            agent = Agent(settings, memoryManager, perception, llmApi, actionExecutor, fileSystem, this)

            currentTask = task
            currentTaskStartTime = System.currentTimeMillis()

            // Update notification for the new task
            notificationManager.notify(NOTIFICATION_ID, createNotification("Agent is running task: $task"))

            try {
                Log.i(TAG, "Executing task: $task")
                
                // --- Intercept trigger creation requests ---
                val parsedTrigger = com.aris.voice.triggers.TriggerManager.getInstance(this).createTriggerFromNaturalLanguage(task)
                if (parsedTrigger != null) {
                    com.aris.voice.triggers.TriggerManager.getInstance(this).addTrigger(parsedTrigger)
                    Log.i(TAG, "Successfully created automation from voice command: $parsedTrigger")
                    try {
                        com.aris.voice.utilities.TTSManager.getInstance(this).speakText("Automation created successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed TTS for trigger voice success", e)
                    }
                    restartConversationalService()
                    continue
                }
                
                trackTaskInFirebase(task)
                val success = kotlinx.coroutines.withTimeoutOrNull(5 * 60 * 1000L) {
                    agent.run(task)
                } ?: false
                if(agent.state.stopped == false) agent.state.stopped = true
                trackTaskCompletion(task, success)
                if (success) {
                    Log.i(TAG, "Task completed successfully: $task")
                    try {
                        com.aris.voice.utilities.TTSManager.getInstance(this).speakText("Task completed")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed TTS for success", e)
                    }
                    restartConversationalService()
                } else {
                    Log.i(TAG, "Task failed or stopped: $task")
                    try {
                        com.aris.voice.utilities.TTSManager.getInstance(this).speakText("Task not completed")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed TTS for failure", e)
                    }
                    restartConversationalService()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Task failed with an exception: $task", e)
                trackTaskCompletion(task, false, e.message)
                try {
                    com.aris.voice.utilities.TTSManager.getInstance(this).speakText("Task not completed")
                } catch (ttsE: Exception) {
                    Log.e(TAG, "Failed TTS for error", ttsE)
                }
                restartConversationalService()
            }
        }

        vibrationJob.cancel()
        Log.i(TAG, "Task queue is empty. Stopping service.")
        stopSelf() // Stop the service only when the queue is empty
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Service is being destroyed.")
//        overlayManager.stopObserving()
        OverlayDispatcher.clearAll()
        overlayManager.stopObserving()
        isRunning = false
        currentTask = null
        taskQueue.clear()
        serviceScope.cancel()
        visualFeedbackManager.hideTtsWave()
        Log.i(TAG, "Service destroyed and all resources cleaned up.")
    }

    /**
     * This service does not provide binding, so we return null.
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * Creates the NotificationChannel for the foreground service.
     * This is required for Android 8.0 (API level 26) and higher.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Agent Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Creates the persistent notification for the foreground service.
     */
    private fun createNotification(contentText: String): Notification {

        val stopIntent = Intent(this, AgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Agent Task: ${currentTask?.take(30) ?: "Starting..."}")
            .setContentText(contentText)
            .addAction(
                android.R.drawable.ic_media_pause, // Using built-in pause icon as stop button
                "Stop A.R.I.S",
                stopPendingIntent
            )
            .setOngoing(true) // Makes notification persistent and harder to dismiss
             .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
    
    private fun getBatteryLevel(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        return batteryIntent?.let {
            val level = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                (level * 100 / scale.toFloat()).toInt()
            } else -1
        } ?: -1
    }

    private fun restartConversationalService() {
        try {
            val serviceIntent = Intent(this, com.aris.voice.ConversationalAgentService::class.java).apply {
                action = com.aris.voice.ConversationalAgentService.ACTION_FORCE_LISTEN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d(TAG, "Automatically restarted ConversationalAgentService to turn mic back on.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to automatically restart ConversationalAgentService: ${e.message}", e)
        }
    }

    /**
     * Tracks the task start in Firebase by appending it to the user's task history array.
     * This method is inspired by FreemiumManager's Firebase operations.
     */
    private suspend fun trackTaskInFirebase(task: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot track task, user is not logged in.")
            return
        }

        try {
            val taskEntry = hashMapOf(
                "task" to task,
                "status" to "started",
                "startedAt" to Timestamp.now(),
                "completedAt" to null,
                "success" to null,
                "errorMessage" to null
            )

            // Append the task to the user's taskHistory array
            db.collection("users").document(currentUser.uid)
                .update("taskHistory", FieldValue.arrayUnion(taskEntry))
                .await()

            Log.d(TAG, "Successfully tracked task start in Firebase for user ${currentUser.uid}: $task")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track task in Firebase", e)
            // Don't fail the task execution if Firebase tracking fails
        }
    }

    /**
     * Updates the task completion status in Firebase.
     * Since Firestore doesn't support updating array elements directly,
     * we'll add a new completion entry to track the result.
     */
    private suspend fun trackTaskCompletion(task: String, success: Boolean, errorMessage: String? = null) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w(TAG, "Cannot track task completion, user is not logged in.")
            return
        }

        try {
            val completionEntry = hashMapOf(
                "task" to task,
                "status" to if (success) "completed" else "failed",
//                "startedAt" to null, // This is a completion entry, not a start entry
                "completedAt" to Timestamp.now(),
                "success" to success,
                "errorMessage" to errorMessage
            )

            // Append the completion status to the user's taskHistory array
            db.collection("users").document(currentUser.uid)
                .update("taskHistory", FieldValue.arrayUnion(completionEntry))
                .await()

            Log.d(TAG, "Successfully tracked task completion in Firebase for user ${currentUser.uid}: $task (success: $success)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to track task completion in Firebase", e)
        }
    }
}
