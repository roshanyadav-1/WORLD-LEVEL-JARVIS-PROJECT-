package com.aris.voice.utilities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import java.util.Locale

data class LauncherAppInfo(
    val packageName: String,
    val label: String,
    val normalizedLabel: String
)

sealed class AppResolutionResult {
    data class Success(val packageName: String, val appLabel: String) : AppResolutionResult()
    data class Ambiguous(val candidates: List<LauncherAppInfo>) : AppResolutionResult()
    object NotFound : AppResolutionResult()
}

class AppLauncherResolver(private val context: Context) {
    private val TAG = "AppLauncherResolver"

    private val appAliases = mapOf(
        "whatsapp" to listOf("whatsapp", "whats app", "wassup", "whatapp", "wtsp", "wassap", "watsp", "watsap", "watsapp", "washap"),
        "instagram" to listOf("instagram", "insta", "instgram", "ig", "instragram", "instagrm", "instram", "ins"),
        "youtube" to listOf("youtube", "yt", "utub", "ytube", "play music"),
        "facebook" to listOf("facebook", "fb", "fbook", "face book"),
        "chrome" to listOf("chrome", "browser", "internet", "safari", "fire fox", "firefox", "opera", "google chrome"),
        "gmail" to listOf("gmail", "mail", "email", "g mail", "inbox"),
        "spotify" to listOf("spotify", "music", "gaana", "spoti", "wynk", "jiosaavn", "saavn", "music app"),
        "phonepe" to listOf("phonepe", "phone pe", "ppe", "pephone", "phonepay"),
        "paytm" to listOf("paytm", "patm", "pay tm", "payt"),
        "gpay" to listOf("gpay", "google pay", "googlepay", "gp"),
        "maps" to listOf("maps", "navigation", "google maps", "map", "rasta", "road"),
        "settings" to listOf("settings", "setting", "phone settings", "system settings", "control panel", "panel"),
        "camera" to listOf("camera", "photo", "video camera", "cam", "snap", "selfie"),
        "calendar" to listOf("calendar", "schedule", "agenda", "reminders", "meet", "matches", "diary"),
        "dialer" to listOf("dialer", "phone", "contacts", "call", "dial", "call list", "phonebook"),
        "calculator" to listOf("calculator", "calc", "hisab", "math", "maths"),
        "files" to listOf("files", "file manager", "manager", "documents", "storage", "downloads"),
        "telegram" to listOf("telegram", "tg", "tele", "telegrame"),
        "twitter" to listOf("twitter", "x", "twt", "twtr"),
        "playstore" to listOf("playstore", "play store", "app store", "market", "google play")
    )

    fun getLaunchableApps(): List<LauncherAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val appsList = mutableListOf<LauncherAppInfo>()
        for (resolveInfo in resolveInfos) {
            val label = resolveInfo.loadLabel(pm).toString()
            val packageName = resolveInfo.activityInfo.packageName
            appsList.add(
                LauncherAppInfo(
                    packageName = packageName,
                    label = label,
                    normalizedLabel = normalize(label)
                )
            )
        }
        return appsList
    }

    fun normalize(str: String): String {
        return str.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), "") // remove punctuation
            .replace("app", "")
            .replace("application", "")
            .trim()
    }

    fun resolveApp(query: String): AppResolutionResult {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) {
            return AppResolutionResult.NotFound
        }

        val launchableApps = getLaunchableApps()
        if (launchableApps.isEmpty()) {
            Log.e(TAG, "No launchable apps found on device.")
            return AppResolutionResult.NotFound
        }

        // 1. Exact Label Match (Case-insensitive & Normalized)
        val exactMatches = launchableApps.filter { it.normalizedLabel == normalizedQuery }
        if (exactMatches.size == 1) {
            return AppResolutionResult.Success(exactMatches[0].packageName, exactMatches[0].label)
        } else if (exactMatches.size > 1) {
            return AppResolutionResult.Ambiguous(exactMatches)
        }

        // 2. Alias Mapping match
        val matchedKeys = mutableSetOf<String>()
        for ((key, aliases) in appAliases) {
            if (key == normalizedQuery || aliases.any { it == normalizedQuery || normalizedQuery.contains(it) || it.contains(normalizedQuery) }) {
                matchedKeys.add(key)
            }
        }

        // Filter apps that match any of our found canonical keys or their aliases
        val aliasMatchedApps = launchableApps.filter { app ->
            matchedKeys.any { key ->
                app.normalizedLabel.contains(key) || key.contains(app.normalizedLabel) ||
                appAliases[key]?.any { alias -> app.normalizedLabel.contains(alias) || alias.contains(app.normalizedLabel) } == true
            }
        }

        if (aliasMatchedApps.size == 1) {
            return AppResolutionResult.Success(aliasMatchedApps[0].packageName, aliasMatchedApps[0].label)
        } else if (aliasMatchedApps.size > 1) {
            return AppResolutionResult.Ambiguous(aliasMatchedApps)
        }

        // 3. Partial Word / Sub-string Matching (App label contains Query or Query contains App label)
        val partialMatches = launchableApps.filter {
            it.normalizedLabel.contains(normalizedQuery) || normalizedQuery.contains(it.normalizedLabel)
        }
        if (partialMatches.size == 1) {
            return AppResolutionResult.Success(partialMatches[0].packageName, partialMatches[0].label)
        } else if (partialMatches.size > 1) {
            val sorted = partialMatches.sortedBy { it.normalizedLabel.length }
            if (sorted[0].normalizedLabel == normalizedQuery || sorted[1].normalizedLabel != sorted[0].normalizedLabel) {
                return AppResolutionResult.Success(sorted[0].packageName, sorted[0].label)
            }
            return AppResolutionResult.Ambiguous(partialMatches)
        }

        // 4. Fuzzy Levenshtein Distance Match (Fuzzy threshold depends on length)
        val fuzzyMatches = mutableListOf<Pair<LauncherAppInfo, Int>>()
        for (app in launchableApps) {
            val dist = levenshtein(normalizedQuery, app.normalizedLabel)
            val threshold = when {
                normalizedQuery.length <= 4 -> 1
                normalizedQuery.length <= 8 -> 2
                else -> 3
            }
            if (dist <= threshold) {
                fuzzyMatches.add(app to dist)
            }
        }

        if (fuzzyMatches.isNotEmpty()) {
            val bestFuzzy = fuzzyMatches.minByOrNull { it.second }!!
            val tied = fuzzyMatches.filter { it.second == bestFuzzy.second }
            if (tied.size == 1) {
                return AppResolutionResult.Success(tied[0].first.packageName, tied[0].first.label)
            } else {
                return AppResolutionResult.Ambiguous(tied.map { it.first })
            }
        }

        return AppResolutionResult.NotFound
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) {
                    prev
                } else {
                    minOf(dp[j - 1], dp[j], prev) + 1
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }
}
