package com.aris.voice.memory

import android.content.Context
import com.aris.voice.core.ArisError
import com.aris.voice.core.ArisResult
import java.util.concurrent.ConcurrentHashMap

class MemoryImpl(private val context: Context) : 
    IWorkingMemory, 
    ILongTermMemory, 
    ISemanticMemory, 
    IEpisodicMemory, 
    ISkillMemory {

    private val activeTasks = ConcurrentHashMap<String, String>()
    private val factsPrefs = context.getSharedPreferences("ArisLongTermFacts", Context.MODE_PRIVATE)
    private val skillsPrefs = context.getSharedPreferences("ArisSkillMemory", Context.MODE_PRIVATE)
    private val relationships = mutableListOf<Triple<String, String, String>>()
    private val episodes = mutableListOf<String>()

    // IWorkingMemory
    override fun getActiveTaskState(taskId: String): String? {
        return activeTasks[taskId]
    }

    override fun saveActiveTaskState(taskId: String, stateJson: String) {
        activeTasks[taskId] = stateJson
    }

    override fun clearActiveTask(taskId: String) {
        activeTasks.remove(taskId)
    }

    // ILongTermMemory
    override suspend fun storeFact(key: String, fact: String): ArisResult<Unit> {
        return try {
            factsPrefs.edit().putString(key, fact).apply()
            ArisResult.Success(Unit)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.MemoryError("STORE_FACT_FAILED", "Failed to store fact for key: $key", e))
        }
    }

    override suspend fun retrieveFact(key: String): ArisResult<String?> {
        return try {
            ArisResult.Success(factsPrefs.getString(key, null))
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.MemoryError("RETRIEVE_FACT_FAILED", "Failed to retrieve fact for key: $key", e))
        }
    }

    override suspend fun deleteFact(key: String): ArisResult<Unit> {
        return try {
            factsPrefs.edit().remove(key).apply()
            ArisResult.Success(Unit)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.MemoryError("DELETE_FACT_FAILED", "Failed to delete fact for key: $key", e))
        }
    }

    // ISemanticMemory
    override suspend fun queryRelationships(concept: String): ArisResult<List<String>> {
        val lowerConcept = concept.lowercase()
        val results = relationships.filter {
            it.first.lowercase().contains(lowerConcept) || it.third.lowercase().contains(lowerConcept)
        }.map { "${it.first} --(${it.second})--> ${it.third}" }
        return ArisResult.Success(results)
    }

    override suspend fun addRelationship(subject: String, relation: String, `object`: String): ArisResult<Unit> {
        relationships.add(Triple(subject, relation, `object`))
        return ArisResult.Success(Unit)
    }

    // IEpisodicMemory
    override suspend fun recordEpisode(title: String, summary: String, success: Boolean): ArisResult<Unit> {
        val timestamp = System.currentTimeMillis()
        val formatted = "[$timestamp] $title ($summary) - Success: $success"
        episodes.add(0, formatted)
        if (episodes.size > 100) {
            episodes.removeAt(episodes.lastIndex)
        }
        return ArisResult.Success(Unit)
    }

    override suspend fun getRecentEpisodes(limit: Int): ArisResult<List<String>> {
        val count = limit.coerceAtMost(episodes.size)
        return ArisResult.Success(episodes.take(count))
    }

    // ISkillMemory
    override suspend fun registerSkill(name: String, stepsJson: String): ArisResult<Unit> {
        return try {
            skillsPrefs.edit().putString(name, stepsJson).apply()
            ArisResult.Success(Unit)
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.MemoryError("REGISTER_SKILL_FAILED", "Failed to register skill: $name", e))
        }
    }

    override suspend fun getSkillSteps(name: String): ArisResult<String?> {
        return try {
            ArisResult.Success(skillsPrefs.getString(name, null))
        } catch (e: Exception) {
            ArisResult.Failure(ArisError.MemoryError("GET_SKILL_STEPS_FAILED", "Failed to retrieve skill: $name", e))
        }
    }
}
