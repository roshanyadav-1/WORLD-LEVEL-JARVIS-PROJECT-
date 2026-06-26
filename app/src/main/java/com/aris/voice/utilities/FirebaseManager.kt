package com.aris.voice.utilities

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.aris.voice.data.UserMemory

class FirebaseManager {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var memoriesListenerRegistration: ListenerRegistration? = null
    
    // Callback to pass updated memories back to the service
    var onMemoriesFetched: ((List<UserMemory>) -> Unit)? = null
    
    private val managerScope = CoroutineScope(Dispatchers.IO)

    fun fetchMemories() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("FirebaseManager", "User not logged in, cannot fetch memories")
            return
        }

        Log.d("FirebaseManager", "Starting async memory fetch for user: ${currentUser.uid}")
        memoriesListenerRegistration = db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirebaseManager", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val memoriesList = snapshot.get("memories") as? List<Map<String, Any>>
                    if (memoriesList != null) {
                        val parsed = memoriesList.mapNotNull { map ->
                            try {
                                UserMemory(
                                    id = map["id"] as? String ?: "",
                                    text = map["text"] as? String ?: "",
                                    source = map["source"] as? String ?: "User",
                                    createdAt = (map["createdAt"] as? Timestamp)?.toDate() ?: Date()
                                )
                            } catch (e: Exception) {
                                Log.e("FirebaseManager", "Error parsing memory", e)
                                null
                            }
                        }.sortedByDescending { it.createdAt }

                        Log.d("FirebaseManager", "Fetched ${parsed.size} memories from Firestore")
                        onMemoriesFetched?.invoke(parsed)
                    } else {
                        Log.d("FirebaseManager", "No memories field found in user document")
                        onMemoriesFetched?.invoke(emptyList())
                    }
                } else {
                    Log.d("FirebaseManager", "Current data: null")
                }
            }
    }
    
    fun removeListener() {
        memoriesListenerRegistration?.remove()
    }

    fun trackConversationStart(conversationId: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        managerScope.launch {
            try {
                val conversationEntry = hashMapOf(
                    "conversationId" to conversationId,
                    "startedAt" to com.google.firebase.Timestamp.now(),
                    "endedAt" to null,
                    "messageCount" to 0,
                    "textModeUsed" to false,
                    "clarificationAttempts" to 0,
                    "sttErrorAttempts" to 0,
                    "endReason" to null,
                    "tasksRequested" to 0,
                    "tasksExecuted" to 0
                )

                db.collection("users").document(currentUser.uid)
                    .update("conversationHistory", com.google.firebase.firestore.FieldValue.arrayUnion(conversationEntry))
                    .await()

                Log.d("FirebaseManager", "Conversation start tracked in Firebase")
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Failed to track conversation start", e)
            }
        }
    }

    fun trackMessage(conversationId: String, role: String, message: String, messageType: String = "text") {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        managerScope.launch {
            try {
                val scrubbedMessage = message
                    .replace(Regex("\\b[A-Z][a-z]+ [A-Z][a-z]+\\b"), "[NAME]")
                    .replace(Regex("\\b\\d{10}\\b"), "[PHONE]")
                    .replace(Regex("\\b[a-zA-Z0-9.]+@[a-zA-Z0-9.]+\\b"), "[EMAIL]")
                    .take(500) // limit length

                val messageEntry = hashMapOf(
                    "conversationId" to conversationId,
                    "role" to role,
                    "message" to scrubbedMessage,
                    "messageType" to messageType,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )

                db.collection("conversations")
                    .document(conversationId)
                    .collection("messages")
                    .add(messageEntry)
                    .await()
                Log.d("FirebaseManager", "Message tracked successfully")
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Failed to track message", e)
            }
        }
    }
    fun trackConversationEnd(conversationId: String, endReason: String, messageCount: Int, textModeUsed: Boolean, clarificationAttempts: Int, sttErrorAttempts: Int, tasksRequested: Int = 0, tasksExecuted: Int = 0) {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        managerScope.launch {
            try {
                val completionEntry = hashMapOf(
                    "conversationId" to conversationId,
                    "endedAt" to com.google.firebase.Timestamp.now(),
                    "messageCount" to messageCount,
                    "textModeUsed" to textModeUsed,
                    "clarificationAttempts" to clarificationAttempts,
                    "sttErrorAttempts" to sttErrorAttempts,
                    "endReason" to endReason,
                    "tasksRequested" to tasksRequested,
                    "tasksExecuted" to tasksExecuted,
                    "status" to "completed"
                )

                db.collection("users").document(currentUser.uid)
                    .update("conversationHistory", com.google.firebase.firestore.FieldValue.arrayUnion(completionEntry))
                    .await()

                Log.d("FirebaseManager", "Successfully tracked conversation end in Firebase: $conversationId ($endReason)")
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Failed to track conversation end in Firebase", e)
            }
        }
    }
}
