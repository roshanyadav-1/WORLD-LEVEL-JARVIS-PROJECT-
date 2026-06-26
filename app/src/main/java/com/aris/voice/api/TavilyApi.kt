package com.aris.voice.api

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONArray
import com.aris.voice.MyApplication
import com.aris.voice.BuildConfig
import com.aris.voice.utilities.NetworkConnectivityManager
import com.aris.voice.utilities.NetworkNotifier
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SearchResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Float,
    val publishedDate: String?
)

data class SearchResponse(
    val query: String,
    val results: List<SearchResult>,
    val answer: String?,
    val error: String?
)

sealed class SearchError {
    object NetworkError : SearchError()
    object RateLimited : SearchError()
    data class ApiError(val code: Int, val message: String) : SearchError()
    object InvalidQuery : SearchError()
}

enum class SearchType {
    FACTUAL,     // "Who is PM of India" → Short answer
    NEWS,        // "latest news India" → Recent articles
    HOW_TO,      // "how to make biryani" → Step by step
    OPINION,     // "best phone 2024" → Opinions/reviews
    LOCAL        // "restaurants near me" → Location-based
}

interface SearchProvider {
    suspend fun search(query: String, maxResults: Int): SearchResponse
}

class TavilyProvider(private val apiKey: String) : SearchProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String, maxResults: Int): SearchResponse {
        return withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext SearchResponse(query, emptyList(), null, "Missing Tavily API Key")
            }
            val searchParameters = JSONObject().apply {
                put("query", query)
                put("search_depth", "basic")
                put("include_answer", true)
                put("max_results", maxResults)
                put("include_raw_content", false)
                put("safe_search", true)
            }
            val request = Request.Builder()
                .url("https://api.tavily.com/search")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .post(searchParameters.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful || body == null) {
                        return@use SearchResponse(query, emptyList(), null, "API call failed with HTTP ${response.code}")
                    }
                    parseSearchResponse(query, body)
                }
            } catch (e: Exception) {
                SearchResponse(query, emptyList(), null, "Search failed: ${e.message}")
            }
        }
    }
    
    private fun parseSearchResponse(query: String, json: String): SearchResponse {
        try {
            val jsonObj = JSONObject(json)
            if (jsonObj.has("error")) {
                return SearchResponse(query, emptyList(), null, jsonObj.getString("error"))
            }

            val answer = if (jsonObj.has("answer") && !jsonObj.isNull("answer")) jsonObj.getString("answer") else null
            val resultsArr = jsonObj.optJSONArray("results") ?: JSONArray()
            
            val searchResults = mutableListOf<SearchResult>()
            for (i in 0 until resultsArr.length()) {
                val resObj = resultsArr.getJSONObject(i)
                searchResults.add(
                    SearchResult(
                        title = resObj.optString("title", ""),
                        url = resObj.optString("url", ""),
                        content = resObj.optString("content", ""),
                        score = resObj.optDouble("score", 0.0).toFloat(),
                        publishedDate = resObj.optString("published_date", null)
                    )
                )
            }
            return SearchResponse(
                query = jsonObj.optString("query", query),
                results = searchResults,
                answer = answer,
                error = null
            )
        } catch (e: Exception) {
            Log.e("TavilyApi", "Failed to parse search response", e)
            return SearchResponse(query, emptyList(), null, "Parse error: ${e.message}")
        }
    }
}

class DuckDuckGoProvider : SearchProvider {
    // Simple HTML fallback parser for DuckDuckGo
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).build()
        
    override suspend fun search(query: String, maxResults: Int): SearchResponse {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$query")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use SearchResponse(query, emptyList(), null, "No body")
                    val results = mutableListOf<SearchResult>()
                    // Basic pattern matching for extraction
                    val titleRegex = "<a class=\"result__url\" href=\"(.*?)\">(.*?)</a>".toRegex(RegexOption.IGNORE_CASE)
                    val snippetRegex = "<a class=\"result__snippet[^>]*>(.*?)</a>".toRegex(RegexOption.IGNORE_CASE)
                    
                    val titles = titleRegex.findAll(body).toList()
                    val snippets = snippetRegex.findAll(body).toList()
                    
                    for (i in 0 until Math.min(titles.size, maxResults)) {
                        results.add(SearchResult(
                            title = titles[i].groupValues[2].replace(Regex("<[^>]*>"), ""),
                            url = titles[i].groupValues[1],
                            content = snippets.getOrNull(i)?.groupValues?.get(1)?.replace(Regex("<[^>]*>"), "") ?: "",
                            score = 1.0f - (i * 0.1f),
                            publishedDate = null
                        ))
                    }
                    SearchResponse(query, results, null, null)
                }
            } catch (e: Exception) {
                SearchResponse(query, emptyList(), null, e.message)
            }
        }
    }
}

class MultiProviderSearch(private val providers: List<SearchProvider>) {
    suspend fun search(query: String, maxResults: Int): SearchResponse {
        for (provider in providers) {
            val result = provider.search(query, maxResults)
            if (result.error == null) {
                return result
            }
        }
        return SearchResponse(query, emptyList(), null, "All providers failed")
    }
}

object TavilyApi {

    private val searchCache = LruCache<String, SearchResponse>(30)
    
