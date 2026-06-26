package com.aris.voice.v2

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.aris.voice.v2.actions.ActionExecutor
import com.aris.voice.v2.fs.FileSystem
import com.aris.voice.v2.llm.GeminiApi
import com.aris.voice.v2.llm.GeminiMessage
import com.aris.voice.v2.message_manager.MemoryManager
import com.aris.voice.v2.perception.Perception
import com.aris.voice.v2.perception.ScreenAnalysis
import com.aris.voice.utilities.SpeechCoordinator
import com.aris.voice.overlay.OverlayDispatcher
import com.aris.voice.overlay.OverlayPriority
import com.aris.voice.overlay.OverlayPosition
import com.aris.voice.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private enum class StepOutcome {
    SUCCESS,
    RETRY,
    BREAK
}

/**
 * The main conductor of the agent.
 * This class owns all the necessary components and runs the primary SENSE -> THINK -> ACT loop.
 */
@RequiresApi(Build.VERSION_CODES.R)
class Agent(
    private val settings: AgentSettings,
    private val memoryManager: MemoryManager,
    private val perception: Perception,
    private val llmApi: GeminiApi,
    private val actionExecutor: ActionExecutor,
    private val fileSystem: FileSystem,
    private val context: Context
) {
    // The agent's internal state, which is updated at each step.
    val state: AgentState = AgentState()
    private val TAG = "AgentV2"
    
    // Speech coordinator for voice notifications
    private val speechCoordinator = SpeechCoordinator.getInstance(context)

    // A complete, long-term record of the entire session.
    val history: AgentHistoryList<Unit> = AgentHistoryList()

    // Screen snapshot memory (last 3 steps)
    private val lastScreenStates = mutableListOf<ScreenAnalysis>()

    // Action summary history for stuck detection
    private val actionHistory = mutableListOf<String>()

    private fun calculateDynamicMaxSteps(task: String): Int {
        val lower = task.lowercase()
        return when {
            // Simple opening/dialing/setting single apps
            lower.contains("open") || lower.contains("launch") || lower.contains("call") || lower.contains("dial") || lower.contains("screenshot") -> 15
            // Complex purchasing, booking or food ordering over many forms
            lower.contains("order") || lower.contains("buy") || lower.contains("book") || lower.contains("food") || lower.contains("fill") || lower.contains("search and") -> 60
            // General multi-step tasks
            else -> 30
        }
    }

    private fun checkBatteryCriticallyLow(): Boolean {
        try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                context.registerReceiver(null, ifilter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) {
                val batteryPct = level * 100f / scale.toFloat()
                val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
                val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
                return batteryPct <= 5.0f && !isCharging
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading battery level", e)
        }
        return false
    }

    /**
     * The main entry point to start the agent's execution loop.
     */
    suspend fun run(initialTask: String, maxSteps: Int = settings.maxSteps): Boolean {
        memoryManager.addNewTask(initialTask)
        state.stopped = false
        Log.d(TAG, "--- Agent starting task: '$initialTask' ---")

        val calculatedMaxSteps = if (maxSteps == settings.maxSteps) {
            calculateDynamicMaxSteps(initialTask)
        } else {
            maxSteps
        }

        while (!state.stopped && state.nSteps <= calculatedMaxSteps) {
            while (AgentService.isPaused) {
                kotlinx.coroutines.delay(500)
            }
            val stepResult = withTimeoutOrNull(settings.stepTimeout * 1000L) {
                Log.d(TAG,"\n--- Step ${state.nSteps}/$calculatedMaxSteps ---")

                // Mid-execution safety battery check
                if (state.nSteps > 0 && state.nSteps % 10 == 0 && checkBatteryCriticallyLow()) {
                    Log.w(TAG, "Battery critically low! Pausing execution.")
                    AgentService.isPaused = true
                    speechCoordinator.speakToUser("Battery is critically low at 5 percent with no power input. Execution has been auto-paused to save your device.")
                    return@withTimeoutOrNull StepOutcome.RETRY
                }

                // 1. SENSE: Observe the current state of the screen asynchronously.
                Log.d(TAG, "👀 Sensing screen state...")
                val screenState = withContext(Dispatchers.Default) {
                    perception.analyze()
                }

                // Update screen snapshot history
                lastScreenStates.add(screenState)
                if (lastScreenStates.size > 3) {
                    lastScreenStates.removeAt(0)
                }

                val hasCaptcha = screenState.uiRepresentation.contains("captcha", ignoreCase = true) || 
                                 screenState.uiRepresentation.contains("I'm not a robot", ignoreCase = true) ||
                                 screenState.uiRepresentation.contains("verify you are human", ignoreCase = true) ||
                                 screenState.uiRepresentation.contains("please solve puzzle", ignoreCase = true)

                if (hasCaptcha) {
                    Log.d(TAG, "CAPTCHA detected, pausing task.")
                    AgentService.isPaused = true
                    speechCoordinator.speakToUser("CAPTCHA verification detected. Please solve it manually, then swipe or resume to continue execution.")
                    return@withTimeoutOrNull StepOutcome.RETRY
                }

                // 2. THINK (Prepare Prompt): Update memory with results of the last step and build prompt
                Log.d(TAG, "🧠 Preparing prompt...")
                memoryManager.createStateMessage(
                    modelOutput = state.lastModelOutput,
                    result = state.lastResult,
                    stepInfo = AgentStepInfo(state.nSteps, calculatedMaxSteps),
                    screenState = screenState
                )

                // 3. THINK (Get Decision): Send messages of current session to LLM
                Log.d(TAG, "🤔 Asking LLM for next action...")
                val messages = memoryManager.getMessages()
                var agentOutput: AgentOutput? = null
                try {
                    agentOutput = llmApi.generateAgentOutput(messages)
                } catch (e: Exception) {
                    if (e.message?.contains("429") == true || e.message?.contains("rate limit") == true) {
                        Log.w(TAG, "Rate limit hit. Rotating key and backing off...")
                        delay(1000)
                        return@withTimeoutOrNull StepOutcome.RETRY
                    } else {
                        Log.e(TAG, "Error seeking LLM output", e)
                    }
                }

                // Handle LLM JSON Failures
                if (agentOutput == null) {
                    Log.d(TAG, "❌ LLM failed to return a valid structured action. Retrying step...")
                    state.consecutiveFailures++
                    memoryManager.addContextMessage(GeminiMessage(text = "System Note: Your previous response was either not valid JSON or null. Always respond with correctly formatted JSON schema action."))
                    
                    if (state.consecutiveFailures >= settings.maxFailures) {
                        Log.d(TAG, "❌ Agent failed too many times consecutively. Exit.")
                        speechCoordinator.speakToUser("Task stopped because agent was unable to organize instructions.")
                        return@withTimeoutOrNull StepOutcome.BREAK
                    }
                    delay(1000)
                    return@withTimeoutOrNull StepOutcome.RETRY
                }
                
                state.consecutiveFailures = 0
                state.lastModelOutput = agentOutput
                Log.d(TAG, agentOutput.toString())
                Log.d(TAG, "🤖 LLM decided: ${agentOutput.nextGoal}")

                // Stuck / Loop detection
                val currentActionSig = (agentOutput.action.joinToString(",") { it::class.simpleName ?: "" }) + ":" + agentOutput.nextGoal
                actionHistory.add(currentActionSig)
                if (actionHistory.size > 3) {
                    actionHistory.removeAt(0)
                }
                if (actionHistory.size == 3 && actionHistory.distinct().size == 1) {
                    Log.w(TAG, "Agent stuck loop detected! Injecting recovery advice.")
                    memoryManager.addContextMessage(GeminiMessage(
                        text = "System Note: It seems you've repeated the exact same goal and action sequence 3 times in a row. The UI state is stuck. Do NOT repeat the same actions again. Try a different strategy, such as scrolling alternative directions, clicking parent tags, typing search queries, or pressing back."
                    ))
                    actionHistory.clear()
                }

                // Show thoughts if overlay enabled
                val sharedPrefs = context.getSharedPreferences("ArisSettings", Context.MODE_PRIVATE)
                if (sharedPrefs.getBoolean(SettingsActivity.KEY_SHOW_THOUGHTS, false)) {
                    val thoughtText = buildString {
                        agentOutput.thinking?.let { if (it.isNotEmpty()) append("Thinking: $it\n") }
                        agentOutput.memory?.let { if (it.isNotEmpty()) append("Memory: $it\n") }
                        agentOutput.nextGoal?.let { if (it.isNotEmpty()) append("Next Goal: $it") }
                    }.trim()

                    if (thoughtText.isNotEmpty()) {
                        OverlayDispatcher.show(
                            text = thoughtText,
                            priority = OverlayPriority.TASKS,
                            duration = 8000L,
                            position = OverlayPosition.TOP
                        )
                    }
                }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val progress = (state.nSteps * 100) / calculatedMaxSteps
                val notification = androidx.core.app.NotificationCompat.Builder(context, "AgentServiceChannelV2")
                    .setContentTitle("Executing: ${AgentService.currentTask?.take(30)}")
                    .setContentText("Step ${state.nSteps}/$calculatedMaxSteps: ${agentOutput.nextGoal?.take(40)}")
                    .setProgress(100, progress, false)
                    .setSmallIcon(com.aris.voice.R.drawable.ic_launcher_foreground)
                    .setOngoing(true)
                    .build()
                notificationManager.notify(14, notification)

                // 4. ACT: Execute actions sequentially
                Log.d(TAG, "💪 Executing actions...")
                val actionResults = mutableListOf<ActionResult>()
                var currentScreenState = screenState
                
                for (action in agentOutput.action) {
                    val result = actionExecutor.execute(
                        action,
                        currentScreenState,
                        context,
                        fileSystem,
                        stepInitialScreenState = screenState
                    )
                    actionResults.add(result)
                    Log.d(TAG, "  - Action '${action::class.simpleName}' executed. Result detail: ${result.longTermMemory ?: result.error ?: "OK"}")

                    if (result.error != null) {
                        Log.d(TAG, "  - 🛑 Action failed. Halting batch sequence execution.")
                        break
                    }

                    if (action != agentOutput.action.last()) {
                        Log.d(TAG, "👀 Re-sensing screen changes during batch action sequence...")
                        delay(50)
                        currentScreenState = withContext(Dispatchers.Default) {
                            perception.analyze()
                        }
                    }
                }
                state.lastResult = actionResults

                // 5. RECORD in session history
                history.addItem(
                    AgentHistory(
                        modelOutput = agentOutput,
                        result = actionResults,
                        state = screenState,
                        metadata = null
                    )
                )

                // Check for Task Completion
                if (actionResults.any { it.isDone == true }) {
                    Log.d(TAG, "✅ Agent completed the task.")
                    speechCoordinator.speakToUser("Task completed successfully.")
                    state.stopped = true
                }

                StepOutcome.SUCCESS
            }

            if (stepResult == null) {
                Log.w(TAG, "⏱️ Step ${state.nSteps} timed out after ${settings.stepTimeout}s")
                state.consecutiveFailures++
                if (state.consecutiveFailures >= settings.maxFailures) {
                    speechCoordinator.speakToUser("Stopping task due to repeated service timeouts.")
                    break
                }
                memoryManager.addContextMessage(GeminiMessage(
                    text = "System: A timeout occurred. The screen or action might be slow to respond. Please review the visual elements meticulously."
                ))
            } else if (stepResult == StepOutcome.RETRY) {
                continue
            } else if (stepResult == StepOutcome.BREAK) {
                break
            } else {
                state.consecutiveFailures = 0
            }

            state.nSteps++
            delay(50)
        }

        if (state.nSteps > calculatedMaxSteps) {
            Log.d(TAG, "--- 🏁 Agent reached max step budget ($calculatedMaxSteps). Stopping. ---")
            speechCoordinator.speakToUser("Agent limit reached. Task execution halted.")
        } else {
            Log.d(TAG, "--- 🏁 Agent run finalized. ---")
        }

        return history.history.lastOrNull()?.result?.any { it.isDone == true } == true
    }
}
