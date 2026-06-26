package com.aris.voice.brain

import com.aris.voice.core.ArisError
import com.aris.voice.core.ArisResult
import com.aris.voice.domain.ArisContext
import com.aris.voice.domain.Decision
import com.aris.voice.domain.DeviceContext
import com.aris.voice.domain.UserIntent
import com.aris.voice.domain.Plan
import com.aris.voice.domain.PlanStep
import com.aris.voice.domain.RiskLevel
import com.aris.voice.domain.StepStatus
import java.util.UUID

class IntentAnalyzerImpl : IIntentAnalyzer {

    private val aliasProvider = com.aris.voice.brain.intent.AppAliasProvider()
    
    private val pipeline = listOf(
        com.aris.voice.brain.intent.IntentNormalizer(),
        com.aris.voice.brain.intent.IntentTokenizer(),
        com.aris.voice.brain.intent.IntentClassifier(),
        com.aris.voice.brain.intent.EntityExtractor(aliasProvider),
        com.aris.voice.brain.intent.ParameterExtractor(),
        com.aris.voice.brain.intent.CapabilityDetector(),
        com.aris.voice.brain.intent.ConfidenceCalculator(),
        com.aris.voice.brain.intent.ClarificationChecker()
    )

    override suspend fun analyzeIntent(rawInput: String): ArisResult<UserIntent> {
        val context = com.aris.voice.brain.intent.IntentContext(rawInput = rawInput)
        
        for (stage in pipeline) {
            stage.process(context)
        }
        
        return ArisResult.Success(context.toUserIntent())
    }
}

class ContextBuilderImpl(
    private val worldModel: IWorldModel,
    private val memoryProvider: com.aris.voice.brain.context.providers.IMemoryProvider? = null
) : IContextBuilder {
    override suspend fun buildContext(userIntent: UserIntent): ArisResult<ArisContext> {
        val worldData = worldModel.getWorldModelData()
        val memoryData = memoryProvider?.getMemoryData(userIntent)

        val context = ArisContext(
            userIntent = userIntent,
            deviceState = worldData.deviceState,
            worldModelData = worldData,
            memoryData = memoryData,
            environment = worldData.environment
        )
        return ArisResult.Success(context)
    }
}

class WorldModelImpl(
    private val deviceStateProvider: com.aris.voice.brain.world.providers.IDeviceStateProvider? = null,
    private val uiStateProvider: com.aris.voice.brain.world.providers.IUiStateProvider? = null,
    private val environmentProvider: com.aris.voice.brain.world.providers.IEnvironmentProvider? = null,
    private val taskStateProvider: com.aris.voice.brain.world.providers.ITaskStateProvider? = null,
    private val capabilityStateProvider: com.aris.voice.brain.world.providers.ICapabilityStateProvider? = null
) : IWorldModel {

    @Volatile
    private var worldModelData = com.aris.voice.domain.WorldModelData(
        deviceState = com.aris.voice.domain.DeviceState(),
        uiState = com.aris.voice.domain.UiState(),
        environment = com.aris.voice.domain.EnvironmentData(),
        taskState = com.aris.voice.domain.TaskState.IDLE,
        capabilityState = com.aris.voice.domain.CapabilityState()
    )

    override fun getWorldModelData(): com.aris.voice.domain.WorldModelData {
        return worldModelData
    }

    override suspend fun updateWorldModel() {
        val deviceState = deviceStateProvider?.getDeviceState() ?: com.aris.voice.domain.DeviceState()
        val uiState = uiStateProvider?.getUiState() ?: com.aris.voice.domain.UiState()
        val env = environmentProvider?.getEnvironmentData() ?: com.aris.voice.domain.EnvironmentData()
        val taskState = taskStateProvider?.getTaskState() ?: com.aris.voice.domain.TaskState.IDLE
        val caps = capabilityStateProvider?.getCapabilityState() ?: com.aris.voice.domain.CapabilityState()

        worldModelData = com.aris.voice.domain.WorldModelData(
            deviceState = deviceState,
            uiState = uiState,
            environment = env,
            taskState = taskState,
            capabilityState = caps
        )
    }
}

