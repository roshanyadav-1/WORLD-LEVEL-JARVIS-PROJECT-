package com.aris.voice.brain

import com.aris.voice.core.ArisResult
import com.aris.voice.domain.ArisContext
import com.aris.voice.domain.Decision
import com.aris.voice.domain.DeviceContext
import com.aris.voice.domain.UserIntent
import com.aris.voice.domain.Plan
import com.aris.voice.domain.PlanStep
import com.aris.voice.domain.RiskLevel

/**
 * Interface for understanding raw user statements and parsing them into a structured Goal.
 */
interface IIntentAnalyzer {
    suspend fun analyzeIntent(rawInput: String): ArisResult<UserIntent>
}

/**
 * Interface for collecting relevant environmental variables and perception snapshots.
 */
interface IContextBuilder {
    suspend fun buildContext(userIntent: UserIntent): ArisResult<ArisContext>
}

/**
 * Interface representing the current state representing the device environment internally.
 */
interface IWorldModel {
    fun getWorldModelData(): com.aris.voice.domain.WorldModelData
    suspend fun updateWorldModel()
}

/**
 * Interface for transforming high-level goals into step-by-step sequential plans.
 */
interface ITaskPlanner {
    suspend fun selectStrategy(goal: UserIntent, context: ArisContext): ArisResult<com.aris.voice.domain.Strategy>
    suspend fun createPlan(strategy: com.aris.voice.domain.Strategy, goal: UserIntent, context: ArisContext): ArisResult<Plan>
}

/**
 * Interface representing the core thinking, LLM and heuristic-driven reasoning pipeline.
 */
interface IReasoningEngine {
    suspend fun evaluateReasoning(
        intent: UserIntent,
        context: ArisContext,
        strategy: com.aris.voice.domain.Strategy,
        plan: Plan,
        decision: com.aris.voice.domain.Decision,
        worldModelData: com.aris.voice.domain.WorldModelData
    ): ArisResult<com.aris.voice.domain.ReasoningResult>
}

/**
 * Interface to evaluate the risk and security status of any planned decision before execution.
 */
interface IRiskValidator {
    suspend fun validateRisk(plan: Plan): ArisResult<RiskLevel>
}

/**
 * Interface to select the minimum appropriate tool required to execute a specific plan step.
 */
interface IToolSelector {
    suspend fun selectToolForStep(step: PlanStep): ArisResult<String>
}

/**
 * Interface to construct and sign-off the final, immutable Decision object.
 */
interface IDecisionEngine {
    fun evaluate(
        intent: UserIntent,
        context: ArisContext,
        strategy: com.aris.voice.domain.Strategy,
        plan: Plan,
        worldModelData: com.aris.voice.domain.WorldModelData
    ): Decision
}

/**
 * Interface to store, retrieve, version, and match learned workflows (Skills).
 */
interface ISkillEngine {
    suspend fun findSkill(intent: UserIntent, strategy: com.aris.voice.domain.Strategy, context: ArisContext): ArisResult<com.aris.voice.domain.SkillMatchResult>
    suspend fun registerSkill(skill: com.aris.voice.domain.Skill): ArisResult<Unit>
    suspend fun updateSkillMetrics(skillId: String, success: Boolean, executionTimeMs: Long): ArisResult<Unit>
    suspend fun createCompositeSkill(skills: List<com.aris.voice.domain.Skill>): ArisResult<com.aris.voice.domain.Skill>
}

/**
 * The main orchestrator connecting all sub-modules of the brain in sequence.
 * Serves as the central point of contact for external controllers (e.g. Conversation engine).
 */
interface IBrainOrchestrator {
    /**
     * Executes the cognitive pipeline: Intent Analysis -> Context -> Planning -> Reasoning -> Verification -> Decision.
     */
    suspend fun think(rawUserRequest: String): ArisResult<Decision>
}
