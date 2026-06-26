package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class YouTubeSearchIntent : AppIntent {
    override val name: String = "YouTubeSearch"

    override fun description(): String =
        "Directly open YouTube search results or play a specified query/video query, bypassing screen interaction."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "query",
            type = "string",
            required = true,
            description = "The query, song name, or channel title to search for on YouTube."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val query = params["query"]?.toString()?.trim().orEmpty()
            if (query.isEmpty()) return null
            
            val uri = Uri.parse("vnd.youtube://results?search_query=${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val pm = context.packageManager
            if (intent.resolveActivity(pm) == null) {
                // Web fallback if the YouTube app is not installed
                return Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            return intent
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://results?search_query=test"))
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
        return dummyIntent.resolveActivity(context.packageManager) != null || 
               fallbackIntent.resolveActivity(context.packageManager) != null
    }
}
