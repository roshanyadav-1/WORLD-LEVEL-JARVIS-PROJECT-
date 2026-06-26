package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class SpotifySearchIntent : AppIntent {
    override val name: String = "SpotifySearch"

    override fun description(): String =
        "Directly open Spotify and search or play a song, artist, album, or playlist."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "query",
            type = "string",
            required = true,
            description = "The track name, artist name, or playlist to search on Spotify (e.g., 'Starboy by The Weeknd')."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val query = params["query"]?.toString()?.trim().orEmpty()
            if (query.isEmpty()) return null
            
            val uri = Uri.parse("spotify:search:${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val pm = context.packageManager
            if (intent.resolveActivity(pm) == null) {
                // Web browser fallback if Spotify app is not installed
                return Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            return intent
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:test"))
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"))
        return dummyIntent.resolveActivity(context.packageManager) != null || 
               fallbackIntent.resolveActivity(context.packageManager) != null
    }
}