class TaskPlannerImpl : ITaskPlanner {
    override suspend fun selectStrategy(goal: UserIntent, context: ArisContext): ArisResult<com.aris.voice.domain.Strategy> {
        val strategyType = when (goal.primaryIntent) {
            "LAUNCH_APP" -> com.aris.voice.domain.StrategyType.OPEN_APPLICATION
            "COMMUNICATION" -> if (goal.action == "MAKE_CALL") com.aris.voice.domain.StrategyType.COMMUNICATION_CALL else com.aris.voice.domain.StrategyType.SEND_MESSAGE
            "UI_INTERACTION" -> com.aris.voice.domain.StrategyType.NAVIGATE_UI
            "SYSTEM_CONTROL" -> com.aris.voice.domain.StrategyType.CHANGE_SETTINGS
            "CALCULATION" -> com.aris.voice.domain.StrategyType.MATH_CALCULATION
            "ALARM" -> com.aris.voice.domain.StrategyType.SET_ALARM
            else -> com.aris.voice.domain.StrategyType.FALLBACK
        }
        return ArisResult.Success(com.aris.voice.domain.Strategy(type = strategyType, description = "Selected strategy for ${goal.primaryIntent}"))
    }

    override suspend fun createPlan(strategy: com.aris.voice.domain.Strategy, goal: UserIntent, context: ArisContext): ArisResult<Plan> {
        val steps = mutableListOf<PlanStep>()
        
        when (strategy.type) {
            com.aris.voice.domain.StrategyType.OPEN_APPLICATION -> {
                steps.add(
                    PlanStep(
                        stepId = "step_1_${UUID.randomUUID()}",
                        description = "Launch application: ${goal.targetApplication ?: "unknown"}",
                        requiredCapability = "PACKAGE_MANAGER",
                        arguments = mapOf("target" to (goal.targetApplication ?: "")),
                        expectedResult = "Application is foregrounded",
                        retryPolicy = com.aris.voice.domain.RetryPolicy.ONCE,
                        failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT,
                        dependencies = emptyList()
                    )
                )
            }
            com.aris.voice.domain.StrategyType.COMMUNICATION_CALL -> {
                steps.add(
                    PlanStep(
                        stepId = "step_1_${UUID.randomUUID()}",
                        description = "Dial number or contact: ${goal.targetEntity ?: "unknown"}",
                        requiredCapability = "TELEPHONY",
                        arguments = mapOf("target" to (goal.targetEntity ?: "")),
                        expectedResult = "Call is initiated",
                        retryPolicy = com.aris.voice.domain.RetryPolicy.NONE,
                        failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT,
                        dependencies = emptyList()
                    )
                )
            }
            com.aris.voice.domain.StrategyType.SEND_MESSAGE -> {
                val step1Id = "step_1_${UUID.randomUUID()}"
                steps.add(
                    PlanStep(
                        stepId = step1Id,
                        description = "Open messaging app: ${goal.targetApplication ?: "SMS"}",
                        requiredCapability = "PACKAGE_MANAGER",
                        arguments = mapOf("target" to (goal.targetApplication ?: "SMS")),
                        expectedResult = "Messaging app is open",
                        retryPolicy = com.aris.voice.domain.RetryPolicy.ONCE,
                        failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT,
                        dependencies = emptyList()
                    )
                )
                
                val step2Id = "step_2_${UUID.randomUUID()}"
                steps.add(
                    PlanStep(
                        stepId = step2Id,
                        description = "Type message content",
                        requiredCapability = "ACCESSIBILITY",
                        arguments = mapOf("text" to (goal.parameters["message_body"] ?: "")),
                        expectedResult = "Message text is entered",
                        retryPolicy = com.aris.voice.domain.RetryPolicy.THREE_TIMES,
                        failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT,
                        dependencies = listOf(step1Id)
                    )
                )
                
                steps.add(
                    PlanStep(
                        stepId = "step_3_${UUID.randomUUID()}",
                        description = "Click send button",
                        requiredCapability = "ACCESSIBILITY",
                        arguments = mapOf("targetDescription" to "Send"),
                        expectedResult = "Message is sent",
                        retryPolicy = com.aris.voice.domain.RetryPolicy.ONCE,
                        failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT,
                        dependencies = listOf(step2Id)
                    )
                )
            }
            com.aris.voice.domain.StrategyType.NAVIGATE_UI -> {
                if (goal.action == "CLICK") {
                    steps.add(
                        PlanStep(
                            stepId = "step_1_${UUID.randomUUID()}",
                            description = "Click on UI element: ${goal.targetEntity ?: ""}",
                            requiredCapability = "ACCESSIBILITY",
                            arguments = mapOf("targetDescription" to (goal.targetEntity ?: "")),
                            expectedResult = "Element is clicked",
                            retryPolicy = com.aris.voice.domain.RetryPolicy.ONCE,
                            failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT
                        )
                    )
                } else if (goal.action == "TYPE") {
                    steps.add(
                        PlanStep(
                            stepId = "step_1_${UUID.randomUUID()}",
                            description = "Type text: ${goal.parameters["text"] ?: ""}",
                            requiredCapability = "ACCESSIBILITY",
                            arguments = mapOf("text" to (goal.parameters["text"] ?: "")),
                            expectedResult = "Text is typed",
                            retryPolicy = com.aris.voice.domain.RetryPolicy.ONCE,
                            failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT
                        )
                    )
                } else if (goal.action == "SCROLL") {
                    steps.add(
                        PlanStep(
                            stepId = "step_1_${UUID.randomUUID()}",
                            description = "Scroll screen",
                            requiredCapability = "ACCESSIBILITY",
                            arguments = mapOf("direction" to if (goal.constraints.contains("DIRECTION_UP")) "UP" else "DOWN"),
                            expectedResult = "Screen is scrolled",
                            retryPolicy = com.aris.voice.domain.RetryPolicy.ONCE,
                            failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT
                        )
                    )
                }
            }
            com.aris.voice.domain.StrategyType.CHANGE_SETTINGS -> {
                 steps.add(
                     PlanStep(
                         stepId = "step_1_${UUID.randomUUID()}",
                         description = "Toggle system setting: ${goal.targetEntity ?: ""}",
                         requiredCapability = "SYSTEM_SETTINGS",
                         arguments = mapOf(
                             "setting" to (goal.targetEntity ?: ""),
                             "state" to (goal.action ?: "")
                         ),
                         expectedResult = "Setting is toggled",
                         retryPolicy = com.aris.voice.domain.RetryPolicy.NONE,
                         failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT
                     )
                 )
            }
            com.aris.voice.domain.StrategyType.MATH_CALCULATION -> {
                 steps.add(
                     PlanStep(
                         stepId = "step_1_${UUID.randomUUID()}",
                         description = "Calculate expression: ${goal.parameters["expression"] ?: ""}",
                         requiredCapability = "LOCAL_COMPUTE",
                         arguments = mapOf("expression" to (goal.parameters["expression"] ?: "")),
                         expectedResult = "Calculation completed",
                         retryPolicy = com.aris.voice.domain.RetryPolicy.NONE,
                         failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT
                     )
                 )
            }
            com.aris.voice.domain.StrategyType.SET_ALARM -> {
                 steps.add(
                     PlanStep(
                         stepId = "step_1_${UUID.randomUUID()}",
                         description = "Set alarm for: ${goal.parameters["time"] ?: ""}",
                         requiredCapability = "ALARM_PROVIDER",
                         arguments = mapOf("time" to (goal.parameters["time"] ?: "")),
                         expectedResult = "Alarm is set",
                         retryPolicy = com.aris.voice.domain.RetryPolicy.NONE,
                         failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT
                     )
                 )
            }
            else -> {
                steps.add(
                    PlanStep(
                        stepId = "step_1_${UUID.randomUUID()}",
                        description = "Analyze intent using LLM as fallback",
                        requiredCapability = "CLOUD_LLM",
                        arguments = mapOf("rawInput" to goal.rawInput),
                        expectedResult = "Intent is parsed by LLM",
                        retryPolicy = com.aris.voice.domain.RetryPolicy.ONCE,
                        failurePolicy = com.aris.voice.domain.FailurePolicy.ABORT
                    )
                )
            }
        }
        
        val plan = Plan(
            planId = UUID.randomUUID().toString(),
            steps = steps
        )
        return ArisResult.Success(plan)
    }
}

