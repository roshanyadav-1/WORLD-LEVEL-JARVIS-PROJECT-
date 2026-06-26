package com.aris.voice.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Memory entity with enhanced metadata queries and pruning/TTL support
 */
@Dao
interface MemoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: Memory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemories(memories: List<Memory>): List<Long>
    
    @Update
    suspend fun updateMemory(memory: Memory): Int
    
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<Memory>>
    
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    suspend fun getAllMemoriesList(): List<Memory>
    
    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): Memory?
    
    @Query("SELECT * FROM memories WHERE memoryType = :type ORDER BY timestamp DESC")
    suspend fun getMemoriesByType(type: String): List<Memory>

    @Query("SELECT * FROM memories WHERE source = :source ORDER BY timestamp DESC")
    suspend fun getMemoriesBySource(source: String): List<Memory>

    @Query("SELECT * FROM memories WHERE originalText LIKE '%' || :searchQuery || '%' ORDER BY timestamp DESC")
    suspend fun searchMemories(searchQuery: String): List<Memory>
    
    @Query("SELECT * FROM memories WHERE expiresAt IS NOT NULL AND expiresAt <= :currentTime")
    suspend fun getExpiredMemories(currentTime: Long): List<Memory>
    
    @Query("DELETE FROM memories WHERE expiresAt IS NOT NULL AND expiresAt <= :currentTime")
    suspend fun deleteExpiredMemories(currentTime: Long): Int
    
    @Query("SELECT * FROM memories ORDER BY importanceScore ASC, accessCount ASC, timestamp ASC LIMIT :limit")
    suspend fun getLeastImportantMemories(limit: Int): List<Memory>

    @Query("SELECT * FROM memories ORDER BY importanceScore DESC, accessCount DESC, timestamp DESC LIMIT :limit")
    suspend fun getMostImportantMemories(limit: Int): List<Memory>
    
    @Query("UPDATE memories SET accessCount = accessCount + 1, lastAccessedAt = :currentTime WHERE id = :id")
    suspend fun incrementAccessCount(id: Long, currentTime: Long): Int

    @Query("UPDATE memories SET importanceScore = :score WHERE id = :id")
    suspend fun updateImportanceScore(id: Long, score: Int): Int
    
    @Delete
    suspend fun deleteMemory(memory: Memory): Int
    
    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long): Int

    @Query("DELETE FROM memories WHERE memoryType = :type")
    suspend fun deleteMemoriesByType(type: String): Int
    
    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories(): Int
    
    @Query("SELECT COUNT(*) FROM memories")
    suspend fun getMemoryCount(): Int
}
