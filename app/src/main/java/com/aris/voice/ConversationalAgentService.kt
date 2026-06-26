package com.aris.voice

import android.annotation.SuppressLint

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aris.voice.api.Eyes
//import com.aris.voice.services.AgentTaskService
import com.aris.voice.utilities.SpeechCoordinator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.graphics.toColorInt
import com.aris.voice.agents.ClarificationAgent
import com.aris.voice.utilities.TTSManager
import com.aris.voice.utilities.addResponse
import com.aris.voice.utilities.getReasoningModelApiResponse
import com.aris.voice.utilities.FreemiumManager
import com.aris.voice.overlay.OverlayManager
import com.aris.voice.overlay.OverlayDispatcher
import com.aris.voice.utilities.ArisState
import com.aris.voice.utilities.UserProfileManager
import com.aris.voice.utilities.VisualFeedbackManager
import com.aris.voice.v2.AgentService
import com.aris.voice.data.UserMemory
import com.google.ai.client.generativeai.type.TextPart
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.aris.voice.utilities.ServicePermissionManager
import com.aris.voice.utilities.ArisStateManager
import com.aris.voice.v2.perception.Perception
import com.aris.voice.v2.perception.SemanticParser
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

data class ModelDecision(
    val type: String = "Reply",
    val reply: String,
    val instruction: String = "",
    val shouldEnd: Boolean = false
)