class ReasoningEngineImpl : IReasoningEngine {
    override suspend fun evaluateReasoning(
        intent: UserIntent,
        context: ArisContext,
        strategy: com.aris.voice.domain.Strategy,
        plan: Plan,
        decision: com.aris.voice.domain.Decision,
        worldModelData: com.aris.voice.domain.WorldModelData
    ): ArisResult<com.aris.voice.domain.ReasoningResult> {
        var requiresReplanning = false
        var requiresLlm = false
        var preferredLlmType = com.aris.voice.domain.LlmType.NONE
        var type = com.aris.voice.domain.ReasoningType.NO_REASONING_NEEDED
        val recoveryActions = mutableListOf<String>()
        var explanation = "No additional reasoning required."
        var confidence = 1.0f

        when (decision.type) {
            com.aris.voice.domain.DecisionType.EXECUTE_PLAN -> {
                type = com.aris.voice.domain.ReasoningType.NO_REASONING_NEEDED
            }
            com.aris.voice.domain.DecisionType.REQUEST_PERMISSION -> {
                type = com.aris.voice.domain.ReasoningType.LEVEL_1_RULE_BASED
                recoveryActions.add("Prompt user for permissions: ${decision.missingPermissions.joinToString()}")
                explanation = "Level 1: Missing permissions detected. Recovery path is to request permissions."
            }
            com.aris.voice.domain.DecisionType.ABORT -> {
                if (decision.missingCapabilities.contains("INTERNET")) {
                    type = com.aris.voice.domain.ReasoningType.LEVEL_1_RULE_BASED
                    recoveryActions.add("Prompt user to check internet connection.")
                    explanation = "Level 1: Internet missing. Aborting."
                } else {
                    type = com.aris.voice.domain.ReasoningType.LEVEL_2_CONTEXTUAL
                    explanation = "Level 2: Required capabilities are missing. Needs contextual fallback strategy."
                    requiresReplanning = true
                }
            }
            com.aris.voice.domain.DecisionType.ASK_FOR_CLARIFICATION -> {
                type = com.aris.voice.domain.ReasoningType.LEVEL_3_LLM_ESCALATION
                requiresLlm = true
                preferredLlmType = com.aris.voice.domain.LlmType.CLOUD_LLM
                explanation = "Level 3: Clarification needed. Cloud LLM should process conversational context."
                confidence = 0.7f
            }
            com.aris.voice.domain.DecisionType.REPLAN -> {
                type = com.aris.voice.domain.ReasoningType.LEVEL_2_CONTEXTUAL
                requiresReplanning = true
                explanation = "Level 2: Decision engine requested replan. Re-evaluating context."
            }
            com.aris.voice.domain.DecisionType.ERROR -> {
                type = com.aris.voice.domain.ReasoningType.LEVEL_3_LLM_ESCALATION
                requiresLlm = true
                preferredLlmType = com.aris.voice.domain.LlmType.CLOUD_LLM
                explanation = "Level 3: Plan error. Cloud LLM should evaluate alternatives."
                confidence = 0.5f
            }
            else -> {
                type = com.aris.voice.domain.ReasoningType.NO_REASONING_NEEDED
            }
        }

        return ArisResult.Success(
            com.aris.voice.domain.ReasoningResult(
                reasoningId = java.util.UUID.randomUUID().toString(),
                type = type,
                updatedStrategy = strategy,
                updatedPlan = plan,
                requiresReplanning = requiresReplanning,
                requiresLlm = requiresLlm,
                preferredLlmType = preferredLlmType,
                recoveryActions = recoveryActions,
                confidence = confidence,
                explanation = explanation
            )
        )
    }
}

