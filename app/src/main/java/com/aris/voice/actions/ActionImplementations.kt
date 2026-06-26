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
    private val actionAdapter: com.aris.voice.adapters.IActionAdapter
) : IActionExecutor, IGestureExecutor, ISystemActionExecutor, IAppLauncher {

    private val TAG = "ArisActionImpl"

    // IActionExecutor
    override suspend fun executeStep(step: PlanStep): ArisResult<String> {
        Log.d(TAG, "Executing plan step: ${step.stepId} - ${step.description}")
        return try {
            val result = actionAdapter.executeAction(step.requiredCapability, step.arguments)
            when (result) {
                is ArisResult.Success -> ArisResult.Success(result.value.toString())
                is ArisResult.Failure -> ArisResult.Failure(result.error)
            }
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("STEP_EXECUTION_FAILED", "Failed executing ${step.requiredCapability}", e))
        }
    }

    // IGestureExecutor
    override suspend fun performClick(x: Int, y: Int): ArisResult<Unit> {
        return try {
            val result = actionAdapter.executeAction("CLICK", mapOf("x" to x, "y" to y))
            if (result is ArisResult.Success) ArisResult.Success(Unit) else ArisResult.Failure((result as ArisResult.Failure).error)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("CLICK_FAILED", "Failed click gesture", e))
        }
    }

    override suspend fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): ArisResult<Unit> {
        return try {
            val result = actionAdapter.executeAction("SWIPE", mapOf("startX" to startX, "startY" to startY, "endX" to endX, "endY" to endY))
            if (result is ArisResult.Success) ArisResult.Success(Unit) else ArisResult.Failure((result as ArisResult.Failure).error)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("SWIPE_FAILED", "Failed swipe gesture", e))
        }
    }

    override suspend fun performScroll(direction: String): ArisResult<Unit> {
        return try {
            val result = actionAdapter.executeAction("SCROLL", mapOf("direction" to direction))
            if (result is ArisResult.Success) ArisResult.Success(Unit) else ArisResult.Failure((result as ArisResult.Failure).error)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("SCROLL_FAILED", "Failed scroll gesture", e))
        }
    }

    override suspend fun performType(text: String): ArisResult<Unit> {
        return try {
            val result = actionAdapter.executeAction("TYPE", mapOf("text" to text))
            if (result is ArisResult.Success) ArisResult.Success(Unit) else ArisResult.Failure((result as ArisResult.Failure).error)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("TYPE_FAILED", "Failed type keyboard gesture", e))
        }
    }

    // ISystemActionExecutor
    override suspend fun performBack(): ArisResult<Unit> {
        return try {
            val result = actionAdapter.executeAction("BACK", emptyMap())
            if (result is ArisResult.Success) ArisResult.Success(Unit) else ArisResult.Failure((result as ArisResult.Failure).error)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("BACK_FAILED", "Failed back action", e))
        }
    }

    override suspend fun performHome(): ArisResult<Unit> {
        return try {
            val result = actionAdapter.executeAction("HOME", emptyMap())
            if (result is ArisResult.Success) ArisResult.Success(Unit) else ArisResult.Failure((result as ArisResult.Failure).error)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("HOME_FAILED", "Failed home action", e))
        }
    }

    override suspend fun toggleSetting(settingKey: String, enable: Boolean): ArisResult<Unit> {
        return try {
            val result = actionAdapter.executeAction("TOGGLE_SETTING", mapOf("settingKey" to settingKey, "enable" to enable))
            if (result is ArisResult.Success) ArisResult.Success(Unit) else ArisResult.Failure((result as ArisResult.Failure).error)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("TOGGLE_SETTING_FAILED", "Failed to toggle setting $settingKey", e))
        }
    }

    override suspend fun controlMedia(command: String): ArisResult<Unit> {
        return try {
            val result = actionAdapter.executeAction("CONTROL_MEDIA", mapOf("command" to command))
            if (result is ArisResult.Success) ArisResult.Success(Unit) else ArisResult.Failure((result as ArisResult.Failure).error)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.ExecutionError("CONTROL_MEDIA_FAILED", "Failed to control media with command $command", e))
        }
    }

    // IAppLauncher
    override suspend fun launchApp(packageName: String): ArisResult<Unit> {
        return try {
            val result = actionAdapter.executeAction("LAUNCH_APP", mapOf("packageName" to packageName))
            if (result is ArisResult.Success) ArisResult.Success(Unit) else ArisResult.Failure((result as ArisResult.Failure).error)
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
