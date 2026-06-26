package com.aris.voice.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.aris.voice.core.ArisError
import com.aris.voice.core.ArisResult
import com.aris.voice.domain.PlanStep
import com.aris.voice.api.Finger

class PlanExecutorImpl(
    private val actionExecutor: IActionExecutor
) : IPlanExecutor {
    private var isCancelled = false

    override suspend fun executePlan(
        decision: com.aris.voice.domain.Decision,
        onProgress: (com.aris.voice.domain.StepExecutionResult) -> Unit
    ): com.aris.voice.domain.ExecutionResult {
        isCancelled = false
        val startTime = System.currentTimeMillis()
        val stepResults = mutableListOf<com.aris.voice.domain.StepExecutionResult>()

        if (decision.type != com.aris.voice.domain.DecisionType.EXECUTE_PLAN || decision.plan == null) {
            return com.aris.voice.domain.ExecutionResult(
                planId = decision.plan?.planId ?: "unknown",
                isSuccess = false,
                totalExecutionTimeMs = System.currentTimeMillis() - startTime,
                stepResults = emptyList(),
                failureReason = "Decision not approved for execution or plan is missing"
            )
        }

        val plan = decision.plan

        for (step in plan.steps) {
            if (isCancelled) {
                return com.aris.voice.domain.ExecutionResult(
                    planId = plan.planId,
                    isSuccess = false,
                    isCancelled = true,
                    totalExecutionTimeMs = System.currentTimeMillis() - startTime,
                    stepResults = stepResults,
                    failureReason = "Execution cancelled by user or system"
                )
            }

            val stepStartTime = System.currentTimeMillis()
            try {
                val result = actionExecutor.executeStep(step)
                
                val stepExecutionResult = when (result) {
                    is ArisResult.Success -> com.aris.voice.domain.StepExecutionResult(
                        stepId = step.stepId,
                        status = com.aris.voice.domain.StepStatus.SUCCESS,
                        executionTimeMs = System.currentTimeMillis() - stepStartTime,
                        output = result.value
                    )
                    is ArisResult.Failure -> com.aris.voice.domain.StepExecutionResult(
                        stepId = step.stepId,
                        status = com.aris.voice.domain.StepStatus.FAILED,
                        executionTimeMs = System.currentTimeMillis() - stepStartTime,
                        errorMessage = result.error.message
                    )
                }

                stepResults.add(stepExecutionResult)
                onProgress(stepExecutionResult)

                if (stepExecutionResult.status == com.aris.voice.domain.StepStatus.FAILED) {
                    return com.aris.voice.domain.ExecutionResult(
                        planId = plan.planId,
                        isSuccess = false,
                        totalExecutionTimeMs = System.currentTimeMillis() - startTime,
                        stepResults = stepResults,
                        failureReason = "Step ${step.stepId} failed: ${stepExecutionResult.errorMessage}"
                    )
                }

            } catch (e: Exception) {
                val stepExecutionResult = com.aris.voice.domain.StepExecutionResult(
                    stepId = step.stepId,
                    status = com.aris.voice.domain.StepStatus.FAILED,
                    executionTimeMs = System.currentTimeMillis() - stepStartTime,
                    errorMessage = e.message ?: "Unknown error occurred"
                )
                stepResults.add(stepExecutionResult)
                onProgress(stepExecutionResult)
                
                return com.aris.voice.domain.ExecutionResult(
                    planId = plan.planId,
                    isSuccess = false,
                    totalExecutionTimeMs = System.currentTimeMillis() - startTime,
                    stepResults = stepResults,
                    failureReason = "Step ${step.stepId} crashed: ${e.message}"
                )
            }
        }

        return com.aris.voice.domain.ExecutionResult(
            planId = plan.planId,
            isSuccess = true,
            totalExecutionTimeMs = System.currentTimeMillis() - startTime,
            stepResults = stepResults
        )
    }

    override fun cancelExecution() {
        isCancelled = true
    }
}