class RiskValidatorImpl : IRiskValidator {
    override suspend fun validateRisk(plan: Plan): ArisResult<RiskLevel> {
        val containsDangerous = plan.steps.any { step ->
            val desc = step.description.lowercase()
            desc.contains("delete") || desc.contains("uninstall") || desc.contains("format")
        }
        val risk = if (containsDangerous) RiskLevel.HIGH else RiskLevel.LOW
        return ArisResult.Success(risk)
    }
}

class ToolSelectorImpl : IToolSelector {
    override suspend fun selectToolForStep(step: PlanStep): ArisResult<String> {
        return ArisResult.Success(step.requiredCapability)
    }
}

class DecisionEngineImpl : IDecisionEngine {
    override fun evaluate(
        intent: UserIntent,
        context: ArisContext,
        strategy: com.aris.voice.domain.Strategy,
        plan: Plan,
        worldModelData: com.aris.voice.domain.WorldModelData
    ): com.aris.voice.domain.Decision {
        val missingCapabilities = mutableListOf<String>()
        val missingPermissions = mutableListOf<String>()
        var requiresConfirmation = false
        var needsClarification = false
        var clarificationQuestion: String? = null
        
        if (plan.steps.isEmpty()) {
            return com.aris.voice.domain.Decision(
                decisionId = UUID.randomUUID().toString(),
                type = com.aris.voice.domain.DecisionType.ERROR,
                reason = "Plan contains no steps.",
                confidence = 0.0f,
                riskLevel = RiskLevel.HIGH,
                canContinue = false
            )
        }
        
        val caps = worldModelData.capabilityState
        for (step in plan.steps) {
            when (step.requiredCapability) {
                "ACCESSIBILITY" -> if (!caps.accessibilityReady) missingCapabilities.add("ACCESSIBILITY")
                "CAMERA" -> if (!caps.cameraReady) missingCapabilities.add("CAMERA")
                "MICROPHONE" -> if (!caps.microphoneReady) missingCapabilities.add("MICROPHONE")
                "INTERNET", "CLOUD_LLM" -> if (!caps.internetAvailable) missingCapabilities.add("INTERNET")
                "BLUETOOTH" -> if (!caps.bluetoothAvailable) missingCapabilities.add("BLUETOOTH")
                "LOCATION" -> if (!caps.locationAvailable) missingCapabilities.add("LOCATION")
                "LOCAL_LLM" -> if (!caps.localLlmAvailable) missingCapabilities.add("LOCAL_LLM")
            }
            
            when (step.requiredCapability) {
                "CAMERA" -> if (!worldModelData.environment.grantedPermissions.contains("android.permission.CAMERA")) missingPermissions.add("android.permission.CAMERA")
                "MICROPHONE" -> if (!worldModelData.environment.grantedPermissions.contains("android.permission.RECORD_AUDIO")) missingPermissions.add("android.permission.RECORD_AUDIO")
                "LOCATION" -> if (!worldModelData.environment.grantedPermissions.contains("android.permission.ACCESS_FINE_LOCATION")) missingPermissions.add("android.permission.ACCESS_FINE_LOCATION")
                "CONTACTS" -> if (!worldModelData.environment.grantedPermissions.contains("android.permission.READ_CONTACTS")) missingPermissions.add("android.permission.READ_CONTACTS")
                "TELEPHONY" -> if (!worldModelData.environment.grantedPermissions.contains("android.permission.CALL_PHONE")) missingPermissions.add("android.permission.CALL_PHONE")
                "ACCESSIBILITY" -> if (!caps.accessibilityReady) missingPermissions.add("ACCESSIBILITY_SERVICE")
            }
        }
        
        if (strategy.type == com.aris.voice.domain.StrategyType.SEND_MESSAGE && intent.targetEntity.isNullOrEmpty() && intent.targetApplication != "SMS") {
            needsClarification = true
            clarificationQuestion = "Who do you want to send the message to?"
        }
        if (strategy.type == com.aris.voice.domain.StrategyType.COMMUNICATION_CALL && intent.targetEntity.isNullOrEmpty()) {
            needsClarification = true
            clarificationQuestion = "Who do you want to call?"
        }
        
        val dangerousKeywords = listOf("delete", "uninstall", "format", "pay", "buy", "factory reset", "emergency")
        requiresConfirmation = plan.steps.any { step ->
            dangerousKeywords.any { step.description.lowercase().contains(it) }
        }
        
        val riskLevel = if (requiresConfirmation) RiskLevel.HIGH else RiskLevel.LOW
        
        if (missingPermissions.isNotEmpty()) {
            return com.aris.voice.domain.Decision(
                decisionId = UUID.randomUUID().toString(),
                type = com.aris.voice.domain.DecisionType.REQUEST_PERMISSION,
                reason = "Missing required permissions.",
                confidence = intent.confidenceScore,
                riskLevel = riskLevel,
                missingPermissions = missingPermissions.distinct(),
                missingCapabilities = missingCapabilities.distinct(),
                canContinue = false,
                plan = plan
            )
        }
        
        if (missingCapabilities.isNotEmpty()) {
            return com.aris.voice.domain.Decision(
                decisionId = UUID.randomUUID().toString(),
                type = com.aris.voice.domain.DecisionType.ABORT,
                reason = "Required capabilities are not available.",
                confidence = intent.confidenceScore,
                riskLevel = riskLevel,
                missingCapabilities = missingCapabilities.distinct(),
                canContinue = false,
                plan = plan
            )
        }
        
        if (needsClarification) {
             return com.aris.voice.domain.Decision(
                decisionId = UUID.randomUUID().toString(),
                type = com.aris.voice.domain.DecisionType.ASK_FOR_CLARIFICATION,
                reason = "Missing necessary information to proceed.",
                confidence = intent.confidenceScore,
                riskLevel = riskLevel,
                clarificationQuestion = clarificationQuestion,
                canContinue = false,
                plan = plan
            )
        }
        
        if (requiresConfirmation) {
            return com.aris.voice.domain.Decision(
                decisionId = UUID.randomUUID().toString(),
                type = com.aris.voice.domain.DecisionType.REQUIRE_USER_CONFIRMATION,
                reason = "Action requires explicit user confirmation.",
                confidence = intent.confidenceScore,
                riskLevel = riskLevel,
                requiresUserConfirmation = true,
                canContinue = false,
                plan = plan
            )
        }
        
        if (intent.confidenceScore < 0.5f) {
            return com.aris.voice.domain.Decision(
                decisionId = UUID.randomUUID().toString(),
                type = com.aris.voice.domain.DecisionType.ASK_FOR_CLARIFICATION,
                reason = "Low confidence in user intent.",
                confidence = intent.confidenceScore,
                riskLevel = riskLevel,
                clarificationQuestion = "I'm not sure I understood completely. Could you rephrase that?",
                canContinue = false,
                plan = plan
            )
        }
        
        return com.aris.voice.domain.Decision(
            decisionId = UUID.randomUUID().toString(),
            type = com.aris.voice.domain.DecisionType.EXECUTE_PLAN,
            reason = "Plan is valid, safe, and ready to execute.",
            confidence = intent.confidenceScore,
            riskLevel = riskLevel,
            canContinue = true,
            plan = plan
        )
    }
}