@SuppressLint("NewApi")
class ConversationalAgentService : Service() {

    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(this) }
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var conversationHistory = listOf<Pair<String, List<Any>>>()
    private val ttsManager by lazy { TTSManager.getInstance(this) }
    private val overlayManager by lazy { OverlayManager.getInstance(this) }
    private val clarificationQuestionViews = java.util.concurrent.CopyOnWriteArrayList<View>()
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(this) }
    private val arisStateManager by lazy { ArisStateManager.getInstance(this) }
    private var isTextModeActive = false
    private val freemiumManager by lazy { FreemiumManager() }
    private val servicePermissionManager by lazy { ServicePermissionManager(this) }

    private var clarificationAttempts = 0
    private val maxClarificationAttempts = 2
    private var sttErrorAttempts = 0
    private var maxSttErrorAttempts = 3
    private var lastScreenContextUpdate = 0L

    private val hideTranscriptionRunnable = Runnable {
        visualFeedbackManager.hideTranscription()
    }

    private val clarificationAgent = ClarificationAgent
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var cachedMemories = listOf<UserMemory>()
    private var hasHeardFirstUtterance = false
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private val perception by lazy { Perception(Eyes(this), SemanticParser()) }
    private val client = com.aris.voice.utilities.NetworkClient.instance
    
    private val firebaseManager by lazy { com.aris.voice.utilities.FirebaseManager() }
    private val clarificationOverlayManager by lazy { com.aris.voice.utilities.ClarificationOverlayManager(this) }
    private var conversationId: String? = null

    private val CONVERSATION_TIMEOUT_MS = 3 * 60 * 1000L  // 3 minutes
    private var lastActivityTime = System.currentTimeMillis()
    private var timeoutJob: kotlinx.coroutines.Job? = null

    companion object {
        const val NOTIFICATION_ID = 3
        const val CHANNEL_ID = "ConversationalAgentChannel"
        const val ACTION_STOP_SERVICE = "com.aris.voice.ACTION_STOP_SERVICE"
        const val ACTION_TRIGGER_DIZZY_SHUTDOWN = "com.aris.voice.ACTION_TRIGGER_DIZZY_SHUTDOWN"
        const val ACTION_FORCE_LISTEN = "com.aris.voice.ACTION_FORCE_LISTEN"
        @Volatile var isRunning = false
        const val MEMORY_ENABLED = true
    }

    private fun safeStartForeground() {
        try {
            val hasMicrophonePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasMicrophonePermission) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        startForeground(
                            NOTIFICATION_ID,
                            createNotification(),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } catch (e: SecurityException) {
                        Log.w("ConvAgent", "Failed to start with microphone type, falling back to specialUse", e)
                        startForeground(
                            NOTIFICATION_ID,
                            createNotification(),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        startForeground(
                            NOTIFICATION_ID,
                            createNotification(),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } catch (e: Exception) {
                        startForeground(NOTIFICATION_ID, createNotification())
                    }
                } else {
                    startForeground(NOTIFICATION_ID, createNotification())
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createNotification())
                }
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Failed to start foreground safely: ${e.message}", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ConvAgent", "Service onCreate")
        
        // Early safe start foreground to satisfy Android requirements
        safeStartForeground()
        
        // Initialize Firebase Analytics
        firebaseAnalytics = Firebase.analytics
        
        // Track service creation
        firebaseAnalytics.logEvent("conversational_agent_started", null)
        
        isRunning = true
        updateHomeScreenWidget()
        isTextModeActive = false // Reset text mode back to voice mode on startup
        createNotificationChannel()
        initializeConversation()
        clarificationAttempts = 0 // Reset clarification attempts counter
        sttErrorAttempts = 0 // Reset STT error attempts counter
        // usedMemories.clear() // Removed
        hasHeardFirstUtterance = false // Reset first utterance flag

        firebaseManager.onMemoriesFetched = { memories ->
            cachedMemories = memories
        }
        firebaseManager.fetchMemories()

        overlayManager.startObserving()
        visualFeedbackManager.showSpeakingOverlay() // <-- ADD THIS LINE
        visualFeedbackManager.showTtsWave()

        showInputBoxIfNeeded()
        visualFeedbackManager.showSmallDeltaGlow()

        // Start state monitoring and set initial state
        arisStateManager.startMonitoring()
        setServiceState(ArisState.IDLE)
        
        lastActivityTime = System.currentTimeMillis()
        timeoutJob = serviceScope.launch {
            while (isRunning) {
                delay(30_000)
                if (System.currentTimeMillis() - lastActivityTime > CONVERSATION_TIMEOUT_MS) {
                    Log.d("ConvAgent", "Conversation timed out due to inactivity")
                    gracefulShutdown("Session timed out. Call me when you need something!", "timeout")
                    break
                }
            }
        }
    }

    private fun updateActivityTime() {
        lastActivityTime = System.currentTimeMillis()
    }

    private fun showInputBoxIfNeeded() {
        // Disabled per user request (Requirement 3: no chat bar / input box during creature interaction / mic listening)
        return
    }

    /**
     * Call this when the user starts interacting with the text input.
     * It stops any ongoing voice interaction.
     */
    private fun enterTextMode() {
        if (isTextModeActive) return
        Log.d("ConvAgent", "Entering Text Mode. Stopping STT/TTS.")
        
        // Track text mode activation
        firebaseAnalytics.logEvent("text_mode_activated", null)
        
        isTextModeActive = true
        setServiceState(ArisState.IDLE)
        speechCoordinator.stopListening()
        speechCoordinator.stopSpeaking()
        // Optionally hide the transcription view since user is typing
        visualFeedbackManager.hideTranscription()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Safe start foreground right at entry
        safeStartForeground()
        Log.d("ConvAgent", "Service onStartCommand action=${intent?.action}")

        if (!servicePermissionManager.isAccessibilityServiceEnabled()) {
            serviceScope.launch {
                ttsManager.speakText("Please enable accessibility service first")
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_TRIGGER_DIZZY_SHUTDOWN) {
            Log.i("ConvAgent", "Received trigger dizzy shutdown action. Setting to dizzy and preparing stop.")
            setServiceState(ArisState.DIZZY)
            speechCoordinator.stopSpeaking()
            speechCoordinator.stopListening()
            
            // Stop any background task executor
            try {
                com.aris.voice.v2.AgentService.stop(this)
            } catch (e: Exception) {
                Log.e("ConvAgent", "Error stopping AgentService", e)
            }
            
            // Vibrate to provide tactile "hit" feedback
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(450, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(450)
            }
            
            // Post delayed complete stop after 3 seconds of adorable spinning stars animation
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopSelf()
            }, 3000)
            
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_FORCE_LISTEN) {
            Log.i("ConvAgent", "Received force listen action. Preparing foreground status and starting listen.")
            speechCoordinator.stopSpeaking()
            setServiceState(ArisState.LISTENING)
            startSttListeningEngine()
            return START_STICKY
        }

        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i("ConvAgent", "Received stop action. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }
        
        if (!servicePermissionManager.isMicrophonePermissionGranted()) {
            Log.e("ConvAgent", "RECORD_AUDIO permission not granted. Shutting down.")
            serviceScope.launch {
                ttsManager.speakText(getString(R.string.microphone_permission_not_granted))
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // Track conversation initiation
        firebaseAnalytics.logEvent("conversation_initiated", null)
        conversationId = java.util.UUID.randomUUID().toString()
        conversationId?.let { firebaseManager.trackConversationStart(it) }
        
        val initialMessage = intent?.getStringExtra("message")
        if (initialMessage != null && initialMessage.isNotEmpty()) {
            serviceScope.launch {
                Log.d("ConvAgent", "Received initial message from intent: $initialMessage")
                enterTextMode()
                processUserInput(initialMessage)
            }
        } else {
            // Skip greeting and start listening immediately
            serviceScope.launch {
                Log.d("ConvAgent", "Starting immediate listening (no greeting)")
                setServiceState(ArisState.LISTENING)
                startImmediateListening()
            }
        }
        return START_STICKY
    }

    /**
     * Gets a personalized greeting using the user's name from memories if available
     * NOTE: This method is kept for potential future use but no longer called on startup
     */
    private fun getPersonalizedGreeting(): String {
        try {
            val userProfile = UserProfileManager(this@ConversationalAgentService)
            Log.d("ConvAgent", "No name found in memories, using generic greeting")
            return "Hey ${userProfile.getName()}!"
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error getting personalized greeting", e)
            return "Hey!"
        }
    }

    private fun startSttListeningEngine() {
        if (isTextModeActive) {
            Log.d("ConvAgent", "In text mode, ensuring input box is visible and skipping voice listening.")
            mainHandler.post {
                showInputBoxIfNeeded() // Re-show the input box for the next turn.
            }
            return // Skip starting the voice listener entirely.
        }

        serviceScope.launch {
            speechCoordinator.startListening(
                onResult = { recognizedText ->
                    if (!isTextModeActive) {
                        Log.d("ConvAgent", "Final user transcription: $recognizedText")
                        
                        // Reset STT error attempts upon a successful transcription! (BUG-6 / IMP-1)
                        sttErrorAttempts = 0
                        
                        setServiceState(ArisState.PROCESSING)
                        visualFeedbackManager.updateTranscription(recognizedText)
                        
                        mainHandler.removeCallbacks(hideTranscriptionRunnable)
                        mainHandler.postDelayed(hideTranscriptionRunnable, 2000) // 2000ms delay to resolve fast vanished texts (BUG-13)

                        // Mark that we've heard the first utterance and trigger memory extraction if not already done
                        if (!hasHeardFirstUtterance) {
                            hasHeardFirstUtterance = true
                            Log.d("ConvAgent", "First utterance received, triggering memory extraction")
                            serviceScope.launch {
                                try {
                                    updateSystemPromptWithScreenContext()
                                } catch (e: Exception) {
                                    Log.e("ConvAgent", "Error during first utterance memory extraction", e)
                                }
                            }
                        }

                        processUserInput(recognizedText)
                    }
                },
                onError = { error ->
                    Log.e("ConvAgent", "STT Error: $error")
                    if (!isTextModeActive) {
                        // Trigger error state in state manager
                        triggerServiceErrorState()

                        // Track STT errors safely on the main/safe thread
                        val sttErrorBundle = android.os.Bundle().apply {
                            putString("error_message", error.take(100))
                            putInt("error_attempt", sttErrorAttempts + 1)
                            putInt("max_attempts", maxSttErrorAttempts)
                        }
                        mainHandler.post {
                            try {
                                firebaseAnalytics.logEvent("stt_error", sttErrorBundle)
                            } catch (e: Exception) {
                                Log.e("ConvAgent", "Failed to log event", e)
                            }
                        }

                        mainHandler.removeCallbacks(hideTranscriptionRunnable)
                        mainHandler.postDelayed(hideTranscriptionRunnable, 2000) // 2000ms delay (BUG-13)

                        sttErrorAttempts++
                        serviceScope.launch {
                            if (sttErrorAttempts >= maxSttErrorAttempts) {
                                mainHandler.post {
                                    firebaseAnalytics.logEvent("conversation_ended_stt_errors", null)
                                }
                                val exitMessage = "I'm having trouble understanding you clearly. Please try calling later!"
                                trackMessage("model", exitMessage, "error_message")
                                gracefulShutdown(exitMessage, "stt_errors")
                            } else {
                                val retryMessage = if (error == "No speech match" || error == "Speech timeout") {
                                    "I didn't hear anything. Could you please say that again?"
                                } else {
                                    "I'm sorry, I didn't catch that. Could you please repeat?"
                                }
                                speakAndThenListen(retryMessage)
                            }
                        }
                    }
                },
                onPartialResult = { partialText ->
                    if (!isTextModeActive) {
                        mainHandler.removeCallbacks(hideTranscriptionRunnable) // Prevent flickering (BUG-11)
                        visualFeedbackManager.updateTranscription(partialText)
                    }
                },
                onListeningStateChange = { listening ->
                    Log.d("ConvAgent", "Listening state: $listening")
                    if (!isTextModeActive) {
                        if (listening) {
                            setServiceState(ArisState.LISTENING)
                            visualFeedbackManager.showTranscription()
                        } else {
                            setServiceState(ArisState.IDLE)
                        }
                    }
                }
            )
        }
    }

    /**
     * Starts listening immediately without speaking any greeting or performing memory extraction
     * Deferred until after first user utterance
     */
    private suspend fun startImmediateListening() {
        Log.d("ConvAgent", "Starting immediate listening without greeting")
        startSttListeningEngine()
    }


    private suspend fun speakAndThenListen(text: String, draw: Boolean = true) {
        updateSystemPromptWithTime()
        setServiceState(ArisState.SPEAKING)
        speechCoordinator.speakText(text)
        Log.d("ConvAgent", "A.R.I.S said: $text")

        startSttListeningEngine()
    }

    // START: ADD THESE NEW METHODS AT THE END OF THE CLASS, before onDestroy()

    // --- CHANGED: Rewritten to process the new custom text format ---
    private fun processUserInput(userInput: String) {
        updateActivityTime()
        serviceScope.launch {
            clarificationOverlayManager.clearQuestions()
            updateSystemPromptWithAgentStatus()
            updateSystemPromptWithScreenContext()
            updateSystemPromptWithTime()
            // Mark that we've heard the first utterance and trigger memory extraction if not already done
            if (!hasHeardFirstUtterance) {
                hasHeardFirstUtterance = true
                Log.d("ConvAgent", "First utterance received via processUserInput, triggering memory extraction")
                try {
                    updateSystemPromptWithScreenContext()
                } catch (e: Exception) {
                    Log.e("ConvAgent", "Error during first utterance memory extraction", e)
                    // Continue execution even if memory extraction fails
                }
            }

            conversationHistory = addResponse("user", userInput, conversationHistory)
            
            // Dynamic Truncation based on character length (approx token limit)
            val MAX_CHAR_LENGTH = 15000 // roughly 3500-4000 tokens
            var currentLength = conversationHistory.sumOf { (_, parts) -> parts.sumOf { it.toString().length } }
            
            if (currentLength > MAX_CHAR_LENGTH && conversationHistory.size > 2) {
                Log.d("ConvAgent", "Truncating conversation history. Current char length: $currentLength")
                val systemPrompt = conversationHistory.first()
                val messagesToKeep = mutableListOf<Pair<String, List<Any>>>()
                var accumulatedLength = systemPrompt.second.sumOf { it.toString().length }
                
                for (i in conversationHistory.indices.reversed()) {
                    if (i == 0) continue
                    val msg = conversationHistory[i]
                    val msgLength = msg.second.sumOf { it.toString().length }
                    
                    if (accumulatedLength + msgLength <= MAX_CHAR_LENGTH || messagesToKeep.isEmpty()) {
                        messagesToKeep.add(0, msg)
                        accumulatedLength += msgLength
                    } else {
                        break
                    }
                }
                conversationHistory = listOf(systemPrompt) + messagesToKeep
                Log.d("ConvAgent", "Truncated to size: ${conversationHistory.size} messages")
            }
            
            // Track user message in Firebase
            trackMessage("user", userInput, "input")

            // Track user input
            val inputBundle = android.os.Bundle().apply {
                putString("input_type", if (isTextModeActive) "text" else "voice")
                putInt("input_length", userInput.length)
                putBoolean("is_command", userInput.equals("stop", ignoreCase = true) || userInput.equals("exit", ignoreCase = true))
            }
            firebaseAnalytics.logEvent("user_input_processed", inputBundle)

            try {
                val stopCommands = setOf("stop", "exit", "quit", "bye", "goodbye", "ruk jao", "band kar", "bas", "rukh jao", "theek hai bye", "ok bye", "shukriya", "thanks bye")
        if (userInput.lowercase() in stopCommands) {
                    firebaseAnalytics.logEvent("conversation_ended_by_command", null)
                    trackMessage("model", "Goodbye!", "farewell")
                    gracefulShutdown("Goodbye!", "command")
                    return@launch
                }

                // --- NEW: Offline Command Processing (No LLM required) ---
                val offlineProcessor = com.aris.voice.utilities.OfflineCommandProcessor(this@ConversationalAgentService)
                val offlineResult = offlineProcessor.processCommandOffline(userInput)
                if (offlineResult.isHandled) {
                    val msg = offlineResult.feedbackSpeech ?: "Done."
                    val offlineBundle = android.os.Bundle().apply {
                        putString("offline_command", userInput.take(100))
                    }
                    firebaseAnalytics.logEvent("offline_command_executed", offlineBundle)
                    trackMessage("model", msg, "offline_command")
                    
                    if (isTextModeActive) {
                        conversationHistory = addResponse("model", msg, conversationHistory)
                    }
                    
                    // Since most offline commands open a new activity (alarms, phone, settings, apps),
                    // we should gracefully shutdown the assistant overlay so the user can interact with the app.
                    gracefulShutdown(msg, "offline_command_executed")
                    return@launch
                }
                // --- END NEW ---

                setServiceState(ArisState.PROCESSING)
                visualFeedbackManager.showThinkingIndicator()
                val defaultJsonResponse = """{"Type": "Reply", "Reply": "I'm sorry, I had an issue.", "Instruction": "", "Should End": "Continue"}"""
                
                // --- NEW: Search Integration via Gemini Web Grounding ---
                val isSearchRequired = com.aris.voice.api.TavilyApi.requiresSearch(userInput) ||
                                       userInput.contains("news", ignoreCase = true) ||
                                       userInput.contains("current", ignoreCase = true) ||
                                       userInput.contains("grounding", ignoreCase = true) ||
                                       userInput.contains("real time", ignoreCase = true) ||
                                       userInput.contains("fact", ignoreCase = true) ||
                                       userInput.contains("weather", ignoreCase = true) ||
                                       userInput.contains("today", ignoreCase = true) ||
                                       userInput.contains("search", ignoreCase = true) ||
                                       userInput.contains("internet", ignoreCase = true) ||
                                       userInput.contains("latest", ignoreCase = true) ||
                                       userInput.contains("samachar", ignoreCase = true) ||
                                       userInput.contains("khabar", ignoreCase = true) ||
                                       userInput.contains("hal chal", ignoreCase = true)

                if (isSearchRequired) {
                    speechCoordinator.speakText("Searching real-time news and facts...")
                }
                
                // Attempt to process via the new Cognitive Runtime
                var cognitiveRuntimeSucceeded = false
                try {
                    val voiceSessionManager = com.aris.voice.di.ArisServiceRegistry.get(com.aris.voice.runtime.IVoiceSessionManager::class.java)
                    val newRuntimeResult = voiceSessionManager.processInput(userInput)
                    
                    if (newRuntimeResult is com.aris.voice.core.ArisResult.Success<*>) {
                        cognitiveRuntimeSucceeded = true
                    } else if (newRuntimeResult is com.aris.voice.core.ArisResult.Failure) {
                        Log.e("ConvAgent", "Cognitive Runtime returned failure: ${newRuntimeResult.error.message}")
                    }
                } catch (e: Exception) {
                    Log.e("ConvAgent", "Cognitive Runtime threw an exception: ${e.message}", e)
                }

                if (cognitiveRuntimeSucceeded) {
                    visualFeedbackManager.hideThinkingIndicator()
                    
                    // After the new runtime finishes processing and speaking, we restart listening if not in text mode
                    if (!isTextModeActive) {
                        startSttListeningEngine()
                    }
                    return@launch
                }

                // Fallback to the legacy runtime if the new runtime fails or returns an error
                Log.e("ConvAgent", "New Cognitive Runtime failed, falling back to legacy reasoning")
                val rawModelResponse = try {
                    getReasoningModelApiResponse(
                        conversationHistory,
                        enableSearch = isSearchRequired,
                        context = this@ConversationalAgentService
                    ) ?: defaultJsonResponse
                } catch (e: Exception) {
                    visualFeedbackManager.hideThinkingIndicator()
                    val message = e.message ?: ""
                    if (message.contains("ERROR_429")) {
                        val msg = "I've hit my API rate limit. Please try again in a few minutes."
                        trackMessage("model", msg, "error_message")
                        if (isTextModeActive) {
                            conversationHistory = addResponse("model", msg, conversationHistory)
                            speakAndThenListen(msg)
                        } else {
                            gracefulShutdown(msg, "api_limit")
                        }
                    } else {
                        val msg = "I'm having trouble connecting to my brain right now. Please try again later."
                        trackMessage("model", msg, "error_message")
                        if (isTextModeActive) {
                            conversationHistory = addResponse("model", msg, conversationHistory)
                            speakAndThenListen(msg)
                        } else {
                            gracefulShutdown(msg, "api_error")
                        }
                    }
                    return@launch
                }
                visualFeedbackManager.hideThinkingIndicator()

                val decision = parseModelResponse(rawModelResponse)
                Log.d("TTS_DEBUG", "Reply received from GeminiApi: -->${rawModelResponse}<--")
                when (decision.type) {
                    "Task" -> {
                        // Track task request
                        val taskBundle = android.os.Bundle().apply {
                            putString("task_instruction", decision.instruction.take(100)) // Limit length for analytics
                            putBoolean("agent_already_running", AgentService.isRunning)
                        }
                        firebaseAnalytics.logEvent("task_requested", taskBundle)
                        
                        if (AgentService.isRunning) {
                            firebaseAnalytics.logEvent("task_rejected_agent_busy", null)
                            val busyMessage = "I'm already working on '${AgentService.currentTask}'. Please let me finish that first, or you can ask me to stop it."
                            speakAndThenListen(busyMessage)
                            conversationHistory = addResponse("model", busyMessage, conversationHistory)
                            return@launch
                        }

                        if (!servicePermissionManager.isAccessibilityServiceEnabled()) {
                            speakAndThenListen(getString(R.string.accessibility_permission_needed_for_task))
                            conversationHistory = addResponse("model", R.string.accessibility_permission_needed_for_task.toString(), conversationHistory)
                            return@launch
                        }

                        Log.d("ConvAgent", "Model identified a task. Checking for clarification...")
                        // --- NEW: Check if the task instruction needs clarification ---
                        clarificationOverlayManager.clearQuestions()
                        if(freemiumManager.canPerformTask()){
                            Log.d("ConvAgent", "Allowance check passed. Proceeding with task.")

                            val SKIP_CLARIFICATION_PHRASES = setOf(
                                "just do it", "figure it out", "use your judgement", "bas kar do", "apne se kar do"
                            )
                            if (SKIP_CLARIFICATION_PHRASES.any { userInput.contains(it, ignoreCase = true) }) {
                                clarificationAttempts = maxClarificationAttempts
                            }

                            if (clarificationAttempts < maxClarificationAttempts) {
                                val (needsClarification, questions) = checkIfClarificationNeeded(
                                    decision.instruction
                                )
                                Log.d("ConcAgent", needsClarification.toString())
                                Log.d("ConcAgent", questions.toString())

                                if (needsClarification) {
                                    // Track clarification needed
                                    val clarificationBundle = android.os.Bundle().apply {
                                        putInt("clarification_attempt", clarificationAttempts + 1)
                                        putInt("questions_count", questions.size)
                                    }
                                    firebaseAnalytics.logEvent("task_clarification_needed", clarificationBundle)
                                    
                                    clarificationAttempts++
                                    clarificationOverlayManager.displayClarificationQuestions(questions)
                                    val questionToAsk = when (questions.size) {
                                        0 -> "Can you provide more details?"
                                        1 -> "Before I do that, ${questions[0]}"
                                        2 -> "I have two quick questions: First, ${questions[0]} Second, ${questions[1]}"
                                        else -> {
                                            val first = questions.dropLast(1).joinToString(". ")
                                            "I need a few details. $first. And finally, ${questions.last()}"
                                        }
                                    }
                                    Log.d(
                                        "ConvAgent",
                                        "Task needs clarification. Asking: '$questionToAsk' (Attempt $clarificationAttempts/$maxClarificationAttempts)"
                                    )
                                    conversationHistory = addResponse(
                                        "model",
                                        "Clarification needed for task: ${decision.instruction}",
                                        conversationHistory
                                    )
                                    trackMessage("model", questionToAsk, "clarification")
                                    speakAndThenListen(questionToAsk, false)
                                    
                                    // Set clarification timeout
                                    timeoutJob?.cancel()
                                    timeoutJob = serviceScope.launch {
                                        kotlinx.coroutines.delay(30_000)
                                        if (isRunning && !speechCoordinator.isCurrentlySpeaking()) {
                                            clarificationOverlayManager.clearQuestions()
                                            gracefulShutdown("I didn't hear back, so I'm canceling.", "clarification_timeout")
                                        }
                                    }
                                } else {
                                    Log.d(
                                        "ConvAgent",
                                        "Task is clear. Executing: ${decision.instruction}"
                                    )
                                    
                                    // Track task execution
                                    firebaseAnalytics.logEvent("task_executed", taskBundle)
                                    
                                    clarificationAttempts = 0 // Reset clarification attempts on successful clear task
                                    val originalInstruction = decision.instruction
                                    AgentService.start(applicationContext, originalInstruction)
                                    trackMessage("model", decision.reply, "task_confirmation")
                                    gracefulShutdown(decision.reply, "task_executed")
                                }
                            } else {
                                Log.d(
                                    "ConvAgent",
                                    "Max clarification attempts reached ($maxClarificationAttempts). Proceeding with task execution."
                                )
                                
                                // Track max clarification attempts reached
                                firebaseAnalytics.logEvent("task_executed_max_clarification", taskBundle)
                                
                                clarificationAttempts = 0 // Reset clarification attempts since we proceed
                                AgentService.start(applicationContext, decision.instruction)
                                trackMessage("model", decision.reply, "task_confirmation")
                                gracefulShutdown(decision.reply, "task_executed")
                            }
                        }else{
                            Log.w("ConvAgent", "User has no tasks remaining. Denying request.")
                            
                            // Track freemium limit reached
                            firebaseAnalytics.logEvent("task_rejected_freemium_limit", null)
                            
                            val freemiumManager = FreemiumManager()
                            val tasksRemaining = freemiumManager.getTasksRemaining() ?: 0
                            val upgradeMessage = if (tasksRemaining <= 3L && tasksRemaining > 0L) {
                                "Hey! Just a heads up, you only have $tasksRemaining free tasks left today."
                            } else {
                                "Hey! You've used all your free tasks for today. Please upgrade to Pro to unlock unlimited tasks."
                            }
                            conversationHistory = addResponse("model", upgradeMessage, conversationHistory)
                            trackMessage("model", upgradeMessage, "freemium_limit")
                            speakAndThenListen(upgradeMessage)
                        }
                    }
                    "KillTask" -> {
                        Log.d("ConvAgent", "Model requested to kill the running agent service.")
                        
                        // Track kill task request
                        val killTaskBundle = android.os.Bundle().apply {
                            putBoolean("task_was_running", AgentService.isRunning)
                        }
                        firebaseAnalytics.logEvent("kill_task_requested", killTaskBundle)
                        
                        if (AgentService.isRunning) {
                            AgentService.stop(applicationContext)
                            trackMessage("model", decision.reply, "kill_task_response")
                            gracefulShutdown(decision.reply, "task_killed")
                        } else {
                            val noTaskMessage = "There was no automation running, but I can help with something else."
                            trackMessage("model", noTaskMessage, "kill_task_response")
                            speakAndThenListen(noTaskMessage)
                        }
                    }
                    else -> { // Default to "Reply"
                        clarificationAttempts = 0 // Reset clarification attempts on normal reply
                        // Track conversational reply
                        val replyBundle = android.os.Bundle().apply {
                            putBoolean("conversation_ended", decision.shouldEnd)
                            putInt("reply_length", decision.reply.length)
                        }
                        firebaseAnalytics.logEvent("conversational_reply", replyBundle)
                        
                        if (decision.shouldEnd) {
                            Log.d("ConvAgent", "Model decided to end the conversation.")
                            firebaseAnalytics.logEvent("conversation_ended_by_model", null)
                            trackMessage("model", decision.reply, "farewell")
                            gracefulShutdown(decision.reply, "model_ended")
                        } else {
                            conversationHistory = addResponse("model", rawModelResponse, conversationHistory)
                            trackMessage("model", decision.reply, "reply")
                            speakAndThenListen(decision.reply)
                        }
                    }
                }
                
                // --- NEW: Perform incremental memory extraction from conversation ---
                try {
                    com.aris.voice.data.MemoryExtractor.extractIncrementally(
                        conversationHistory, 
                        com.aris.voice.data.MemoryManager.getInstance(this@ConversationalAgentService)
                    )
                } catch (e: Exception) {
                    Log.e("ConvAgent", "Incremental memory extraction failed", e)
                }

            } catch (e: Exception) {
                Log.e("ConvAgent", "Error processing user input: ${e.message}", e)
                
                // Trigger error state in state manager
                triggerServiceErrorState()
                
                // Track processing errors
                val errorBundle = android.os.Bundle().apply {
                    putString("error_message", e.message?.take(100) ?: "Unknown error")
                    putString("error_type", e.javaClass.simpleName)
                }
                firebaseAnalytics.logEvent("input_processing_error", errorBundle)
                
                when {
                    e is java.io.IOException -> speakAndThenListen("Network issue, please try again")
                    e.message?.contains("API key", ignoreCase = true) == true -> {
                        gracefulShutdown("API key issue. Please check settings.", "api_key_error")
                    }
                    else -> speakAndThenListen("Something went wrong, please repeat")
                }
            }
        }
    }

    //    private suspend fun getGroundedStepsForTask(taskInstruction: String): String {
//        Log.d("ConvAgent", "Performing grounded search for task: '$taskInstruction'")
//
//        // We create a specific prompt for the search.
//        val searchPrompt = """
//        Search the web and provide a concise, step-by-step guide for a human assistant to perform the following task on an Android phone: '$taskInstruction'.
//        Focus on the exact taps and settings involved.
//    """.trimIndent()
//
//        // Here we use the direct REST API call with search that we created previously.
//        // We need an instance of GeminiApi to call it.
//        // NOTE: You might need to adjust how you get your GeminiApi instance.
//        // For now, we'll assume we can create one or access it.
//        val geminiApi = GeminiApi("gemini-2.5-flash", ApiKeyManager, 2)
//
//        val searchResult = geminiApi.generateGroundedContent(searchPrompt)
//        Log.d("CONVO_SEARCH", searchResult.toString())
//        return if (!searchResult.isNullOrBlank()) {
//            searchResult
//        } else {
//            ""
//        }
//    }
    private suspend fun checkIfClarificationNeeded(instruction: String): Pair<Boolean, List<String>> {
        Log.d("ConvAgent", "Checking for clarification on instruction: '$instruction'")
        return try {
            val result = clarificationAgent.analyze(instruction, conversationHistory, this)
            val needsClarification = result.status == "NEEDS_CLARIFICATION"
            Pair(needsClarification, result.questions)
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error analyzing clarification", e)
            Pair(false, emptyList())
        }
    }

    private fun initializeConversation() {
        val memoryContextSection = if (MEMORY_ENABLED) {
            """
            Use these memories to answer the user's question with his personal data
            ### Memory Context Start ###
            {memory_context}
            ### Memory Context Ends ###
            """
        } else {
            """
            ### Memory Status ###
            Memory system is temporarily disabled. Aris cannot remember or learn from previous conversations at this time.
            some memories added by developers
            {memory_context}
            ### End Memory Status ###
            """
        }

        val systemPrompt = """
            You are a helpful voice assistant called Aris that can either have a conversation or ask an executor to execute tasks on the user's phone.
            The executor can speak, listen, see the screen, tap the screen, and basically use the phone as a normal human would.

            {agent_status_context}

            ### Current Screen Context ###
            {screen_context}
            ### End Screen Context ###

            Some Guideline:
            1. If the user ask you to do something creative, you do this task and be the most creative person in the world.
            2. If you know the user's name from the memories, refer to them by their name to make the conversation more personal and friendly as often as possible.
            3. Use the current screen context to better understand what the user is looking at and provide more relevant responses.
            4. If the user asks about something on the screen, you can reference the screen content directly.
            5. Always ask for clarification if the user's request is ambiguous or unclear.
            6. When the user ask to sing, shout or produce any sound, just generate text, we will sing it for you.
            7. You were created by Roshan Yadav. You are Aris, a highly advanced local AI voice assistant.
            8. Give a warning for the tasks related to banking, games, shopping and app with Canvas (no a11y tree) that you wont be able to do them properly but you will try your best.
            
            $memoryContextSection
            
            Analyze the user's request and respond ONLY with a single, valid JSON object.
            Do not include any text, notes, or explanations outside of the JSON object.
            The JSON object must have the following structure:
            
            {
              "Type": "String",
              "Reply": "String",
              "Instruction": "String",
              "Should End": "String"
            }

            Here are the rules for the JSON values:
            - "Type": Must be one of "Task", "Reply", or "KillTask".
              - Use "Task" if the user is asking you to DO something on the device (e.g., "open settings", "send a text to Mom").
              - Use "Reply" for conversational questions (e.g., "what's the weather?", "tell me a joke").
              - Use "KillTask" ONLY if an automation task is running and the user wants to stop it.
            - "Reply": The text to speak to the user. This is a confirmation for a "Task", or the direct answer for a "Reply".
            - "Instruction": The precise, literal instruction for the task agent. This field should be an empty string "" if the "Type" is not "Task".
            - "Should End": Must be either "Continue" or "Finished". Use "Finished" only when the conversation is naturally over.
        
            Current Time : {time_context}
            Current Location : {location_context}
        """.trimIndent()

        conversationHistory = addResponse("system", systemPrompt, emptyList())
    }

    private fun updateSystemPromptWithTime() {
        val currentPromptText = conversationHistory.firstOrNull()?.second
            ?.filterIsInstance<TextPart>()?.firstOrNull()?.text ?: return

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", java.util.Locale.getDefault())
        val formattedTime = dateFormat.format(java.util.Date())

        val timeRegex = Regex("Current Time : (\\{time_context\\}|.*)")
        val newTimeLine = "Current Time : $formattedTime"
        var updatedPromptText = timeRegex.replace(currentPromptText, newTimeLine)

        val locationContext = getCurrentLocationContext()
        val locationRegex = Regex("Current Location : (\\{location_context\\}|.*)")
        val newLocationLine = "Current Location : $locationContext"
        updatedPromptText = locationRegex.replace(updatedPromptText, newLocationLine)

        // Replace the first system message with the updated prompt
        conversationHistory = conversationHistory.toMutableList().apply {
            set(0, "system" to listOf(TextPart(updatedPromptText)))
        }
        Log.d("ConvAgent", "System prompt updated with time: $formattedTime and location: $locationContext")
    }
    private fun updateSystemPromptWithAgentStatus() {
        val currentPromptText = conversationHistory.firstOrNull()?.second
            ?.filterIsInstance<TextPart>()?.firstOrNull()?.text ?: return

        val agentStatusContext = if (AgentService.isRunning) {
            """
            IMPORTANT CONTEXT: An automation task is currently running in the background.
            Task Description: "${AgentService.currentTask}".
            If the user asks to stop, cancel, or kill this task, you MUST use the "KillTask" type.
            """.trimIndent()
        } else {
            "CONTEXT: No automation task is currently running."
        }

        val updatedPromptText = currentPromptText.replace("{agent_status_context}", agentStatusContext)

        // Replace the first system message with the updated prompt
        conversationHistory = conversationHistory.toMutableList().apply {
            set(0, "system" to listOf(TextPart(updatedPromptText)))
        }
        Log.d("ConvAgent", "System prompt updated with agent status: ${AgentService.isRunning}")
    }

    /**
     * Updates the system prompt with relevant memories and current screen context
     */
    @SuppressLint("NewApi")
    private suspend fun updateSystemPromptWithScreenContext() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        val timeSinceLastUpdate = System.currentTimeMillis() - lastScreenContextUpdate
        if (timeSinceLastUpdate < 30_000) return
        lastScreenContextUpdate = System.currentTimeMillis()
        try {

            val analysis = perception.analyze(all = true)
            Log.d("ConvAgent", "Screen analysis: ${analysis.uiRepresentation}")
            val currentPrompt = conversationHistory.firstOrNull()?.second
                ?.filterIsInstance<TextPart>()?.firstOrNull()?.text ?: return

            // Update screen context first with a clean, highly condensed list of visible texts
            val cleanScreenContext = cleanUiRepresentationForConversationalAgent(analysis.uiRepresentation)
            var updatedPrompt = currentPrompt.replace("{screen_context}", cleanScreenContext)

            // Check if memory is enabled before processing memories
            if (!MEMORY_ENABLED) {
                var userProfile = UserProfileManager(this@ConversationalAgentService)

                Log.d("ConvAgent", "Memory is disabled, skipping memory operations")
                Log.d("ConvAgent", "User name is ${userProfile.getName()}")
                // Replace memory context with disabled message

                updatedPrompt = updatedPrompt.replace("{memory_context}", "User name is ${userProfile.getName()}")
            } else {
                // Fetch user profile summary from our local Room / SQLite memory manager!
                val memoryManager = com.aris.voice.data.MemoryManager.getInstance(this@ConversationalAgentService)
                val localProfileSummary = memoryManager.getUserProfileSummary()
                
                val memoryContext = if (localProfileSummary.isNotBlank()) {
                    Log.d("ConvAgent", "Injecting local SQLite/Room memories into context")
                    localProfileSummary
                } else if (cachedMemories.isNotEmpty()) {
                    Log.d("ConvAgent", "Fallback: Injecting ${cachedMemories.size} cached memories into context")
                    val topMemories = cachedMemories.take(15)
                    topMemories.joinToString("\n") { memory -> 
                        "- ${memory.text} (Source: ${memory.source})" 
                    }
                } else {
                     Log.d("ConvAgent", "No local or cloud memories available yet")
                     "No memories stored in the local SQLite database yet."
                }
                updatedPrompt = updatedPrompt.replace("{memory_context}", memoryContext)
            }

            if (updatedPrompt.isNotEmpty()) {
                // Replace the first system message with updated prompt
                conversationHistory = conversationHistory.toMutableList().apply {
                    set(0, "system" to listOf(TextPart(updatedPrompt)))
                }
                Log.d("ConvAgent", "Updated system prompt with screen context and memories")
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error updating system prompt with memories and screen context", e)
        }
    }

    private fun getCurrentLocationContext(): String {
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse && !hasFine) {
            return "Location permissions not granted by user. If they ask about local weather or news, politely ask them to grant location permission first."
        }
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val providers = locationManager.getProviders(true)
            var bestLocation: android.location.Location? = null
            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            }
            if (bestLocation != null) {
                val lat = bestLocation.latitude
                val lng = bestLocation.longitude
                var cityAndCountry = ""
                if (android.location.Geocoder.isPresent()) {
                    try {
                        val geocoder = android.location.Geocoder(this, java.util.Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lng, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val locality = address.locality ?: address.subAdminArea ?: ""
                            val adminArea = address.adminArea ?: ""
                            val country = address.countryName ?: ""
                            cityAndCountry = listOfNotNull(locality, adminArea, country).filter { it.isNotBlank() }.joinToString(", ")
                        }
                    } catch (e: Exception) {
                        Log.e("ConvAgent", "Geocoder failed: ${e.message}")
                    }
                }
                return if (cityAndCountry.isNotBlank()) {
                    "$cityAndCountry (Coordinates: lat=$lat, lng=$lng)"
                } else {
                    "Coordinates lat=$lat, lng=$lng"
                }
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Failed to retrieve current location: ${e.message}")
        }
        return "Location permissions granted but unable to retrieve coordinates. Prompt user to enable GPS/location services if they ask for weather or news."
    }

    private fun cleanUiRepresentationForConversationalAgent(uiRep: String): String {
        if (uiRep.isBlank()) return "Empty Screen"
        val lines = uiRep.split("\n")
        val extractedTexts = mutableListOf<String>()
        val textRegex = Regex("""text:"([^"]+)"""")
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank() || trimmedLine.contains("[Start of page]") || trimmedLine.contains("[End of page]") || trimmedLine.contains("pixels above") || trimmedLine.contains("pixels below")) {
                continue
            }
            
            val match = textRegex.find(trimmedLine)
            if (match != null) {
                val value = match.groupValues[1].trim()
                if (value.isNotEmpty() && !extractedTexts.contains(value)) {
                    extractedTexts.add(value)
                }
            } else {
                // If it's a plain text line without "text:" format, e.g. bullet list or status
                val plainText = trimmedLine.replace(Regex("""^[*\t\s\-\[\]\d]+"""), "").trim()
                if (plainText.isNotEmpty() && !extractedTexts.contains(plainText) && plainText.length < 100) {
                    extractedTexts.add(plainText)
                }
            }
        }
        
        return if (extractedTexts.isEmpty()) {
            "Empty or transient screen state."
        } else {
            extractedTexts.take(30).joinToString(", ") // Limit to top 30 elements to save major tokens
        }
    }

    /**
     * Extracts current memory context from the system prompt
     */
    private fun extractCurrentMemoryContext(prompt: String): List<String> {
        return try {
            val memorySection = prompt.substringAfter("### Memory Context Start ###")
                .substringBefore("### Memory Context Ends ###")
                .trim()

            if (memorySection.isNotEmpty() && !memorySection.contains("{memory_context}")) {
                memorySection.lines()
                    .filter { it.trim().startsWith("- ") }
                    .map { it.trim().substring(2) } // Remove "- " prefix
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ConvAgent", "Error extracting current memory context", e)
            emptyList()
        }
    }
    private fun parseModelResponse(response: String): ModelDecision {
        try {
            var cleanResponse = response.trim()
            val startIndex = cleanResponse.indexOf('{')
            val endIndex = cleanResponse.lastIndexOf('}')
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                cleanResponse = cleanResponse.substring(startIndex, endIndex + 1)
            }
            
            val gson = com.google.gson.Gson()
            val mapType = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            val data: Map<String, String>? = gson.fromJson(cleanResponse, mapType)

            val type = data?.get("Type") ?: "Reply"
            val reply = data?.get("Reply") ?: ""
            val instruction = data?.get("Instruction") ?: ""
            val shouldEndStr = data?.get("Should End") ?: "Continue"
            val shouldEnd = shouldEndStr.equals("Finished", ignoreCase = true)
            
            // Add a fallback reply if the model provides an empty one for a conversational turn.
            val finalReply = if (reply.isEmpty() && type.equals("Reply", ignoreCase = true)) {
                "I'm not sure how to respond to that."
            } else {
                reply
            }

            return ModelDecision(type, finalReply, instruction, shouldEnd)
        } catch (e: org.json.JSONException) {
            Log.e("ConvAgent", "Error parsing JSON response, falling back. Response: $response", e)
            // Fallback for malformed JSON
            return ModelDecision(reply = "I seem to have gotten my thoughts tangled. Could you repeat that?")
        } catch (e: Exception) {
            Log.e("ConvAgent", "Generic error parsing model response, falling back. Response: $response", e)
            return ModelDecision(reply = "I had a minor issue processing that. Could you try again?")
        }
    }

    private fun updateNotificationState(state: String) {
        val text = when(state) {
            "listening" -> "🎙️ Listening..."
            "thinking" -> "🤔 Thinking..."
            "speaking" -> "💬 Speaking..."
            "task" -> "⚙️ Executing task..."
            else -> "A.R.I.S Active"
        }
        val stopIntent = Intent(this, ConversationalAgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("A.R.I.S Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun setServiceState(state: ArisState) {
        arisStateManager.setState(state)
        when (state) {
            ArisState.LISTENING -> updateNotificationState("listening")
            ArisState.PROCESSING -> updateNotificationState("thinking")
            ArisState.SPEAKING -> updateNotificationState("speaking")
            else -> updateNotificationState("active")
        }
    }

    private fun triggerServiceErrorState() {
        arisStateManager.triggerErrorState()
        updateNotificationState("error")
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ConversationalAgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("A.R.I.S Active")
            .setContentText("Listening for your commands...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause, // Using built-in pause icon as stop button
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Conversational Agent Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Displays a list of futuristic-styled clarification questions at the top of the screen.
     * Each question animates in from the top with a fade-in effect.
     *
     * @param questions The list of question strings to display.
     */


    private suspend fun gracefulShutdown(exitMessage: String? = null, endReason: String = "graceful") {
        // Track graceful shutdown
        val shutdownBundle = android.os.Bundle().apply {
            putBoolean("had_exit_message", exitMessage != null)
            putInt("conversation_length", conversationHistory.size)
            putBoolean("text_mode_used", isTextModeActive)
            putInt("clarification_attempts", clarificationAttempts)
            putInt("stt_error_attempts", sttErrorAttempts)
        }
        firebaseAnalytics.logEvent("conversation_ended_gracefully", shutdownBundle)
        
        // Track conversation end in Firebase

        conversationId?.let { 
            firebaseManager.trackConversationEnd(it, endReason, conversationHistory.size, isTextModeActive, clarificationAttempts, sttErrorAttempts)
        }
        
        visualFeedbackManager.hideSmallDeltaGlow()
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideInputBox()

        if (exitMessage != null) {
                speechCoordinator.speakText(exitMessage)
                while(speechCoordinator.isCurrentlySpeaking()) {
                    kotlinx.coroutines.delay(100)
                }
                kotlinx.coroutines.delay(500)
            }
            // 1. Extract memories from the conversation before ending
            // Removed old memory extraction logic
            triggerMemoryGeneration()
            
            // Go to home screen automatically when task is completed (Requirement 5)
            if (endReason == "task_executed" || endReason == "command" || endReason == "task_killed") {
                try {
                    ScreenInteractionService.instance?.performHome()
                } catch (e: Exception) {
                    Log.e("ConvAgent", "Failed to execute performHome: ${e.message}")
                }
            }
            
            // 3. Stop the service
            stopSelf()

    }

    /**
     * Immediately stops all TTS, STT, and background tasks, hides all UI, and stops the service.
     * This is used for forceful termination, like an outside tap.
     */
    private suspend fun instantShutdown() {
        // Track instant shutdown
        val instantShutdownBundle = android.os.Bundle().apply {
            putInt("conversation_length", conversationHistory.size)
            putBoolean("text_mode_used", isTextModeActive)
            putInt("clarification_attempts", clarificationAttempts)
            putInt("stt_error_attempts", sttErrorAttempts)
        }
        firebaseAnalytics.logEvent("conversation_ended_instantly", instantShutdownBundle)
        
        // Track conversation end in Firebase
        conversationId?.let { 
            firebaseManager.trackConversationEnd(it, "instant", conversationHistory.size, isTextModeActive, clarificationAttempts, sttErrorAttempts)
        }
        
        Log.d("ConvAgent", "Instant shutdown triggered by user.")
        withContext(Dispatchers.Main) {
            speechCoordinator.stopSpeaking()
            speechCoordinator.stopListening()
            visualFeedbackManager.hideSmallDeltaGlow()
            visualFeedbackManager.hideTtsWave()
            visualFeedbackManager.hideTranscription()
            visualFeedbackManager.hideSpeakingOverlay()
            visualFeedbackManager.hideInputBox()
            clarificationOverlayManager.clearQuestions()
        }

        clarificationOverlayManager.clearQuestions()
        // Make a thread-safe copy of the conversation history.
        // Removed old memory extraction logic
        triggerMemoryGeneration()
        
        // serviceScope.cancel() removed to prevent pending writes from failing
        isRunning = false
        updateHomeScreenWidget()
        stopSelf()
    }


    /**
     * Tracks individual messages in the conversation.
     * Fire and forget operation.
     */
    private fun trackMessage(role: String, message: String, messageType: String = "text") {
        conversationId?.let {
            firebaseManager.trackMessage(it, role, message, messageType)
        }
    }

    override fun onDestroy() {
        firebaseManager.removeListener()
        super.onDestroy()
        Log.d("ConvAgent", "Service onDestroy")
        memoryScope.cancel()
        serviceScope.cancel()
        
        // Ensure microphone and speech systems are completely stopped
        speechCoordinator.stopSpeaking()
        speechCoordinator.stopListening()
        speechCoordinator.shutdown()

        overlayManager.stopObserving()
        firebaseAnalytics.logEvent("conversational_agent_destroyed", null)
        
        // Track conversation end if not already tracked
        if (conversationId != null) {
            conversationId?.let { 
                firebaseManager.trackConversationEnd(it, "service_destroyed", conversationHistory.size, isTextModeActive, clarificationAttempts, sttErrorAttempts)
            }
        }
        
        clarificationOverlayManager.clearQuestions()
        client.dispatcher.cancelAll()
        serviceScope.cancel()
        isRunning = false
        updateHomeScreenWidget()
        
        // Stop state monitoring and set final state
        arisStateManager.setState(ArisState.IDLE)
        arisStateManager.stopMonitoring()
        visualFeedbackManager.hideSmallDeltaGlow()
        visualFeedbackManager.hideSpeakingOverlay() // <-- ADD THIS LINE
        // USE the new manager to hide the wave and transcription view
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideInputBox()

    }

    override fun onBind(intent: Intent?): IBinder? = null



    private val memoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun triggerMemoryGeneration() {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val userEmail = currentUser?.email

        if (userEmail == null) {
            Log.w("ConvAgent", "User email not found, cannot trigger memory generation")
            return
        }

        Log.d("ConvAgent", "Triggering memory generation for email: $userEmail")

        try {
            val json = JSONObject()
            json.put("email", userEmail)

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(BuildConfig.GCLOUD_PROXY_URL)
                .addHeader("X-API-Key", BuildConfig.GCLOUD_PROXY_URL_KEY)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e("ConvAgent", "Memory generation request failed", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            Log.e("ConvAgent", "Memory generation request failed with code: ${it.code}")
                        } else {
                            Log.d("ConvAgent", "Memory generation request sent successfully. Response: ${it.body?.string()}")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("ConvAgent", "Memory generation request failed to start", e)
        }
    }

    private fun updateHomeScreenWidget() {
        try {
            val context = applicationContext
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val thisWidget = android.content.ComponentName(context, com.aris.voice.ArisWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val intent = Intent(context, com.aris.voice.ArisWidgetProvider::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("ConvAgent", "Failed to update HomeScreen Widget", e)
        }
    }

}