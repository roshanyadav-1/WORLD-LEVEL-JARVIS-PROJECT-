package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.app.SearchManager
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class WebSearchIntent : AppIntent {
    override val name: String = "WebSearch"

    override fun description(): String =
        "Open a direct web search on Google or the system's default search engine."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "query",
            type = "string",
            required = true,
            description = "The search text or query term."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val query = params["query"]?.toString()?.trim().orEmpty()
            if (query.isEmpty()) return null
            return Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra(SearchManager.QUERY, "test") }
        return dummyIntent.resolveActivity(context.packageManager) != null
    }
}
