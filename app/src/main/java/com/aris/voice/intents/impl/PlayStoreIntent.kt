package com.aris.voice.intents.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aris.voice.intents.AppIntent
import com.aris.voice.intents.ParameterSpec

class PlayStoreIntent : AppIntent {
    override val name: String = "PlayStore"

    override fun description(): String =
        "Directly open Google Play Store to search for an app or navigate to a target app details page."

    override fun parametersSpec(): List<ParameterSpec> = listOf(
        ParameterSpec(
            name = "query",
            type = "string",
            required = false,
            description = "The application name or category search query (e.g., 'subway surfers', 'finance')."
        ),
        ParameterSpec(
            name = "package_name",
            type = "string",
            required = false,
            description = "The specific Android application package ID to open directly (e.g., 'com.instagram.android')."
        )
    )

    override fun buildIntent(context: Context, params: Map<String, Any?>): Intent? {
        try {
            val query = params["query"]?.toString()?.trim().orEmpty()
            val packageName = params["package_name"]?.toString()?.trim().orEmpty()

            if (query.isEmpty() && packageName.isEmpty()) return null

            val uri = if (packageName.isNotEmpty()) {
                Uri.parse("market://details?id=$packageName")
            } else {
                Uri.parse("market://search?q=${Uri.encode(query)}")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.android.vending")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val pm = context.packageManager
            if (intent.resolveActivity(pm) != null) {
                return intent
            }

            // Web Browser Fallback if Google Play Store is not installed/enabled
            val webUri = if (packageName.isNotEmpty()) {
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            } else {
                Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}")
            }
            return Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            return null
        }
    }
    
    override fun isSupported(context: Context): Boolean {
        val dummyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=test")).apply {
            setPackage("com.android.vending")
        }
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com"))
        return dummyIntent.resolveActivity(context.packageManager) != null || 
               fallbackIntent.resolveActivity(context.packageManager) != null
    }
}