class BrainOrchestratorImpl(
    private val intentAnalyzer: IIntentAnalyzer,
    private val contextBuilder: IContextBuilder,
    private val worldModel: IWorldModel,
    private val taskPlanner: ITaskPlanner,
    private val reasoningEngine: IReasoningEngine,
    private val riskValidator: IRiskValidator,
    private val toolSelector: IToolSelector,
    private val decisionEngine: IDecisionEngine
) : IBrainOrchestrator {

    override suspend fun think(rawUserRequest: String): ArisResult<Decision> {
        return try {
            val goal = when (val res = intentAnalyzer.analyzeIntent(rawUserRequest)) {
                is ArisResult.Success -> res.value
                is ArisResult.Failure -> return ArisResult.Failure(res.error)
            }

            val context = when (val res = contextBuilder.buildContext(goal)) {
                is ArisResult.Success -> res.value
                is ArisResult.Failure -> return ArisResult.Failure(res.error)
            }
            // worldModel context was removed in favor of internal updating.
            // worldModel.updateWorldModel() is handled by WorldModel internally or before context building,
            // but for orchestrator, we might just update it here.
            worldModel.updateWorldModel()

            val strategy = when (val res = taskPlanner.selectStrategy(goal, context)) {
                is ArisResult.Success -> res.value
                is ArisResult.Failure -> return ArisResult.Failure(res.error)
            }

            val plan = when (val res = taskPlanner.createPlan(strategy, goal, context)) {
                is ArisResult.Success -> res.value
                is ArisResult.Failure -> return ArisResult.Failure(res.error)
            }

            val risk = when (val res = riskValidator.validateRisk(plan)) {
                is ArisResult.Success -> res.value
                is ArisResult.Failure -> return ArisResult.Failure(res.error)
            }

            val decision = decisionEngine.evaluate(
                intent = goal,
                context = context,
                strategy = strategy,
                plan = plan,
                worldModelData = worldModel.getWorldModelData()
            )

            val reasoningResult = when (val res = reasoningEngine.evaluateReasoning(goal, context, strategy, plan, decision, worldModel.getWorldModelData())) {
                is ArisResult.Success -> res.value
                is ArisResult.Failure -> return ArisResult.Failure(res.error)
            }
            
            // For now, return the initial decision. A future module might use reasoningResult to alter the decision.
            ArisResult.Success(decision)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.BrainError("COGNITION_PIPELINE_ERROR", "Thinking execution failed unexpectedly", e))
        }
    }
}