    // We get apiKey from BuildConfig instead of constructor
    private val apiKey: String
        get() = BuildConfig.TAVILY_API ?: ""
        
    private val multiSearch = MultiProviderSearch(listOf(
        TavilyProvider(apiKey),
        DuckDuckGoProvider()
    ))

    fun detectSearchType(query: String): SearchType {
        val lower = query.lowercase()
        return when {
            lower.startsWith("how to") || lower.startsWith("how do") || lower.startsWith("steps to") -> SearchType.HOW_TO
            lower.contains(Regex("latest|today|recent|news")) -> SearchType.NEWS
            lower.contains(Regex("near me|nearby|location")) -> SearchType.LOCAL
            lower.contains(Regex("best|top|review|recommend")) -> SearchType.OPINION
            else -> SearchType.FACTUAL
        }
    }

    fun requiresSearch(userInput: String): Boolean {
        val searchIndicators = listOf(
            "what is", "who is", "when did", "where is", "how to",
            "latest", "current", "today", "news", "price", "weather"
        )
        return searchIndicators.any { userInput.lowercase().contains(it) }
    }

    private val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())

    private val offlineKnowledge = mapOf(
        "what time is it" to { "The time is ${timeFormatter.format(Date())}" },
        "what's the time" to { "The time is ${timeFormatter.format(Date())}" },
        "today's date" to { "Today is ${dateFormatter.format(Date())}" },
        "what is the date" to { "Today is ${dateFormatter.format(Date())}" }
    )

    fun answerOffline(query: String): String? {
        val lower = query.lowercase()
        return offlineKnowledge.entries
            .find { (key, _) -> lower.contains(key) }
            ?.value?.invoke()
    }

    fun classifyError(response: Response): SearchError {
        return when (response.code) {
            429 -> SearchError.RateLimited
            400 -> SearchError.InvalidQuery
            in 500..599 -> SearchError.ApiError(response.code, "Server error")
            else -> SearchError.ApiError(response.code, "Unknown error")
        }
    }

    fun scrubPII(query: String): String {
        return query
            .replace(Regex("\\b\\d{10}\\b"), "[PHONE]")
            .replace(Regex("\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b"), "[EMAIL]")
    }
    
    fun scoreResult(result: SearchResult, query: String): Float {
        val queryWords = query.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }
        if (queryWords.isEmpty()) return result.score
        
        val matchCount = queryWords.count { word ->
            result.title.contains(word, ignoreCase = true) ||
            result.content.contains(word, ignoreCase = true)
        }
        return (matchCount.toFloat() / queryWords.size) * result.score
    }

    // Main search interface method
    suspend fun search(query: String, maxResults: Int): SearchResponse {
        val isOnline = try {
            NetworkConnectivityManager(MyApplication.appContext).isNetworkAvailable()
        } catch (e: Exception) {
            false
        }
        
        if (!isOnline) {
            try { NetworkNotifier.notifyOffline() } catch (e: Exception) {}
            return SearchResponse(query, emptyList(), null, "offline")
        }
        
        val scrubbedQuery = scrubPII(query)
        val cached = searchCache.get(scrubbedQuery)
        if (cached != null) return cached

        val response = multiSearch.search(scrubbedQuery, maxResults)
        if (response.error == null) {
            searchCache.put(scrubbedQuery, response)
        }
        return response
    }
    
    suspend fun searchCached(query: String): SearchResponse {
        return search(query, 5)
    }

    suspend fun searchAndSummarize(query: String, maxLength: Int = 1000): String {
        // Fallback for offline answers
        answerOffline(query)?.let { return it }

        val results = searchCached(query)
        if (results.error != null) return "Search failed: ${results.error}"
        
        val answer = results.answer
        if (!answer.isNullOrBlank()) {
            return answer
        }

        if (results.results.isEmpty()) {
            return "I couldn't find any relevant information for that."
        }

        return results.results.take(3).joinToString("\n\n") { result ->
            "**${result.title}**\n${result.content.take(300)}"
        }.take(maxLength)
    }
    
    fun formatSearchResults(results: List<SearchResult>): String {
        return buildString {
            appendLine("Based on my search:")
            results.take(3).forEachIndexed { i, result ->
                appendLine("${i+1}. ${result.title}")
                appendLine("   ${result.content.take(150)}")
                appendLine("   Source: ${result.url}")
            }
        }
    }
    
    fun makeVoiceFriendly(searchResult: String): String {
        return searchResult
            .replace(Regex("https?://[^\\s]+"), "")  // URLs remove
            .replace(Regex("\\[\\d+\\]"), "")         // Citation markers remove
            .replace("&amp;", "and")
            .replace("&lt;", "less than")
            .replace("&gt;", "greater than")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .trim()
    }
}

class SearchTool(private val tavilyApi: TavilyApi) {
    suspend fun searchFor(query: String): String {
        return tavilyApi.searchAndSummarize(query)
    }
    
    suspend fun findAppInPlayStore(appName: String): String {
        return searchFor("$appName Android app Google Play Store")
    }
    
    suspend fun getRecentNews(topic: String): String {
        return searchFor("$topic news today site:news.google.com OR bbc.com OR ndtv.com")
    }
}
