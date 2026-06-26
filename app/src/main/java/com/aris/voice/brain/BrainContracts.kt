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
    suspend fun evaluate(
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
 * Interface to coordinate the full execution lifecycle of a Plan.
 */
interface IExecutionOrchestrator {
    suspend fun executeDecision(
        decision: Decision,
        onProgress: (com.aris.voice.domain.ExecutionProgress) -> Unit
    ): com.aris.voice.domain.ExecutionResult

    fun pause()
    fun resume()
    fun cancel()
}

/**
 * Interface to store, retrieve, update, and manage the long-term cognitive memory of the Brain.
 */
interface IMemoryEngine {
    suspend fun store(memory: com.aris.voice.domain.MemoryItem): ArisResult<Unit>
    suspend fun retrieve(memoryId: String): ArisResult<com.aris.voice.domain.MemoryItem?>
    suspend fun update(memory: com.aris.voice.domain.MemoryItem): ArisResult<Unit>
    suspend fun delete(memoryId: String): ArisResult<Unit>
    suspend fun archive(memoryId: String): ArisResult<Unit>
    suspend fun search(query: com.aris.voice.domain.MemoryQuery): ArisResult<List<com.aris.voice.domain.MemoryItem>>
    suspend fun expireTemporaryMemories(): ArisResult<Unit>
}

/**
 * Interface to convert completed executions into reusable experience records and summaries.
 */
interface IExperienceEngine {
    suspend fun recordExperience(
        intent: UserIntent,
        strategy: com.aris.voice.domain.Strategy,
        decision: Decision,
        executionResult: com.aris.voice.domain.ExecutionResult,
        worldModelSnapshot: com.aris.voice.domain.WorldModelData,
        memoryReferences: List<String> = emptyList(),
        skillReference: String? = null
    ): ArisResult<com.aris.voice.domain.ExperienceRecord>
    
    suspend fun getExperiences(intentName: String? = null, limit: Int = 100): ArisResult<List<com.aris.voice.domain.ExperienceRecord>>
    suspend fun generateSummary(intentName: String): ArisResult<com.aris.voice.domain.ExperienceSummary>
}

/**
 * Interface to store, retrieve and organize structured knowledge.
 */
interface IKnowledgeEngine {
    suspend fun addKnowledge(node: com.aris.voice.domain.KnowledgeNode): ArisResult<Unit>
    suspend fun retrieveKnowledge(knowledgeId: String): ArisResult<com.aris.voice.domain.KnowledgeNode?>
    suspend fun updateKnowledge(node: com.aris.voice.domain.KnowledgeNode): ArisResult<Unit>
    suspend fun archiveKnowledge(knowledgeId: String): ArisResult<Unit>
    suspend fun searchKnowledge(query: String): ArisResult<com.aris.voice.domain.KnowledgeQueryResult>
    suspend fun addRelation(relation: com.aris.voice.domain.KnowledgeRelation): ArisResult<Unit>
    suspend fun traverseRelationships(startNodeId: String, depth: Int = 1): ArisResult<com.aris.voice.domain.KnowledgeGraph>
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
    
    /**
     * Processes the result of an executed plan to generate a final conversational decision.
     */
    suspend fun processExecutionResult(executionResult: com.aris.voice.domain.ExecutionResult): ArisResult<Decision>
}