class SkillEngineImpl : ISkillEngine {
    private val storedSkills = mutableMapOf<String, com.aris.voice.domain.Skill>()

    override suspend fun findSkill(intent: UserIntent, strategy: com.aris.voice.domain.Strategy, context: ArisContext): ArisResult<com.aris.voice.domain.SkillMatchResult> {
        val activeSkills = storedSkills.values.filter { it.state == com.aris.voice.domain.SkillState.ACTIVE || it.state == com.aris.voice.domain.SkillState.DEGRADED }
        
        if (activeSkills.isEmpty()) {
            return ArisResult.Success(
                com.aris.voice.domain.SkillMatchResult(
                    matchType = com.aris.voice.domain.SkillMatchType.NO_MATCH,
                    matchedSkill = null,
                    confidence = 0.0f
                )
            )
        }

        var bestMatch: com.aris.voice.domain.Skill? = null
        var highestScore = -1.0f

        for (skill in activeSkills) {
            var matchScore = 0.0f
            
            if (skill.triggerIntent == intent.primaryIntent) matchScore += 30.0f
            if (skill.strategyType == strategy.type) matchScore += 20.0f
            if (skill.targetApplication == intent.targetApplication && intent.targetApplication != null) matchScore += 20.0f
            if (skill.category == intent.primaryIntent) matchScore += 10.0f

            matchScore += (skill.skillScore * 20.0f)

            if (matchScore > highestScore) {
                highestScore = matchScore
                bestMatch = skill
            }
        }

        if (bestMatch != null && highestScore >= 50.0f) {
             return ArisResult.Success(
                com.aris.voice.domain.SkillMatchResult(
                    matchType = com.aris.voice.domain.SkillMatchType.EXACT_MATCH,
                    matchedSkill = bestMatch,
                    confidence = bestMatch.confidence
                )
            )
        } else if (bestMatch != null && highestScore > 20.0f) {
            return ArisResult.Success(
                com.aris.voice.domain.SkillMatchResult(
                    matchType = com.aris.voice.domain.SkillMatchType.PARTIAL_MATCH,
                    matchedSkill = bestMatch,
                    confidence = bestMatch.confidence * 0.7f
                )
            )
        }

        return ArisResult.Success(
            com.aris.voice.domain.SkillMatchResult(
                matchType = com.aris.voice.domain.SkillMatchType.NO_MATCH,
                matchedSkill = null,
                confidence = 0.0f
            )
        )
    }

