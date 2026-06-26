package com.aris.voice.utilities

import android.util.Log
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.aris.voice.MyApplication
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.util.Calendar

class FreemiumManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val billingClient: BillingClient = MyApplication.billingClient

    enum class ProFeatures {
        TRIGGERS,
        MEMORY_BOOST,
        AGENT_MAX_STEPS,
        VOICE_PRESETS
    }

    object ProductSkus {
        const val MONTHLY_PRO = "aris_premium_monthly"
        const val YEARLY_PRO = "aris_premium_yearly"
        const val LIFETIME = "aris_lifetime"
    }

    companion object {
        const val DAILY_TASK_LIMIT = 15 // Set your daily task limit here
    }

    private val prefs by lazy {
        MyApplication.appContext.getSharedPreferences("FreemiumPrefs", Context.MODE_PRIVATE)
    }

    suspend fun getDeveloperMessage(): String {
        return try {
            val document = db.collection("settings").document("freemium").get().await()
            document.getString("developerMessage") ?: ""
        } catch (e: Exception) {
            Log.e("FreemiumManager", "Error fetching developer message from Firestore.", e)
            ""
        }
    }
    
    suspend fun verifyPurchase(purchaseToken: String, sku: String): Boolean {
        return withContext(Dispatchers.IO) {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            try {
                val result = functions
                    .getHttpsCallable("verifyPurchase")
                    .call(hashMapOf("token" to purchaseToken, "sku" to sku))
                    .await()
                result.data as? Boolean ?: false
            } catch (e: Exception) {
                Logger.e("FreemiumManager", "Purchase verification failed via Cloud Functions", e)
                false 
            }
        }
    }
    
    fun restorePurchases(context: Context) {
        val billingClient = BillingClient.newBuilder(context)
            .setListener { _, _ -> }
            .enablePendingPurchases()
            .build()
            
        billingClient.startConnection(object : com.android.billingclient.api.BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: com.android.billingclient.api.BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingClient.queryPurchasesAsync(
                        QueryPurchasesParams.newBuilder()
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    ) { result, purchases ->
                        purchases.forEach { purchase ->
                            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                // Update firestore & Cache
                                val currentUser = auth.currentUser
                                if (currentUser != null) {
                                    db.collection("users").document(currentUser.uid)
                                        .update("plan", ProductSkus.MONTHLY_PRO)
                                    setCachedSubscriptionStatus(true)
                                }
                            }
                        }
                    }
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun getCachedSubscriptionStatus(): Boolean {
        return prefs.getBoolean("is_pro_unlocked", false)
    }

    private fun setCachedSubscriptionStatus(isPro: Boolean) {
        prefs.edit()
            .putBoolean("is_pro_unlocked", isPro)
            .putLong("cache_time", System.currentTimeMillis())
            .apply()
    }

    fun invalidateSubscriptionCache() {
        prefs.edit().remove("cache_time").apply()
    }

    suspend fun isUserSubscribed(): Boolean {
        val lastCacheTime = prefs.getLong("cache_time", 0)
        val isPro = getCachedSubscriptionStatus()
        
        if (System.currentTimeMillis() - lastCacheTime < 5 * 60_000L) {
            return isPro
        }

        val currentUser = auth.currentUser ?: return isPro
        val result = try {
            val document = db.collection("users").document(currentUser.uid).get().await()
            if (document.exists()) {
                val plan = document.getString("plan")
                plan == "pro" || plan == ProductSkus.MONTHLY_PRO || plan == ProductSkus.YEARLY_PRO || plan == ProductSkus.LIFETIME
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.e("FreemiumManager", "Error checking user plan from Firestore", e)
            isPro // Return fallback cache on failure
        }
        
        setCachedSubscriptionStatus(result)
        return result
    }

    suspend fun provisionUserIfNeeded() {
        val currentUser = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(currentUser.uid)

        try {
            val document = userDocRef.get().await()
            if (!document.exists()) {
                Logger.d("FreemiumManager", "Provisioning new user: ${currentUser.uid}")
                val newUser = hashMapOf(
                    "email" to currentUser.email,
                    "plan" to "free",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(newUser).await()
            }
        } catch (e: Exception) {
            Logger.e("FreemiumManager", "Error provisioning user", e)
        }
    }

    data class TrialStatus(
        val isInTrial: Boolean,
        val daysRemaining: Int,
        val startDate: java.util.Date
    )

    suspend fun getTrialStatus(): TrialStatus {
        val currentUser = auth.currentUser ?: return TrialStatus(false, 0, java.util.Date())
        return try {
            val userDoc = db.collection("users").document(currentUser.uid).get().await()
            val createdAt = userDoc.getTimestamp("createdAt")?.toDate() ?: return TrialStatus(false, 0, java.util.Date())
            val daysSinceCreation = ((System.currentTimeMillis() - createdAt.time) / 86_400_000).toInt()
            val TRIAL_DAYS = 7
            
            TrialStatus(
                isInTrial = daysSinceCreation < TRIAL_DAYS,
                daysRemaining = (TRIAL_DAYS - daysSinceCreation).coerceAtLeast(0),
                startDate = createdAt
            )
        } catch (e: Exception) {
            TrialStatus(false, 0, java.util.Date())
        }
    }

    suspend fun getTasksRemaining(): Long? {
        val currentUser = auth.currentUser ?: return DAILY_TASK_LIMIT.toLong()
        if (isUserSubscribed() || getTrialStatus().isInTrial) return Long.MAX_VALUE
        
        val localCached = prefs.getLong("tasks_remaining_cache_${currentUser.uid}", -1)
        if (localCached >= 0) {
            return localCached
        }

        return try {
            val document = db.collection("users").document(currentUser.uid).get().await()
            val rem = document.getLong("tasksRemaining") ?: DAILY_TASK_LIMIT.toLong()
            prefs.edit().putLong("tasks_remaining_cache_${currentUser.uid}", rem).apply()
            rem
        } catch (e: Exception) {
            Logger.e("FreemiumManager", "Error fetching tasks remaining", e)
            DAILY_TASK_LIMIT.toLong()
        }
    }

    private suspend fun resetDailyTaskCountIfNeeded(currentUserUid: String) {
        val lastReset = prefs.getLong("last_daily_reset_$currentUserUid", 0)
        
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (lastReset < startOfToday) {
            try {
                db.collection("users").document(currentUserUid)
                    .update("tasksRemaining", DAILY_TASK_LIMIT).await()
                prefs.edit()
                    .putLong("last_daily_reset_$currentUserUid", startOfToday)
                    .putLong("tasks_remaining_cache_$currentUserUid", DAILY_TASK_LIMIT.toLong())
                    .apply()
            } catch (e: Exception) {
                Logger.e("FreemiumManager", "Error resetting daily tasks", e)
            }
        }
    }

    private fun getOfflineGraceTasksUsedToday(uid: String): Int {
        val key = "offline_grace_used_today_$uid"
        val todayString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val lastUsedDay = prefs.getString("offline_grace_day_$uid", "")
        if (lastUsedDay != todayString) {
            prefs.edit().putString("offline_grace_day_$uid", todayString).putInt(key, 0).apply()
            return 0
        }
        return prefs.getInt(key, 0)
    }
    
    private fun incrementOfflineGraceTasksUsed(uid: String) {
        val key = "offline_grace_used_today_$uid"
        val current = getOfflineGraceTasksUsedToday(uid)
        prefs.edit().putInt(key, current + 1).apply()
    }

    suspend fun canPerformTask(): Boolean {
        if (isUserSubscribed() || getTrialStatus().isInTrial) return true
        val currentUser = auth.currentUser ?: return true
        
        resetDailyTaskCountIfNeeded(currentUser.uid)

        return try {
            // Local fast cache check
            val cachedCount = prefs.getLong("tasks_remaining_cache_${currentUser.uid}", -1)
            if (cachedCount > 0) return true
            
            val document = db.collection("users").document(currentUser.uid).get().await()
            val tasksRemaining = document.getLong("tasksRemaining") ?: 0
            prefs.edit().putLong("tasks_remaining_cache_${currentUser.uid}", tasksRemaining).apply()
            tasksRemaining > 0
        } catch (e: Exception) {
            Logger.e("FreemiumManager", "Error performing task count check", e)
            // Offline Grace Limit Allowance checks
            val offlineUsed = getOfflineGraceTasksUsedToday(currentUser.uid)
            if (offlineUsed < 1) {
                incrementOfflineGraceTasksUsed(currentUser.uid)
                Logger.d("FreemiumManager", "Granted offline task grace access.")
                true
            } else {
                false
            }
        }
    }

    /**
     * Optimistic fast decrement
     */
    suspend fun decrementTaskCount(): Boolean {
        if (isUserSubscribed() || getTrialStatus().isInTrial) return true
        val currentUser = auth.currentUser ?: return false

        // Locally update optimistic numbers instantly
        val currentLocal = getTasksRemaining() ?: DAILY_TASK_LIMIT.toLong()
        val nextLocal = (currentLocal - 1).coerceAtLeast(0)
        prefs.edit().putLong("tasks_remaining_cache_${currentUser.uid}", nextLocal).apply()

        // Sync Firestore in background silently
        MyApplication.appContext.let { _ ->
            scope.launch {
                try {
                    val userDocRef = db.collection("users").document(currentUser.uid)
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(userDocRef)
                        val firestoreCount = snapshot.getLong("tasksRemaining") ?: DAILY_TASK_LIMIT.toLong()
                        val newCount = (firestoreCount - 1).coerceAtLeast(0)
                        transaction.update(userDocRef, "tasksRemaining", newCount)
                    }.await()
                    Logger.d("FreemiumManager", "Silent background update completed tasksRemaining.")
                } catch (e: Exception) {
                    Logger.e("FreemiumManager", "Failed silent background task count write sync.", e)
                }
            }
        }
        return true
    }

    /**
     * Feature gating system
     */
    suspend fun canAccessFeature(feature: ProFeatures): Boolean {
        if (isUserSubscribed()) return true
        return when (feature) {
            ProFeatures.TRIGGERS -> false
            ProFeatures.MEMORY_BOOST -> false
            ProFeatures.AGENT_MAX_STEPS -> getTrialStatus().isInTrial
            ProFeatures.VOICE_PRESETS -> getTrialStatus().isInTrial
        }
    }

    fun getFeatureLockedMessage(feature: ProFeatures): String {
        return "The feature ${feature.name.lowercase()} is exclusive to Pro. Speak 'upgrade now' to open purchase panel"
    }

    /**
     * Smart contextual messages and checks
     */
    fun shouldShowUpsell(afterSuccessfulTask: Boolean = true): Boolean {
        if (getCachedSubscriptionStatus()) return false
        val count = prefs.getInt("successful_tasks_counter", 0) + 1
        prefs.edit().putInt("successful_tasks_counter", count).apply()
        return afterSuccessfulTask && (count % 3 == 0)
    }

    fun getUpsellMessage(): String {
        return "You're getting great value out of Axel! Unlock full custom triggers and memory profiles with Aris Premium."
    }
}
