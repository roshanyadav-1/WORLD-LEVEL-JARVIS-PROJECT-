package com.aris.voice.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MemoryType {
    FACT,
    PREFERENCE,
    RELATIONSHIP,
    HABIT,
    GOAL,
    SCHEDULE,
    TASK_RESULT,
    GENERAL;

    companion object {
        fun fromString(value: String?): MemoryType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: GENERAL
        }
    }
}

object MemorySource {
    const val CONVERSATION = "conversation"
    const val OBSERVATION = "observation"
    const val INFERENCE = "inference"
    const val SYSTEM = "system"
    const val USER_INPUT = "user_input"
}

/**
 * Memory entity for storing user information with embeddings and advanced metadata.
 * Now properly indexed and annotated for production readiness.
 */
@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["memoryType"]),
        Index(value = ["importanceScore"]),
        Index(value = ["expiresAt"])
    ]
)
data class Memory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "originalText")
    val originalText: String,

    @ColumnInfo(name = "embedding")
    val embedding: String, // Stored as JSON string of numbers

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "memoryType", defaultValue = "'GENERAL'")
    val memoryType: String = MemoryType.GENERAL.name,

    @ColumnInfo(name = "importanceScore", defaultValue = "50")
    val importanceScore: Int = 50,

    @ColumnInfo(name = "accessCount", defaultValue = "0")
    val accessCount: Int = 0,

    @ColumnInfo(name = "lastAccessedAt", defaultValue = "0")
    val lastAccessedAt: Long = 0L,

    @ColumnInfo(name = "expiresAt", defaultValue = "NULL")
    val expiresAt: Long? = null,

    @ColumnInfo(name = "source", defaultValue = "'conversation'")
    val source: String = MemorySource.CONVERSATION
) {
    /**
     * Checks if the memory has expired based on the current time.
     */
    fun isExpired(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return expiresAt != null && currentTimeMillis > expiresAt
    }

    /**
     * Returns the memoryType string as a typed MemoryType Enum.
     */
    fun getTypedMemoryType(): MemoryType {
        return MemoryType.fromString(memoryType)
    }

    /**
     * Parses the JSON string embedding into a List of Floats safely.
     */
    fun getEmbeddingList(): List<Float> {
        if (embedding.isBlank()) return emptyList()
        return try {
            embedding.removePrefix("[").removeSuffix("]").split(",").mapNotNull { 
                it.trim().toFloatOrNull() 
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Helper to create a copy with incremented access count.
     */
    fun withIncrementedAccess(currentTimeMillis: Long = System.currentTimeMillis()): Memory {
        return this.copy(
            accessCount = this.accessCount + 1,
            lastAccessedAt = currentTimeMillis
        )
    }
}