    override suspend fun registerSkill(skill: com.aris.voice.domain.Skill): ArisResult<Unit> {
        val existingSkill = storedSkills[skill.skillId]
        if (existingSkill != null && existingSkill.version >= skill.version) {
            return ArisResult.Success(Unit)
        }
        val calculatedScore = calculateDynamicSkillScore(skill)
        storedSkills[skill.skillId] = skill.copy(skillScore = calculatedScore)
        return ArisResult.Success(Unit)
    }

    override suspend fun updateSkillMetrics(skillId: String, success: Boolean, executionTimeMs: Long): ArisResult<Unit> {
        val skill = storedSkills[skillId] ?: return ArisResult.Failure(ArisError.BrainError("SKILL_NOT_FOUND", "Skill with ID $skillId not found", null))
        
        val newUsageCount = skill.usageCount + 1
        val newSuccessRate = if (success) {
            ((skill.successRate * skill.usageCount) + 1.0f) / newUsageCount
        } else {
            (skill.successRate * skill.usageCount) / newUsageCount
        }
        
        val newFailureRate = if (!success) {
            ((skill.failureRate * skill.usageCount) + 1.0f) / newUsageCount
        } else {
            (skill.failureRate * skill.usageCount) / newUsageCount
        }
        
        val newAvgExecutionTime = if (executionTimeMs > 0) {
            ((skill.averageExecutionTimeMs * skill.usageCount) + executionTimeMs) / newUsageCount
        } else {
            skill.averageExecutionTimeMs
        }

        var newState = skill.state
        if (newFailureRate > 0.5f && newUsageCount > 5) {
            newState = com.aris.voice.domain.SkillState.DEGRADED
        }
        if (newFailureRate > 0.8f && newUsageCount > 10) {
            newState = com.aris.voice.domain.SkillState.DEPRECATED
        }

        var updatedSkill = skill.copy(
            successRate = newSuccessRate,
            failureRate = newFailureRate,
            averageExecutionTimeMs = newAvgExecutionTime,
            usageCount = newUsageCount,
            lastSuccessfulExecution = if (success) System.currentTimeMillis() else skill.lastSuccessfulExecution,
            lastFailedExecution = if (!success) System.currentTimeMillis() else skill.lastFailedExecution,
            state = newState,
            metadata = skill.metadata.copy(lastUpdated = System.currentTimeMillis())
        )
        
        val newScore = calculateDynamicSkillScore(updatedSkill)
        updatedSkill = updatedSkill.copy(skillScore = newScore)

        storedSkills[skillId] = updatedSkill
        return ArisResult.Success(Unit)
    }

