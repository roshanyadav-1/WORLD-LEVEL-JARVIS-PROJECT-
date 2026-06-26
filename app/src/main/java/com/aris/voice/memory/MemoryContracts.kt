package com.aris.voice.memory

import com.aris.voice.core.ArisResult

/**
 * Base generic interface representing stored memory constructs.
 */
interface IMemory {
    val id: String
    val timestamp: Long
}

/**
 * Temporary fast memory representing active dialogue or execution context.
 */
interface IWorkingMemory {
    fun getActiveTaskState(taskId: String): String?
    fun saveActiveTaskState(taskId: String, stateJson: String)
    fun clearActiveTask(taskId: String)
}

/**
 * Long term storage memory representing user facts, permanent configurations, or persistent variables.
 */
interface ILongTermMemory {
    suspend fun storeFact(key: String, fact: String): ArisResult<Unit>
    suspend fun retrieveFact(key: String): ArisResult<String?>
    suspend fun deleteFact(key: String): ArisResult<Unit>
}

/**
 * Semantic relational memory containing concept graphs, world facts, and library mappings.
 */
interface ISemanticMemory {
    suspend fun queryRelationships(concept: String): ArisResult<List<String>>
    suspend fun addRelationship(subject: String, relation: String, `object`: String): ArisResult<Unit>
}

/**
 * Episodic memory containing historic task logs, execution completions, and past chronological context.
 */
interface IEpisodicMemory {
    suspend fun recordEpisode(title: String, summary: String, success: Boolean): ArisResult<Unit>
    suspend fun getRecentEpisodes(limit: Int): ArisResult<List<String>>
}

/**
 * Skill memory capturing learned workflows, action shortcuts, and custom trigger logic.
 */
interface ISkillMemory {
    suspend fun registerSkill(name: String, stepsJson: String): ArisResult<Unit>
    suspend fun getSkillSteps(name: String): ArisResult<String?>
}
