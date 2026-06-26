package com.aris.voice.data

import android.content.Context
import android.util.Log
import com.aris.voice.api.EmbeddingService
import com.aris.voice.MyApplication
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Manager class for handling memory operations with embeddings and advanced metadata
 */
class MemoryManager(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val memoryDao = database.memoryDao()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private fun getDefaultImportanceAndTtl(type: MemoryType): Pair<Int, Long?> {
        val importance = when(type) {
            MemoryType.RELATIONSHIP -> 85
            MemoryType.PREFERENCE -> 80
            MemoryType.SCHEDULE -> 75
            MemoryType.FACT -> 70
            MemoryType.GOAL -> 65
            MemoryType.HABIT -> 60
            MemoryType.TASK_RESULT -> 50
            MemoryType.GENERAL -> 50
        }
        val daysTtl = when(type) {
            MemoryType.SCHEDULE -> 30L
            MemoryType.TASK_RESULT -> 7L
            MemoryType.GENERAL -> 90L
            else -> null
        }
        val expiresAt = daysTtl?.let { System.currentTimeMillis() + it * 24L * 60L * 60L * 1000L }
        return Pair(importance, expiresAt)
    }

    /**
     * Base implementation of addMemory with full parameters
     */
    suspend fun addMemory(
        originalText: String, 
        type: MemoryType = MemoryType.GENERAL,
        customImportance: Int? = null,
        source: String = "conversation",
        checkDuplicates: Boolean = true
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MemoryManager", "Adding memory ($type): ${originalText.take(100)}...")
                
                val isOnline = try {
                    com.aris.voice.utilities.NetworkConnectivityManager(MyApplication.appContext).isNetworkAvailable()
                } catch (e: Exception) {
                    false
                }

                if (checkDuplicates && isOnline) {
                    val similarMemories = findSimilarMemories(originalText, similarityThreshold = 0.92f)
                    if (similarMemories.isNotEmpty()) {
                        Log.d("MemoryManager", "Found ${similarMemories.size} similar memories, skipping duplicate")
                        return@withContext true
                    }
                }
                
                val embedding = if (isOnline) {
                    EmbeddingService.generateEmbedding(
                        text = originalText,
                        taskType = "RETRIEVAL_DOCUMENT"
                    )
                } else {
                    null // Will generate later or rely on raw text matching
                }
                
                if (embedding == null && isOnline) {
                    Log.w("MemoryManager", "Failed to generate embedding for text, but storing anyway")
                }
                
                val embeddingJson = if (embedding != null) JSONArray(embedding).toString() else "[]"
                val (defaultImportance, defaultExpiry) = getDefaultImportanceAndTtl(type)
                
                val memory = Memory(
                    originalText = originalText,
                    embedding = embeddingJson,
                    memoryType = type.name,
                    importanceScore = customImportance ?: defaultImportance,
                    expiresAt = defaultExpiry,
                    source = source
                )
                
                val id = memoryDao.insertMemory(memory)
                Log.d("MemoryManager", "Successfully added memory with ID: $id")
                
                // Sync to Firestore if it's a new memory generated during conversations
                if (source != "FirestoreSync" && isOnline) {
                    syncMemoryToFirestore(
                        originalText = originalText,
                        type = type,
                        importance = customImportance ?: defaultImportance,
                        source = source
                    )
                }
                
                // Prune old memories if count exceeds limit (capped at 500)
                pruneIfNeeded()
                
                return@withContext true
                
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error adding memory $e", e)
                return@withContext false
            }
        }
    }

    /**
     * Overloaded backward compatible version of addMemory
     */
    suspend fun addMemory(originalText: String, checkDuplicates: Boolean = true): Boolean {
        return addMemory(originalText, MemoryType.GENERAL, null, "conversation", checkDuplicates)
    }

    /**
     * Fire-and-forget version of addMemory that is not tied to an Activity scope.
     */
    fun addMemoryFireAndForget(originalText: String, checkDuplicates: Boolean = true) {
        ioScope.launch {
            try {
                val result = addMemory(originalText, checkDuplicates)
                Log.d("MemoryManager", "Fire-and-forget addMemory result=$result")
            } catch (e: Exception) {
                Log.e("MemoryManager", "Fire-and-forget addMemory error", e)
            }
        }
    }
    
    /**
     * Search for relevant memories based on a query with combined similarity and metadata ranking logic
     */
    suspend fun searchMemories(query: String, topK: Int = 3): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MemoryManager", "Searching memories for query: ${query.take(100)}...")
                
                val queryEmbedding = EmbeddingService.generateEmbedding(
                    text = query,
                    taskType = "RETRIEVAL_QUERY"
                )
                
                if (queryEmbedding == null) {
                    Log.e("MemoryManager", "Failed to generate embedding for query")
                    return@withContext emptyList()
                }
                
                val memoriesList = memoryDao.getAllMemoriesList()
                if (memoriesList.isEmpty()) {
                    Log.d("MemoryManager", "No memories found in database")
                    return@withContext emptyList()
                }
                
                val current = System.currentTimeMillis()
                val scoredMemories = memoriesList.mapNotNull { memory ->
                    // Skip if expired
                    if (memory.expiresAt != null && memory.expiresAt <= current) {
                        return@mapNotNull null
                    }
                    
                    val memoryEmbedding = parseEmbeddingFromJson(memory.embedding)
                    val similarity = calculateCosineSimilarity(queryEmbedding, memoryEmbedding)
                    
                    // Normalize importance score (0-100) to a 0.0-1.0 scale
                    val normImportance = memory.importanceScore.toFloat() / 100.0f
                    
                    // Combined score = 0.6 * similarity + 0.4 * normalized importance
                    val combinedScore = 0.6f * similarity + 0.4f * normImportance
                    
                    Triple(memory, combinedScore, similarity)
                }.sortedByDescending { it.second }
                
                // Increment access count & update lastAccessedAt for top matches
                val topMatches = scoredMemories.take(topK)
                topMatches.forEach { (memory, _, _) ->
                    memoryDao.incrementAccessCount(memory.id, current)
                }
                
                val topMemoriesText = topMatches.map { it.first.originalText }
                Log.d("MemoryManager", "Found ${topMemoriesText.size} relevant memories")
                return@withContext topMemoriesText
                
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error searching memories", e)
                return@withContext emptyList()
            }
        }
    }
    
    /**
     * Get relevant memories for a task and format them for prompt augmentation
     */
    suspend fun getRelevantMemories(taskDescription: String): String {
        val relevantMemories = searchMemories(taskDescription, topK = 3)
        
        return if (relevantMemories.isNotEmpty()) {
            buildString {
                appendLine("--- Relevant Information ---")
                relevantMemories.forEach { memory ->
                    appendLine("- $memory")
                }
                appendLine()
                appendLine("--- My Task ---")
                appendLine(taskDescription)
            }
        } else {
            taskDescription
        }
    }
    
    /**
     * Generate synthetic user profile for prompt augmentation from typed memory lists
     */
    suspend fun getUserProfileSummary(): String {
        return withContext(Dispatchers.IO) {
            try {
                val facts = memoryDao.getMemoriesByType(MemoryType.FACT.name).take(10)
                val preferences = memoryDao.getMemoriesByType(MemoryType.PREFERENCE.name).take(15)
                val relationships = memoryDao.getMemoriesByType(MemoryType.RELATIONSHIP.name).take(10)
                val habits = memoryDao.getMemoriesByType(MemoryType.HABIT.name).take(10)
                val goals = memoryDao.getMemoriesByType(MemoryType.GOAL.name).take(10)
                
                if (facts.isEmpty() && preferences.isEmpty() && relationships.isEmpty() && habits.isEmpty() && goals.isEmpty()) {
                    return@withContext ""
                }
                
                buildString {
                    appendLine("--- CONVERSATIONAL USER PROFILE ---")
                    if (facts.isNotEmpty()) {
                        appendLine("[Key Facts about User]:")
                        facts.forEach { appendLine("- ${it.originalText}") }
                    }
                    if (preferences.isNotEmpty()) {
                        appendLine("[User Preferences]:")
                        preferences.forEach { appendLine("- ${it.originalText}") }
                    }
                    if (relationships.isNotEmpty()) {
                        appendLine("[User Relationships]:")
                        relationships.forEach { appendLine("- ${it.originalText}") }
                    }
                    if (habits.isNotEmpty()) {
                        appendLine("[User Habits]:")
                        habits.forEach { appendLine("- ${it.originalText}") }
                    }
                    if (goals.isNotEmpty()) {
                        appendLine("[User Long-term Goals]:")
                        goals.forEach { appendLine("- ${it.originalText}") }
                    }
                }
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error constructing user profile summary", e)
                ""
            }
        }
    }

    /**
     * Automated cleanups for expired records
     */
    suspend fun cleanupExpiredMemories() {
        withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                val deletedCount = memoryDao.deleteExpiredMemories(currentTime)
                if (deletedCount > 0) {
                    Log.d("MemoryManager", "Cleaned up $deletedCount expired memories.")
                }
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error cleaning up expired memories", e)
            }
        }
    }

    private suspend fun pruneIfNeeded() {
        val count = memoryDao.getMemoryCount()
        if (count >= 500) {
            Log.d("MemoryManager", "Pruning memory storage (total: $count). Deleting 100 least important memories.")
            val leastImportant = memoryDao.getLeastImportantMemories(100)
            leastImportant.forEach { 
                memoryDao.deleteMemory(it) 
            }
        }
    }
    
    /**
     * Get memory count
     */
    suspend fun getMemoryCount(): Int {
        return withContext(Dispatchers.IO) {
            memoryDao.getMemoryCount()
        }
    }
    
    /**
     * Get all memories as a list
     */
    suspend fun getAllMemoriesList(): List<Memory> {
        return withContext(Dispatchers.IO) {
            memoryDao.getAllMemoriesList()
        }
    }
    
    /**
     * Delete all memories
     */
    suspend fun clearAllMemories() {
        withContext(Dispatchers.IO) {
            memoryDao.deleteAllMemories()
            Log.d("MemoryManager", "All memories cleared")
        }
    }
    
    /**
     * Delete a specific memory by ID
     */
    suspend fun deleteMemoryById(id: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                memoryDao.deleteMemoryById(id)
                Log.d("MemoryManager", "Successfully deleted memory with ID: $id")
                true
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error deleting memory with ID: $id", e)
                false
            }
        }
    }
    
    /**
     * Find memories similar to the given text using raw similarity (for duplicate filtering)
     */
    suspend fun findSimilarMemories(text: String, similarityThreshold: Float = 0.82f): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val queryEmbedding = EmbeddingService.generateEmbedding(
                    text = text,
                    taskType = "RETRIEVAL_QUERY"
                )
                
                if (queryEmbedding == null) {
                    Log.e("MemoryManager", "Failed to generate embedding for similarity check")
                    return@withContext emptyList()
                }
                
                val allMemories = memoryDao.getAllMemoriesList()
                if (allMemories.isEmpty()) {
                    return@withContext emptyList()
                }
                
                val similarMemories = allMemories.mapNotNull { memory ->
                    val memoryEmbedding = parseEmbeddingFromJson(memory.embedding)
                    val similarity = calculateCosineSimilarity(queryEmbedding, memoryEmbedding)
                    
                    if (similarity >= similarityThreshold) {
                        Log.d("MemoryManager", "Found similar memory (similarity: $similarity): ${memory.originalText.take(50)}...")
                        memory.originalText
                    } else {
                        null
                    }
                }
                
                Log.d("MemoryManager", "Found ${similarMemories.size} similar memories with threshold $similarityThreshold")
                return@withContext similarMemories
                
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error finding similar memories", e)
                return@withContext emptyList()
            }
        }
    }
    
    /**
     * Parse embedding from JSON string
     */
    private fun parseEmbeddingFromJson(embeddingJson: String): List<Float> {
        return try {
            val jsonArray = JSONArray(embeddingJson)
            (0 until jsonArray.length()).map { i ->
                jsonArray.getDouble(i).toFloat()
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error parsing embedding JSON", e)
            emptyList()
        }
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    private fun calculateCosineSimilarity(vector1: List<Float>, vector2: List<Float>): Float {
        if (vector1.size != vector2.size) {
            Log.w("MemoryManager", "Vector dimensions don't match: ${vector1.size} vs ${vector2.size}")
            return 0f
        }
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vector1.indices) {
            dotProduct += vector1[i] * vector2[i]
            norm1 += vector1[i] * vector1[i]
            norm2 += vector2[i] * vector2[i]
        }
        
        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    /**
     * Uploads an extracted/new memory to the cloud firestore memories array under current user document.
     */
    private fun syncMemoryToFirestore(
        originalText: String,
        type: MemoryType,
        importance: Int,
        source: String
    ) {
        try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser ?: return
            val db = FirebaseFirestore.getInstance()
            
            val docRef = db.collection("users").document(currentUser.uid)
            val firestoreId = java.util.UUID.randomUUID().toString()
            val memoryMap = hashMapOf(
                "id" to firestoreId,
                "text" to originalText,
                "source" to source,
                "createdAt" to Timestamp.now(),
                "memoryType" to type.name,
                "importanceScore" to importance
            )
            
            docRef.update("memories", FieldValue.arrayUnion(memoryMap))
                .addOnSuccessListener {
                    Log.d("MemoryManager", "Successfully synced extracted memory to Firestore: $originalText")
                }
                .addOnFailureListener { e ->
                    Log.e("MemoryManager", "Failed to sync extracted memory to Firestore", e)
                }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Firestore error in syncMemoryToFirestore: ${e.message}")
        }
    }

    /**
     * Synchronizes raw memories loaded from Firestore down to the local offline-capable SQLite/Room file.
     * Generates vector embeddings on the fly for any newly found user memories.
     */
    suspend fun syncFirestoreWithLocal(firestoreMemories: List<UserMemory>) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("MemoryManager", "Syncing ${firestoreMemories.size} Firestore memories with local Room database")
                val localMemories = memoryDao.getAllMemoriesList()
                
                // 1. Find memories locally that do not exist in Firestore anymore (Pruned / Deleted)
                val firestoreTexts = firestoreMemories.map { it.text.trim().lowercase() }.toSet()
                val deletedLocally = localMemories.filter { local ->
                    local.originalText.trim().lowercase() !in firestoreTexts
                }
                
                if (deletedLocally.isNotEmpty()) {
                    Log.d("MemoryManager", "Deleting ${deletedLocally.size} stale local memories")
                    deletedLocally.forEach { local ->
                        memoryDao.deleteMemory(local)
                        Log.d("MemoryManager", "Removed local memory: ${local.originalText}")
                    }
                }
                
                // 2. Find new memories in Firestore that do not exist locally
                val localTexts = localMemories.map { it.originalText.trim().lowercase() }.toSet()
                val addedFirestore = firestoreMemories.filter { firestore ->
                    firestore.text.trim().lowercase() !in localTexts
                }
                
                if (addedFirestore.isNotEmpty()) {
                    Log.d("MemoryManager", "Syncing ${addedFirestore.size} new memories from Firestore to SQLite")
                    addedFirestore.forEach { firestore ->
                        val type = MemoryType.fromString(firestore.memoryType)
                        
                        addMemory(
                            originalText = firestore.text,
                            type = type,
                            customImportance = firestore.importanceScore,
                            source = "FirestoreSync", // Set as FirestoreSync to avoid uploading it back
                            checkDuplicates = false
                        )
                    }
                }
                Log.d("MemoryManager", "Syncing completed cleanly")
            } catch (e: Exception) {
                Log.e("MemoryManager", "Error syncing Firestore memories to local Room", e)
            }
        }
    }
    
    companion object {
        private var instance: MemoryManager? = null
        
        fun getInstance(context: Context = MyApplication.appContext): MemoryManager {
            return instance ?: synchronized(this) {
                instance ?: MemoryManager(context).also { instance = it }
            }
        }
    }
}