    override suspend fun createCompositeSkill(skills: List<com.aris.voice.domain.Skill>): ArisResult<com.aris.voice.domain.Skill> {
        if (skills.isEmpty()) {
            return ArisResult.Failure(ArisError.BrainError("EMPTY_SKILLS", "Cannot create composite skill from empty list", null))
        }

        val compositeId = "composite_" + java.util.UUID.randomUUID().toString()
        val combinedSteps = skills.flatMap { it.workflow.steps }.toMutableList()

        val compositePlan = com.aris.voice.domain.Plan(
            planId = "plan_$compositeId",
            goalId = "composite_goal",
            steps = combinedSteps,
            estimatedComplexity = skills.sumOf { it.workflow.estimatedComplexity ?: 1 }
        )

        val compositeSkill = com.aris.voice.domain.Skill(
            skillId = compositeId,
            name = "Composite Skill: " + skills.joinToString(", ") { it.name },
            description = "Temporary composite skill",
            category = "COMPOSITE",
            triggerIntent = skills.first().triggerIntent,
            strategyType = skills.first().strategyType,
            requiredCapabilities = skills.flatMap { it.requiredCapabilities }.distinct(),
            requiredPermissions = skills.flatMap { it.requiredPermissions }.distinct(),
            workflow = compositePlan,
            compositeSkillIds = skills.map { it.skillId },
            state = com.aris.voice.domain.SkillState.ACTIVE
        )

        val score = calculateDynamicSkillScore(compositeSkill)
        
        return ArisResult.Success(compositeSkill.copy(skillScore = score))
    }

    private fun calculateDynamicSkillScore(skill: com.aris.voice.domain.Skill): Float {
        val baseScore = skill.successRate * 0.5f
        val usageBonus = (skill.usageCount.coerceAtMost(100) / 100.0f) * 0.2f
        val confidenceScore = skill.confidence * 0.2f
        val penalty = skill.failureRate * 0.3f
        
        return (baseScore + usageBonus + confidenceScore - penalty).coerceIn(0.0f, 1.0f)
    }
}