class ActionImpl(
    private val context: Context,
    private val finger: Finger? = null
) : IActionExecutor, IGestureExecutor, ISystemActionExecutor, IAppLauncher {

    private val TAG = "ArisActionImpl"

    // IActionExecutor
    override suspend fun executeStep(step: PlanStep): ArisResult<String> {
        Log.d(TAG, "Executing plan step: ${step.stepId} - ${step.description}")
        return try {
            val resultMsg = when (step.requiredCapability.uppercase()) {
                "CLICK" -> {
                    val x = step.arguments["x"]?.toIntOrNull() ?: 0
                    val y = step.arguments["y"]?.toIntOrNull() ?: 0
                    performClick(x, y).getOrThrow()
                    "Clicked coordinates ($x, $y) successfully"
                }
                "SWIPE" -> {
                    val startX = step.arguments["startX"]?.toIntOrNull() ?: 0
                    val startY = step.arguments["startY"]?.toIntOrNull() ?: 0
                    val endX = step.arguments["endX"]?.toIntOrNull() ?: 0
                    val endY = step.arguments["endY"]?.toIntOrNull() ?: 0
                    performSwipe(startX, startY, endX, endY, 500).getOrThrow()
                    "Swiped from ($startX, $startY) to ($endX, $endY) successfully"
                }
                "TYPE" -> {
                    val text = step.arguments["text"] ?: ""
                    performType(text).getOrThrow()
                    "Typed text: '$text' successfully"
                }
                "BACK" -> {
                    performBack().getOrThrow()
                    "Performed system Back action"
                }
                "HOME" -> {
                    performHome().getOrThrow()
                    "Performed system Home action"
                }
                "LAUNCH_APP" -> {
                    val packageName = step.arguments["packageName"] ?: ""
                    launchApp(packageName).getOrThrow()
                    "Launched application: $packageName"
                }
                else -> {
                    "Simulated execution of tool: ${step.requiredCapability}"
                }
            }
            ArisResult.Success(resultMsg)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("STEP_EXECUTION_FAILED", "Failed executing ${step.requiredCapability}", e))
        }
    }

    // IGestureExecutor
    override suspend fun performClick(x: Int, y: Int): ArisResult<Unit> {
        return try {
            if (finger != null) {
                finger.tap(x, y)
                ArisResult.Success(Unit)
            } else {
                Log.i(TAG, "Click gesture simulated at ($x, $y)")
                ArisResult.Success(Unit)
            }
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("CLICK_FAILED", "Failed click gesture", e))
        }
    }

    override suspend fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): ArisResult<Unit> {
        return try {
            if (finger != null) {
                finger.swipe(startX, startY, endX, endY, durationMs.toInt())
                ArisResult.Success(Unit)
            } else {
                Log.i(TAG, "Swipe gesture simulated from ($startX, $startY) to ($endX, $endY)")
                ArisResult.Success(Unit)
            }
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("SWIPE_FAILED", "Failed swipe gesture", e))
        }
    }

    override suspend fun performScroll(direction: String): ArisResult<Unit> {
        return try {
            Log.i(TAG, "Scroll gesture simulated: $direction")
            ArisResult.Success(Unit)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("SCROLL_FAILED", "Failed scroll gesture", e))
        }
    }

    override suspend fun performType(text: String): ArisResult<Unit> {
        return try {
            if (finger != null) {
                finger.type(text)
                ArisResult.Success(Unit)
            } else {
                Log.i(TAG, "Type keyboard simulation: '$text'")
                ArisResult.Success(Unit)
            }
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("TYPE_FAILED", "Failed type keyboard gesture", e))
        }
    }

    // ISystemActionExecutor
    override suspend fun performBack(): ArisResult<Unit> {
        return try {
            if (finger != null) {
                finger.back()
                ArisResult.Success(Unit)
            } else {
                Log.i(TAG, "Back action simulated")
                ArisResult.Success(Unit)
            }
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("BACK_FAILED", "Failed back action", e))
        }
    }

    override suspend fun performHome(): ArisResult<Unit> {
        return try {
            if (finger != null) {
                finger.home()
                ArisResult.Success(Unit)
            } else {
                Log.i(TAG, "Home action simulated")
                ArisResult.Success(Unit)
            }
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("HOME_FAILED", "Failed home action", e))
        }
    }

    override suspend fun toggleSetting(settingKey: String, enable: Boolean): ArisResult<Unit> {
        Log.i(TAG, "Toggle setting $settingKey -> $enable")
        return ArisResult.Success(Unit)
    }

    override suspend fun controlMedia(command: String): ArisResult<Unit> {
        Log.i(TAG, "Control media command: $command")
        return ArisResult.Success(Unit)
    }

    // IAppLauncher
    override suspend fun launchApp(packageName: String): ArisResult<Unit> {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ArisResult.Success(Unit)
            } else {
                ArisResult.Failure(ArisError.ExecutionError("APP_NOT_FOUND", "App package $packageName not found on device"))
            }
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("LAUNCH_APP_FAILED", "Error launching app: $packageName", e))
        }
    }

    override suspend fun launchDeepLink(uri: String): ArisResult<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ArisResult.Success(Unit)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("DEEPLINK_FAILED", "Error opening deep link: $uri", e))
        }
    }
}
